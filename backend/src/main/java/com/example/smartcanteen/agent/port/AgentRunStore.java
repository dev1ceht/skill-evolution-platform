package com.example.smartcanteen.agent.port;

import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.AgentStep;
import com.example.smartcanteen.agent.domain.AgentRunDecision;
import com.example.smartcanteen.agent.domain.AgentRunEvent;
import com.example.smartcanteen.domain.CanteenScope;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

public interface AgentRunStore {

    Optional<AgentRun> findByIdempotency(
            String actorUserId, CanteenScope scope, String idempotencyKey);

    Optional<AgentRun> findById(String runId);

    /**
     * Inserts the bootstrap atomically and returns the durable row selected under the idempotency
     * key. The returned run is this caller's candidate when it won, or the existing winner when
     * another transaction (including the current transaction) already owns the key.
     */
    AgentRun insert(AgentRun run, List<AgentStep> steps);

    void update(AgentRun expected, AgentRun updated);

    void updateStep(AgentStep step);

    default void markStepReconciliationRequired(
            String runId, String stepId, String errorCode, String errorMessage, Instant finishedAt) {
        // In-memory/fake stores can opt out; JDBC persists the recovery checkpoint.
    }

    void appendDecision(AgentRunDecision decision);

    default Optional<AgentRunDecision> findDecisionByIdempotency(
            String runId, String actorUserId, String idempotencyKey) {
        return Optional.empty();
    }

    List<AgentRunDecision> listDecisions(String runId);

    List<AgentRunEvent> listEvents(String runId);

    default List<AgentRun> findStaleExecuting(Instant cutoff) {
        return List.of();
    }

    void appendEvent(
            String runId,
            String eventType,
            String fromStatus,
            String toStatus,
            String actorUserId,
            String payloadJson);
}
