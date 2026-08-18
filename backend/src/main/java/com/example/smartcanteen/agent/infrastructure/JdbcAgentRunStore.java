package com.example.smartcanteen.agent.infrastructure;

import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.AgentRunClaim;
import com.example.smartcanteen.agent.domain.AgentRunClaimLostException;
import com.example.smartcanteen.agent.domain.AgentRunDecision;
import com.example.smartcanteen.agent.domain.AgentRunEvent;
import com.example.smartcanteen.agent.domain.AgentStep;
import com.example.smartcanteen.agent.domain.RunStatus;
import com.example.smartcanteen.agent.port.AgentRunStore;
import com.example.smartcanteen.domain.CanteenScope;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAgentRunStore implements AgentRunStore {

    private final JdbcTemplate jdbc;

    public JdbcAgentRunStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    // A duplicate insert may race with a committed winner while the caller's
    // Repeatable Read snapshot is already established. Use a fresh read so the
    // runtime can turn that database race into a deterministic replay.
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
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
    public AgentRun insert(AgentRun run, List<AgentStep> steps) {
        // Duplicate-safe SQL keeps the caller's transaction usable after a
        // concurrent winner. The bootstrap event and steps are only written by
        // the caller whose run ID is the durable row's ID, so the whole Run
        // remains atomic with any surrounding assistant conversation writes.
        jdbc.update(
                "INSERT INTO agent_runs (run_id, idempotency_key, request_hash, actor_user_id, "
                        + "actor_username, school_id, canteen_id, intent, skill_id, skill_version, "
                        + "manifest_digest, plan_hash, plan_json, input_json, status, current_step, "
                        + "result_json, error_code, error_message, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE run_id = run_id",
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
        AgentRun persisted = jdbc.queryForObject(
                "SELECT * FROM agent_runs WHERE actor_user_id = ? AND school_id = ? "
                        + "AND canteen_id = ? AND idempotency_key = ? FOR UPDATE",
                this::map,
                run.actorUserId(),
                run.scope().schoolId(),
                run.scope().canteenId(),
                run.idempotencyKey());
        if (!run.runId().equals(persisted.runId())) {
            return persisted;
        }
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
        return persisted;
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
    @Transactional(readOnly = true)
    public List<AgentRun> findPlanned(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbc.query(
                "SELECT * FROM agent_runs WHERE status = 'PLANNED' "
                        + "ORDER BY updated_at, run_id LIMIT ?",
                this::map,
                safeLimit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentRun> findPlanned(int limit, Set<CanteenScope> scopes) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<Object> parameters = new ArrayList<>();
        String scopeFilter = scopeFilter("", scopes, parameters);
        if (scopeFilter == null) {
            return List.of();
        }
        String sql = "SELECT * FROM agent_runs WHERE status = 'PLANNED' "
                + "AND (" + scopeFilter + ") "
                + "ORDER BY updated_at, run_id LIMIT ?";
        parameters.add(safeLimit);
        return jdbc.query(sql, this::map, parameters.toArray());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentRun> findStaleExecuting(Instant cutoff) {
        return jdbc.query(
                "SELECT r.* FROM agent_runs r "
                        + "LEFT JOIN agent_run_claims c ON c.run_id = r.run_id "
                        + "WHERE r.status = 'EXECUTING' AND r.updated_at < ? "
                        + "AND (c.run_id IS NULL OR c.expires_at <= CURRENT_TIMESTAMP) "
                        + "ORDER BY r.updated_at, r.run_id",
                this::map,
                Timestamp.from(cutoff));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentRun> findStaleExecuting(Instant cutoff, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return jdbc.query(
                "SELECT r.* FROM agent_runs r "
                        + "LEFT JOIN agent_run_claims c ON c.run_id = r.run_id "
                        + "WHERE r.status = 'EXECUTING' AND r.updated_at < ? "
                        + "AND (c.run_id IS NULL OR c.expires_at <= CURRENT_TIMESTAMP) "
                        + "ORDER BY r.updated_at, r.run_id LIMIT ?",
                this::map,
                Timestamp.from(cutoff),
                safeLimit);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AgentRun> findStaleExecuting(
            Instant cutoff, int limit, Set<CanteenScope> scopes) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        List<Object> parameters = new ArrayList<>();
        String scopeFilter = scopeFilter("r", scopes, parameters);
        if (scopeFilter == null) {
            return List.of();
        }
        String sql = "SELECT r.* FROM agent_runs r "
                + "LEFT JOIN agent_run_claims c ON c.run_id = r.run_id "
                + "WHERE r.status = 'EXECUTING' AND r.updated_at < ? "
                + "AND (c.run_id IS NULL OR c.expires_at <= CURRENT_TIMESTAMP) "
                + "AND (" + scopeFilter + ") "
                + "ORDER BY r.updated_at, r.run_id LIMIT ?";
        parameters.add(0, Timestamp.from(cutoff));
        parameters.add(safeLimit);
        return jdbc.query(sql, this::map, parameters.toArray());
    }

    @Override
    @Transactional
    public boolean confirmStaleExecution(String runId, long expectedVersion) {
        Optional<RunState> run = jdbc.query(
                        "SELECT status, version FROM agent_runs WHERE run_id = ? FOR UPDATE",
                        (result, row) -> new RunState(
                                result.getString("status"), result.getLong("version")),
                        runId)
                .stream()
                .findFirst();
        if (run.isEmpty()
                || run.get().version() != expectedVersion
                || !"EXECUTING".equals(run.get().status())) {
            return false;
        }
        Optional<Instant> expiresAt = jdbc.query(
                        "SELECT expires_at FROM agent_run_claims WHERE run_id = ? FOR UPDATE",
                        (result, row) -> result.getTimestamp("expires_at").toInstant(),
                        runId)
                .stream()
                .findFirst();
        return expiresAt.isEmpty() || !expiresAt.get().isAfter(databaseNow());
    }

    @Override
    public boolean supportsExecutionClaims() {
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AgentRunClaim> claimExecution(
            String runId, String ownerId, Duration leaseDuration) {
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("Execution lease duration must be positive");
        }
        String status = jdbc.query(
                        "SELECT status FROM agent_runs WHERE run_id = ? FOR UPDATE",
                        (result, row) -> result.getString("status"),
                        runId)
                .stream()
                .findFirst()
                .orElse(null);
        if (!"PLANNED".equals(status)) {
            return Optional.empty();
        }
        Instant now = databaseNow();
        Optional<AgentRunClaim> existing = jdbc.query(
                        "SELECT run_id, owner_id, claim_token, claimed_at, expires_at "
                                + "FROM agent_run_claims WHERE run_id = ? FOR UPDATE",
                        this::mapClaim,
                        runId)
                .stream()
                .findFirst();
        if (existing.isPresent() && existing.get().expiresAt().isAfter(now)) {
            return Optional.empty();
        }
        Instant expiresAt = now.plus(leaseDuration);
        AgentRunClaim next = new AgentRunClaim(
                runId,
                ownerId,
                "CLAIM-" + UUID.randomUUID(),
                now,
                expiresAt);
        if (existing.isPresent()) {
            jdbc.update(
                    "UPDATE agent_run_claims SET owner_id = ?, claim_token = ?, "
                            + "claimed_at = ?, expires_at = ? WHERE run_id = ?",
                    next.ownerId(),
                    next.token(),
                    Timestamp.from(next.claimedAt()),
                    Timestamp.from(next.expiresAt()),
                    next.runId());
        } else {
            jdbc.update(
                    "INSERT INTO agent_run_claims "
                            + "(run_id, owner_id, claim_token, claimed_at, expires_at) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    next.runId(),
                    next.ownerId(),
                    next.token(),
                    Timestamp.from(next.claimedAt()),
                    Timestamp.from(next.expiresAt()));
        }
        return Optional.of(next);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean renewExecutionClaim(AgentRunClaim claim, Duration leaseDuration) {
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("Execution lease duration must be positive");
        }
        Instant now = databaseNow();
        int changed = jdbc.update(
                "UPDATE agent_run_claims SET expires_at = ? WHERE run_id = ? "
                        + "AND owner_id = ? AND claim_token = ? AND expires_at > ?",
                Timestamp.from(now.plus(leaseDuration)),
                claim.runId(),
                claim.ownerId(),
                claim.token(),
                Timestamp.from(now));
        return changed == 1;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean releaseExecutionClaim(AgentRunClaim claim) {
        return jdbc.update(
                "DELETE FROM agent_run_claims WHERE run_id = ? AND owner_id = ? AND claim_token = ?",
                claim.runId(),
                claim.ownerId(),
                claim.token()) == 1;
    }

    @Override
    @Transactional
    public void updateClaimed(AgentRun expected, AgentRun updated, AgentRunClaim claim) {
        requireClaimRun(expected.runId(), claim);
        requireClaimRun(updated.runId(), claim);
        requireClaim(claim);
        update(expected, updated);
    }

    @Override
    @Transactional
    public void updateStepClaimed(AgentStep step, AgentRunClaim claim) {
        requireClaimRun(step.runId(), claim);
        requireClaim(claim);
        updateStep(step);
    }

    @Override
    @Transactional
    public void appendEventClaimed(
            String runId,
            String eventType,
            String fromStatus,
            String toStatus,
            String actorUserId,
            String payloadJson,
            AgentRunClaim claim) {
        requireClaimRun(runId, claim);
        requireClaim(claim);
        appendEvent(runId, eventType, fromStatus, toStatus, actorUserId, payloadJson);
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

    private AgentRunClaim mapClaim(ResultSet result, int row) throws SQLException {
        return new AgentRunClaim(
                result.getString("run_id"),
                result.getString("owner_id"),
                result.getString("claim_token"),
                result.getTimestamp("claimed_at").toInstant(),
                result.getTimestamp("expires_at").toInstant());
    }

    private static String scopeFilter(
            String alias, Set<CanteenScope> scopes, List<Object> parameters) {
        if (scopes == null || scopes.isEmpty()) {
            return null;
        }
        String prefix = alias == null || alias.isBlank() ? "" : alias + ".";
        return scopes.stream()
                .map(scope -> {
                    parameters.add(scope.schoolId());
                    parameters.add(scope.canteenId());
                    return prefix + "school_id = ? AND " + prefix + "canteen_id = ?";
                })
                .collect(java.util.stream.Collectors.joining(" OR "));
    }

    private record RunState(String status, long version) {
    }

    private void requireClaim(AgentRunClaim claim) {
        jdbc.queryForObject(
                "SELECT run_id FROM agent_runs WHERE run_id = ? FOR UPDATE",
                String.class,
                claim.runId());
        Optional<AgentRunClaim> current = jdbc.query(
                        "SELECT run_id, owner_id, claim_token, claimed_at, expires_at "
                                + "FROM agent_run_claims WHERE run_id = ? FOR UPDATE",
                        this::mapClaim,
                        claim.runId())
                .stream()
                        .findFirst();
        if (current.isEmpty()
                || !claim.ownerId().equals(current.get().ownerId())
                || !claim.token().equals(current.get().token())
                || !current.get().expiresAt().isAfter(databaseNow())) {
            throw new AgentRunClaimLostException(claim.runId());
        }
    }

    private Instant databaseNow() {
        return jdbc.queryForObject(
                "SELECT CURRENT_TIMESTAMP",
                (result, row) -> result.getTimestamp(1).toInstant());
    }

    private void requireClaimRun(String runId, AgentRunClaim claim) {
        if (!claim.runId().equals(runId)) {
            throw new IllegalArgumentException(
                    "Agent execution claim does not belong to Run: " + runId);
        }
    }
}
