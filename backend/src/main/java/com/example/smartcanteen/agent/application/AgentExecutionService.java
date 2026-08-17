package com.example.smartcanteen.agent.application;

import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.AgentStep;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.domain.RunStatus;
import com.example.smartcanteen.agent.domain.SkillDefinition;
import com.example.smartcanteen.agent.port.AgentRunStore;
import com.example.smartcanteen.agent.port.SkillRegistry;
import com.example.smartcanteen.agent.port.ToolExecutor;
import com.example.smartcanteen.application.BusinessAuthorizationPolicy;
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

/** Coordinates a claimed Run and one or more registered tools. */
@Service
public class AgentExecutionService {

    private static final Logger log = LoggerFactory.getLogger(AgentExecutionService.class);

    private final AgentRunStore runs;
    private final SkillRegistry skills;
    private final List<ToolExecutor> tools;
    private final BusinessAuthorizationPolicy policy;
    private final Clock clock;
    private final AuditStore audits;

    @Autowired
    public AgentExecutionService(
            AgentRunStore runs,
            SkillRegistry skills,
            List<ToolExecutor> tools,
            BusinessAuthorizationPolicy policy,
            AuditStore audits) {
        this(runs, skills, tools, policy, Clock.systemUTC(), audits);
    }

    public AgentExecutionService(
            AgentRunStore runs,
            SkillRegistry skills,
            List<ToolExecutor> tools,
            BusinessAuthorizationPolicy policy) {
        this(runs, skills, tools, policy, Clock.systemUTC(), null);
    }

    public AgentExecutionService(
            AgentRunStore runs,
            SkillRegistry skills,
            List<ToolExecutor> tools,
            BusinessAuthorizationPolicy policy,
            Clock clock) {
        this(runs, skills, tools, policy, clock, null);
    }

    public AgentExecutionService(
            AgentRunStore runs,
            SkillRegistry skills,
            List<ToolExecutor> tools,
            BusinessAuthorizationPolicy policy,
            Clock clock,
            AuditStore audits) {
        this.runs = Objects.requireNonNull(runs, "runs");
        this.skills = Objects.requireNonNull(skills, "skills");
        this.tools = Objects.requireNonNull(tools, "tools");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.audits = audits;
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
        AgentRun current = runs.findById(requested.runId()).orElseThrow(() ->
                new IllegalArgumentException("Agent Run not found: " + requested.runId()));
        requireOwner(current, context);
        if (isTerminal(current.status())) {
            return current;
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
        policy.requireCurrentExecution(current, context, skill);
        String toolName = skill.runtime().toolForIntent(current.intent());
        ToolExecutor tool = resolveTool(toolName);

        Instant startedAt = clock.instant();
        AgentRun executing = current.withStatus(RunStatus.EXECUTING, "step-1", startedAt);
        runs.update(current, executing);
        runs.appendEvent(
                current.runId(),
                "RUN_EXECUTING",
                current.status().name(),
                executing.status().name(),
                context.actorUserId(),
                null);

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
        runs.updateStep(executingStep);

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
                    runs.updateStep(activeStep);
                }
                try {
                    result = tool.execute(toolName, context, current.inputJson());
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
                runs.updateStep(timedOutStep);
                AgentRun timedOut = executing.withFailure(code, detail, deadlineStatus, finishedAt);
                runs.update(executing, timedOut);
                runs.appendEvent(
                        current.runId(),
                        deadlineStatus == RunStatus.TIMED_OUT
                                ? "RUN_TIMED_OUT" : "RUN_RECONCILIATION_REQUIRED",
                        executing.status().name(),
                        timedOut.status().name(),
                        context.actorUserId(),
                        detail);
                appendAudit(current, context, "AGENT_RUN_DEADLINE", "FAILURE", code);
                return timedOut;
            }
            AgentStep succeededStep = new AgentStep(
                    activeStep.runId(), activeStep.stepId(), activeStep.stepOrder(),
                    activeStep.toolName(), activeStep.idempotencyKey(), activeStep.inputDigest(),
                    "SUCCEEDED", activeStep.attemptCount(), result.resultJson(), null, null,
                    activeStep.startedAt(), finishedAt);
            runs.updateStep(succeededStep);
            AgentRun succeeded = executing.withSuccess(result.resultJson(), finishedAt);
            runs.update(executing, succeeded);
            runs.appendEvent(
                    current.runId(),
                    "RUN_SUCCEEDED",
                    executing.status().name(),
                    succeeded.status().name(),
                    context.actorUserId(),
                    result.resultJson());
            appendAudit(current, context, "AGENT_RUN_EXECUTE", "SUCCESS", null);
            return succeeded;
        } catch (RuntimeException exception) {
            Instant failedAt = clock.instant();
            AgentStep failedStep = new AgentStep(
                    activeStep.runId(), activeStep.stepId(), activeStep.stepOrder(),
                    activeStep.toolName(), activeStep.idempotencyKey(), activeStep.inputDigest(),
                    "FAILED", activeStep.attemptCount(), null, "TOOL_FAILED",
                    safeMessage(exception), activeStep.startedAt(), failedAt);
            runs.updateStep(failedStep);
            AgentRun failed = executing.withFailure(
                    "TOOL_FAILED", safeMessage(exception), RunStatus.FAILED, failedAt);
            runs.update(executing, failed);
            runs.appendEvent(
                    current.runId(),
                    "RUN_FAILED",
                    executing.status().name(),
                    failed.status().name(),
                    context.actorUserId(),
                    safeMessage(exception));
            appendAudit(current, context, "AGENT_RUN_EXECUTE", "FAILURE", safeMessage(exception));
            return failed;
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
}
