package com.example.smartcanteen.agent.domain;

import java.time.Instant;

/**
 * Bounded, scope-specific Agent Runtime metrics for an operator dashboard.
 *
 * <p>The values are intentionally aggregates: no user IDs, run IDs, menu IDs or raw error
 * messages are exposed by this view.
 */
public record AgentMetrics(
        String schoolId,
        String canteenId,
        Instant from,
        Instant to,
        long totalRuns,
        long succeededRuns,
        long failedRuns,
        long rejectedRuns,
        long cancelledRuns,
        long timedOutRuns,
        long reconciliationRequiredRuns,
        long waitingConfirmationRuns,
        double successRate,
        long averageRunDurationMs,
        long averageConfirmationWaitMs,
        long toolExecutions,
        long toolFailures,
        long averageToolDurationMs,
        long idempotencyReplayCount,
        long authorizationDeniedCount) {
}
