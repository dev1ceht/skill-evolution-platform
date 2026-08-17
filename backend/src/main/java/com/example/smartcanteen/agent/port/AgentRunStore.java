package com.example.smartcanteen.agent.port;

import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.AgentRunClaim;
import com.example.smartcanteen.agent.domain.AgentRunDecision;
import com.example.smartcanteen.agent.domain.AgentRunEvent;
import com.example.smartcanteen.agent.domain.AgentStep;
import com.example.smartcanteen.domain.CanteenScope;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    /** Returns true when this store provides durable, fenced execution claims. */
    default boolean supportsExecutionClaims() {
        return false;
    }

    /** Claims a planned Run for one worker for the supplied lease duration. */
    default Optional<AgentRunClaim> claimExecution(
            String runId, String ownerId, Duration leaseDuration) {
        return Optional.empty();
    }

    /** Extends a still-owned claim; implementations return false after fencing or expiry. */
    default boolean renewExecutionClaim(AgentRunClaim claim, Duration leaseDuration) {
        return false;
    }

    /** Releases a claim only when its owner and fencing token still match. */
    default boolean releaseExecutionClaim(AgentRunClaim claim) {
        return false;
    }

    /** Persists a Run transition while holding the supplied fencing claim. */
    default void updateClaimed(AgentRun expected, AgentRun updated, AgentRunClaim claim) {
        throw claimsUnsupported();
    }

    /** Persists a Step checkpoint while holding the supplied fencing claim. */
    default void updateStepClaimed(AgentStep step, AgentRunClaim claim) {
        throw claimsUnsupported();
    }

    /** Appends an event while holding the supplied fencing claim. */
    default void appendEventClaimed(
            String runId,
            String eventType,
            String fromStatus,
            String toStatus,
            String actorUserId,
            String payloadJson,
            AgentRunClaim claim) {
        throw claimsUnsupported();
    }

    private static UnsupportedOperationException claimsUnsupported() {
        return new UnsupportedOperationException(
                "This AgentRunStore does not support execution claim fencing");
    }

    void appendEvent(
            String runId,
            String eventType,
            String fromStatus,
            String toStatus,
            String actorUserId,
            String payloadJson);
}
