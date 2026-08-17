package com.example.smartcanteen.assistant.domain;

import java.time.Instant;
import java.util.List;

/** Read-only transcript projection for one owned assistant conversation. */
public record AssistantConversationHistory(
        String conversationId,
        String status,
        Instant createdAt,
        Instant updatedAt,
        List<Entry> turns) {

    public AssistantConversationHistory {
        turns = turns == null ? List.of() : List.copyOf(turns);
    }

    public static AssistantConversationHistory empty(String conversationId) {
        return new AssistantConversationHistory(conversationId, "NEW", null, null, List.of());
    }

    public record Entry(String userMessage, AssistantTurn response) {
    }
}
