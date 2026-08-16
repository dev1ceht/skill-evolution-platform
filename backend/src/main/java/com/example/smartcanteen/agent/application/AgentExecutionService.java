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
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates a claimed Run and one or more registered tools. */
@Service
public class AgentExecutionService {

    private final AgentRunStore runs;
    private final SkillRegistry skills;
    private final ToolExecutor tools;
    private final BusinessAuthorizationPolicy policy;
    private final Clock clock;

    @Autowired
    public AgentExecutionService(
            AgentRunStore runs,
            SkillRegistry skills,
            ToolExecutor tools,
            BusinessAuthorizationPolicy policy) {
        this(runs, skills, tools, policy, Clock.systemUTC());
    }

    public AgentExecutionService(
            AgentRunStore runs,
            SkillRegistry skills,
            ToolExecutor tools,
            BusinessAuthorizationPolicy policy,
            Clock clock) {
        this.runs = Objects.requireNonNull(runs, "runs");
        this.skills = Objects.requireNonNull(skills, "skills");
        this.tools = Objects.requireNonNull(tools, "tools");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
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
        if (skill.runtime().tools().size() != 1) {
            throw new IllegalStateException(
                    "The initial executor only supports one tool per read-only Run");
        }

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

        String toolName = skill.runtime().tools().get(0);
        AgentStep executingStep = new AgentStep(
                current.runId(),
                "step-1",
                0,
                toolName,
                current.runId() + ":step-1",
                current.requestHash(),
                "EXECUTING",
                1,
                null,
                null,
                null,
                startedAt,
                null);
        runs.updateStep(executingStep);

        try {
            ToolExecutor.ToolResult result = tools.execute(toolName, context, current.inputJson());
            Instant finishedAt = clock.instant();
            AgentStep succeededStep = new AgentStep(
                    executingStep.runId(), executingStep.stepId(), executingStep.stepOrder(),
                    executingStep.toolName(), executingStep.idempotencyKey(), executingStep.inputDigest(),
                    "SUCCEEDED", executingStep.attemptCount(), result.resultJson(), null, null,
                    executingStep.startedAt(), finishedAt);
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
            return succeeded;
        } catch (RuntimeException exception) {
            Instant failedAt = clock.instant();
            AgentStep failedStep = new AgentStep(
                    executingStep.runId(), executingStep.stepId(), executingStep.stepOrder(),
                    executingStep.toolName(), executingStep.idempotencyKey(), executingStep.inputDigest(),
                    "FAILED", executingStep.attemptCount(), null, "TOOL_FAILED",
                    safeMessage(exception), executingStep.startedAt(), failedAt);
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
            return failed;
        }
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
}
