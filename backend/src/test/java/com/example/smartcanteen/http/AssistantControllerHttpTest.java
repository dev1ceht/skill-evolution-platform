package com.example.smartcanteen.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
class AssistantControllerHttpTest {

    private static final String SCHOOL_ID = "SCHOOL-ASSIST-HTTP";
    private static final String CANTEEN_ID = "CANTEEN-ASSIST-HTTP";
    private static final AuthPrincipal PRINCIPAL = new AuthPrincipal(
            "USER-ASSIST-HTTP",
            "assistant-http-user",
            "Assistant HTTP User",
            Role.CANTEEN_STAFF,
            SCHOOL_ID,
            CANTEEN_ID);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedTraceabilityRecord() {
        jdbc.update("DELETE FROM assistant_turns");
        jdbc.update("DELETE FROM assistant_conversations");
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
                SCHOOL_ID, CANTEEN_ID, "ING-ASSIST", "Assistant Ingredient", "VEGETABLE", "kg");
        jdbc.update(
                "INSERT INTO suppliers (school_id, canteen_id, supplier_id, name) "
                        + "VALUES (?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "SUP-ASSIST", "Assistant Supplier");
        jdbc.update(
                "INSERT INTO inventory_batches (school_id, canteen_id, batch_id, order_id, "
                        + "ingredient_id, supplier_id, batch_no, quantity_base, base_unit, "
                        + "purchase_price, trace_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "BATCH-ASSIST", "ORDER-ASSIST", "ING-ASSIST", "SUP-ASSIST",
                "BATCH-NO-ASSIST", 10, "kg", 2.50, "TRACE-ASSIST-001");
        jdbc.update(
                "INSERT INTO traceability_records (school_id, canteen_id, trace_code, batch_id, "
                        + "order_id, ingredient_id, supplier_id, quantity_base, base_unit) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "TRACE-ASSIST-001", "BATCH-ASSIST", "ORDER-ASSIST",
                "ING-ASSIST", "SUP-ASSIST", 10, "kg");
    }

    @Test
    void resolves_a_natural_language_traceability_message_and_links_the_agent_run() throws Exception {
        mvc.perform(message("message-001", "请查询 TRACE-ASSIST-001 的食品溯源", "CONV-ASSIST-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.kind").value("RESULT"))
                .andExpect(jsonPath("$.data.intent").value("traceability.query"))
                .andExpect(jsonPath("$.data.runStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.ingredientName").value("Assistant Ingredient"))
                .andExpect(jsonPath("$.data.message").value(
                        org.hamcrest.Matchers.containsString("Assistant Ingredient")));
    }

    @Test
    void asks_for_missing_trace_code_without_creating_a_run() throws Exception {
        mvc.perform(message("message-002", "帮我查一下这批食材的溯源", "CONV-ASSIST-002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CLARIFICATION"))
                .andExpect(jsonPath("$.data.missingFields[0]").value("traceCode"))
                .andExpect(jsonPath("$.data.runId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void replays_a_message_by_idempotency_and_rejects_different_payloads() throws Exception {
        String first = mvc.perform(message("message-003", "查询 TRACE-ASSIST-001", "CONV-ASSIST-003"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String turnId = com.jayway.jsonpath.JsonPath.read(first, "$.data.turnId");

        mvc.perform(message("message-003", "查询 TRACE-ASSIST-001", "CONV-ASSIST-003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.turnId").value(turnId));

        mvc.perform(message("message-003", "查询 TRACE-OTHER", "CONV-ASSIST-003"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void scopes_agent_run_idempotency_to_the_conversation() throws Exception {
        String first = mvc.perform(message("shared-key", "查询 TRACE-ASSIST-001", "CONV-ASSIST-004"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String second = mvc.perform(message("shared-key", "查询 TRACE-ASSIST-001", "CONV-ASSIST-005"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String firstRunId = com.jayway.jsonpath.JsonPath.read(first, "$.data.runId");
        String secondRunId = com.jayway.jsonpath.JsonPath.read(second, "$.data.runId");
        assertNotEquals(firstRunId, secondRunId);
    }

    @Test
    void does_not_expose_tool_failure_details_in_the_assistant_message() throws Exception {
        mvc.perform(message("message-004", "查询 TRACE-ASSIST-MISSING", "CONV-ASSIST-006"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("RESULT"))
                .andExpect(jsonPath("$.data.runStatus").value("FAILED"))
                .andExpect(jsonPath("$.data.message").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                                "Traceability code not found"))));
    }

    private MockHttpServletRequestBuilder message(
            String idempotencyKey, String text, String conversationId) {
        return post("/api/v1/assistant/conversations/{conversationId}/messages", conversationId)
                .queryParam("schoolId", SCHOOL_ID)
                .queryParam("canteenId", CANTEEN_ID)
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Request-Id", "request-" + idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + text + "\"}")
                .requestAttr(AuthPrincipal.class.getName(), PRINCIPAL);
    }
}
