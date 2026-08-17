package com.example.smartcanteen.assistant.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Durable state for a conversation that is waiting for one or more missing fields. */
public record AssistantClarification(
        String conversationId,
        String intent,
        String originalMessage,
        List<String> missingFields,
        Instant createdAt,
        Instant updatedAt) {

    public AssistantClarification {
        requireText("conversationId", conversationId, 64);
        requireText("intent", intent, 128);
        if (!intent.equals("traceability.query") && !intent.equals("menu.query")) {
            throw new IllegalArgumentException("Unsupported clarification intent: " + intent);
        }
        requireText("originalMessage", originalMessage, 2000);
        Objects.requireNonNull(missingFields, "missingFields");
        missingFields = missingFields.stream()
                .map(field -> requireText("missingField", field, 64))
                .distinct()
                .toList();
        if (missingFields.isEmpty()) {
            throw new IllegalArgumentException("missingFields must not be empty");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (createdAt.isAfter(updatedAt)) {
            throw new IllegalArgumentException("createdAt must not be after updatedAt");
        }
    }

    private static String requireText(String name, String value, int maxLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must be non-blank and at most "
                    + maxLength + " characters");
        }
        return value;
    }
}
