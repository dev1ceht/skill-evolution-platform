package com.example.smartcanteen.agent.infrastructure;

import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.AgentStep;
import com.example.smartcanteen.agent.domain.AgentRunDecision;
import com.example.smartcanteen.agent.domain.AgentRunEvent;
import com.example.smartcanteen.agent.domain.RunStatus;
import com.example.smartcanteen.agent.port.AgentRunStore;
import com.example.smartcanteen.domain.CanteenScope;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAgentRunStore implements AgentRunStore {

    private final JdbcTemplate jdbc;

    public JdbcAgentRunStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<AgentRun> findByIdempotency(
            String actorUserId, CanteenScope scope, String idempotencyKey) {
        return jdbc.query(
                        "SELECT * FROM agent_runs WHERE actor_user_id = ? AND school_id = ? "
                                + "AND canteen_id = ? AND idempotency_key = ?",
                        this::map,
                        actorUserId,
                        scope.schoolId(),
                        scope.canteenId(),
                        idempotencyKey)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<AgentRun> findById(String runId) {
        return jdbc.query("SELECT * FROM agent_runs WHERE run_id = ?", this::map, runId)
                .stream()
                .findFirst();
    }

    @Override
    public void insert(AgentRun run, List<AgentStep> steps) {
        jdbc.update(
                "INSERT INTO agent_runs (run_id, idempotency_key, request_hash, actor_user_id, "
                        + "actor_username, school_id, canteen_id, intent, skill_id, skill_version, "
                        + "manifest_digest, plan_hash, plan_json, input_json, status, current_step, "
                        + "result_json, error_code, error_message, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                run.runId(),
                run.idempotencyKey(),
                run.requestHash(),
                run.actorUserId(),
                run.actorUsername(),
                run.scope().schoolId(),
                run.scope().canteenId(),
                run.intent(),
                run.skillId(),
                run.skillVersion(),
                run.manifestDigest(),
                run.planHash(),
                run.planJson(),
                run.inputJson(),
                run.status().name(),
                run.currentStep(),
                run.resultJson(),
                run.errorCode(),
                run.errorMessage(),
                run.version(),
                Timestamp.from(run.createdAt()),
                Timestamp.from(run.updatedAt()));
        jdbc.update(
                "INSERT INTO agent_run_events (event_id, run_id, event_sequence, event_type, "
                        + "from_status, to_status, actor_user_id, payload_json, occurred_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "EVENT-" + run.runId(),
                run.runId(),
                1L,
                "RUN_PLANNED",
                null,
                run.status().name(),
                run.actorUserId(),
                run.planJson(),
                Timestamp.from(run.updatedAt()));
        for (AgentStep step : steps == null ? List.<AgentStep>of() : steps) {
            jdbc.update(
                    "INSERT INTO agent_steps (run_id, step_id, step_order, tool_name, status, "
                            + "idempotency_key, input_digest, attempt_count, result_json, error_code, "
                            + "error_message, started_at, finished_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    step.runId(),
                    step.stepId(),
                    step.stepOrder(),
                    step.toolName(),
                    step.status(),
                    step.idempotencyKey(),
                    step.inputDigest(),
                    step.attemptCount(),
                    step.resultJson(),
                    step.errorCode(),
                    step.errorMessage(),
                    step.startedAt() == null ? null : Timestamp.from(step.startedAt()),
                    step.finishedAt() == null ? null : Timestamp.from(step.finishedAt()));
        }
    }

    @Override
    public void update(AgentRun expected, AgentRun updated) {
        if (!expected.runId().equals(updated.runId())) {
            throw new IllegalArgumentException("Agent Run identity cannot change during an update");
        }
        if (expected.version() + 1 != updated.version()) {
            throw new IllegalArgumentException("Agent Run version must increment exactly once");
        }
        if (!expected.status().canTransitionTo(updated.status())) {
            throw new IllegalStateException(
                    "Invalid Agent Run transition: " + expected.status() + " -> " + updated.status());
        }
        int changed = jdbc.update(
                "UPDATE agent_runs SET status = ?, current_step = ?, result_json = ?, "
                        + "error_code = ?, error_message = ?, version = ?, updated_at = ? "
                        + "WHERE run_id = ? AND version = ?",
                updated.status().name(),
                updated.currentStep(),
                updated.resultJson(),
                updated.errorCode(),
                updated.errorMessage(),
                updated.version(),
                Timestamp.from(updated.updatedAt()),
                expected.runId(),
                expected.version());
        if (changed != 1) {
            throw new IllegalStateException("Agent Run was changed by another executor: " + expected.runId());
        }
    }

    @Override
    public void updateStep(AgentStep step) {
        int changed = jdbc.update(
                "UPDATE agent_steps SET status = ?, attempt_count = ?, result_json = ?, "
                        + "error_code = ?, error_message = ?, started_at = ?, finished_at = ? "
                        + "WHERE run_id = ? AND step_id = ?",
                step.status(),
                step.attemptCount(),
                step.resultJson(),
                step.errorCode(),
                step.errorMessage(),
                step.startedAt() == null ? null : Timestamp.from(step.startedAt()),
                step.finishedAt() == null ? null : Timestamp.from(step.finishedAt()),
                step.runId(),
                step.stepId());
        if (changed != 1) {
            throw new IllegalStateException("Agent Step was not found: " + step.runId() + "/" + step.stepId());
        }
    }

    @Override
    public void markStepReconciliationRequired(
            String runId, String stepId, String errorCode, String errorMessage, Instant finishedAt) {
        int changed = jdbc.update(
                "UPDATE agent_steps SET status = 'RECONCILIATION_REQUIRED', error_code = ?, "
                        + "error_message = ?, finished_at = ? WHERE run_id = ? AND step_id = ?",
                errorCode,
                errorMessage,
                Timestamp.from(finishedAt),
                runId,
                stepId);
        if (changed != 1) {
            throw new IllegalStateException("Agent Step was not found: " + runId + "/" + stepId);
        }
    }

    @Override
    public void appendDecision(AgentRunDecision decision) {
        jdbc.update(
                "INSERT INTO agent_run_decisions (decision_id, run_id, decision_type, outcome, "
                        + "actor_user_id, idempotency_key, plan_hash, request_hash, expires_at, comment, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" ,
                decision.decisionId(),
                decision.runId(),
                decision.decisionType(),
                decision.outcome(),
                decision.actorUserId(),
                decision.idempotencyKey(),
                decision.planHash(),
                decision.requestHash(),
                decision.expiresAt() == null ? null : Timestamp.from(decision.expiresAt()),
                decision.comment(),
                Timestamp.from(decision.createdAt()));
    }

    @Override
    public Optional<AgentRunDecision> findDecisionByIdempotency(
            String runId, String actorUserId, String idempotencyKey) {
        return jdbc.query(
                        "SELECT decision_id, run_id, idempotency_key, decision_type, outcome, "
                                + "actor_user_id, plan_hash, request_hash, expires_at, comment, created_at "
                                + "FROM agent_run_decisions WHERE run_id = ? AND actor_user_id = ? "
                                + "AND idempotency_key = ?",
                        this::mapDecision,
                        runId,
                        actorUserId,
                        idempotencyKey)
                .stream()
                .findFirst();
    }

    @Override
    public List<AgentRunDecision> listDecisions(String runId) {
        return jdbc.query(
                "SELECT decision_id, run_id, idempotency_key, decision_type, outcome, actor_user_id, plan_hash, "
                        + "request_hash, expires_at, comment, created_at FROM agent_run_decisions "
                        + "WHERE run_id = ? ORDER BY created_at, decision_id",
                (result, row) -> mapDecision(result, row),
                runId);
    }

    private AgentRunDecision mapDecision(ResultSet result, int row) throws SQLException {
        return new AgentRunDecision(
                result.getString("decision_id"),
                result.getString("run_id"),
                result.getString("idempotency_key"),
                result.getString("decision_type"),
                result.getString("outcome"),
                result.getString("actor_user_id"),
                result.getString("plan_hash"),
                result.getString("request_hash"),
                result.getString("comment"),
                result.getTimestamp("expires_at") == null
                        ? null : result.getTimestamp("expires_at").toInstant(),
                result.getTimestamp("created_at").toInstant());
    }

    @Override
    public List<AgentRunEvent> listEvents(String runId) {
        return jdbc.query(
                "SELECT event_id, run_id, event_sequence, event_type, from_status, to_status, "
                        + "actor_user_id, payload_json, occurred_at FROM agent_run_events "
                        + "WHERE run_id = ? ORDER BY event_sequence",
                (result, row) -> new AgentRunEvent(
                        result.getString("event_id"),
                        result.getString("run_id"),
                        result.getLong("event_sequence"),
                        result.getString("event_type"),
                        result.getString("from_status"),
                        result.getString("to_status"),
                        result.getString("actor_user_id"),
                        result.getString("payload_json"),
                        result.getTimestamp("occurred_at").toInstant()),
                runId);
    }

    @Override
    public List<AgentRun> findStaleExecuting(Instant cutoff) {
        return jdbc.query(
                "SELECT * FROM agent_runs WHERE status = 'EXECUTING' AND updated_at < ? "
                        + "ORDER BY updated_at, run_id",
                this::map,
                Timestamp.from(cutoff));
    }

    @Override
    public void appendEvent(
            String runId,
            String eventType,
            String fromStatus,
            String toStatus,
            String actorUserId,
            String payloadJson) {
        // Serialize sequence allocation on the parent Run row. A bare MAX()+1 is
        // racy when a decision and a recovery command append events concurrently.
        jdbc.queryForObject(
                "SELECT run_id FROM agent_runs WHERE run_id = ? FOR UPDATE",
                String.class,
                runId);
        Long nextSequence = jdbc.queryForObject(
                "SELECT COALESCE(MAX(event_sequence), 0) + 1 FROM agent_run_events WHERE run_id = ?",
                Long.class,
                runId);
        jdbc.update(
                "INSERT INTO agent_run_events (event_id, run_id, event_sequence, event_type, "
                        + "from_status, to_status, actor_user_id, payload_json, occurred_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                "EVENT-" + runId + "-" + Objects.requireNonNull(nextSequence),
                runId,
                nextSequence,
                eventType,
                fromStatus,
                toStatus,
                actorUserId,
                payloadJson);
    }

    private AgentRun map(ResultSet result, int row) throws SQLException {
        return new AgentRun(
                result.getString("run_id"),
                result.getString("idempotency_key"),
                result.getString("request_hash"),
                result.getString("actor_user_id"),
                result.getString("actor_username"),
                new CanteenScope(result.getString("school_id"), result.getString("canteen_id")),
                result.getString("intent"),
                result.getString("skill_id"),
                result.getString("skill_version"),
                result.getString("manifest_digest"),
                result.getString("plan_hash"),
                result.getString("plan_json"),
                result.getString("input_json"),
                RunStatus.valueOf(result.getString("status")),
                result.getString("current_step"),
                result.getString("result_json"),
                result.getString("error_code"),
                result.getString("error_message"),
                result.getLong("version"),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant());
    }
}
