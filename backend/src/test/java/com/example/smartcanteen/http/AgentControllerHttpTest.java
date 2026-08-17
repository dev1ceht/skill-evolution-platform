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
    private static final AuthPrincipal APPROVER = new AuthPrincipal(
            "USER-AGENT-APPROVER",
            "agent-approver",
            "Agent Approver",
            Role.SCHOOL_ADMIN,
            SCHOOL_ID,
            CANTEEN_ID);
    private static final AuthPrincipal PUBLISHER = new AuthPrincipal(
            "USER-AGENT-PUBLISHER",
            "agent-publisher",
            "Agent Publisher",
            Role.SCHOOL_ADMIN,
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
        jdbc.update("DELETE FROM agent_run_claims");
        jdbc.update("DELETE FROM agent_runs");
        jdbc.update("DELETE FROM daily_menu_items WHERE school_id = ?", SCHOOL_ID);
        jdbc.update("DELETE FROM daily_menus WHERE school_id = ?", SCHOOL_ID);
        jdbc.update("DELETE FROM dish_ingredients WHERE school_id = ?", SCHOOL_ID);
        jdbc.update("DELETE FROM dishes WHERE school_id = ?", SCHOOL_ID);
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
        jdbc.update("MERGE INTO schools (id, name) KEY(id) VALUES (?, ?)", SCHOOL_ID, "Agent school");
        jdbc.update(
                "MERGE INTO canteens (id, school_id, name) KEY(id) VALUES (?, ?, ?)",
                CANTEEN_ID, SCHOOL_ID, "Agent canteen");
        jdbc.update(
                "INSERT INTO dishes (school_id, canteen_id, dish_id, name, category, status, version) "
                        + "VALUES (?, ?, ?, ?, ?, 'ACTIVE', 0)",
                SCHOOL_ID, CANTEEN_ID, "DISH-AGENT", "Agent dish", "MAIN");
        jdbc.update(
                "INSERT INTO dish_ingredients (school_id, canteen_id, dish_id, ingredient_id, quantity, unit) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "DISH-AGENT", "ING-AGENT", 1, "kg");
        jdbc.update(
                "INSERT INTO daily_menus (school_id, canteen_id, menu_id, menu_date, meal_time, status, version) "
                        + "VALUES (?, ?, ?, CURRENT_DATE, 'LUNCH', 'DRAFT', 0)",
                SCHOOL_ID, CANTEEN_ID, "MENU-AGENT");
        jdbc.update(
                "INSERT INTO daily_menu_items (school_id, canteen_id, menu_id, dish_id, estimated_quantity, sort_order) "
                        + "VALUES (?, ?, ?, ?, 100, 0)",
                SCHOOL_ID, CANTEEN_ID, "MENU-AGENT", "DISH-AGENT");
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
    void skills_endpoint_exposes_the_active_read_skill_and_menu_write_skill() throws Exception {
        mvc.perform(get("/api/v1/agent/skills"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == 'smart-canteen.traceability')].available")
                        .value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$.data[?(@.id == 'smart-canteen.menu-approval')].available")
                        .value(org.hamcrest.Matchers.contains(true)));
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

    @Test
    void menu_agent_separates_run_confirmation_from_domain_approval_and_publish() throws Exception {
        String submit = mvc.perform(startMenuRequest(
                        "menu-agent-submit", "menu.submit", "MENU-AGENT", 0, PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING_CONFIRMATION"))
                .andReturn().getResponse().getContentAsString();
        String submitRunId = com.jayway.jsonpath.JsonPath.read(submit, "$.data.runId");
        String confirmKey = "confirm-" + submitRunId + "-0";
        mvc.perform(confirmRequest(submitRunId, 0, PRINCIPAL, confirmKey, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.status").value("PENDING_APPROVAL"));
        mvc.perform(confirmRequest(submitRunId, 0, PRINCIPAL, confirmKey, "different comment"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        String decision = mvc.perform(startMenuRequest(
                        "menu-agent-decision", "menu.record-decision", "MENU-AGENT", 1, APPROVER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING_CONFIRMATION"))
                .andReturn().getResponse().getContentAsString();
        String decisionRunId = com.jayway.jsonpath.JsonPath.read(decision, "$.data.runId");
        mvc.perform(confirmRequest(decisionRunId, 0, APPROVER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.status").value("APPROVED"));

        String publish = mvc.perform(startMenuRequest(
                        "menu-agent-publish", "menu.publish", "MENU-AGENT", 2, PUBLISHER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("WAITING_CONFIRMATION"))
                .andReturn().getResponse().getContentAsString();
        String publishRunId = com.jayway.jsonpath.JsonPath.read(publish, "$.data.runId");
        mvc.perform(confirmRequest(publishRunId, 0, PUBLISHER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.status").value("PUBLISHED"));
        mvc.perform(get("/api/v1/agent/runs/{runId}/events", publishRunId)
                        .queryParam("schoolId", SCHOOL_ID)
                        .queryParam("canteenId", CANTEEN_ID)
                        .requestAttr(AuthPrincipal.class.getName(), PUBLISHER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.eventType == 'RUN_SUCCEEDED')]").isNotEmpty());
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

    private MockHttpServletRequestBuilder startMenuRequest(
            String idempotencyKey,
            String intent,
            String menuId,
            long menuVersion,
            AuthPrincipal principal) {
        return post("/api/v1/agent/runs")
                .queryParam("schoolId", SCHOOL_ID)
                .queryParam("canteenId", CANTEEN_ID)
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Request-Id", "request-" + idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"intent\":\"%s\",\"input\":{\"menuId\":\"%s\",\"menuVersion\":%d"
                        + ("menu.record-decision".equals(intent)
                                ? ",\"decision\":\"APPROVE\",\"comment\":\"checked\"" : "")
                        + "}}" ).formatted(intent, menuId, menuVersion))
                .requestAttr(AuthPrincipal.class.getName(), principal);
    }

    private MockHttpServletRequestBuilder confirmRequest(
            String runId, long version, AuthPrincipal principal) {
        return confirmRequest(
                runId, version, principal, "confirm-" + runId + "-" + version, null);
    }

    private MockHttpServletRequestBuilder confirmRequest(
            String runId,
            long version,
            AuthPrincipal principal,
            String idempotencyKey,
            String comment) {
        String body = "{\"version\":" + version + ",\"decisionType\":\"RUN_CONFIRM\""
                + (comment == null ? "" : ",\"comment\":\"" + comment + "\"")
                + "}";
        return post("/api/v1/agent/runs/{runId}/decisions", runId)
                .queryParam("schoolId", SCHOOL_ID)
                .queryParam("canteenId", CANTEEN_ID)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .requestAttr(AuthPrincipal.class.getName(), principal);
    }
}
