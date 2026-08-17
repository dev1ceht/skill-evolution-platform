package com.example.smartcanteen.assistant.domain;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.domain.CanteenScope;
import java.time.Instant;
import java.util.Objects;

/** Durable owner and scope for an assistant conversation. */
public record AssistantConversation(
        String conversationId,
        String actorUserId,
        String actorUsername,
        CanteenScope scope,
        String status,
        Instant createdAt,
        Instant updatedAt) {

    public AssistantConversation {
        requireText("conversationId", conversationId, 64);
        requireText("actorUserId", actorUserId, 64);
        requireText("actorUsername", actorUsername, 128);
        Objects.requireNonNull(scope, "scope");
        requireText("status", status, 32);
        if (!status.equals("ACTIVE")
                && !status.equals("WAITING_CLARIFICATION")
                && !status.equals("CLOSED")) {
            throw new IllegalArgumentException("Unsupported conversation status: " + status);
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static AssistantConversation active(
            String conversationId, ExecutionContext context, Instant now) {
        return new AssistantConversation(
                conversationId,
                context.actorUserId(),
                context.actorUsername(),
                context.scope(),
                "ACTIVE",
                now,
                now);
    }

    private static void requireText(String name, String value, int maxLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must be non-blank and at most "
                    + maxLength + " characters");
        }
    }
}
