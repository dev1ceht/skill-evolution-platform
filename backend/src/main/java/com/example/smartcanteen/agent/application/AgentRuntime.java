package com.example.smartcanteen.agent.application;

import com.example.smartcanteen.agent.domain.AgentPlan;
import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.AgentStep;
import com.example.smartcanteen.agent.domain.AgentRunDecision;
import com.example.smartcanteen.agent.domain.AgentRunEvent;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.domain.StartRunCommand;
import com.example.smartcanteen.agent.domain.RunStatus;
import com.example.smartcanteen.agent.port.AgentRunStore;
import com.example.smartcanteen.agent.port.SkillRegistry;
import com.example.smartcanteen.application.port.AuditStore;
import com.example.smartcanteen.domain.AuditLog;
import com.example.smartcanteen.agent.domain.SkillDefinition;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.ForbiddenException;
import com.example.smartcanteen.security.Role;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Creates durable, immutable Agent plans. Tool execution is intentionally a later seam. */
@Service
public class AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);
    public static final String AGENT_RUN_RECOVERY_PERMISSION = "AGENT_RUN_RECOVER";

    private final SkillRegistry skills;
    private final AgentRunStore runs;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final AuditStore audits;

    @Autowired
    public AgentRuntime(
            SkillRegistry skills,
            AgentRunStore runs,
            ObjectMapper objectMapper,
            AuditStore audits) {
        this(skills, runs, objectMapper, Clock.systemUTC(), audits);
    }

    public AgentRuntime(SkillRegistry skills, AgentRunStore runs, ObjectMapper objectMapper) {
        this(skills, runs, objectMapper, Clock.systemUTC(), null);
    }

    public AgentRuntime(
            SkillRegistry skills,
            AgentRunStore runs,
            ObjectMapper objectMapper,
            Clock clock) {
        this(skills, runs, objectMapper, clock, null);
    }

    public AgentRuntime(
            SkillRegistry skills,
            AgentRunStore runs,
            ObjectMapper objectMapper,
            Clock clock,
            AuditStore audits) {
        this.skills = skills;
        this.runs = runs;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.audits = audits;
    }

    @Transactional
    public AgentRun start(StartRunCommand command, ExecutionContext context) {
        if (!command.requestId().equals(context.requestId())) {
            throw new IllegalArgumentException("requestId does not match execution context");
        }
        SkillDefinition skill = skills.findByIntent(command.intent())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No active Skill is registered for intent: " + command.intent()));
        String canonicalInput = canonicalize(command.inputJson());
        AgentPlan plan = plan(command, context, skill, canonicalInput);
        Optional<AgentRun> existing = runs.findByIdempotency(
                context.actorUserId(), context.scope(), command.idempotencyKey());
        if (existing.isPresent()) {
            AgentRun previous = existing.get();
            if (!previous.requestHash().equals(plan.inputDigest())) {
                throw new IllegalStateException(
                        "Idempotency key was already used for a different Agent request");
            }
            runs.appendEvent(
                    previous.runId(),
                    "RUN_IDEMPOTENCY_REPLAY",
                    previous.status().name(),
                    previous.status().name(),
                    context.actorUserId(),
                    null);
            return previous;
        }

        Instant now = clock.instant();
        String runId = "RUN-" + UUID.randomUUID();
        AgentRun run = new AgentRun(
                runId,
                command.idempotencyKey(),
                plan.inputDigest(),
                context.actorUserId(),
                context.actorUsername(),
                context.scope(),
                command.intent(),
                skill.id(),
                skill.version(),
                skill.manifestDigest(),
                plan.planHash(),
                plan.planJson(),
                canonicalInput,
                initialStatus(skill),
                null,
                null,
                null,
                null,
                0,
                now,
                now);
        List<AgentStep> steps = new ArrayList<>();
        for (int index = 0; index < plan.tools().size(); index++) {
            String stepId = "step-" + (index + 1);
            steps.add(new AgentStep(
                    runId,
                    stepId,
                    index,
                    plan.tools().get(index),
                    runId + ":" + stepId + ":" + plan.planHash(),
                    plan.inputDigest(),
                    "PENDING",
                    0,
                    null,
                    null,
                    null,
                    null,
                    null));
        }
        AgentRun persisted = runs.insert(run, steps);
        if (!run.runId().equals(persisted.runId())) {
            // The unique actor/scope/idempotency constraint is the concurrency
            // authority. A competing transaction may win between the lookup
            // above and the insert; convert that race into normal replay or a
            // deterministic same-key/different-payload conflict without
            // committing the losing caller's partial transaction. The store
            // returns the row selected in this transaction, so this also works
            // when the same outer transaction starts the key twice.
            if (!persisted.requestHash().equals(plan.inputDigest())) {
                throw new IllegalStateException(
                        "Idempotency key was already used for a different Agent request");
            }
            runs.appendEvent(
                    persisted.runId(),
                    "RUN_IDEMPOTENCY_REPLAY",
                    persisted.status().name(),
                    persisted.status().name(),
                    context.actorUserId(),
                    null);
            return persisted;
        }
        appendAudit(run, context, "AGENT_RUN_PLAN", "SUCCESS", null);
        return run;
    }

    public Optional<AgentRun> find(String runId) {
        return runs.findById(runId);
    }

    public List<AgentRunEvent> events(String runId) {
        return runs.listEvents(runId);
    }

    @Transactional
    public AgentRun decide(
            String runId,
            long expectedVersion,
            String decisionType,
            String comment,
            String idempotencyKey,
            ExecutionContext context) {
        AgentRun current = runs.findById(runId).orElseThrow(() ->
                new AgentRunNotFoundException(runId));
        requireOwner(current, context);
        requireIdempotencyKey(idempotencyKey);
        String normalized = decisionType == null ? "" : decisionType.trim().toUpperCase();
        String normalizedComment = comment == null ? null : comment.trim();
        String requestHash = decisionRequestHash(expectedVersion, normalized, normalizedComment);
        Optional<AgentRunDecision> previous = runs.findDecisionByIdempotency(
                runId, context.actorUserId(), idempotencyKey);
        if (previous.isPresent()) {
            AgentRunDecision replay = previous.get();
            if (!replay.planHash().equals(current.planHash())
                    || !replay.decisionType().equals(normalized)
                    || !Objects.equals(replay.requestHash(), requestHash)) {
                throw new IllegalStateException(
                        "Idempotency key was already used for a different Agent decision");
            }
            return current;
        }
        if (current.version() != expectedVersion) {
            throw new IllegalStateException("Agent Run version is stale: " + runId);
        }
        RunStatus next;
        String outcome;
        switch (normalized) {
            case "RUN_CONFIRM" -> {
                if (current.status() != RunStatus.WAITING_CONFIRMATION) {
                    throw new IllegalStateException(
                            "Run confirmation is not available from status " + current.status());
                }
                next = RunStatus.PLANNED;
                outcome = "ACCEPTED";
            }
            case "RUN_REJECT" -> {
                if (current.status() != RunStatus.WAITING_CONFIRMATION) {
                    throw new IllegalStateException(
                            "Run rejection is not available from status " + current.status());
                }
                next = RunStatus.REJECTED;
                outcome = "REJECTED";
            }
            case "RUN_CANCEL" -> {
                if (current.status() != RunStatus.WAITING_CONFIRMATION
                        && current.status() != RunStatus.PLANNED) {
                    throw new IllegalStateException(
                            "Run cancellation is not available from status " + current.status());
                }
                next = RunStatus.CANCELLED;
                outcome = "CANCELLED";
            }
            default -> throw new IllegalArgumentException("Unsupported Agent Run decision: " + decisionType);
        }
        Instant now = clock.instant();
        AgentRunDecision decision = new AgentRunDecision(
                "DECISION-" + UUID.randomUUID(),
                runId,
                idempotencyKey,
                normalized,
                outcome,
                context.actorUserId(),
                current.planHash(),
                requestHash,
                normalizedComment,
                null,
                now);
        try {
            runs.appendDecision(decision);
        } catch (DuplicateKeyException duplicate) {
            AgentRunDecision concurrent = runs.findDecisionByIdempotency(
                            runId, context.actorUserId(), idempotencyKey)
                    .orElseThrow(() -> duplicate);
            if (!concurrent.planHash().equals(current.planHash())
                    || !concurrent.decisionType().equals(normalized)
                    || !Objects.equals(concurrent.requestHash(), requestHash)) {
                throw new IllegalStateException(
                        "Idempotency key was already used for a different Agent decision",
                        duplicate);
            }
            return runs.findById(runId).orElse(current);
        }
        AgentRun updated = current.withStatus(next, null, now);
        runs.update(current, updated);
        runs.appendEvent(
                runId,
                normalized,
                current.status().name(),
                updated.status().name(),
                context.actorUserId(),
                comment);
        appendAudit(updated, context, "AGENT_RUN_DECISION", "SUCCESS", normalized);
        return updated;
    }

    private static void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key must be 1-128 characters");
        }
    }

    private static String decisionRequestHash(
            long expectedVersion, String normalizedDecision, String normalizedComment) {
        String comment = normalizedComment == null ? "" : normalizedComment;
        return digest(expectedVersion + "\n" + normalizedDecision + "\n" + comment);
    }

    @Transactional
    public AgentRun markReconciliationRequired(
            String runId, long expectedVersion, ExecutionContext context) {
        return markReconciliationRequiredInternal(
                runId,
                expectedVersion,
                context,
                "agent-manual-recovery-" + runId + "-v" + expectedVersion);
    }

    /**
     * Marks an interrupted Run from the internal stale-claim recovery path.
     *
     * <p>The deterministic idempotency key is retained in the event/audit evidence, while the
     * expected version fences duplicate recovery attempts at the state transition boundary.
     */
    @Transactional
    public AgentRun markReconciliationRequiredFromRecovery(
            String runId,
            long expectedVersion,
            ExecutionContext context,
            String idempotencyKey) {
        requireRecoveryAuthority(context);
        if (!runs.supportsExecutionClaims()
                || !runs.confirmStaleExecution(runId, expectedVersion)) {
            throw new IllegalStateException(
                    "Agent Run claim is no longer stale or durable claim fencing is unavailable: "
                            + runId);
        }
        return markReconciliationRequiredInternal(
                runId, expectedVersion, context, idempotencyKey);
    }

    private AgentRun markReconciliationRequiredInternal(
            String runId,
            long expectedVersion,
            ExecutionContext context,
            String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey);
        AgentRun current = runs.findById(runId).orElseThrow(() ->
                new AgentRunNotFoundException(runId));
        requireOwner(current, context);
        if (current.version() != expectedVersion) {
            throw new IllegalStateException("Agent Run version is stale: " + runId);
        }
        if (current.status() != RunStatus.EXECUTING) {
            return current;
        }
        Instant now = clock.instant();
        AgentRun updated = current.withFailure(
                "RECOVERY_REQUIRED",
                "Execution was interrupted; business outcome requires reconciliation",
                RunStatus.RECONCILIATION_REQUIRED,
                now);
        if (current.currentStep() != null) {
            runs.markStepReconciliationRequired(
                    runId,
                    current.currentStep(),
                    "RECOVERY_REQUIRED",
                    updated.errorMessage(),
                    now);
        }
        runs.update(current, updated);
        runs.appendEvent(
                runId,
                "RUN_RECONCILIATION_REQUIRED",
                current.status().name(),
                updated.status().name(),
                context.actorUserId(),
                recoveryEvidence(updated.errorMessage(), idempotencyKey));
        appendAudit(
                updated,
                context,
                "AGENT_RUN_RECOVERY",
                "SUCCESS",
                recoveryEvidence(updated.errorCode(), idempotencyKey));
        return updated;
    }

    private static void requireRecoveryAuthority(ExecutionContext context) {
        if (context == null
                || !context.hasRole(Role.SYSTEM_ADMIN)
                || !context.hasPermission(AGENT_RUN_RECOVERY_PERMISSION)) {
            throw new ForbiddenException("Agent Run recovery permission is required");
        }
    }

    private static String recoveryEvidence(String detail, String idempotencyKey) {
        return detail + " (idempotencyKey=" + idempotencyKey + ")";
    }

    private static void requireOwner(AgentRun run, ExecutionContext context) {
        if (!run.actorUserId().equals(context.actorUserId())
                || !run.scope().equals(context.scope())) {
            throw new com.example.smartcanteen.security.ForbiddenException(
                    "User is outside the Agent Run scope");
        }
    }

    private void appendAudit(
            AgentRun run, ExecutionContext context, String action, String outcome, String detail) {
        if (audits == null) {
            return;
        }
        try {
            audits.append(new AuditLog(
                    AgentAuditId.forRun(run.runId(), action),
                    context.actorUserId(),
                    action,
                    "AGENT_RUN",
                    run.runId(),
                    run.scope().schoolId(),
                    run.scope().canteenId(),
                    outcome,
                    detail,
                    context.requestId(),
                    clock.instant()));
        } catch (RuntimeException exception) {
            log.warn("Agent audit write failed runId={} action={}", run.runId(), action);
            runs.appendEvent(
                    run.runId(),
                    "AUDIT_WRITE_FAILED",
                    run.status().name(),
                    run.status().name(),
                    context.actorUserId(),
                    "Audit evidence unavailable for " + action);
        }
    }

    private AgentPlan plan(
            StartRunCommand command,
            ExecutionContext context,
            SkillDefinition skill,
            String canonicalInput) {
        String inputDigest = digest(command.intent() + "\n" + canonicalInput);
        String selectedTool = skill.runtime().toolForIntent(command.intent());
        String planJson = writePlanJson(command, context.scope(), skill, inputDigest, selectedTool);
        return new AgentPlan(
                skill.id(),
                skill.version(),
                skill.manifestDigest(),
                command.intent(),
                context.scope(),
                inputDigest,
                List.of(selectedTool),
                digest(planJson),
                planJson);
    }

    private String writePlanJson(
            StartRunCommand command,
            CanteenScope scope,
            SkillDefinition skill,
            String inputDigest,
            String selectedTool) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("intent", command.intent());
        plan.put("skillId", skill.id());
        plan.put("skillVersion", skill.version());
        plan.put("manifestDigest", skill.manifestDigest());
        plan.put("inputDigest", inputDigest);
        plan.put("schoolId", scope.schoolId());
        plan.put("canteenId", scope.canteenId());
        plan.put("tools", List.of(selectedTool));
        // Keep operator-reviewable business coordinates in the immutable plan. The full input
        // remains separately canonicalized/digested; the assistant idempotency token is omitted
        // so the plan does not echo a transport credential.
        if (skill.runtime() != null && "write".equals(skill.runtime().sideEffect())) {
            try {
                JsonNode input = objectMapper.readTree(command.inputJson());
                Map<String, Object> businessParameters = new LinkedHashMap<>();
                if (input != null && input.isObject()) {
                    Iterator<Map.Entry<String, JsonNode>> fields = input.fields();
                    while (fields.hasNext()) {
                        Map.Entry<String, JsonNode> field = fields.next();
                        if (isSafePlanField(field.getKey())) {
                            JsonNode reviewable = reviewablePlanValue(field.getKey(), field.getValue());
                            if (reviewable != null) {
                                businessParameters.put(
                                        field.getKey(), objectMapper.convertValue(reviewable, Object.class));
                            }
                        }
                    }
                }
                plan.put("businessParameters", businessParameters);
            } catch (JsonProcessingException exception) {
                throw new IllegalArgumentException("Agent write input must be valid JSON", exception);
            }
        }
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize Agent plan", exception);
        }
    }

    private static boolean isSafePlanField(String field) {
        return switch (field) {
            case "menuId", "menuVersion", "decision", "periodStart", "periodEnd", "planId", "supplierId", "orderId",
                    "ingredientId", "materialId", "quantity", "unit", "unitPrice",
                    "purchasePrice", "batchNo", "reason", "warnId", "orderType",
                    "expectedDeliveryAt", "remark", "productionDate", "expiryDate", "processTime",
                    "processContent", "processFile", "items" -> true;
            default -> false;
        };
    }

    /** Keeps nested order/receipt lines reviewable without echoing transport credentials. */
    private JsonNode reviewablePlanValue(String field, JsonNode value) {
        if (!"items".equals(field)) {
            return value;
        }
        if (value == null || !value.isArray()) {
            return null;
        }
        ArrayNode sanitized = objectMapper.createArrayNode();
        for (JsonNode item : value) {
            if (item == null || !item.isObject()) {
                continue;
            }
            ObjectNode line = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = item.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (isSafePlanLineField(entry.getKey())) {
                    line.set(entry.getKey(), entry.getValue());
                }
            }
            sanitized.add(line);
        }
        return sanitized;
    }

    private static boolean isSafePlanLineField(String field) {
        return switch (field) {
            case "ingredientId", "materialId", "quantity", "unit", "unitPrice",
                    "purchasePrice", "batchNo", "productionDate", "expiryDate" -> true;
            default -> false;
        };
    }

    private static RunStatus initialStatus(SkillDefinition skill) {
        if ("write".equals(skill.runtime().sideEffect())
                && "required-before-write".equals(skill.runtime().runConfirmation())) {
            return RunStatus.WAITING_CONFIRMATION;
        }
        return RunStatus.PLANNED;
    }

    private static String digest(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** Canonicalizes structured input so equivalent JSON cannot bypass idempotency checks. */
    private String canonicalize(String inputJson) {
        try {
            return objectMapper.writeValueAsString(canonicalNode(objectMapper.readTree(inputJson)));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Agent input must be valid JSON", exception);
        }
    }

    private JsonNode canonicalNode(JsonNode node) {
        if (node == null || node.isValueNode()) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode array = objectMapper.createArrayNode();
            for (JsonNode item : node) {
                array.add(canonicalNode(item));
            }
            return array;
        }
        ObjectNode object = objectMapper.createObjectNode();
        List<String> names = new ArrayList<>();
        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            names.add(fields.next());
        }
        Collections.sort(names);
        for (String name : names) {
            object.set(name, canonicalNode(node.get(name)));
        }
        return object;
    }
}
