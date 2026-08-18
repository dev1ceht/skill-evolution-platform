package com.example.smartcanteen.agent.application;

import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.AgentRunClaim;
import com.example.smartcanteen.agent.domain.AgentRunClaimLostException;
import com.example.smartcanteen.agent.domain.AgentStep;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.domain.RunStatus;
import com.example.smartcanteen.agent.domain.SkillDefinition;
import com.example.smartcanteen.agent.port.AgentRunStore;
import com.example.smartcanteen.agent.port.SkillRegistry;
import com.example.smartcanteen.agent.port.ToolExecutor;
import com.example.smartcanteen.application.BusinessAuthorizationPolicy;
import com.example.smartcanteen.application.AgentWriteRolloutPolicy;
import com.example.smartcanteen.application.port.AuditStore;
import com.example.smartcanteen.domain.AuditLog;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Coordinates a synchronous or fencing-claimed Run and one or more registered tools. */
@Service
public class AgentExecutionService {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionService.class);

    private final AgentRunStore runs;
    private final SkillRegistry skills;
    private final List<ToolExecutor> tools;
    private final BusinessAuthorizationPolicy policy;
    private final Clock clock;
    private final AuditStore audits;
    private final AgentWriteRolloutPolicy writeRollout;

    @Autowired
    public AgentExecutionService(
            AgentRunStore runs,
            SkillRegistry skills,
            List<ToolExecutor> tools,
            BusinessAuthorizationPolicy policy,
            AuditStore audits,
            AgentWriteRolloutPolicy writeRollout) {
        this(runs, skills, tools, policy, Clock.systemUTC(), audits, writeRollout);
    }

    public AgentExecutionService(
            AgentRunStore runs,
            SkillRegistry skills,
            List<ToolExecutor> tools,
            BusinessAuthorizationPolicy policy) {
        this(runs, skills, tools, policy, Clock.systemUTC(), null,
                AgentWriteRolloutPolicy.disabled());
    }

    public AgentExecutionService(
            AgentRunStore runs,
            SkillRegistry skills,
            List<ToolExecutor> tools,
            BusinessAuthorizationPolicy policy,
            Clock clock) {
        this(runs, skills, tools, policy, clock, null, AgentWriteRolloutPolicy.disabled());
    }

    public AgentExecutionService(
            AgentRunStore runs,
            SkillRegistry skills,
            List<ToolExecutor> tools,
            BusinessAuthorizationPolicy policy,
            Clock clock,
            AuditStore audits) {
        this(runs, skills, tools, policy, clock, audits, AgentWriteRolloutPolicy.disabled());
    }

    public AgentExecutionService(
            AgentRunStore runs,
            SkillRegistry skills,
            List<ToolExecutor> tools,
            BusinessAuthorizationPolicy policy,
            Clock clock,
            AuditStore audits,
            AgentWriteRolloutPolicy writeRollout) {
        this.runs = Objects.requireNonNull(runs, "runs");
        this.skills = Objects.requireNonNull(skills, "skills");
        this.tools = Objects.requireNonNull(tools, "tools");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.audits = audits;
        this.writeRollout = Objects.requireNonNull(writeRollout, "writeRollout");
    }

    public AgentExecutionService(
            AgentRunStore runs,
            SkillRegistry skills,
            ToolExecutor tool,
            BusinessAuthorizationPolicy policy,
            Clock clock) {
        this(runs, skills, List.of(tool), policy, clock);
    }

    @Transactional
    public AgentRun execute(AgentRun requested, ExecutionContext context) {
        return executeInternal(requested, context, null);
    }

    /**
     * Executes a Run after a worker acquired its durable fencing claim. This method deliberately
     * has no surrounding transaction: every state checkpoint must go through the claim-aware
     * store methods so a stale worker cannot commit after its lease expires.
     */
    public AgentRun executeClaimed(
            AgentRun requested, ExecutionContext context, AgentRunClaim claim) {
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(claim, "claim");
        if (!runs.supportsExecutionClaims()) {
            throw new IllegalStateException(
                    "Agent Run execution claims are required for worker execution");
        }
        if (!requested.runId().equals(claim.runId())) {
            throw new IllegalArgumentException(
                    "Agent execution claim does not belong to Run: " + requested.runId());
        }
        return executeInternal(requested, context, claim);
    }

    /**
     * Performs the read-only authorization and Skill snapshot checks required before a worker
     * writes its infrastructure claim. The claimed execution path repeats the checks after claim
     * acquisition to close the time-of-check/time-of-use window.
     */
    public AgentRun validateExecutable(AgentRun requested, ExecutionContext context) {
        Objects.requireNonNull(requested, "requested");
        Objects.requireNonNull(context, "context");
        return prepare(requested, context).run();
    }

    private AgentRun executeInternal(
            AgentRun requested, ExecutionContext context, AgentRunClaim claim) {
        ExecutionPreparation preparation = prepare(requested, context);
        AgentRun current = preparation.run();
        if (isTerminal(current.status())) {
            return current;
        }
        SkillDefinition skill = preparation.skill();
        String toolName = skill.runtime().toolForIntent(current.intent());
        ToolExecutor tool = resolveTool(toolName);

        Instant startedAt = clock.instant();
        AgentRun executing = current.withStatus(RunStatus.EXECUTING, "step-1", startedAt);
        updateRun(current, executing, claim);
        appendEvent(
                current.runId(),
                "RUN_EXECUTING",
                current.status().name(),
                executing.status().name(),
                context.actorUserId(),
                null,
                claim);

        AgentStep executingStep = new AgentStep(
                current.runId(),
                "step-1",
                0,
                toolName,
                current.runId() + ":step-1:" + current.planHash(),
                current.requestHash(),
                "EXECUTING",
                1,
                null,
                null,
                null,
                startedAt,
                null);
        updateStep(executingStep, claim);

        AgentStep activeStep = executingStep;
        try {
            ToolExecutor.ToolResult result = null;
            RuntimeException lastFailure = null;
            int maxAttempts = maxAttempts(skill);
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                if (attempt > 1) {
                    activeStep = new AgentStep(
                            activeStep.runId(), activeStep.stepId(), activeStep.stepOrder(),
                            activeStep.toolName(), activeStep.idempotencyKey(), activeStep.inputDigest(),
                            "EXECUTING", attempt, null, null, null,
                            activeStep.startedAt(), null);
                    updateStep(activeStep, claim);
                }
                try {
                    String toolInput = "write".equals(skill.runtime().sideEffect())
                            ? withStepBusinessIdempotency(
                                    current.inputJson(),
                                    activeStep.idempotencyKey(),
                                    current.intent(),
                                    current.createdAt())
                            : current.inputJson();
                    result = tool.execute(toolName, context, toolInput);
                    lastFailure = null;
                    break;
                } catch (RuntimeException exception) {
                    lastFailure = exception;
                }
            }
            if (lastFailure != null) {
                throw lastFailure;
            }
            Instant finishedAt = clock.instant();
            long elapsedMs = Math.max(0, java.time.Duration.between(startedAt, finishedAt).toMillis());
            if (elapsedMs > skill.runtime().deadlineMs()) {
                RunStatus deadlineStatus = "write".equals(skill.runtime().sideEffect())
                        ? RunStatus.RECONCILIATION_REQUIRED
                        : RunStatus.TIMED_OUT;
                String code = deadlineStatus == RunStatus.TIMED_OUT
                        ? "DEADLINE_EXCEEDED" : "RECOVERY_REQUIRED";
                String detail = "Agent tool exceeded deadline of "
                        + skill.runtime().deadlineMs() + "ms (elapsed " + elapsedMs + "ms)";
                AgentStep timedOutStep = new AgentStep(
                        activeStep.runId(), activeStep.stepId(), activeStep.stepOrder(),
                        activeStep.toolName(), activeStep.idempotencyKey(), activeStep.inputDigest(),
                        deadlineStatus == RunStatus.TIMED_OUT ? "TIMED_OUT" : "RECONCILIATION_REQUIRED",
                        activeStep.attemptCount(), null, code, detail,
                        activeStep.startedAt(), finishedAt);
                updateStep(timedOutStep, claim);
                AgentRun timedOut = executing.withFailure(code, detail, deadlineStatus, finishedAt);
                updateRun(executing, timedOut, claim);
                appendEvent(
                        current.runId(),
                        deadlineStatus == RunStatus.TIMED_OUT
                                ? "RUN_TIMED_OUT" : "RUN_RECONCILIATION_REQUIRED",
                        executing.status().name(),
                        timedOut.status().name(),
                        context.actorUserId(),
                        detail,
                        claim);
                appendAudit(current, context, "AGENT_RUN_DEADLINE", "FAILURE", code, claim);
                return timedOut;
            }
            AgentStep succeededStep = new AgentStep(
                    activeStep.runId(), activeStep.stepId(), activeStep.stepOrder(),
                    activeStep.toolName(), activeStep.idempotencyKey(), activeStep.inputDigest(),
                    "SUCCEEDED", activeStep.attemptCount(), result.resultJson(), null, null,
                    activeStep.startedAt(), finishedAt);
            updateStep(succeededStep, claim);
            AgentRun succeeded = executing.withSuccess(result.resultJson(), finishedAt);
            updateRun(executing, succeeded, claim);
            appendEvent(
                    current.runId(),
                    "RUN_SUCCEEDED",
                    executing.status().name(),
                    succeeded.status().name(),
                    context.actorUserId(),
                    result.resultJson(),
                    claim);
            appendAudit(current, context, "AGENT_RUN_EXECUTE", "SUCCESS", null, claim);
            return succeeded;
        } catch (AgentRunClaimLostException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            Instant failedAt = clock.instant();
            AgentStep failedStep = new AgentStep(
                    activeStep.runId(), activeStep.stepId(), activeStep.stepOrder(),
                    activeStep.toolName(), activeStep.idempotencyKey(), activeStep.inputDigest(),
                    "FAILED", activeStep.attemptCount(), null, "TOOL_FAILED",
                    safeMessage(exception), activeStep.startedAt(), failedAt);
            updateStep(failedStep, claim);
            AgentRun failed = executing.withFailure(
                    "TOOL_FAILED", safeMessage(exception), RunStatus.FAILED, failedAt);
            updateRun(executing, failed, claim);
            appendEvent(
                    current.runId(),
                    "RUN_FAILED",
                    executing.status().name(),
                    failed.status().name(),
                    context.actorUserId(),
                    safeMessage(exception),
                    claim);
            appendAudit(
                    current, context, "AGENT_RUN_EXECUTE", "FAILURE", safeMessage(exception), claim);
            return failed;
        }
    }

    private ExecutionPreparation prepare(AgentRun requested, ExecutionContext context) {
        AgentRun current = runs.findById(requested.runId()).orElseThrow(() ->
                new IllegalArgumentException("Agent Run not found: " + requested.runId()));
        requireOwner(current, context);
        if (isTerminal(current.status())) {
            return new ExecutionPreparation(current, null);
        }
        if (current.status() != RunStatus.PLANNED) {
            throw new IllegalStateException(
                    "Agent Run cannot execute from status " + current.status());
        }
        SkillDefinition skill = skills.find(current.skillId(), current.skillVersion())
                .filter(SkillDefinition::isAvailable)
                .orElseThrow(() -> new IllegalStateException(
                        "Skill snapshot is no longer executable: "
                                + current.skillId() + "@" + current.skillVersion()));
        if (!current.manifestDigest().equals(skill.manifestDigest())) {
            throw new IllegalStateException("Skill manifest changed after Run creation");
        }
        if ("write".equals(skill.runtime().sideEffect())
                && !current.intent().startsWith("menu.")) {
            writeRollout.requireEnabled(context.scope(), current.intent());
        }
        policy.requireCurrentExecution(current, context, skill);
        policy.requireDomainApproval(context, current.intent());
        return new ExecutionPreparation(current, skill);
    }

    private static String withStepBusinessIdempotency(
            String inputJson, String stepKey, String intent, Instant plannedAt) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode parsed = mapper.readTree(inputJson);
            if (parsed == null || !parsed.isObject()) {
                throw new IllegalArgumentException("Agent write input must be a JSON object");
            }
            com.fasterxml.jackson.databind.node.ObjectNode object = (com.fasterxml.jackson.databind.node.ObjectNode) parsed;
            object.put("businessIdempotencyKey", stepKey);
            if ("alert.dispose".equals(intent) && !object.hasNonNull("processTime")) {
                object.put("processTime", plannedAt.toString());
            }
            return mapper.writeValueAsString(object);
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Agent write input cannot be prepared", exception);
        }
    }

    private void updateRun(AgentRun expected, AgentRun updated, AgentRunClaim claim) {
        if (claim == null) {
            runs.update(expected, updated);
        } else {
            runs.updateClaimed(expected, updated, claim);
        }
    }

    private void updateStep(AgentStep step, AgentRunClaim claim) {
        if (claim == null) {
            runs.updateStep(step);
        } else {
            runs.updateStepClaimed(step, claim);
        }
    }

    private void appendEvent(
            String runId,
            String eventType,
            String fromStatus,
            String toStatus,
            String actorUserId,
            String payloadJson,
            AgentRunClaim claim) {
        if (claim == null) {
            runs.appendEvent(runId, eventType, fromStatus, toStatus, actorUserId, payloadJson);
        } else {
            runs.appendEventClaimed(
                    runId, eventType, fromStatus, toStatus, actorUserId, payloadJson, claim);
        }
    }

    private ToolExecutor resolveTool(String toolName) {
        return tools.stream()
                .filter(candidate -> candidate.supports(toolName))
                .findFirst()
                .orElseGet(() -> {
                    if (tools.size() == 1) {
                        return tools.get(0);
                    }
                    throw new IllegalStateException("Tool is not registered: " + toolName);
                });
    }

    private static int maxAttempts(SkillDefinition skill) {
        return "read".equals(skill.runtime().sideEffect())
                && "read-only-bounded".equals(skill.runtime().retryPolicy()) ? 2 : 1;
    }

    private static void requireOwner(AgentRun run, ExecutionContext context) {
        if (!run.actorUserId().equals(context.actorUserId())
                || !run.scope().equals(context.scope())) {
            throw new com.example.smartcanteen.security.ForbiddenException(
                    "User is outside the Agent Run scope");
        }
    }

    private static boolean isTerminal(RunStatus status) {
        return switch (status) {
            case SUCCEEDED, FAILED, REJECTED, CANCELLED, TIMED_OUT, RECONCILIATION_REQUIRED -> true;
            default -> false;
        };
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private void appendAudit(
            AgentRun run,
            ExecutionContext context,
            String action,
            String outcome,
            String detail,
            AgentRunClaim claim) {
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
            appendEvent(
                    run.runId(),
                    "AUDIT_WRITE_FAILED",
                    run.status().name(),
                    run.status().name(),
                    context.actorUserId(),
                    "Audit evidence unavailable for " + action,
                    claim);
        }
    }

    private record ExecutionPreparation(AgentRun run, SkillDefinition skill) {
    }
}
