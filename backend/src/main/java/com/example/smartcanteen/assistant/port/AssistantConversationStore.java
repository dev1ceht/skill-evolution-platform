package com.example.smartcanteen.assistant.port;

import com.example.smartcanteen.assistant.domain.AssistantConversation;
import com.example.smartcanteen.assistant.domain.AssistantClarification;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence seam for conversation ownership and append-only assistant turns. */
public interface AssistantConversationStore {

    AssistantConversation ensureConversation(String conversationId, ExecutionContext context, Instant now);

    Optional<AssistantConversation> findConversation(String conversationId);

    Optional<StoredTurn> findByIdempotency(
            String conversationId, String actorUserId, String idempotencyKey);

    List<StoredTurn> listTurns(String conversationId, int limit);

    /** Serializes clarification resolution and turn append for one conversation. */
    void lockConversation(String conversationId);

    void updateStatus(String conversationId, String status, Instant updatedAt);

    Optional<AssistantClarification> findClarification(String conversationId);

    void saveClarification(AssistantClarification clarification);

    void clearClarification(String conversationId);

    long nextSequence(String conversationId);

    void append(StoredTurn turn);

    record StoredTurn(
            String turnId,
            String conversationId,
            long sequence,
            String idempotencyKey,
            String requestHash,
            String message,
            String responseJson,
            String kind,
            String intent,
            String runId,
            String runStatus,
            Instant createdAt) {
    }
}
