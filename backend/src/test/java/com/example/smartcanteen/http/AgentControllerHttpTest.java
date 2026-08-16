package com.example.smartcanteen.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class AgentControllerHttpTest {

    private static final String SCHOOL_ID = "SCHOOL-AGENT-HTTP";
    private static final String CANTEEN_ID = "CANTEEN-AGENT-HTTP";
    private static final AuthPrincipal PRINCIPAL = new AuthPrincipal(
            "USER-AGENT-HTTP",
            "agent-http-user",
            "Agent HTTP User",
            Role.CANTEEN_STAFF,
            SCHOOL_ID,
            CANTEEN_ID);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedTraceabilityRecord() {
        jdbc.update("DELETE FROM agent_run_events");
        jdbc.update("DELETE FROM agent_steps");
        jdbc.update("DELETE FROM agent_run_decisions");
        jdbc.update("DELETE FROM agent_runs");
        jdbc.update("DELETE FROM traceability_records WHERE school_id = ?", SCHOOL_ID);
        jdbc.update("DELETE FROM inventory_batches WHERE school_id = ?", SCHOOL_ID);
        jdbc.update("DELETE FROM ingredients WHERE school_id = ?", SCHOOL_ID);
        jdbc.update("DELETE FROM suppliers WHERE school_id = ?", SCHOOL_ID);
        jdbc.update(
                "INSERT INTO ingredients (school_id, canteen_id, ingredient_id, name, category, "
                        + "base_unit) VALUES (?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "ING-AGENT", "Agent Ingredient", "VEGETABLE", "kg");
        jdbc.update(
                "INSERT INTO suppliers (school_id, canteen_id, supplier_id, name) "
                        + "VALUES (?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "SUP-AGENT", "Agent Supplier");
        jdbc.update(
                "INSERT INTO inventory_batches (school_id, canteen_id, batch_id, order_id, "
                        + "ingredient_id, supplier_id, batch_no, quantity_base, base_unit, "
                        + "purchase_price, trace_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "BATCH-AGENT", "ORDER-AGENT", "ING-AGENT", "SUP-AGENT",
                "BATCH-NO-AGENT", 10, "kg", 2.50, "TRACE-AGENT-001");
        jdbc.update(
                "INSERT INTO traceability_records (school_id, canteen_id, trace_code, batch_id, "
                        + "order_id, ingredient_id, supplier_id, quantity_base, base_unit) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "TRACE-AGENT-001", "BATCH-AGENT", "ORDER-AGENT",
                "ING-AGENT", "SUP-AGENT", 10, "kg");
    }

    @Test
    void executes_structured_traceability_intent_and_persists_the_run() throws Exception {
        String response = mvc.perform(startRequest("agent-http-001", "TRACE-AGENT-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.intent").value("traceability.query"))
                .andExpect(jsonPath("$.data.result.traceCode").value("TRACE-AGENT-001"))
                .andExpect(jsonPath("$.data.result.ingredientName").value("Agent Ingredient"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String runId = com.jayway.jsonpath.JsonPath.read(response, "$.data.runId");
        mvc.perform(get("/api/v1/agent/runs/{runId}", runId)
                        .queryParam("schoolId", SCHOOL_ID)
                        .queryParam("canteenId", CANTEEN_ID)
                        .requestAttr(AuthPrincipal.class.getName(), PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value(runId))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
    }

    @Test
    void same_idempotency_key_is_replayed_but_different_input_is_rejected() throws Exception {
        String first = mvc.perform(startRequest("agent-http-002", "TRACE-AGENT-001"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String firstRunId = com.jayway.jsonpath.JsonPath.read(first, "$.data.runId");

        mvc.perform(startRequest("agent-http-002", "TRACE-AGENT-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.runId").value(firstRunId));

        mvc.perform(startRequest("agent-http-002", "TRACE-AGENT-OTHER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void skills_endpoint_exposes_the_active_read_skill_and_blocked_menu_skill() throws Exception {
        mvc.perform(get("/api/v1/agent/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == 'smart-canteen.traceability')].available")
                        .value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.data[?(@.id == 'smart-canteen.menu-approval')].available")
                        .value(org.hamcrest.Matchers.contains(false)));
    }

    @Test
    void missing_run_is_reported_as_not_found() throws Exception {
        mvc.perform(get("/api/v1/agent/runs/RUN-MISSING")
                        .queryParam("schoolId", SCHOOL_ID)
                        .queryParam("canteenId", CANTEEN_ID)
                        .requestAttr(AuthPrincipal.class.getName(), PRINCIPAL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40400));
    }

    private MockHttpServletRequestBuilder startRequest(String idempotencyKey, String traceCode) {
        return post("/api/v1/agent/runs")
                .queryParam("schoolId", SCHOOL_ID)
                .queryParam("canteenId", CANTEEN_ID)
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Request-Id", "request-" + idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"intent\":\"traceability.query\",\"input\":{\"traceCode\":\""
                        + traceCode + "\"}}")
                .requestAttr(AuthPrincipal.class.getName(), PRINCIPAL);
    }
}
