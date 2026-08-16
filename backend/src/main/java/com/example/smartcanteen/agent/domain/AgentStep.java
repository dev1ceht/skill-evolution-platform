package com.example.smartcanteen.agent.domain;

import java.time.Instant;
import java.util.Objects;

public record AgentStep(
        String runId,
        String stepId,
        int stepOrder,
        String toolName,
        String idempotencyKey,
        String inputDigest,
        String status,
        int attemptCount,
        String resultJson,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt) {

    public AgentStep {
        requireText("runId", runId);
        requireText("stepId", stepId);
        requireText("toolName", toolName);
        requireText("idempotencyKey", idempotencyKey);
        requireText("inputDigest", inputDigest);
        requireText("status", status);
        if (stepOrder < 0 || attemptCount < 0) {
            throw new IllegalArgumentException("stepOrder and attemptCount must not be negative");
        }
    }

    private static void requireText(String name, String value) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
