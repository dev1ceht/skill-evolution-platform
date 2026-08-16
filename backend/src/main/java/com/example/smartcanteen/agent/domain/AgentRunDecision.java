package com.example.smartcanteen.agent.domain;

import java.time.Instant;
import java.util.Objects;

/** Immutable record of a run-level confirmation, rejection, or cancellation. */
public record AgentRunDecision(
        String decisionId,
        String runId,
        String idempotencyKey,
        String decisionType,
        String outcome,
        String actorUserId,
        String planHash,
        String requestHash,
        String comment,
        Instant expiresAt,
        Instant createdAt) {

    public AgentRunDecision {
        requireText("decisionId", decisionId);
        requireText("runId", runId);
        requireText("idempotencyKey", idempotencyKey);
        if (idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey exceeds 128 characters");
        }
        requireText("decisionType", decisionType);
        requireText("outcome", outcome);
        requireText("actorUserId", actorUserId);
        requireText("planHash", planHash);
        if (requestHash != null && requestHash.length() != 64) {
            throw new IllegalArgumentException("requestHash must be a SHA-256 digest");
        }
        if (comment != null && comment.length() > 500) {
            throw new IllegalArgumentException("comment exceeds 500 characters");
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }

    private static void requireText(String name, String value) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
