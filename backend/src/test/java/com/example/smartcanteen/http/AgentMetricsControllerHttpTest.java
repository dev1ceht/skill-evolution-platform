package com.example.smartcanteen.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AgentMetricsControllerHttpTest {

    private static final String SCHOOL_ID = "SCHOOL-METRICS-HTTP";
    private static final String CANTEEN_ID = "CANTEEN-METRICS-HTTP";
    private static final AuthPrincipal PRINCIPAL = new AuthPrincipal(
            "USER-METRICS-HTTP",
            "metrics-user",
            "Metrics User",
            Role.SCHOOL_ADMIN,
            SCHOOL_ID,
            CANTEEN_ID);
    private static final AuthPrincipal OTHER_PRINCIPAL = new AuthPrincipal(
            "USER-METRICS-OTHER",
            "other-user",
            "Other User",
            Role.SCHOOL_ADMIN,
            SCHOOL_ID,
            CANTEEN_ID);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearEvidence() {
        jdbc.update("DELETE FROM audit_logs WHERE school_id = ? AND canteen_id = ?", SCHOOL_ID, CANTEEN_ID);
        jdbc.update("DELETE FROM agent_run_events WHERE run_id IN (?, ?, ?)",
                "RUN-METRICS-1", "RUN-METRICS-2", "RUN-METRICS-OLD");
        jdbc.update("DELETE FROM agent_steps WHERE run_id IN (?, ?, ?)",
                "RUN-METRICS-1", "RUN-METRICS-2", "RUN-METRICS-OLD");
        jdbc.update("DELETE FROM agent_runs WHERE run_id IN (?, ?, ?)",
                "RUN-METRICS-1", "RUN-METRICS-2", "RUN-METRICS-OLD");
        insertRun(
                "RUN-METRICS-1",
                "SUCCEEDED",
                Instant.parse("2026-08-17T08:00:00Z"),
                Instant.parse("2026-08-17T08:00:02Z"));
        insertRun(
                "RUN-METRICS-2",
                "WAITING_CONFIRMATION",
                Instant.parse("2026-08-17T09:00:00Z"),
                Instant.parse("2026-08-17T09:00:00Z"));
        insertRun(
                "RUN-METRICS-OLD",
                "WAITING_CONFIRMATION",
                Instant.parse("2026-08-16T23:00:00Z"),
                Instant.parse("2026-08-16T23:30:00Z"));
        jdbc.update(
                "INSERT INTO agent_steps (run_id, step_id, step_order, tool_name, status, "
                        + "idempotency_key, input_digest, attempt_count, started_at, finished_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "RUN-METRICS-1", "step-1", 0, "traceability.query", "SUCCEEDED",
                "RUN-METRICS-1:step-1", "d".repeat(64), 1,
                Instant.parse("2026-08-17T08:00:00Z"), Instant.parse("2026-08-17T08:00:02Z"));
        insertEvent("EVENT-METRICS-1", "RUN-METRICS-1", 1, "RUN_PLANNED", null, "SUCCEEDED", "2026-08-17T08:00:00Z");
        insertEvent("EVENT-METRICS-REPLAY", "RUN-METRICS-1", 2, "RUN_IDEMPOTENCY_REPLAY", "SUCCEEDED", "SUCCEEDED", "2026-08-17T08:00:03Z");
        insertEvent("EVENT-METRICS-2", "RUN-METRICS-2", 1, "RUN_PLANNED", null, "WAITING_CONFIRMATION", "2026-08-17T09:00:00Z");
        insertEvent("EVENT-METRICS-3", "RUN-METRICS-2", 2, "RUN_CONFIRM", "WAITING_CONFIRMATION", "PLANNED", "2026-08-17T09:00:03Z");
        insertEvent("EVENT-METRICS-OLD", "RUN-METRICS-OLD", 1, "RUN_PLANNED", null, "WAITING_CONFIRMATION", "2026-08-16T23:30:00Z");
        jdbc.update(
                "INSERT INTO audit_logs (audit_id, actor_user_id, action, resource_type, resource_id, "
                        + "school_id, canteen_id, outcome, detail, request_id, created_at) "
                        + "VALUES (?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                "AUDIT-METRICS-DENIAL", "AGENT_AUTHORIZATION_DENIAL", "AGENT_HTTP",
                "/api/v1/agent/runs/RUN-METRICS-1", SCHOOL_ID, CANTEEN_ID, "FAILURE",
                "agent authorization denied", "request-metrics-denial",
                Instant.parse("2026-08-17T09:00:04Z"));
    }

    @Test
    void exposes_scope_limited_runtime_metrics_without_run_identifiers() throws Exception {
        mvc.perform(get("/api/v1/agent/runs/RUN-METRICS-1")
                        .queryParam("schoolId", SCHOOL_ID)
                        .queryParam("canteenId", CANTEEN_ID)
                        .header("X-Request-Id", "metrics-denial-request")
                        .requestAttr(AuthPrincipal.class.getName(), OTHER_PRINCIPAL))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/agent/metrics")
                        .queryParam("schoolId", SCHOOL_ID)
                        .queryParam("canteenId", CANTEEN_ID)
                        .queryParam("from", "2026-08-17T00:00:00Z")
                        .queryParam("to", "2026-08-18T00:00:00Z")
                        .requestAttr(AuthPrincipal.class.getName(), PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalRuns").value(2))
                .andExpect(jsonPath("$.data.succeededRuns").value(1))
                .andExpect(jsonPath("$.data.waitingConfirmationRuns").value(2))
                .andExpect(jsonPath("$.data.successRate").value(0.5))
                .andExpect(jsonPath("$.data.averageRunDurationMs").value(2_000))
                .andExpect(jsonPath("$.data.averageConfirmationWaitMs").value(3_000))
                .andExpect(jsonPath("$.data.toolExecutions").value(1))
                .andExpect(jsonPath("$.data.idempotencyReplayCount").value(1))
                .andExpect(jsonPath("$.data.authorizationDeniedCount").value(2))
                .andExpect(jsonPath("$.data.runId").doesNotExist());

        String requestId = jdbc.queryForObject(
                "SELECT request_id FROM audit_logs WHERE action = 'AGENT_AUTHORIZATION_DENIAL' "
                        + "AND school_id = ? AND canteen_id = ? ORDER BY created_at DESC, audit_id DESC LIMIT 1",
                String.class,
                SCHOOL_ID,
                CANTEEN_ID);
        assertThat(requestId).isEqualTo("metrics-denial-request");
    }

    @Test
    void rejects_a_metrics_window_longer_than_thirty_one_days() throws Exception {
        mvc.perform(get("/api/v1/agent/metrics")
                        .queryParam("schoolId", SCHOOL_ID)
                        .queryParam("canteenId", CANTEEN_ID)
                        .queryParam("from", "2026-07-01T00:00:00Z")
                        .queryParam("to", "2026-08-18T00:00:00Z")
                        .requestAttr(AuthPrincipal.class.getName(), PRINCIPAL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    private void insertRun(String runId, String status, Instant createdAt, Instant updatedAt) {
        jdbc.update(
                "INSERT INTO agent_runs (run_id, idempotency_key, request_hash, actor_user_id, "
                        + "actor_username, school_id, canteen_id, intent, skill_id, skill_version, "
                        + "manifest_digest, plan_hash, plan_json, input_json, status, version, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                runId, runId, "a".repeat(64), "USER-METRICS-HTTP", "metrics-user",
                SCHOOL_ID, CANTEEN_ID, "traceability.query", "smart-canteen.traceability", "1.0.0",
                "b".repeat(64), "c".repeat(64), "{}", "{}", status, 0, createdAt, updatedAt);
    }

    private void insertEvent(
            String eventId,
            String runId,
            long sequence,
            String type,
            String from,
            String to,
            String occurredAt) {
        jdbc.update(
                "INSERT INTO agent_run_events (event_id, run_id, event_sequence, event_type, "
                        + "from_status, to_status, actor_user_id, payload_json, occurred_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                eventId, runId, sequence, type, from, to, "USER-METRICS-HTTP", null,
                Instant.parse(occurredAt));
    }
}
