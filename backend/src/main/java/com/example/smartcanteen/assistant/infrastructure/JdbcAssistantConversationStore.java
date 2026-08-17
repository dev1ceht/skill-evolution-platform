package com.example.smartcanteen.assistant.infrastructure;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.assistant.domain.AssistantClarification;
import com.example.smartcanteen.assistant.domain.AssistantConversation;
import com.example.smartcanteen.assistant.domain.AssistantPendingAction;
import com.example.smartcanteen.assistant.port.AssistantConversationStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.ForbiddenException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** MySQL/H2 adapter for durable assistant conversations and turns. */
@Repository
public class JdbcAssistantConversationStore implements AssistantConversationStore {

    private final JdbcTemplate jdbc;

    public JdbcAssistantConversationStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AssistantConversation ensureConversation(
            String conversationId, ExecutionContext context, Instant now) {
        Optional<AssistantConversation> existing = findConversation(conversationId);
        if (existing.isPresent()) {
            AssistantConversation conversation = existing.get();
            if (!conversation.actorUserId().equals(context.actorUserId())
                    || !conversation.scope().equals(context.scope())) {
                throw new ForbiddenException("Conversation is outside the requested scope");
            }
            return conversation;
        }

        AssistantConversation created = AssistantConversation.active(conversationId, context, now);
        try {
            jdbc.update(
                    "INSERT INTO assistant_conversations (conversation_id, actor_user_id, "
                            + "actor_username, school_id, canteen_id, status, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    created.conversationId(),
                    created.actorUserId(),
                    created.actorUsername(),
                    created.scope().schoolId(),
                    created.scope().canteenId(),
                    created.status(),
                    Timestamp.from(created.createdAt()),
                    Timestamp.from(created.updatedAt()));
            return created;
        } catch (DuplicateKeyException duplicate) {
            AssistantConversation concurrent = findConversation(conversationId)
                    .orElseThrow(() -> duplicate);
            if (!concurrent.actorUserId().equals(context.actorUserId())
                    || !concurrent.scope().equals(context.scope())) {
                throw new ForbiddenException("Conversation is outside the requested scope");
            }
            return concurrent;
        }
    }

    @Override
    public Optional<StoredTurn> findByIdempotency(
            String conversationId, String actorUserId, String idempotencyKey) {
        return jdbc.query(
                        "SELECT t.turn_id, t.conversation_id, t.turn_sequence, t.idempotency_key, "
                                + "t.request_hash, t.message, t.response_json, t.kind, t.intent, "
                                + "t.run_id, t.run_status, t.created_at "
                                + "FROM assistant_turns t JOIN assistant_conversations c "
                                + "ON c.conversation_id = t.conversation_id "
                                + "WHERE t.conversation_id = ? AND c.actor_user_id = ? "
                                + "AND t.idempotency_key = ?",
                        this::mapTurn,
                        conversationId,
                        actorUserId,
                        idempotencyKey)
                .stream()
                .findFirst();
    }

    @Override
    public long nextSequence(String conversationId) {
        jdbc.queryForObject(
                "SELECT conversation_id FROM assistant_conversations "
                        + "WHERE conversation_id = ? FOR UPDATE",
                String.class,
                conversationId);
        Long next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(turn_sequence), 0) + 1 FROM assistant_turns "
                        + "WHERE conversation_id = ?",
                Long.class,
                conversationId);
        return next == null ? 1L : next;
    }

    @Override
    public void append(StoredTurn turn) {
        jdbc.update(
                "INSERT INTO assistant_turns (turn_id, conversation_id, turn_sequence, "
                        + "idempotency_key, request_hash, message, response_json, kind, intent, "
                        + "run_id, run_status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                turn.turnId(),
                turn.conversationId(),
                turn.sequence(),
                turn.idempotencyKey(),
                turn.requestHash(),
                turn.message(),
                turn.responseJson(),
                turn.kind(),
                turn.intent(),
                turn.runId(),
                turn.runStatus(),
                Timestamp.from(turn.createdAt()));
        jdbc.update(
                "UPDATE assistant_conversations SET updated_at = ? WHERE conversation_id = ?",
                Timestamp.from(turn.createdAt()),
                turn.conversationId());
    }

    @Override
    public Optional<AssistantConversation> findConversation(String conversationId) {
        return jdbc.query(
                        "SELECT conversation_id, actor_user_id, actor_username, school_id, "
                                + "canteen_id, status, created_at, updated_at "
                                + "FROM assistant_conversations WHERE conversation_id = ?",
                        this::mapConversation,
                        conversationId)
                .stream()
                .findFirst();
    }

    @Override
    public List<StoredTurn> listTurns(String conversationId, int limit) {
        return jdbc.query(
                "SELECT t.turn_id, t.conversation_id, t.turn_sequence, t.idempotency_key, "
                        + "t.request_hash, t.message, t.response_json, t.kind, t.intent, "
                        + "t.run_id, t.run_status, t.created_at "
                        + "FROM assistant_turns t WHERE t.conversation_id = ? "
                        + "ORDER BY t.turn_sequence ASC LIMIT ?",
                this::mapTurn,
                conversationId,
                limit);
    }

    @Override
    public void lockConversation(String conversationId) {
        jdbc.queryForObject(
                "SELECT conversation_id FROM assistant_conversations "
                        + "WHERE conversation_id = ? FOR UPDATE",
                String.class,
                conversationId);
    }

    @Override
    public void updateStatus(String conversationId, String status, Instant updatedAt) {
        jdbc.update(
                "UPDATE assistant_conversations SET status = ?, updated_at = ? "
                        + "WHERE conversation_id = ?",
                status,
                Timestamp.from(updatedAt),
                conversationId);
    }

    @Override
    public Optional<AssistantClarification> findClarification(String conversationId) {
        return jdbc.query(
                        "SELECT conversation_id, intent, original_message, missing_fields, "
                                + "created_at, updated_at FROM assistant_clarifications "
                                + "WHERE conversation_id = ?",
                        this::mapClarification,
                        conversationId)
                .stream()
                .findFirst();
    }

    @Override
    public void saveClarification(AssistantClarification clarification) {
        String missingFields = String.join(",", clarification.missingFields());
        int updated = jdbc.update(
                "UPDATE assistant_clarifications SET intent = ?, original_message = ?, "
                        + "missing_fields = ?, updated_at = ? WHERE conversation_id = ?",
                clarification.intent(),
                clarification.originalMessage(),
                missingFields,
                Timestamp.from(clarification.updatedAt()),
                clarification.conversationId());
        if (updated > 0) {
            return;
        }
        try {
            jdbc.update(
                    "INSERT INTO assistant_clarifications (conversation_id, intent, "
                            + "original_message, missing_fields, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    clarification.conversationId(),
                    clarification.intent(),
                    clarification.originalMessage(),
                    missingFields,
                    Timestamp.from(clarification.createdAt()),
                    Timestamp.from(clarification.updatedAt()));
        } catch (DuplicateKeyException duplicate) {
            jdbc.update(
                    "UPDATE assistant_clarifications SET intent = ?, original_message = ?, "
                            + "missing_fields = ?, updated_at = ? WHERE conversation_id = ?",
                    clarification.intent(),
                    clarification.originalMessage(),
                    missingFields,
                    Timestamp.from(clarification.updatedAt()),
                    clarification.conversationId());
        }
    }

    @Override
    public void clearClarification(String conversationId) {
        jdbc.update(
                "DELETE FROM assistant_clarifications WHERE conversation_id = ?",
                conversationId);
    }

    @Override
    public Optional<AssistantPendingAction> findPendingAction(String conversationId) {
        return jdbc.query(
                        "SELECT conversation_id, intent, run_id, run_version, menu_id, "
                                + "menu_version, plan_hash, created_at, updated_at "
                                + "FROM assistant_pending_actions WHERE conversation_id = ?",
                        this::mapPendingAction,
                        conversationId)
                .stream()
                .findFirst();
    }

    @Override
    public void savePendingAction(AssistantPendingAction action) {
        int updated = jdbc.update(
                "UPDATE assistant_pending_actions SET intent = ?, run_id = ?, run_version = ?, "
                        + "menu_id = ?, menu_version = ?, plan_hash = ?, updated_at = ? "
                        + "WHERE conversation_id = ?",
                action.intent(),
                action.runId(),
                action.runVersion(),
                action.menuId(),
                action.menuVersion(),
                action.planHash(),
                Timestamp.from(action.updatedAt()),
                action.conversationId());
        if (updated > 0) {
            return;
        }
        try {
            jdbc.update(
                    "INSERT INTO assistant_pending_actions (conversation_id, intent, run_id, "
                            + "run_version, menu_id, menu_version, plan_hash, created_at, updated_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    action.conversationId(),
                    action.intent(),
                    action.runId(),
                    action.runVersion(),
                    action.menuId(),
                    action.menuVersion(),
                    action.planHash(),
                    Timestamp.from(action.createdAt()),
                    Timestamp.from(action.updatedAt()));
        } catch (DuplicateKeyException duplicate) {
            jdbc.update(
                    "UPDATE assistant_pending_actions SET intent = ?, run_id = ?, run_version = ?, "
                            + "menu_id = ?, menu_version = ?, plan_hash = ?, updated_at = ? "
                            + "WHERE conversation_id = ?",
                    action.intent(),
                    action.runId(),
                    action.runVersion(),
                    action.menuId(),
                    action.menuVersion(),
                    action.planHash(),
                    Timestamp.from(action.updatedAt()),
                    action.conversationId());
        }
    }

    @Override
    public void clearPendingAction(String conversationId) {
        jdbc.update(
                "DELETE FROM assistant_pending_actions WHERE conversation_id = ?",
                conversationId);
    }

    private AssistantConversation mapConversation(ResultSet result, int row) throws SQLException {
        return new AssistantConversation(
                result.getString("conversation_id"),
                result.getString("actor_user_id"),
                result.getString("actor_username"),
                new CanteenScope(result.getString("school_id"), result.getString("canteen_id")),
                result.getString("status"),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant());
    }

    private StoredTurn mapTurn(ResultSet result, int row) throws SQLException {
        return new StoredTurn(
                result.getString("turn_id"),
                result.getString("conversation_id"),
                result.getLong("turn_sequence"),
                result.getString("idempotency_key"),
                result.getString("request_hash"),
                result.getString("message"),
                result.getString("response_json"),
                result.getString("kind"),
                result.getString("intent"),
                result.getString("run_id"),
                result.getString("run_status"),
                result.getTimestamp("created_at").toInstant());
    }

    private AssistantClarification mapClarification(ResultSet result, int row)
            throws SQLException {
        return new AssistantClarification(
                result.getString("conversation_id"),
                result.getString("intent"),
                result.getString("original_message"),
                Arrays.stream(result.getString("missing_fields").split(",", -1)).toList(),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant());
    }

    private AssistantPendingAction mapPendingAction(ResultSet result, int row)
            throws SQLException {
        return new AssistantPendingAction(
                result.getString("conversation_id"),
                result.getString("intent"),
                result.getString("run_id"),
                result.getLong("run_version"),
                result.getString("menu_id"),
                result.getLong("menu_version"),
                result.getString("plan_hash"),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant());
    }
}
