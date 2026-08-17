package com.example.smartcanteen.assistant.domain;

import java.time.Instant;
import java.util.Objects;

/** Durable preview awaiting an explicit Agent Run confirmation. */
public record AssistantPendingAction(
        String conversationId,
        String intent,
        String runId,
        long runVersion,
        String menuId,
        long menuVersion,
        String planHash,
        Instant createdAt,
        Instant updatedAt) {

    public AssistantPendingAction {
        requireText("conversationId", conversationId, 64);
        if (!"menu.publish".equals(intent)) {
            throw new IllegalArgumentException("Unsupported pending action intent: " + intent);
        }
        requireText("runId", runId, 64);
        requireText("menuId", menuId, 64);
        requireText("planHash", planHash, 64);
        if (runVersion < 0 || menuVersion < 0) {
            throw new IllegalArgumentException("Action versions cannot be negative");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (createdAt.isAfter(updatedAt)) {
            throw new IllegalArgumentException("createdAt cannot be after updatedAt");
        }
    }

    private static void requireText(String name, String value, int maxLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must be non-blank and at most "
                    + maxLength + " characters");
        }
    }
}
