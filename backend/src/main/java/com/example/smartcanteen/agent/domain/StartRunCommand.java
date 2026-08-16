package com.example.smartcanteen.agent.domain;

import java.util.Objects;

/** Structured Agent input. Identity and scope are deliberately supplied by ExecutionContext. */
public record StartRunCommand(
        String requestId,
        String intent,
        String inputJson,
        String idempotencyKey) {

    public StartRunCommand {
        requireText("requestId", requestId);
        requireText("intent", intent);
        requireText("inputJson", inputJson);
        requireText("idempotencyKey", idempotencyKey);
        if (idempotencyKey.length() > 128) {
            throw new IllegalArgumentException("idempotencyKey must be at most 128 characters");
        }
    }

    private static void requireText(String name, String value) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
