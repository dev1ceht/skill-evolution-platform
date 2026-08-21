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
import com.example.smartcanteen.application.DailyMenuService;
import com.example.smartcanteen.application.AssistantRolloutPolicy;
import com.example.smartcanteen.assistant.domain.AssistantConversation;
import com.example.smartcanteen.assistant.domain.AssistantConversationHistory;
import com.example.smartcanteen.assistant.domain.AssistantClarification;
import com.example.smartcanteen.assistant.domain.AssistantPendingAction;
import com.example.smartcanteen.assistant.domain.AssistantResolution;
import com.example.smartcanteen.assistant.domain.AssistantTurn;
import com.example.smartcanteen.domain.DailyMenu;
import com.example.smartcanteen.assistant.port.AssistantConversationStore;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.ForbiddenException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
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
    private final DailyMenuService menus;
    private final AssistantRolloutPolicy rollout;
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
            DailyMenuService menus,
            ObjectMapper objectMapper,
            AssistantRolloutPolicy rollout) {
        this(
                resolver,
                conversations,
                runtime,
                execution,
                skills,
                policy,
                menus,
                objectMapper,
                Clock.systemUTC(),
                rollout);
    }

    /** Compatibility constructor for focused tests; production wiring uses the rollout bean. */
    public AssistantConversationService(
            AssistantIntentResolver resolver,
            AssistantConversationStore conversations,
            AgentRuntime runtime,
            AgentExecutionService execution,
            SkillRegistry skills,
            BusinessAuthorizationPolicy policy,
            DailyMenuService menus,
            ObjectMapper objectMapper) {
        this(
                resolver,
                conversations,
                runtime,
                execution,
                skills,
                policy,
                menus,
                objectMapper,
                Clock.systemUTC(),
                new AssistantRolloutPolicy(true, "*"));
    }

    public AssistantConversationService(
            AssistantIntentResolver resolver,
            AssistantConversationStore conversations,
            AgentRuntime runtime,
            AgentExecutionService execution,
            SkillRegistry skills,
            BusinessAuthorizationPolicy policy,
            DailyMenuService menus,
            ObjectMapper objectMapper,
            Clock clock) {
        this(
                resolver,
                conversations,
                runtime,
                execution,
                skills,
                policy,
                menus,
                objectMapper,
                clock,
                new AssistantRolloutPolicy(true, "*"));
    }

    public AssistantConversationService(
            AssistantIntentResolver resolver,
            AssistantConversationStore conversations,
            AgentRuntime runtime,
            AgentExecutionService execution,
            SkillRegistry skills,
            BusinessAuthorizationPolicy policy,
            DailyMenuService menus,
            ObjectMapper objectMapper,
            Clock clock,
            AssistantRolloutPolicy rollout) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.conversations = Objects.requireNonNull(conversations, "conversations");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.execution = Objects.requireNonNull(execution, "execution");
        this.skills = Objects.requireNonNull(skills, "skills");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.rollout = Objects.requireNonNull(rollout, "rollout");
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

        conversations.lockConversation(conversation.conversationId());
        Optional<AssistantClarification> pendingClarification = conversations.findClarification(
                conversation.conversationId());
        PendingActionState pendingActionState = reconcilePendingAction(
                conversation,
                conversations.findPendingAction(conversation.conversationId()));
        Optional<AssistantPendingAction> pendingAction = pendingActionState.activeAction();
        Optional<AssistantPendingAction> resolutionPendingAction = pendingActionState.wasReconciled()
                ? pendingActionState.staleAction()
                : pendingAction;
        AssistantResolution resolution = resolver.resolve(
                normalizedMessage, pendingClarification, resolutionPendingAction, context);
        AssistantTurn response;
        if (pendingActionState.wasReconciled()
                && isPendingActionDecision(resolution)) {
            response = pendingActionReconciled(
                    conversation,
                    pendingActionState.staleAction().orElseThrow(),
                    pendingActionState.currentRun());
        } else if (pendingAction.isPresent() && requiresPendingActionGuard(resolution)) {
            response = pendingActionReminder(
                    conversation,
                    pendingAction.get());
        } else {
            response = switch (resolution.type()) {
                case CLARIFICATION -> newTurn(
                        conversation,
                        conversations.nextSequence(conversation.conversationId()),
                        "CLARIFICATION",
                        resolution.message(),
                        resolution.intent(),
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
                case MENU_QUERY -> executeMenu(
                        conversation,
                        resolution,
                        normalizedKey,
                        context,
                        principal);
                case INVENTORY_QUERY -> executeInventory(
                        conversation,
                        resolution,
                        normalizedKey,
                        context,
                        principal);
                case MENU_PUBLISH_REQUEST -> previewMenuPublish(
                        conversation,
                        resolution,
                        normalizedKey,
                        context,
                        principal);
                case WRITE_REQUEST -> previewWrite(
                        conversation,
                        resolution,
                        normalizedKey,
                        context,
                        principal);
                case CONFIRM_PENDING_ACTION -> confirmPendingAction(
                        conversation,
                        resolution,
                        pendingAction.orElseThrow(() -> new IllegalStateException(
                                "No pending action is available for confirmation")),
                        normalizedKey,
                        context);
                case CANCEL_PENDING_ACTION -> cancelPendingAction(
                        conversation,
                        resolution,
                        pendingAction.orElseThrow(() -> new IllegalStateException(
                                "No pending action is available for cancellation")),
                        normalizedKey,
                        context);
            };
        }

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
        updateConversationState(
                conversation,
                normalizedMessage,
                resolution,
                pendingClarification,
                pendingAction,
                response);
        return response;
    }

    private void updateConversationState(
            AssistantConversation conversation,
            String normalizedMessage,
            AssistantResolution resolution,
            Optional<AssistantClarification> previousClarification,
            Optional<AssistantPendingAction> previousAction,
            AssistantTurn response) {
        if (previousAction.isPresent() && requiresPendingActionGuard(resolution)) {
            conversations.clearClarification(conversation.conversationId());
            conversations.updateStatus(
                    conversation.conversationId(), "WAITING_CONFIRMATION", clock.instant());
            return;
        }
        if (resolution.type() == AssistantResolution.Type.CLARIFICATION
                && resolution.intent() != null
                && !resolution.missingFields().isEmpty()) {
            Optional<AssistantClarification> sameIntent = previousClarification.filter(
                    item -> item.intent().equals(resolution.intent()));
            String originalMessage = sameIntent
                    .map(AssistantClarification::originalMessage)
                    .orElse(normalizedMessage);
            Instant now = clock.instant();
            conversations.saveClarification(new AssistantClarification(
                    conversation.conversationId(),
                    resolution.intent(),
                    originalMessage,
                    resolution.missingFields(),
                    sameIntent.map(AssistantClarification::createdAt).orElse(now),
                    now));
            conversations.updateStatus(
                    conversation.conversationId(), "WAITING_CLARIFICATION", now);
            conversations.clearPendingAction(conversation.conversationId());
            return;
        }
        if (resolution.type() == AssistantResolution.Type.MENU_PUBLISH_REQUEST
                || resolution.type() == AssistantResolution.Type.WRITE_REQUEST) {
            AgentRun run = runtime.find(response.runId()).orElseThrow(() ->
                    new IllegalStateException("Preview Agent Run was not persisted"));
            JsonNode input = parseNullable(run.inputJson());
            long resourceVersion = resolution.type() == AssistantResolution.Type.MENU_PUBLISH_REQUEST
                    ? input == null ? -1 : input.path("menuVersion").asLong(-1)
                    : 0;
            if (resourceVersion < 0) {
                throw new IllegalStateException("Preview Agent Run is missing menuVersion");
            }
            Instant now = clock.instant();
            Optional<AssistantPendingAction> sameAction = previousAction.filter(
                    item -> item.intent().equals(resolution.intent()));
            conversations.clearClarification(conversation.conversationId());
            conversations.savePendingAction(new AssistantPendingAction(
                    conversation.conversationId(),
                    resolution.intent(),
                    run.runId(),
                    run.version(),
                    pendingResourceId(resolution),
                    resourceVersion,
                    run.planHash(),
                    sameAction.map(AssistantPendingAction::createdAt).orElse(now),
                    now));
            conversations.updateStatus(
                    conversation.conversationId(), "WAITING_CONFIRMATION", now);
            return;
        }
        Instant now = clock.instant();
        conversations.clearClarification(conversation.conversationId());
        conversations.clearPendingAction(conversation.conversationId());
        conversations.updateStatus(conversation.conversationId(), "ACTIVE", now);
    }

    @Transactional(readOnly = true)
    public AssistantConversationHistory history(
            String conversationId,
            ExecutionContext context,
            int limit) {
        String normalizedConversationId = requireText("conversationId", conversationId, 64);
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        Objects.requireNonNull(context, "context");
        Optional<AssistantConversation> existing = conversations.findConversation(
                normalizedConversationId);
        if (existing.isEmpty()) {
            return AssistantConversationHistory.empty(normalizedConversationId);
        }
        AssistantConversation conversation = existing.get();
        if (!conversation.actorUserId().equals(context.actorUserId())
                || !conversation.scope().equals(context.scope())) {
            throw new ForbiddenException("Conversation is outside the requested scope");
        }
        List<AssistantConversationHistory.Entry> entries = conversations.listTurns(
                        normalizedConversationId, limit)
                .stream()
                .map(stored -> new AssistantConversationHistory.Entry(
                        stored.message(), parseTurn(stored.responseJson())))
                .toList();
        return new AssistantConversationHistory(
                conversation.conversationId(),
                conversation.status(),
                conversation.createdAt(),
                conversation.updatedAt(),
                entries);
    }

    private AssistantTurn previewMenuPublish(
            AssistantConversation conversation,
            AssistantResolution resolution,
            String idempotencyKey,
            ExecutionContext context,
            AuthPrincipal principal) {
        DailyMenu menu = menus.get(context.scope(), resolution.menuId());
        SkillDefinition skill = skills.findByIntent(resolution.intent())
                .filter(SkillDefinition::isAvailable)
                .orElseThrow(() -> new IllegalStateException(
                        "No active Skill is registered for intent: " + resolution.intent()));
        policy.requireSkillAccess(principal, skill);
        policy.requireIntentAccess(context, resolution.intent());

        String inputJson = writeJson(Map.of(
                "menuId", menu.id(),
                "menuVersion", menu.version()));
        String runIdempotencyKey = "assistant-run-"
                + digest(conversation.conversationId() + ":" + idempotencyKey).substring(0, 48);
        AgentRun run = runtime.start(
                new StartRunCommand(
                        context.requestId(),
                        resolution.intent(),
                        inputJson,
                        runIdempotencyKey),
                context);
        if (run.status() != RunStatus.WAITING_CONFIRMATION) {
            throw new IllegalStateException(
                    "Menu publish preview must wait for explicit confirmation");
        }
        return newTurn(
                conversation,
                conversations.nextSequence(conversation.conversationId()),
                "CONFIRMATION_REQUIRED",
                "已生成菜单发布计划：" + menu.id() + "，版本 " + menu.version()
                        + "，当前状态「" + menu.status() + "」。回复“确认发布”执行，回复“取消”放弃。",
                resolution.intent(),
                run.runId(),
                run.status().name(),
                parseNullable(run.planJson()),
                List.of());
    }

    private AssistantTurn previewWrite(
            AssistantConversation conversation,
            AssistantResolution resolution,
            String idempotencyKey,
            ExecutionContext context,
            AuthPrincipal principal) {
        rollout.requireBusinessWrites();
        SkillDefinition skill = skills.findByIntent(resolution.intent())
                .filter(SkillDefinition::isAvailable)
                .orElseThrow(() -> new IllegalStateException(
                        "No active Skill is registered for intent: " + resolution.intent()));
        policy.requireSkillAccess(principal, skill);
        policy.requireIntentAccess(context, resolution.intent());

        Map<String, String> input = new LinkedHashMap<>(resolution.parameters());
        input.put("businessIdempotencyKey", idempotencyKey);
        String inputJson = writeJson(input);
        String runIdempotencyKey = "assistant-run-"
                + digest(conversation.conversationId() + ":" + idempotencyKey).substring(0, 48);
        AgentRun run = runtime.start(
                new StartRunCommand(
                        context.requestId(),
                        resolution.intent(),
                        inputJson,
                        runIdempotencyKey),
                context);
        if (run.status() != RunStatus.WAITING_CONFIRMATION) {
            throw new IllegalStateException("Business write preview must wait for explicit confirmation");
        }
        return newTurn(
                conversation,
                conversations.nextSequence(conversation.conversationId()),
                "CONFIRMATION_REQUIRED",
                resolution.message() + "回复“确认”执行，回复“取消”放弃。",
                resolution.intent(),
                run.runId(),
                run.status().name(),
                parseNullable(run.planJson()),
                List.of());
    }

    private AssistantTurn confirmPendingAction(
            AssistantConversation conversation,
            AssistantResolution resolution,
            AssistantPendingAction pending,
            String idempotencyKey,
            ExecutionContext context) {
        if (!"menu.publish".equals(pending.intent())) {
            rollout.requireBusinessWrites();
        }
        requirePendingActionMatches(resolution, pending);
        AgentRun current = runtime.find(pending.runId()).orElseThrow(() ->
                new IllegalStateException("Pending business write Run no longer exists"));
        if (current.version() != pending.runVersion()
                || !current.planHash().equals(pending.planHash())) {
            throw new IllegalStateException("Pending business write plan is stale");
        }
        AgentRun confirmed = runtime.decide(
                pending.runId(),
                pending.runVersion(),
                "RUN_CONFIRM",
                null,
                decisionIdempotencyKey(conversation.conversationId(), idempotencyKey),
                context);
        AgentRun completed = confirmed.status() == RunStatus.PLANNED
                ? execution.execute(confirmed, context)
                : confirmed;
        return newTurn(
                conversation,
                conversations.nextSequence(conversation.conversationId()),
                "RESULT",
                actionResultMessage(completed, pending),
                resolution.intent(),
                completed.runId(),
                completed.status().name(),
                parseNullable(completed.resultJson()),
                List.of());
    }

    private AssistantTurn cancelPendingAction(
            AssistantConversation conversation,
            AssistantResolution resolution,
            AssistantPendingAction pending,
            String idempotencyKey,
            ExecutionContext context) {
        requirePendingActionMatches(resolution, pending);
        AgentRun current = runtime.find(pending.runId()).orElseThrow(() ->
                new IllegalStateException("Pending business write Run no longer exists"));
        if (current.version() != pending.runVersion()
                || !current.planHash().equals(pending.planHash())) {
            throw new IllegalStateException("Pending business write plan is stale");
        }
        AgentRun cancelled = runtime.decide(
                pending.runId(),
                pending.runVersion(),
                "RUN_CANCEL",
                null,
                decisionIdempotencyKey(conversation.conversationId(), idempotencyKey),
                context);
        return newTurn(
                conversation,
                conversations.nextSequence(conversation.conversationId()),
                "RESULT",
                "已取消待处理写入计划：" + pendingLabel(pending) + "。",
                resolution.intent(),
                cancelled.runId(),
                cancelled.status().name(),
                null,
                List.of());
    }

    private AssistantTurn pendingActionReminder(
            AssistantConversation conversation,
            AssistantPendingAction pending) {
        AgentRun run = runtime.find(pending.runId()).orElseThrow(() ->
                new IllegalStateException("Pending business write Run no longer exists"));
        return newTurn(
                conversation,
                conversations.nextSequence(conversation.conversationId()),
                "CONFIRMATION_REQUIRED",
                "当前仍有待确认的写入计划：" + pendingLabel(pending) + "。请回复“确认”执行，或回复“取消”放弃。",
                pending.intent(),
                run.runId(),
                run.status().name(),
                parseNullable(run.planJson()),
                List.of());
    }

    private AssistantTurn pendingActionReconciled(
            AssistantConversation conversation,
            AssistantPendingAction pending,
            Optional<AgentRun> currentRun) {
        String status = currentRun.map(run -> run.status().name()).orElse("UNKNOWN");
        String message = currentRun.isPresent()
                ? "待确认的写入计划已由其他入口处理，当前 Agent Run 状态为「"
                        + status + "」，助手待处理状态已同步清理。"
                : "待确认的业务写入 Run " + pending.runId()
                        + " 已不存在，助手待处理状态已同步清理。";
        return newTurn(
                conversation,
                conversations.nextSequence(conversation.conversationId()),
                "RESULT",
                message,
                pending.intent(),
                pending.runId(),
                status,
                currentRun.map(run -> parseNullable(run.resultJson())).orElse(null),
                List.of());
    }

    private PendingActionState reconcilePendingAction(
            AssistantConversation conversation,
            Optional<AssistantPendingAction> pendingAction) {
        if (pendingAction.isEmpty()) {
            return new PendingActionState(Optional.empty(), Optional.empty(), Optional.empty());
        }
        AssistantPendingAction pending = pendingAction.get();
        Optional<AgentRun> current = runtime.find(pending.runId());
        boolean valid = current.isPresent()
                && current.get().status() == RunStatus.WAITING_CONFIRMATION
                && current.get().version() == pending.runVersion()
                && current.get().planHash().equals(pending.planHash());
        if (valid) {
            return new PendingActionState(Optional.of(pending), Optional.empty(), current);
        }
        conversations.clearPendingAction(conversation.conversationId());
        conversations.clearClarification(conversation.conversationId());
        return new PendingActionState(Optional.empty(), Optional.of(pending), current);
    }

    private static boolean requiresPendingActionGuard(AssistantResolution resolution) {
        return switch (resolution.type()) {
            case CONFIRM_PENDING_ACTION, CANCEL_PENDING_ACTION -> false;
            default -> true;
        };
    }

    private static boolean isPendingActionDecision(AssistantResolution resolution) {
        return resolution.type() == AssistantResolution.Type.CONFIRM_PENDING_ACTION
                || resolution.type() == AssistantResolution.Type.CANCEL_PENDING_ACTION;
    }

    private static void requirePendingActionMatches(
            AssistantResolution resolution, AssistantPendingAction pending) {
        if (!pending.intent().equals(resolution.intent())) {
            throw new IllegalStateException("Pending action intent does not match confirmation");
        }
    }

    private static String decisionIdempotencyKey(String conversationId, String messageKey) {
        return "assistant-decision-" + digest(conversationId + ":" + messageKey).substring(0, 48);
    }

    private record PendingActionState(
            Optional<AssistantPendingAction> activeAction,
            Optional<AssistantPendingAction> staleAction,
            Optional<AgentRun> currentRun) {

        private PendingActionState {
            activeAction = activeAction == null ? Optional.empty() : activeAction;
            staleAction = staleAction == null ? Optional.empty() : staleAction;
            currentRun = currentRun == null ? Optional.empty() : currentRun;
        }

        private boolean wasReconciled() {
            return staleAction.isPresent();
        }
    }

    private static String actionResultMessage(AgentRun run, AssistantPendingAction pending) {
        if (run.status() == RunStatus.SUCCEEDED) {
            return "已完成写入操作：" + pendingLabel(pending) + "。";
        }
        if (run.status() == RunStatus.RECONCILIATION_REQUIRED) {
            return "写入结果未知，需要人工对账：" + pendingLabel(pending) + "。";
        }
        return "写入操作未完成，请查看运行状态后重试或人工处理：" + pendingLabel(pending) + "。";
    }

    private static String pendingResourceId(AssistantResolution resolution) {
        if (resolution.type() == AssistantResolution.Type.MENU_PUBLISH_REQUEST) {
            return resolution.menuId();
        }
        return switch (resolution.intent()) {
            case "procurement.order.create" -> resolution.parameters().getOrDefault(
                    "supplierId", "采购订单");
            case "procurement.order.receive" -> resolution.parameters().getOrDefault(
                    "orderId", "采购收货");
            case "inventory.receive" -> resolution.parameters().getOrDefault(
                    "materialId", "库存入库");
            case "inventory.stock-out" -> resolution.parameters().getOrDefault(
                    "ingredientId", "库存出库");
            case "alert.dispose" -> resolution.parameters().getOrDefault(
                    "warnId", "预警处置");
            default -> "采购计划";
        };
    }

    private static String pendingLabel(AssistantPendingAction pending) {
        if ("menu.publish".equals(pending.intent())) {
            return "菜单发布计划 " + pending.resourceId()
                    + "（版本 " + pending.resourceVersion() + "）";
        }
        return pending.intent() + " / " + pending.resourceId();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize assistant action input", exception);
        }
    }

    private AssistantTurn executeTraceability(
            AssistantConversation conversation,
            AssistantResolution resolution,
            String idempotencyKey,
            ExecutionContext context,
            AuthPrincipal principal) {
        return executeReadOnly(
                conversation,
                resolution.intent(),
                Map.of("traceCode", resolution.traceCode()),
                idempotencyKey,
                context,
                principal,
                (run, result) -> assistantMessage(run, result, resolution.traceCode()));
    }

    private AssistantTurn executeMenu(
            AssistantConversation conversation,
            AssistantResolution resolution,
            String idempotencyKey,
            ExecutionContext context,
            AuthPrincipal principal) {
        Map<String, Object> input = new LinkedHashMap<>(resolution.parameters());
        if (resolution.menuId() != null) {
            input.put("menuId", resolution.menuId());
        }
        return executeReadOnly(
                conversation,
                resolution.intent(),
                input,
                idempotencyKey,
                context,
                principal,
                (run, result) -> assistantMenuMessage(run, result, resolution));
    }

    private AssistantTurn executeInventory(
            AssistantConversation conversation,
            AssistantResolution resolution,
            String idempotencyKey,
            ExecutionContext context,
            AuthPrincipal principal) {
        Map<String, Object> input = new LinkedHashMap<>();
        resolution.parameters().forEach((key, value) -> {
            if ("warningOnly".equals(key)) {
                input.put(key, Boolean.parseBoolean(value));
            } else {
                input.put(key, value);
            }
        });
        return executeReadOnly(
                conversation,
                resolution.intent(),
                input,
                idempotencyKey,
                context,
                principal,
                AssistantConversationService::assistantInventoryMessage);
    }

    private AssistantTurn executeReadOnly(
            AssistantConversation conversation,
            String intent,
            Map<String, Object> input,
            String idempotencyKey,
            ExecutionContext context,
            AuthPrincipal principal,
            java.util.function.BiFunction<AgentRun, JsonNode, String> messageFactory) {
        SkillDefinition skill = skills.findByIntent(intent)
                .filter(SkillDefinition::isAvailable)
                .orElseThrow(() -> new IllegalStateException(
                        "No active Skill is registered for intent: " + intent));
        policy.requireSkillAccess(principal, skill);
        policy.requireIntentAccess(context, intent);

        String inputJson;
        try {
            inputJson = objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize assistant tool input", exception);
        }
        String runIdempotencyKey = "assistant-run-"
                + digest(conversation.conversationId() + ":" + idempotencyKey).substring(0, 48);
        AgentRun run = runtime.start(
                new StartRunCommand(
                        context.requestId(),
                        intent,
                        inputJson,
                        runIdempotencyKey),
                context);
        if (run.status() == RunStatus.PLANNED) {
            run = execution.execute(run, context);
        }
        JsonNode result = parseNullable(run.resultJson());
        return newTurn(
                conversation,
                conversations.nextSequence(conversation.conversationId()),
                "RESULT",
                messageFactory.apply(run, result),
                intent,
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

    private static String assistantMenuMessage(
            AgentRun run, JsonNode result, AssistantResolution resolution) {
        if (!"SUCCEEDED".equals(run.status().name())) {
            return "菜单查询未完成，请查看运行状态后重试或人工处理。";
        }
        if (result != null && result.path("records").isArray()) {
            String date = resolution.parameters().getOrDefault("menuDate", "未知日期");
            String mealTime = resolution.parameters().getOrDefault("mealTime", "全部餐次");
            long total = result.path("total").canConvertToLong()
                    ? result.path("total").asLong()
                    : result.path("records").size();
            return "已完成菜单查询：日期「" + date + "」、餐次「" + mealTime
                    + "」，共 " + total + " 个已发布菜单。";
        }
        String menuId = resolution.menuId() == null ? "未知菜单" : resolution.menuId();
        String date = textOr(result, "menuDate", "未知日期");
        String mealTime = textOr(result, "mealTime", "未知餐次");
        String status = textOr(result, "status", "未知状态");
        String version = textOr(result, "version", "未知版本");
        int itemCount = result != null && result.path("items").isArray()
                ? result.path("items").size() : 0;
        return "已完成菜单查询：" + menuId + "，日期「" + date + "」、餐次「" + mealTime
                + "」、状态「" + status + "」、版本「" + version + "」，共 " + itemCount + " 道菜。";
    }

    private static String assistantInventoryMessage(AgentRun run, JsonNode result) {
        if (!"SUCCEEDED".equals(run.status().name())) {
            return "库存查询未完成，请查看运行状态后重试或人工处理。";
        }
        long total = result == null || !result.path("total").canConvertToLong()
                ? result == null || !result.path("records").isArray()
                        ? 0 : result.path("records").size()
                : result.path("total").asLong();
        long warnings = 0;
        if (result != null && result.path("records").isArray()) {
            for (JsonNode record : result.path("records")) {
                if (record.path("warning").asBoolean(false)) {
                    warnings++;
                }
            }
        }
        return "已完成库存查询：返回 " + total + " 项食材，其中 " + warnings + " 项低于或等于预警阈值。";
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
