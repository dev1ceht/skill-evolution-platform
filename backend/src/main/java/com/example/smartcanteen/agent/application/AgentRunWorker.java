package com.example.smartcanteen.agent.application;

import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.AgentRunClaim;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.port.AgentRunStore;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
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
    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(10);

    private final AgentRunStore runs;
    private final AgentExecutionService execution;
    private final Duration leaseDuration;
    private final Duration heartbeatInterval;
    private final ScheduledExecutorService heartbeatExecutor;

    @Autowired
    public AgentRunWorker(
            AgentRunStore runs,
            AgentExecutionService execution,
            @Value("${agent.runtime.execution-lease:PT30S}") Duration leaseDuration,
            @Value("${agent.runtime.execution-heartbeat:PT10S}") Duration heartbeatInterval) {
        this(runs, execution, leaseDuration, heartbeatInterval, newHeartbeatExecutor());
    }

    public AgentRunWorker(
            AgentRunStore runs,
            AgentExecutionService execution,
            Duration leaseDuration) {
        this(runs, execution, leaseDuration, defaultHeartbeatFor(leaseDuration), newHeartbeatExecutor());
    }

    AgentRunWorker(
            AgentRunStore runs,
            AgentExecutionService execution,
            Duration leaseDuration,
            Duration heartbeatInterval,
            ScheduledExecutorService heartbeatExecutor) {
        this.runs = Objects.requireNonNull(runs, "runs");
        this.execution = Objects.requireNonNull(execution, "execution");
        this.leaseDuration = requirePositive(leaseDuration);
        this.heartbeatInterval = requireHeartbeat(heartbeatInterval, this.leaseDuration);
        this.heartbeatExecutor = Objects.requireNonNull(heartbeatExecutor, "heartbeatExecutor");
    }

    public AgentRunWorker(AgentRunStore runs, AgentExecutionService execution) {
        this(runs, execution, DEFAULT_LEASE_DURATION, DEFAULT_HEARTBEAT_INTERVAL);
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
        ScheduledFuture<?> heartbeat = null;
        try {
            heartbeat = startHeartbeat(claim);
            AgentRun run = runs.findById(runId).orElseThrow(() ->
                    new AgentRunNotFoundException(runId));
            return execution.executeClaimed(run, context, claim);
        } finally {
            if (heartbeat != null) {
                heartbeat.cancel(false);
            }
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

    @PreDestroy
    public void shutdown() {
        heartbeatExecutor.shutdownNow();
    }

    private ScheduledFuture<?> startHeartbeat(AgentRunClaim claim) {
        long intervalMs = heartbeatInterval.toMillis();
        return heartbeatExecutor.scheduleAtFixedRate(
                () -> {
                    try {
                        if (!runs.renewExecutionClaim(claim, leaseDuration)) {
                            log.warn("Agent execution claim heartbeat was fenced runId={}", claim.runId());
                        }
                    } catch (RuntimeException exception) {
                        // The next fenced checkpoint remains the authority. A transient heartbeat
                        // error must not turn into an unfenced write or mask the tool result.
                        log.warn("Agent execution claim heartbeat failed runId={}", claim.runId(), exception);
                    }
                },
                intervalMs,
                intervalMs,
                TimeUnit.MILLISECONDS);
    }

    private static Duration requirePositive(Duration value) {
        Objects.requireNonNull(value, "leaseDuration");
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("Execution lease duration must be positive");
        }
        return value;
    }

    private static Duration requireHeartbeat(Duration heartbeat, Duration lease) {
        Duration value = requirePositive(heartbeat);
        if (!value.minus(lease).isNegative()) {
            throw new IllegalArgumentException("Execution heartbeat must be shorter than the lease");
        }
        if (value.toMillis() < 1) {
            throw new IllegalArgumentException("Execution heartbeat must be at least 1ms");
        }
        return value;
    }

    private static Duration defaultHeartbeatFor(Duration lease) {
        Duration value = requirePositive(lease).dividedBy(3);
        return value.toMillis() < 1 ? Duration.ofMillis(1) : value;
    }

    private static ScheduledExecutorService newHeartbeatExecutor() {
        AtomicLong sequence = new AtomicLong();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(
                    runnable,
                    "agent-run-heartbeat-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newScheduledThreadPool(1, factory);
    }

    private static void requireText(String name, String value) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
