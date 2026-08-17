package com.example.smartcanteen.agent.domain;

import java.time.Instant;
import java.util.Objects;

/** Fencing lease held by one worker while it executes an Agent Run. */
public record AgentRunClaim(
        String runId,
        String ownerId,
        String token,
        Instant claimedAt,
        Instant expiresAt) {

    public AgentRunClaim {
        requireText("runId", runId);
        requireText("ownerId", ownerId);
        requireText("token", token);
        Objects.requireNonNull(claimedAt, "claimedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(claimedAt)) {
            throw new IllegalArgumentException("expiresAt must be after claimedAt");
        }
    }

    private static void requireText(String name, String value) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
