package com.example.smartcanteen.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.smartcanteen.agent.application.AgentMetricsService;
import com.example.smartcanteen.agent.domain.AgentMetrics;
import com.example.smartcanteen.agent.port.AgentMetricsStore;
import com.example.smartcanteen.domain.CanteenScope;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentMetricsServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-17T10:00:00Z");
    private static final CanteenScope SCOPE = new CanteenScope("SCHOOL-METRICS", "CANTEEN-METRICS");

    @Test
    void aggregates_run_status_duration_confirmation_and_tool_evidence() {
        AgentMetricsStore store = (scope, from, to) -> new AgentMetricsStore.Snapshot(
                List.of(
                        run("RUN-1", "SUCCEEDED", "2026-08-17T09:00:00Z", "2026-08-17T09:00:02Z"),
                        run("RUN-2", "FAILED", "2026-08-17T09:01:00Z", "2026-08-17T09:01:05Z"),
                        run("RUN-3", "WAITING_CONFIRMATION", "2026-08-17T09:02:00Z", "2026-08-17T09:02:00Z")),
                List.of(
                        step("SUCCEEDED", "2026-08-17T09:00:00Z", "2026-08-17T09:00:02Z"),
                        step("FAILED", "2026-08-17T09:01:00Z", "2026-08-17T09:01:05Z")),
                List.of(
                        event("RUN-1", "RUN_IDEMPOTENCY_REPLAY", "SUCCEEDED", "SUCCEEDED", "2026-08-17T09:00:03Z"),
                        event("RUN-3", "RUN_PLANNED", null, "WAITING_CONFIRMATION", "2026-08-17T09:02:00Z"),
                        event("RUN-3", "RUN_CONFIRM", "WAITING_CONFIRMATION", "PLANNED", "2026-08-17T09:02:03Z")),
                4);
        AgentMetrics metrics = new AgentMetricsService(
                store, Clock.fixed(NOW, ZoneOffset.UTC)).collect(
                        SCOPE,
                        Instant.parse("2026-08-17T08:00:00Z"),
                        Instant.parse("2026-08-17T10:00:00Z"));

        assertThat(metrics.totalRuns()).isEqualTo(3);
        assertThat(metrics.succeededRuns()).isEqualTo(1);
        assertThat(metrics.failedRuns()).isEqualTo(1);
        assertThat(metrics.waitingConfirmationRuns()).isEqualTo(1);
        assertThat(metrics.successRate()).isCloseTo(1.0 / 3.0, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(metrics.averageRunDurationMs()).isEqualTo(3500);
        assertThat(metrics.averageConfirmationWaitMs()).isEqualTo(3000);
        assertThat(metrics.toolExecutions()).isEqualTo(2);
        assertThat(metrics.toolFailures()).isEqualTo(1);
        assertThat(metrics.averageToolDurationMs()).isEqualTo(3500);
        assertThat(metrics.idempotencyReplayCount()).isEqualTo(1);
        assertThat(metrics.authorizationDeniedCount()).isEqualTo(4);
    }

    @Test
    void uses_a_last_day_window_by_default_and_rejects_oversized_or_reversed_windows() {
        AgentMetricsStore store = (scope, from, to) -> new AgentMetricsStore.Snapshot(
                List.of(), List.of(), List.of(), 0);
        AgentMetricsService service = new AgentMetricsService(
                store, Clock.fixed(NOW, ZoneOffset.UTC));

        AgentMetrics metrics = service.collect(SCOPE, null, null);
        assertThat(metrics.from()).isEqualTo(NOW.minusSeconds(24 * 60 * 60));
        assertThat(metrics.to()).isEqualTo(NOW);

        assertThatThrownBy(() -> service.collect(SCOPE, NOW, NOW.minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("before");
        assertThatThrownBy(() -> service.collect(SCOPE, NOW.minusSeconds(32 * 24 * 60 * 60L), NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("31 days");
    }

    @Test
    void carries_cross_window_confirmation_and_uses_own_step_boundaries() {
        AgentMetricsStore store = (scope, from, to) -> new AgentMetricsStore.Snapshot(
                List.of(run("RUN-IN-WINDOW", "SUCCEEDED", "2026-08-17T08:10:00Z", "2026-08-17T08:10:02Z")),
                List.of(run("RUN-COMPLETED-IN-WINDOW", "SUCCEEDED", "2026-08-17T07:00:00Z", "2026-08-17T08:30:00Z")),
                List.of(
                        step("SUCCEEDED", "2026-08-17T07:59:00Z", "2026-08-17T08:01:00Z"),
                        step("SUCCEEDED", "2026-08-17T08:10:00Z", "2026-08-17T08:11:00Z")),
                List.of(
                        event("RUN-COMPLETED-IN-WINDOW", "RUN_PLANNED", null,
                                "WAITING_CONFIRMATION", "2026-08-17T07:30:00Z"),
                        event("RUN-COMPLETED-IN-WINDOW", "RUN_CONFIRM", "WAITING_CONFIRMATION",
                                "PLANNED", "2026-08-17T08:30:00Z"),
                        event("RUN-IN-WINDOW", "RUN_IDEMPOTENCY_REPLAY", "SUCCEEDED", "SUCCEEDED",
                                "2026-08-17T09:00:00Z")),
                2,
                1);

        AgentMetrics metrics = new AgentMetricsService(
                store, Clock.fixed(NOW, ZoneOffset.UTC)).collect(
                        SCOPE,
                        Instant.parse("2026-08-17T08:00:00Z"),
                        Instant.parse("2026-08-17T10:00:00Z"));

        assertThat(metrics.totalRuns()).isEqualTo(1);
        assertThat(metrics.waitingConfirmationRuns()).isEqualTo(1);
        assertThat(metrics.averageConfirmationWaitMs()).isEqualTo(60 * 60 * 1000);
        assertThat(metrics.averageRunDurationMs()).isEqualTo(90 * 60 * 1000);
        assertThat(metrics.toolExecutions()).isEqualTo(1);
        assertThat(metrics.averageToolDurationMs()).isEqualTo(90 * 1000);
        assertThat(metrics.idempotencyReplayCount()).isEqualTo(1);
    }

    private static AgentMetricsStore.RunRecord run(
            String id, String status, String created, String updated) {
        return new AgentMetricsStore.RunRecord(
                id, status, Instant.parse(created), Instant.parse(updated));
    }

    private static AgentMetricsStore.StepRecord step(
            String status, String started, String finished) {
        return new AgentMetricsStore.StepRecord(
                status, Instant.parse(started), Instant.parse(finished));
    }

    private static AgentMetricsStore.EventRecord event(
            String runId, String type, String from, String to, String occurred) {
        return new AgentMetricsStore.EventRecord(
                runId, type, from, to, Instant.parse(occurred));
    }
}
