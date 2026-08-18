package com.example.smartcanteen.agent.application;

import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.AgentRunClaim;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.port.AgentRunStore;
import java.time.Duration;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claim-aware worker entry point for asynchronous Agent Run execution.
 *
 * <p>The worker method suspends any ambient transaction. Claim acquisition, each fenced state
 * checkpoint, and release therefore happen in independent short transactions; a tool call never
 * keeps a database transaction open.
 */
@Service
public class AgentRunWorker {

    private static final Logger log = LoggerFactory.getLogger(AgentRunWorker.class);
    private static final Duration DEFAULT_LEASE_DURATION = Duration.ofSeconds(30);

    private final AgentRunStore runs;
    private final AgentExecutionService execution;
    private final Duration leaseDuration;

    @Autowired
    public AgentRunWorker(
            AgentRunStore runs,
            AgentExecutionService execution,
            @Value("${agent.runtime.execution-lease:PT30S}") Duration leaseDuration) {
        this.runs = Objects.requireNonNull(runs, "runs");
        this.execution = Objects.requireNonNull(execution, "execution");
        this.leaseDuration = requirePositive(leaseDuration);
    }

    public AgentRunWorker(AgentRunStore runs, AgentExecutionService execution) {
        this(runs, execution, DEFAULT_LEASE_DURATION);
    }

    /** Claims a planned Run, executes it with fenced writes, and releases the claim. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AgentRun claimAndExecute(
            String runId, String ownerId, ExecutionContext context) {
        requireText("runId", runId);
        requireText("ownerId", ownerId);
        Objects.requireNonNull(context, "context");
        if (!runs.supportsExecutionClaims()) {
            throw new IllegalStateException(
                    "Agent Run worker requires a store with durable execution claims");
        }
        AgentRun candidate = runs.findById(runId).orElseThrow(() ->
                new AgentRunNotFoundException(runId));
        execution.validateExecutable(candidate, context);
        AgentRunClaim claim = runs.claimExecution(runId, ownerId, leaseDuration)
                .orElseThrow(() -> new AgentRunClaimUnavailableException(runId));
        try {
            AgentRun run = runs.findById(runId).orElseThrow(() ->
                    new AgentRunNotFoundException(runId));
            return execution.executeClaimed(run, context, claim);
        } finally {
            try {
                if (!runs.releaseExecutionClaim(claim)) {
                    log.warn("Agent execution claim release was fenced runId={}", claim.runId());
                }
            } catch (RuntimeException exception) {
                // Do not mask the execution result; an unreleased claim will expire and can be
                // recovered by the future stale-run/worker handoff.
                log.warn("Agent execution claim release failed runId={}", claim.runId(), exception);
            }
        }
    }

    private static Duration requirePositive(Duration value) {
        Objects.requireNonNull(value, "leaseDuration");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("Execution lease duration must be positive");
        }
        return value;
    }

    private static void requireText(String name, String value) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
