package com.example.smartcanteen.assistant.application;

import com.example.smartcanteen.agent.application.AgentExecutionService;
import com.example.smartcanteen.agent.application.AgentRuntime;
import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.domain.RunStatus;
import com.example.smartcanteen.agent.domain.SkillDefinition;
import com.example.smartcanteen.agent.domain.StartRunCommand;
import com.example.smartcanteen.agent.port.SkillRegistry;
import com.example.smartcanteen.application.BusinessAuthorizationPolicy;
import com.example.smartcanteen.assistant.domain.AssistantConversation;
import com.example.smartcanteen.assistant.domain.AssistantResolution;
import com.example.smartcanteen.assistant.domain.AssistantTurn;
import com.example.smartcanteen.assistant.port.AssistantConversationStore;
import com.example.smartcanteen.security.AuthPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deep application module for one assistant message. It owns conversation idempotency and
 * delegates business truth and execution to the existing authorization and Agent Runtime seams.
 */
@Service
public class AssistantConversationService {

    private final AssistantIntentResolver resolver;
    private final AssistantConversationStore conversations;
    private final AgentRuntime runtime;
    private final AgentExecutionService execution;
    private final SkillRegistry skills;
    private final BusinessAuthorizationPolicy policy;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public AssistantConversationService(
            AssistantIntentResolver resolver,
            AssistantConversationStore conversations,
            AgentRuntime runtime,
            AgentExecutionService execution,
            SkillRegistry skills,
            BusinessAuthorizationPolicy policy,
            ObjectMapper objectMapper) {
        this(resolver, conversations, runtime, execution, skills, policy, objectMapper, Clock.systemUTC());
    }

    public AssistantConversationService(
            AssistantIntentResolver resolver,
            AssistantConversationStore conversations,
            AgentRuntime runtime,
            AgentExecutionService execution,
            SkillRegistry skills,
            BusinessAuthorizationPolicy policy,
            ObjectMapper objectMapper,
            Clock clock) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.conversations = Objects.requireNonNull(conversations, "conversations");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.execution = Objects.requireNonNull(execution, "execution");
        this.skills = Objects.requireNonNull(skills, "skills");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional
    public AssistantTurn handle(
            String conversationId,
            String message,
            String idempotencyKey,
            ExecutionContext context,
            AuthPrincipal principal) {
        String normalizedConversationId = requireText("conversationId", conversationId, 64);
        String normalizedMessage = requireText("message", message, 2000).trim();
        String normalizedKey = requireText("idempotencyKey", idempotencyKey, 128);
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(principal, "principal");

        AssistantConversation conversation = conversations.ensureConversation(
                normalizedConversationId, context, clock.instant());
        String requestHash = digest(normalizedMessage);
        Optional<AssistantConversationStore.StoredTurn> previous = conversations.findByIdempotency(
                conversation.conversationId(), context.actorUserId(), normalizedKey);
        if (previous.isPresent()) {
            AssistantConversationStore.StoredTurn stored = previous.get();
            if (!stored.requestHash().equals(requestHash)) {
                throw new IllegalStateException(
                        "Idempotency key was already used for a different assistant message");
            }
            return parseTurn(stored.responseJson());
        }

        AssistantResolution resolution = resolver.resolve(normalizedMessage);
        AssistantTurn response = switch (resolution.type()) {
            case CLARIFICATION -> newTurn(
                    conversation,
                    conversations.nextSequence(conversation.conversationId()),
                    "CLARIFICATION",
                    resolution.message(),
                    null,
                    null,
                    null,
                    null,
                    resolution.missingFields());
            case UNSUPPORTED -> newTurn(
                    conversation,
                    conversations.nextSequence(conversation.conversationId()),
                    "UNSUPPORTED",
                    resolution.message(),
                    null,
                    null,
                    null,
                    null,
                    List.of());
            case TRACEABILITY_QUERY -> executeTraceability(
                    conversation,
                    resolution,
                    normalizedKey,
                    context,
                    principal);
        };

        AssistantConversationStore.StoredTurn stored = new AssistantConversationStore.StoredTurn(
                response.turnId(),
                conversation.conversationId(),
                response.sequence(),
                normalizedKey,
                requestHash,
                normalizedMessage,
                responseJson(response),
                response.kind(),
                response.intent(),
                response.runId(),
                response.runStatus(),
                response.createdAt());
        try {
            conversations.append(stored);
        } catch (DuplicateKeyException duplicate) {
            AssistantConversationStore.StoredTurn concurrent = conversations.findByIdempotency(
                            conversation.conversationId(), context.actorUserId(), normalizedKey)
                    .orElseThrow(() -> duplicate);
            if (!concurrent.requestHash().equals(requestHash)) {
                throw new IllegalStateException(
                        "Idempotency key was already used for a different assistant message",
                        duplicate);
            }
            return parseTurn(concurrent.responseJson());
        }
        return response;
    }

    private AssistantTurn executeTraceability(
            AssistantConversation conversation,
            AssistantResolution resolution,
            String idempotencyKey,
            ExecutionContext context,
            AuthPrincipal principal) {
        SkillDefinition skill = skills.findByIntent(resolution.intent())
                .filter(SkillDefinition::isAvailable)
                .orElseThrow(() -> new IllegalStateException(
                        "No active Skill is registered for intent: " + resolution.intent()));
        policy.requireSkillAccess(principal, skill);
        policy.requireIntentAccess(context, resolution.intent());

        String inputJson;
        try {
            inputJson = objectMapper.writeValueAsString(Map.of("traceCode", resolution.traceCode()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize assistant tool input", exception);
        }
        String runIdempotencyKey = "assistant-run-"
                + digest(conversation.conversationId() + ":" + idempotencyKey).substring(0, 48);
        AgentRun run = runtime.start(
                new StartRunCommand(
                        context.requestId(),
                        resolution.intent(),
                        inputJson,
                        runIdempotencyKey),
                context);
        if (run.status() == RunStatus.PLANNED) {
            run = execution.execute(run, context);
        }
        JsonNode result = parseNullable(run.resultJson());
        String message = assistantMessage(run, result, resolution.traceCode());
        return newTurn(
                conversation,
                conversations.nextSequence(conversation.conversationId()),
                "RESULT",
                message,
                resolution.intent(),
                run.runId(),
                run.status().name(),
                result,
                List.of());
    }

    private AssistantTurn newTurn(
            AssistantConversation conversation,
            long sequence,
            String kind,
            String message,
            String intent,
            String runId,
            String runStatus,
            JsonNode result,
            List<String> missingFields) {
        return new AssistantTurn(
                conversation.conversationId(),
                "TURN-" + UUID.randomUUID(),
                sequence,
                kind,
                message,
                intent,
                runId,
                runStatus,
                result,
                missingFields,
                clock.instant());
    }

    private static String assistantMessage(AgentRun run, JsonNode result, String traceCode) {
        if (!"SUCCEEDED".equals(run.status().name())) {
            return "溯源查询未完成，请查看运行状态后重试或人工处理。";
        }
        String ingredient = textOr(result, "ingredientName", textOr(result, "ingredientId", "未知食材"));
        String batch = textOr(result, "batchId", "未知批次");
        String supplier = textOr(result, "supplierName", textOr(result, "supplierId", "未知供应商"));
        return "已完成溯源查询：" + traceCode + " 对应食材「" + ingredient
                + "」、批次「" + batch + "」、供应商「" + supplier + "」。";
    }

    private static String textOr(JsonNode node, String field, String fallback) {
        if (node == null) {
            return fallback;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? fallback : value.asText();
    }

    private AssistantTurn parseTurn(String responseJson) {
        try {
            return objectMapper.readValue(responseJson, AssistantTurn.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored assistant response is invalid", exception);
        }
    }

    private String responseJson(AssistantTurn response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize assistant response", exception);
        }
    }

    private static String requireText(String name, String value, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must be non-blank and at most "
                    + maxLength + " characters");
        }
        return value;
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private JsonNode parseNullable(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            return objectMapper.getNodeFactory().textNode(json);
        }
    }
}
