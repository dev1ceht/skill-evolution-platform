package com.example.smartcanteen.agent.infrastructure;

import com.example.smartcanteen.agent.port.AgentMetricsStore;
import com.example.smartcanteen.domain.CanteenScope;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** MySQL/H2-compatible evidence queries for the Agent metrics dashboard. */
@Repository
public class JdbcAgentMetricsStore implements AgentMetricsStore {

    private final JdbcTemplate jdbc;

    public JdbcAgentMetricsStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Snapshot collect(CanteenScope scope, Instant from, Instant to) {
        Timestamp fromValue = Timestamp.from(from);
        Timestamp toValue = Timestamp.from(to);
        List<RunRecord> runs = jdbc.query(
                "SELECT run_id, status, created_at, updated_at FROM agent_runs "
                        + "WHERE school_id = ? AND canteen_id = ? AND created_at >= ? AND created_at < ? "
                        + "ORDER BY created_at, run_id",
                (result, row) -> new RunRecord(
                        result.getString("run_id"),
                        result.getString("status"),
                        result.getTimestamp("created_at").toInstant(),
                        result.getTimestamp("updated_at").toInstant()),
                scope.schoolId(), scope.canteenId(), fromValue, toValue);
        List<RunRecord> terminalRuns = jdbc.query(
                "SELECT run_id, status, created_at, updated_at FROM agent_runs "
                        + "WHERE school_id = ? AND canteen_id = ? "
                        + "AND status IN ('SUCCEEDED', 'FAILED', 'REJECTED', 'CANCELLED', 'TIMED_OUT', "
                        + "'RECONCILIATION_REQUIRED') AND updated_at >= ? AND updated_at < ?",
                (result, row) -> new RunRecord(
                        result.getString("run_id"),
                        result.getString("status"),
                        result.getTimestamp("created_at").toInstant(),
                        result.getTimestamp("updated_at").toInstant()),
                scope.schoolId(), scope.canteenId(), fromValue, toValue);
        List<StepRecord> steps = jdbc.query(
                "SELECT s.status, s.started_at, s.finished_at FROM agent_steps s "
                        + "JOIN agent_runs r ON r.run_id = s.run_id "
                        + "WHERE r.school_id = ? AND r.canteen_id = ? "
                        + "AND ((s.started_at >= ? AND s.started_at < ?) "
                        + "OR (s.finished_at >= ? AND s.finished_at < ?))",
                (result, row) -> new StepRecord(
                        result.getString("status"),
                        instant(result.getTimestamp("started_at")),
                        instant(result.getTimestamp("finished_at"))),
                scope.schoolId(), scope.canteenId(), fromValue, toValue, fromValue, toValue);
        List<EventRecord> events = new ArrayList<>();
        events.addAll(jdbc.query(
                "SELECT e.run_id, e.event_type, e.from_status, e.to_status, e.occurred_at "
                        + "FROM agent_run_events e JOIN agent_runs r ON r.run_id = e.run_id "
                        + "WHERE r.school_id = ? AND r.canteen_id = ? "
                        + "AND e.occurred_at < ? "
                        + "AND e.event_sequence = ("
                        + "SELECT MAX(previous.event_sequence) FROM agent_run_events previous "
                        + "WHERE previous.run_id = e.run_id AND previous.occurred_at < ?) "
                        + "ORDER BY e.run_id, e.event_sequence",
                (result, row) -> new EventRecord(
                        result.getString("run_id"),
                        result.getString("event_type"),
                        result.getString("from_status"),
                        result.getString("to_status"),
                        result.getTimestamp("occurred_at").toInstant()),
                scope.schoolId(), scope.canteenId(), fromValue, fromValue));
        events.addAll(jdbc.query(
                "SELECT e.run_id, e.event_type, e.from_status, e.to_status, e.occurred_at "
                        + "FROM agent_run_events e JOIN agent_runs r ON r.run_id = e.run_id "
                        + "WHERE r.school_id = ? AND r.canteen_id = ? "
                        + "AND e.occurred_at >= ? AND e.occurred_at < ? "
                        + "ORDER BY e.run_id, e.event_sequence",
                (result, row) -> new EventRecord(
                        result.getString("run_id"),
                        result.getString("event_type"),
                        result.getString("from_status"),
                        result.getString("to_status"),
                        result.getTimestamp("occurred_at").toInstant()),
                scope.schoolId(), scope.canteenId(), fromValue, toValue));
        Long denials = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'AGENT_AUTHORIZATION_DENIAL' "
                        + "AND school_id = ? AND canteen_id = ? AND created_at >= ? AND created_at < ?",
                Long.class,
                scope.schoolId(), scope.canteenId(), fromValue, toValue);
        Long waiting = jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_runs WHERE school_id = ? AND canteen_id = ? "
                        + "AND status = 'WAITING_CONFIRMATION' AND updated_at < ?",
                Long.class,
                scope.schoolId(), scope.canteenId(), toValue);
        return new Snapshot(
                runs,
                terminalRuns,
                steps,
                events,
                denials == null ? 0 : denials,
                waiting == null ? 0 : waiting);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
