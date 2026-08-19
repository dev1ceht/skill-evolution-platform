package com.example.smartcanteen.agent.port;

import com.example.smartcanteen.domain.CanteenScope;
import java.time.Instant;
import java.util.List;

/** Durable read boundary for bounded Agent Runtime metrics. */
public interface AgentMetricsStore {

    Snapshot collect(CanteenScope scope, Instant from, Instant to);

    record Snapshot(
            List<RunRecord> runs,
            List<RunRecord> terminalRuns,
            List<StepRecord> steps,
            List<EventRecord> events,
            long authorizationDeniedCount,
            long currentWaitingConfirmationRuns) {
        public Snapshot {
            runs = List.copyOf(runs == null ? List.of() : runs);
            terminalRuns = List.copyOf(terminalRuns == null ? List.of() : terminalRuns);
            steps = List.copyOf(steps == null ? List.of() : steps);
            events = List.copyOf(events == null ? List.of() : events);
        }

    }

    record RunRecord(String runId, String status, Instant createdAt, Instant updatedAt) {
    }

    record StepRecord(String status, Instant startedAt, Instant finishedAt) {
    }

    record EventRecord(
            String runId,
            String eventType,
            String fromStatus,
            String toStatus,
            Instant occurredAt) {
    }
}
