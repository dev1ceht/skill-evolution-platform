package com.example.smartcanteen.http;

import com.example.smartcanteen.agent.application.AgentExecutionService;
import com.example.smartcanteen.agent.application.AgentRunNotFoundException;
import com.example.smartcanteen.agent.application.AgentRuntime;
import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.AgentRunEvent;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.domain.SkillDefinition;
import com.example.smartcanteen.agent.domain.StartRunCommand;
import com.example.smartcanteen.agent.port.SkillRegistry;
import com.example.smartcanteen.application.BusinessAuthorizationPolicy;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.ForbiddenException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

/** HTTP adapter for the first Agent Runtime vertical slice. */
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private final AgentRuntime runtime;
    private final AgentExecutionService execution;
    private final BusinessAuthorizationPolicy policy;
    private final SkillRegistry skills;
    private final ObjectMapper objectMapper;

    public AgentController(
            AgentRuntime runtime,
            AgentExecutionService execution,
            BusinessAuthorizationPolicy policy,
            SkillRegistry skills,
            ObjectMapper objectMapper) {
        this.runtime = runtime;
        this.execution = execution;
        this.policy = policy;
        this.skills = skills;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates an immutable plan and immediately executes read-only Skills. Write Skills will
     * remain a separate confirmation/approval flow once they are activated.
     */
    @PostMapping("/runs")
    public ApiResponse<RunView> start(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody StartRunRequest body) {
        AuthPrincipal principal = principal(request);
        SkillDefinition skill = skills.findByIntent(body.intent()).orElseThrow(() ->
                new IllegalArgumentException(
                        "No active Skill is registered for intent: " + body.intent()));
        policy.requireSkillAccess(principal, skill);
        CanteenScope scope = new CanteenScope(schoolId, canteenId);
        String resolvedRequestId = requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString()
                : requestId;
        ExecutionContext context = policy.establishContext(
                principal,
                resolvedRequestId,
                scope,
                "write".equals(skill.runtime().sideEffect()));
        policy.requireIntentAccess(context, body.intent());
        String inputJson = writeInput(body.input());
        AgentRun run = runtime.start(new StartRunCommand(
                resolvedRequestId, body.intent(), inputJson, idempotencyKey), context);
        if ("read".equals(skill.runtime().sideEffect())
                && run.status().name().equals("PLANNED")) {
            run = execution.execute(run, context);
        }
        return ApiResponse.ok(RunView.from(run, objectMapper));
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<RunView> get(
            HttpServletRequest request,
            @PathVariable String runId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestParam String schoolId,
            @RequestParam String canteenId) {
        AuthPrincipal principal = principal(request);
        CanteenScope scope = new CanteenScope(schoolId, canteenId);
        String resolvedRequestId = requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString()
                : requestId;
        ExecutionContext context = policy.establishContext(
                principal, resolvedRequestId, scope, false);
        AgentRun run = runtime.find(runId).orElseThrow(() ->
                new AgentRunNotFoundException(runId));
        requireOwner(run, context);
        return ApiResponse.ok(RunView.from(run, objectMapper));
    }

    @PostMapping("/runs/{runId}/decisions")
    @Transactional
    public ApiResponse<RunView> decide(
            HttpServletRequest request,
            @PathVariable String runId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody RunDecisionRequest body) {
        AuthPrincipal principal = principal(request);
        CanteenScope scope = new CanteenScope(schoolId, canteenId);
        ExecutionContext context = policy.establishContext(
                principal, resolvedRequestId(requestId), scope, true);
        AgentRun run = runtime.decide(
                runId,
                body.version(),
                body.decisionType(),
                body.comment(),
                idempotencyKey,
                context);
        if ("RUN_CONFIRM".equalsIgnoreCase(body.decisionType())
                && run.status() == com.example.smartcanteen.agent.domain.RunStatus.PLANNED) {
            run = execution.execute(run, context);
        }
        return ApiResponse.ok(RunView.from(run, objectMapper));
    }

    @PostMapping("/runs/{runId}/cancel")
    public ApiResponse<RunView> cancel(
            HttpServletRequest request,
            @PathVariable String runId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody RunVersionRequest body) {
        return decide(
                request,
                runId,
                idempotencyKey,
                requestId,
                schoolId,
                canteenId,
                new RunDecisionRequest(body.version(), "RUN_CANCEL", null));
    }

    @PostMapping("/runs/{runId}/resume")
    public ApiResponse<RunView> resume(
            HttpServletRequest request,
            @PathVariable String runId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody RunVersionRequest body) {
        requireIdempotencyKey(idempotencyKey);
        AuthPrincipal principal = principal(request);
        CanteenScope scope = new CanteenScope(schoolId, canteenId);
        ExecutionContext context = policy.establishContext(
                principal, resolvedRequestId(requestId), scope, true);
        AgentRun run = runtime.find(runId).orElseThrow(() ->
                new AgentRunNotFoundException(runId));
        requireOwner(run, context);
        if (run.status() == com.example.smartcanteen.agent.domain.RunStatus.EXECUTING) {
            run = runtime.markReconciliationRequired(runId, body.version(), context);
        } else if (run.status() == com.example.smartcanteen.agent.domain.RunStatus.PLANNED) {
            if (run.version() != body.version()) {
                throw new IllegalStateException("Agent Run version is stale: " + runId);
            }
            run = execution.execute(run, context);
        }
        return ApiResponse.ok(RunView.from(run, objectMapper));
    }

    @GetMapping("/runs/{runId}/events")
    public ApiResponse<List<EventView>> events(
            HttpServletRequest request,
            @PathVariable String runId,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestParam String schoolId,
            @RequestParam String canteenId) {
        AuthPrincipal principal = principal(request);
        CanteenScope scope = new CanteenScope(schoolId, canteenId);
        ExecutionContext context = policy.establishContext(
                principal, resolvedRequestId(requestId), scope, false);
        AgentRun run = runtime.find(runId).orElseThrow(() ->
                new AgentRunNotFoundException(runId));
        requireOwner(run, context);
        return ApiResponse.ok(runtime.events(runId).stream()
                .map(event -> EventView.from(event, objectMapper)).toList());
    }

    @GetMapping("/skills")
    public ApiResponse<List<SkillView>> skills() {
        return ApiResponse.ok(skills.list().stream().map(SkillView::from).toList());
    }

    private static void requireOwner(AgentRun run, ExecutionContext context) {
        if (!run.actorUserId().equals(context.actorUserId())
                || !run.scope().equals(context.scope())) {
            throw new ForbiddenException("User is outside the Agent Run scope");
        }
    }

    private static AuthPrincipal principal(HttpServletRequest request) {
        Object value = request.getAttribute(AuthPrincipal.class.getName());
        if (value instanceof AuthPrincipal current) {
            return current;
        }
        throw new ForbiddenException("Authentication is required");
    }

    private static String resolvedRequestId(String requestId) {
        return requestId == null || requestId.isBlank()
                ? UUID.randomUUID().toString()
                : requestId;
    }

    private static void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key must be 1-128 characters");
        }
    }

    private String writeInput(JsonNode input) {
        try {
            return objectMapper.writeValueAsString(input);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Agent input cannot be serialized", exception);
        }
    }

    public record StartRunRequest(
            @NotBlank String intent,
            @NotNull JsonNode input) {
    }

    public record RunView(
            String runId,
            long version,
            String status,
            String intent,
            String actorUserId,
            String actorUsername,
            String schoolId,
            String canteenId,
            String skillId,
            String skillVersion,
            String manifestDigest,
            String planHash,
            JsonNode plan,
            JsonNode result,
            String errorCode,
            String errorMessage,
            String currentStep,
            Instant createdAt,
            Instant updatedAt) {

        static RunView from(AgentRun run, ObjectMapper objectMapper) {
            return new RunView(
                    run.runId(),
                    run.version(),
                    run.status().name(),
                    run.intent(),
                    run.actorUserId(),
                    run.actorUsername(),
                    run.scope().schoolId(),
                    run.scope().canteenId(),
                    run.skillId(),
                    run.skillVersion(),
                    run.manifestDigest(),
                    run.planHash(),
                    parse(objectMapper, run.planJson()),
                    parse(objectMapper, run.resultJson()),
                    run.errorCode(),
                    run.errorMessage(),
                    run.currentStep(),
                    run.createdAt(),
                    run.updatedAt());
        }

        static JsonNode parse(ObjectMapper objectMapper, String json) {
            if (json == null) {
                return null;
            }
            try {
                return objectMapper.readTree(json);
            } catch (IOException exception) {
                return objectMapper.getNodeFactory().textNode(json);
            }
        }
    }

    public record RunDecisionRequest(
            long version,
            @NotBlank String decisionType,
            @jakarta.validation.constraints.Size(max = 500) String comment) {
    }

    public record RunVersionRequest(long version) {
    }

    public record EventView(
            String eventId,
            String runId,
            long eventSequence,
            String eventType,
            String fromStatus,
            String toStatus,
            String actorUserId,
            JsonNode payload,
            Instant occurredAt) {

        static EventView from(AgentRunEvent event, ObjectMapper objectMapper) {
            return new EventView(
                    event.eventId(),
                    event.runId(),
                    event.sequence(),
                    event.eventType(),
                    event.fromStatus(),
                    event.toStatus(),
                    event.actorUserId(),
                    RunView.parse(objectMapper, event.payloadJson()),
                    event.occurredAt());
        }
    }

    public record SkillView(
            String id,
            String version,
            String status,
            String riskLevel,
            String approval,
            String scope,
            List<String> permissions,
            String manifestDigest,
            boolean available,
            RuntimeView runtime) {

        static SkillView from(SkillDefinition skill) {
            return new SkillView(
                    skill.id(),
                    skill.version(),
                    skill.status(),
                    skill.riskLevel(),
                    skill.approval(),
                    skill.scope(),
                    skill.permissions(),
                    skill.manifestDigest(),
                    skill.isAvailable(),
                    RuntimeView.from(skill.runtime()));
        }
    }

    public record RuntimeView(
            List<String> intents,
            String inputSchema,
            String outputSchema,
            List<String> tools,
            java.util.Map<String, String> toolByIntent,
            String sideEffect,
            String runConfirmation,
            String domainApproval,
            String activation,
            long deadlineMs,
            String retryPolicy,
            String evidence) {

        static RuntimeView from(SkillDefinition.RuntimePolicy runtime) {
            if (runtime == null) {
                return null;
            }
            return new RuntimeView(
                    runtime.intents(),
                    runtime.inputSchema(),
                    runtime.outputSchema(),
                    runtime.tools(),
                    runtime.toolByIntent(),
                    runtime.sideEffect(),
                    runtime.runConfirmation(),
                    runtime.domainApproval(),
                    runtime.activation(),
                    runtime.deadlineMs(),
                    runtime.retryPolicy(),
                    runtime.evidence());
        }
    }
}
