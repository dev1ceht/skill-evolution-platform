package com.example.smartcanteen.agent.application;

import com.example.smartcanteen.agent.domain.AgentMetrics;
import com.example.smartcanteen.agent.port.AgentMetricsStore;
import com.example.smartcanteen.domain.CanteenScope;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Builds a bounded, scope-specific metrics view from durable Run/Step/Event evidence. */
@Service
public class AgentMetricsService {

    private static final Duration DEFAULT_WINDOW = Duration.ofHours(24);
    private static final Duration MAX_WINDOW = Duration.ofDays(31);
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "SUCCEEDED", "FAILED", "REJECTED", "CANCELLED", "TIMED_OUT",
            "RECONCILIATION_REQUIRED");
    private static final Set<String> TOOL_FAILURE_STATUSES = Set.of(
            "FAILED", "TIMED_OUT", "RECONCILIATION_REQUIRED");

    private final AgentMetricsStore store;
    private final Clock clock;

    @Autowired
    public AgentMetricsService(AgentMetricsStore store) {
        this(store, Clock.systemUTC());
    }

    public AgentMetricsService(AgentMetricsStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public AgentMetrics collect(CanteenScope scope, Instant from, Instant to) {
        Objects.requireNonNull(scope, "scope");
        Instant resolvedTo = to == null ? clock.instant() : to;
        Instant resolvedFrom = from == null ? resolvedTo.minus(DEFAULT_WINDOW) : from;
        validateWindow(resolvedFrom, resolvedTo);

        AgentMetricsStore.Snapshot snapshot = store.collect(scope, resolvedFrom, resolvedTo);
        Map<String, Long> statusCounts = new HashMap<>();
        snapshot.runs().forEach(run ->
                statusCounts.merge(run.status(), 1L, Long::sum));

        long totalRuns = snapshot.runs().size();
        long succeededRuns = count(statusCounts, "SUCCEEDED");
        long terminalRunSamples = 0;
        long totalRunDurationMs = 0;
        for (AgentMetricsStore.RunRecord run : snapshot.terminalRuns()) {
            if (!TERMINAL_STATUSES.contains(run.status())
                    || run.createdAt() == null
                    || run.updatedAt() == null) {
                continue;
            }
            long duration = durationMs(run.createdAt(), run.updatedAt());
            if (duration >= 0) {
                totalRunDurationMs += duration;
                terminalRunSamples++;
            }
        }

        long waitingRuns = snapshot.currentWaitingConfirmationRuns();
        long confirmationWaitMs = 0;
        long confirmationSamples = 0;
        Map<String, Instant> waitingSince = new HashMap<>();
        for (AgentMetricsStore.EventRecord event : snapshot.events()) {
            boolean entersWaiting = "WAITING_CONFIRMATION".equals(event.toStatus())
                    && !"WAITING_CONFIRMATION".equals(event.fromStatus());
            boolean leavesWaiting = "WAITING_CONFIRMATION".equals(event.fromStatus())
                    && !"WAITING_CONFIRMATION".equals(event.toStatus());
            if (entersWaiting) {
                waitingSince.put(event.runId(), event.occurredAt());
            } else if (leavesWaiting) {
                Instant started = waitingSince.remove(event.runId());
                if (started != null && event.occurredAt() != null) {
                    long duration = durationMs(started, event.occurredAt());
                    if (duration >= 0) {
                        confirmationWaitMs += duration;
                        confirmationSamples++;
                    }
                }
            }
        }

        long toolExecutions = 0;
        long toolFailures = 0;
        long toolDurationMs = 0;
        long toolDurationSamples = 0;
        for (AgentMetricsStore.StepRecord step : snapshot.steps()) {
            boolean startedInWindow = within(step.startedAt(), resolvedFrom, resolvedTo);
            boolean finishedInWindow = within(step.finishedAt(), resolvedFrom, resolvedTo);
            if (startedInWindow) {
                toolExecutions++;
            }
            if (TOOL_FAILURE_STATUSES.contains(step.status())
                    && (startedInWindow || finishedInWindow)) {
                toolFailures++;
            }
            if (step.startedAt() != null && finishedInWindow) {
                long duration = durationMs(step.startedAt(), step.finishedAt());
                if (duration >= 0) {
                    toolDurationMs += duration;
                    toolDurationSamples++;
                }
            }
        }

        long replayCount = snapshot.events().stream()
                .filter(event -> "RUN_IDEMPOTENCY_REPLAY".equals(event.eventType())
                        && within(event.occurredAt(), resolvedFrom, resolvedTo))
                .count();

        return new AgentMetrics(
                scope.schoolId(),
                scope.canteenId(),
                resolvedFrom,
                resolvedTo,
                totalRuns,
                succeededRuns,
                count(statusCounts, "FAILED"),
                count(statusCounts, "REJECTED"),
                count(statusCounts, "CANCELLED"),
                count(statusCounts, "TIMED_OUT"),
                count(statusCounts, "RECONCILIATION_REQUIRED"),
                waitingRuns,
                totalRuns == 0 ? 0.0 : (double) succeededRuns / totalRuns,
                average(totalRunDurationMs, terminalRunSamples),
                average(confirmationWaitMs, confirmationSamples),
                toolExecutions,
                toolFailures,
                average(toolDurationMs, toolDurationSamples),
                replayCount,
                snapshot.authorizationDeniedCount());
    }

    private static void validateWindow(Instant from, Instant to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("Metrics from must be before or equal to to");
        }
        if (Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
            throw new IllegalArgumentException("Metrics window cannot exceed 31 days");
        }
    }

    private static long count(Map<String, Long> counts, String status) {
        return counts.getOrDefault(status, 0L);
    }

    private static long average(long total, long samples) {
        return samples == 0 ? 0 : Math.round((double) total / samples);
    }

    private static long durationMs(Instant from, Instant to) {
        return Duration.between(from, to).toMillis();
    }

    private static boolean within(Instant value, Instant from, Instant to) {
        return value != null && !value.isBefore(from) && value.isBefore(to);
    }
}
