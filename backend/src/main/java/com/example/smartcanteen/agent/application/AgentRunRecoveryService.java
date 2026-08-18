package com.example.smartcanteen.agent.application;

import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.domain.RunStatus;
import com.example.smartcanteen.agent.port.AgentRunStore;
import com.example.smartcanteen.application.AgentSchedulerRolloutPolicy;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Reconciles execution Runs only after their persistence heartbeat and claim lease are stale. */
@Service
@ConditionalOnProperty(
        prefix = "agent.runtime.scheduler", name = "enabled", havingValue = "true")
public class AgentRunRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(AgentRunRecoveryService.class);
    private static final int MAX_BATCH_SIZE = 100;

    private final AgentRunStore runs;
    private final AgentRuntime runtime;
    private final AgentSchedulerRolloutPolicy rollout;
    private final Clock clock;
    private final Duration staleAfter;
    private final int batchSize;

    @Autowired
    public AgentRunRecoveryService(
            AgentRunStore runs,
            AgentRuntime runtime,
            AgentSchedulerRolloutPolicy rollout,
            @Value("${agent.runtime.scheduler.stale-after:PT2M}") Duration staleAfter,
            @Value("${agent.runtime.scheduler.batch-size:10}") int batchSize) {
        this(runs, runtime, rollout, Clock.systemUTC(), staleAfter, batchSize);
    }

    public AgentRunRecoveryService(
            AgentRunStore runs,
            AgentRuntime runtime,
            AgentSchedulerRolloutPolicy rollout,
            Clock clock,
            Duration staleAfter,
            int batchSize) {
        this.runs = Objects.requireNonNull(runs, "runs");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.rollout = Objects.requireNonNull(rollout, "rollout");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.staleAfter = requirePositive(staleAfter);
        this.batchSize = requireBatchSize(batchSize);
    }

    @Scheduled(fixedDelayString = "${agent.runtime.scheduler.recovery-interval-ms:10000}")
    public void recover() {
        recoverOnce();
    }

    public int recoverOnce() {
        if (!runs.supportsExecutionClaims()) {
            throw new IllegalStateException(
                    "Agent stale-run recovery requires a store with durable execution claims");
        }
        List<AgentRun> stale = runs.findStaleExecuting(
                clock.instant().minus(staleAfter), batchSize, rollout.allowedScopes());
        int recovered = 0;
        for (AgentRun run : stale) {
            try {
                rollout.requireEnabled(run.scope());
                AgentRun result = runtime.markReconciliationRequiredFromRecovery(
                        run.runId(),
                        run.version(),
                        recoveryContext(run),
                        recoveryIdempotencyKey(run));
                if (result.status() == RunStatus.RECONCILIATION_REQUIRED) {
                    recovered++;
                }
            } catch (RuntimeException exception) {
                // Optimistic versioning and the transactional Runtime method arbitrate races with
                // a manual recovery or another scheduler. Continue scanning the remaining Runs.
                log.warn("Agent stale-run recovery could not process runId={}", run.runId(), exception);
            }
        }
        return recovered;
    }

    private ExecutionContext recoveryContext(AgentRun run) {
        CanteenScope scope = run.scope();
        AuthPrincipal principal = new AuthPrincipal(
                run.actorUserId(),
                run.actorUsername(),
                run.actorUsername(),
                Role.SYSTEM_ADMIN,
                scope.schoolId(),
                scope.canteenId());
        return ExecutionContext.fromTrustedPrincipal(
                "agent-recovery-" + run.runId(),
                principal,
                scope,
                Set.of(Role.SYSTEM_ADMIN),
                Set.of(AgentRuntime.AGENT_RUN_RECOVERY_PERMISSION));
    }

    private String recoveryIdempotencyKey(AgentRun run) {
        return "agent-recovery-" + run.runId() + "-v" + run.version();
    }

    private static Duration requirePositive(Duration value) {
        Objects.requireNonNull(value, "staleAfter");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("staleAfter must be positive");
        }
        return value;
    }

    private static int requireBatchSize(int value) {
        if (value < 1 || value > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException("recovery batch size must be between 1 and 100");
        }
        return value;
    }
}
