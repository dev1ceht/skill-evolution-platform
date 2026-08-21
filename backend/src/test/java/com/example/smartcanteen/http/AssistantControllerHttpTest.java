package com.example.smartcanteen.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

@SpringBootTest(properties = "agent.write.enabled=true")
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
        jdbc.update(
                "MERGE INTO schools (id, name) KEY (id) VALUES (?, ?)",
                SCHOOL_ID,
                "Assistant HTTP School");
        jdbc.update(
                "MERGE INTO canteens (id, school_id, name) KEY (id) VALUES (?, ?, ?)",
                CANTEEN_ID,
                SCHOOL_ID,
                "Assistant HTTP Canteen");
        jdbc.update("DELETE FROM assistant_pending_actions");
        jdbc.update("DELETE FROM assistant_clarifications");
        jdbc.update("DELETE FROM assistant_turns");
        jdbc.update("DELETE FROM assistant_conversations");
        jdbc.update("DELETE FROM agent_run_events");
        jdbc.update("DELETE FROM agent_steps");
        jdbc.update("DELETE FROM agent_run_decisions");
        jdbc.update("DELETE FROM agent_run_claims");
        jdbc.update("DELETE FROM agent_runs");
        jdbc.update("DELETE FROM procurement_plan_orders WHERE school_id = ?", SCHOOL_ID);
        jdbc.update("DELETE FROM procurement_plan_items WHERE school_id = ?", SCHOOL_ID);
        jdbc.update("DELETE FROM procurement_plan_menus WHERE school_id = ?", SCHOOL_ID);
        jdbc.update("DELETE FROM procurement_plans WHERE school_id = ?", SCHOOL_ID);
        jdbc.update("DELETE FROM purchase_order_items WHERE school_id = ?", SCHOOL_ID);
        jdbc.update("DELETE FROM purchase_orders WHERE school_id = ?", SCHOOL_ID);
        jdbc.update("DELETE FROM traceability_records WHERE school_id = ?", SCHOOL_ID);
        jdbc.update("DELETE FROM inventory WHERE school_id = ?", SCHOOL_ID);
        jdbc.update("DELETE FROM inventory_batches WHERE school_id = ?", SCHOOL_ID);
        jdbc.update("DELETE FROM ingredients WHERE school_id = ?", SCHOOL_ID);
        jdbc.update("DELETE FROM suppliers WHERE school_id = ?", SCHOOL_ID);
        jdbc.update("DELETE FROM traffic_forecasts WHERE school_id = ?", SCHOOL_ID);
        jdbc.update("DELETE FROM daily_menu_items WHERE school_id = ?", SCHOOL_ID);
        jdbc.update("DELETE FROM daily_menus WHERE school_id = ?", SCHOOL_ID);
        jdbc.update("DELETE FROM dish_ingredients WHERE school_id = ?", SCHOOL_ID);
        jdbc.update("DELETE FROM dishes WHERE school_id = ?", SCHOOL_ID);
        jdbc.update(
                "INSERT INTO ingredients (school_id, canteen_id, ingredient_id, name, category, "
                        + "base_unit) VALUES (?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "ING-ASSIST", "Assistant Ingredient", "VEGETABLE", "g");
        jdbc.update(
                "INSERT INTO inventory (school_id, canteen_id, material_id, quantity_base, base_unit, "
                        + "warning_threshold, last_update_time) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                SCHOOL_ID, CANTEEN_ID, "ING-ASSIST", 10, "kg", 20);
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
    void resumes_a_pending_clarification_when_the_user_supplies_the_missing_trace_code()
            throws Exception {
        mvc.perform(message(
                        "clarification-start",
                        "帮我查一下这批食材的溯源",
                        "CONV-ASSIST-CLARIFY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CLARIFICATION"));
        assertEquals(
                "WAITING_CLARIFICATION",
                jdbc.queryForObject(
                        "SELECT status FROM assistant_conversations WHERE conversation_id = ?",
                        String.class,
                        "CONV-ASSIST-CLARIFY"));

        mvc.perform(message(
                        "clarification-answer",
                        "TRACE-ASSIST-001",
                        "CONV-ASSIST-CLARIFY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("RESULT"))
                .andExpect(jsonPath("$.data.intent").value("traceability.query"))
                .andExpect(jsonPath("$.data.result.traceCode").value("TRACE-ASSIST-001"));
        assertEquals(
                "ACTIVE",
                jdbc.queryForObject(
                        "SELECT status FROM assistant_conversations WHERE conversation_id = ?",
                        String.class,
                        "CONV-ASSIST-CLARIFY"));
    }

    @Test
    void clears_pending_clarification_for_a_new_unsupported_request() throws Exception {
        mvc.perform(message(
                        "new-intent-start",
                        "帮我查一下这批食材的溯源",
                        "CONV-ASSIST-NEW-INTENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CLARIFICATION"));

        mvc.perform(message(
                        "new-intent-unsupported",
                        "帮我安排明天的采购",
                        "CONV-ASSIST-NEW-INTENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("UNSUPPORTED"));
        assertEquals(
                "ACTIVE",
                jdbc.queryForObject(
                        "SELECT status FROM assistant_conversations WHERE conversation_id = ?",
                        String.class,
                        "CONV-ASSIST-NEW-INTENT"));

        mvc.perform(message(
                        "new-intent-after-clear",
                        "TRACE-ASSIST-001",
                        "CONV-ASSIST-NEW-INTENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("RESULT"))
                .andExpect(jsonPath("$.data.intent").value("traceability.query"));
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

    @Test
    void resolves_a_menu_query_and_returns_the_menu_result() throws Exception {
        jdbc.update(
                "INSERT INTO daily_menus (school_id, canteen_id, menu_id, menu_date, meal_time, status, version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "M001", "2026-08-17", "LUNCH", "PUBLISHED", 2);
        jdbc.update(
                "INSERT INTO daily_menu_items (school_id, canteen_id, menu_id, dish_id, estimated_quantity, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "M001", "DISH-ASSIST-001", 120, 1);

        mvc.perform(message("menu-message-001", "请查询 M001 的菜单", "CONV-ASSIST-MENU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("RESULT"))
                .andExpect(jsonPath("$.data.intent").value("menu.query"))
                .andExpect(jsonPath("$.data.runStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.id").value("M001"))
                .andExpect(jsonPath("$.data.result.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.result.items[0].dishId").value("DISH-ASSIST-001"));
    }

    @Test
    void resolves_a_date_menu_query_and_returns_only_published_menus() throws Exception {
        jdbc.update(
                "INSERT INTO daily_menus (school_id, canteen_id, menu_id, menu_date, meal_time, status, version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "M003", "2026-08-17", "LUNCH", "PUBLISHED", 2);
        jdbc.update(
                "INSERT INTO daily_menu_items (school_id, canteen_id, menu_id, dish_id, estimated_quantity, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "M003", "DISH-ASSIST-001", 120, 1);
        jdbc.update(
                "INSERT INTO daily_menus (school_id, canteen_id, menu_id, menu_date, meal_time, status, version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "M004", "2026-08-17", "DINNER", "DRAFT", 1);

        mvc.perform(message(
                        "menu-date-message-001",
                        "查询 2026-08-17 的菜单",
                        "CONV-ASSIST-MENU-DATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("RESULT"))
                .andExpect(jsonPath("$.data.intent").value("menu.query"))
                .andExpect(jsonPath("$.data.runStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.records.length()").value(1))
                .andExpect(jsonPath("$.data.result.records[0].id").value("M003"))
                .andExpect(jsonPath("$.data.result.records[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.message").value(
                        org.hamcrest.Matchers.containsString("2026-08-17")));
    }

    @Test
    void resolves_an_inventory_query_and_returns_the_system_warning_fact() throws Exception {
        mvc.perform(message(
                        "inventory-message-001",
                        "哪些食材库存不足？",
                        "CONV-ASSIST-INVENTORY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("RESULT"))
                .andExpect(jsonPath("$.data.intent").value("inventory.query"))
                .andExpect(jsonPath("$.data.runStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.records[0].ingredientId").value("ING-ASSIST"))
                .andExpect(jsonPath("$.data.result.records[0].warning").value(true))
                .andExpect(jsonPath("$.data.message").value(
                        org.hamcrest.Matchers.containsString("1 项低于或等于预警阈值")));
    }

    @Test
    void resolves_a_menu_ingredient_gap_query_without_creating_a_procurement_plan()
            throws Exception {
        jdbc.update(
                "INSERT INTO dishes (school_id, canteen_id, dish_id, name, category, status, version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "DISH-GAP-001", "西兰花炒肉", "主菜", "ACTIVE", 0);
        jdbc.update(
                "INSERT INTO dish_ingredients (school_id, canteen_id, dish_id, ingredient_id, quantity, unit) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "DISH-GAP-001", "ING-ASSIST", 500, "g");
        jdbc.update(
                "UPDATE inventory SET quantity_base = ?, base_unit = ? "
                        + "WHERE school_id = ? AND canteen_id = ? AND material_id = ?",
                10000, "g", SCHOOL_ID, CANTEEN_ID, "ING-ASSIST");
        jdbc.update(
                "INSERT INTO daily_menus (school_id, canteen_id, menu_id, menu_date, meal_time, status, version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "M005", "2026-08-22", "LUNCH", "PUBLISHED", 1);
        jdbc.update(
                "INSERT INTO daily_menu_items (school_id, canteen_id, menu_id, dish_id, estimated_quantity, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "M005", "DISH-GAP-001", 40, 1);

        mvc.perform(message(
                        "gap-message-001",
                        "检查 2026-08-22 午餐菜单有没有原材料不足",
                        "CONV-ASSIST-GAP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("RESULT"))
                .andExpect(jsonPath("$.data.intent").value("procurement.gap.query"))
                .andExpect(jsonPath("$.data.runStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.menuDate").value("2026-08-22"))
                .andExpect(jsonPath("$.data.result.sourceMenuIds[0]").value("M005"))
                .andExpect(jsonPath("$.data.result.items[0].ingredientId").value("ING-ASSIST"))
                .andExpect(jsonPath("$.data.result.items[0].shortageBaseQuantity").value(10000))
                .andExpect(jsonPath("$.data.result.shortageCount").value(1))
                .andExpect(jsonPath("$.data.message").value(
                        org.hamcrest.Matchers.containsString("未创建采购计划")));

        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM procurement_plans WHERE school_id = ?",
                        Integer.class,
                SCHOOL_ID));
    }

    @Test
    void reports_an_empty_gap_analysis_without_creating_a_procurement_plan() throws Exception {
        mvc.perform(message(
                        "gap-empty-message-001",
                        "检查 2026-09-01 午餐菜单有没有原材料不足",
                        "CONV-ASSIST-GAP-EMPTY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("RESULT"))
                .andExpect(jsonPath("$.data.intent").value("procurement.gap.query"))
                .andExpect(jsonPath("$.data.runStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.sourceMenuIds.length()").value(0))
                .andExpect(jsonPath("$.data.result.items.length()").value(0))
                .andExpect(jsonPath("$.data.message").value(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("未找到"),
                                org.hamcrest.Matchers.containsString("未创建采购计划"))));

        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM procurement_plans WHERE school_id = ?",
                        Integer.class,
                SCHOOL_ID));
    }

    @Test
    void previews_and_confirms_a_procurement_application_draft_without_creating_an_order()
            throws Exception {
        jdbc.update(
                "INSERT INTO dishes (school_id, canteen_id, dish_id, name, category, status, version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "DISH-DRAFT-001", "采购草稿菜", "主菜", "ACTIVE", 1);
        jdbc.update(
                "INSERT INTO dish_ingredients (school_id, canteen_id, dish_id, ingredient_id, quantity, unit) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "DISH-DRAFT-001", "ING-ASSIST", 500, "g");
        jdbc.update(
                "UPDATE inventory SET quantity_base = ?, base_unit = ? "
                        + "WHERE school_id = ? AND canteen_id = ? AND material_id = ?",
                1000, "g", SCHOOL_ID, CANTEEN_ID, "ING-ASSIST");
        jdbc.update(
                "INSERT INTO daily_menus (school_id, canteen_id, menu_id, menu_date, meal_time, status, version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "M007", "2026-08-22", "LUNCH", "PUBLISHED", 1);
        jdbc.update(
                "INSERT INTO daily_menu_items (school_id, canteen_id, menu_id, dish_id, "
                        + "estimated_quantity, sort_order) VALUES (?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "M007", "DISH-DRAFT-001", 40, 1);

        int plansBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM procurement_plans WHERE school_id = ?", Integer.class, SCHOOL_ID);
        mvc.perform(message(
                        "procurement-draft-preview-001",
                        "帮我生成 2026-08-22 的采购申请草稿",
                        "CONV-ASSIST-PROCUREMENT-DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.data.intent").value("procurement.plan.generate"))
                .andExpect(jsonPath("$.data.runStatus").value("WAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.data.result.businessParameters.periodStart")
                        .value("2026-08-22"))
                .andExpect(jsonPath("$.data.result.businessParameters.periodEnd")
                        .value("2026-08-22"))
                .andExpect(jsonPath("$.data.message")
                        .value(org.hamcrest.Matchers.containsString("确认")));
        assertEquals(
                plansBefore,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM procurement_plans WHERE school_id = ?",
                        Integer.class,
                        SCHOOL_ID));

        String confirmed = mvc.perform(message(
                        "procurement-draft-confirm-001",
                        "确认",
                        "CONV-ASSIST-PROCUREMENT-DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("RESULT"))
                .andExpect(jsonPath("$.data.intent").value("procurement.plan.generate"))
                .andExpect(jsonPath("$.data.runStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.result.items[0].requiredBaseQuantity").value(20000))
                .andExpect(jsonPath("$.data.result.items[0].inventoryBaseQuantity").value(1000))
                .andExpect(jsonPath("$.data.result.items[0].shortageBaseQuantity").value(19000))
                .andExpect(jsonPath("$.data.message")
                        .value(org.hamcrest.Matchers.containsString("采购申请 Draft")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String turnId = com.jayway.jsonpath.JsonPath.read(confirmed, "$.data.turnId");

        mvc.perform(message(
                        "procurement-draft-confirm-001",
                        "确认",
                        "CONV-ASSIST-PROCUREMENT-DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.turnId").value(turnId))
                .andExpect(jsonPath("$.data.result.status").value("DRAFT"));
        assertEquals(
                plansBefore + 1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM procurement_plans WHERE school_id = ?",
                        Integer.class,
                        SCHOOL_ID));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM purchase_orders WHERE school_id = ?",
                        Integer.class,
                        SCHOOL_ID));

        mvc.perform(message(
                        "procurement-draft-confirm-001",
                        "取消",
                        "CONV-ASSIST-PROCUREMENT-DRAFT"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void asks_for_the_period_before_starting_a_procurement_draft_run() throws Exception {
        int runsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_runs WHERE actor_user_id = ?", Integer.class,
                PRINCIPAL.userId());

        mvc.perform(message(
                        "procurement-draft-clarification-001",
                        "请生成采购申请草稿",
                        "CONV-ASSIST-PROCUREMENT-DRAFT-CLARIFY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CLARIFICATION"))
                .andExpect(jsonPath("$.data.intent").value("procurement.plan.generate"))
                .andExpect(jsonPath("$.data.missingFields[0]").value("periodStart"))
                .andExpect(jsonPath("$.data.missingFields[1]").value("periodEnd"))
                .andExpect(jsonPath("$.data.runId").value(org.hamcrest.Matchers.nullValue()));

        assertEquals(
                runsBefore,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM agent_runs WHERE actor_user_id = ?", Integer.class,
                        PRINCIPAL.userId()));
    }

    @Test
    void resumes_a_procurement_draft_clarification_with_the_missing_date() throws Exception {
        mvc.perform(message(
                        "procurement-draft-resume-start-001",
                        "请生成采购申请草稿",
                        "CONV-ASSIST-PROCUREMENT-DRAFT-RESUME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CLARIFICATION"))
                .andExpect(jsonPath("$.data.intent").value("procurement.plan.generate"));

        mvc.perform(message(
                        "procurement-draft-resume-date-001",
                        "2026-08-22",
                        "CONV-ASSIST-PROCUREMENT-DRAFT-RESUME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.data.intent").value("procurement.plan.generate"))
                .andExpect(jsonPath("$.data.runStatus").value("WAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.data.result.businessParameters.periodStart")
                        .value("2026-08-22"))
                .andExpect(jsonPath("$.data.result.businessParameters.periodEnd")
                        .value("2026-08-22"));
    }

    @Test
    void resolves_a_versioned_traffic_forecast_without_guessing_or_writing() throws Exception {
        jdbc.update(
                "INSERT INTO traffic_forecasts (school_id, canteen_id, forecast_date, meal_time, "
                        + "expected_diner_count, lower_bound, upper_bound, model_version, source, generated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "2026-08-22", "LUNCH", 850, 810, 880,
                "study-traffic-v1", "GENERATED_STUDY_FACT", "2026-08-21 09:00:00");

        int runsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_runs WHERE actor_user_id = ?", Integer.class,
                PRINCIPAL.userId());
        mvc.perform(message(
                        "traffic-message-001",
                        "查询 2026-08-22 午餐预计有多少人用餐",
                        "CONV-ASSIST-TRAFFIC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("RESULT"))
                .andExpect(jsonPath("$.data.intent").value("traffic.forecast.query"))
                .andExpect(jsonPath("$.data.runStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.available").value(true))
                .andExpect(jsonPath("$.data.result.expectedDinerCount").value(850))
                .andExpect(jsonPath("$.data.result.lowerBound").value(810))
                .andExpect(jsonPath("$.data.result.upperBound").value(880))
                .andExpect(jsonPath("$.data.message").value(
                        org.hamcrest.Matchers.containsString("预测区间")));
        assertEquals(
                runsBefore + 1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM agent_runs WHERE actor_user_id = ?", Integer.class,
                        PRINCIPAL.userId()));
    }

    @Test
    void selects_the_latest_retained_forecast_version_for_the_same_business_slice() throws Exception {
        jdbc.update(
                "INSERT INTO traffic_forecasts (school_id, canteen_id, forecast_date, meal_time, "
                        + "expected_diner_count, lower_bound, upper_bound, model_version, source, generated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "2026-08-22", "LUNCH", 790, 760, 820,
                "study-traffic-v0", "GENERATED_STUDY_FACT", "2026-08-21 08:00:00",
                SCHOOL_ID, CANTEEN_ID, "2026-08-22", "LUNCH", 850, 810, 880,
                "study-traffic-v1", "GENERATED_STUDY_FACT", "2026-08-21 09:00:00");

        mvc.perform(message(
                        "traffic-version-message-001",
                        "查询 2026-08-22 午餐预计有多少人用餐",
                        "CONV-ASSIST-TRAFFIC-VERSION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.expectedDinerCount").value(850))
                .andExpect(jsonPath("$.data.result.modelVersion").value("study-traffic-v1"));
        assertEquals(
                2,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM traffic_forecasts WHERE school_id = ? "
                                + "AND forecast_date = ? AND meal_time = ?", Integer.class,
                        SCHOOL_ID, "2026-08-22", "LUNCH"));
    }

    @Test
    void asks_for_the_date_when_meal_prep_request_is_missing_it() throws Exception {
        mvc.perform(message(
                        "meal-prep-clarification-001",
                        "午餐应该备多少份？",
                        "CONV-ASSIST-MEAL-PREP-CLARIFY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CLARIFICATION"))
                .andExpect(jsonPath("$.data.intent").value("meal_plan.query"))
                .andExpect(jsonPath("$.data.missingFields[0]").value("menuDate"))
                .andExpect(jsonPath("$.data.runId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void asks_for_the_meal_time_when_traffic_forecast_request_is_missing_it() throws Exception {
        mvc.perform(message(
                        "traffic-clarification-001",
                        "明天预计有多少人用餐？",
                        "CONV-ASSIST-TRAFFIC-CLARIFY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CLARIFICATION"))
                .andExpect(jsonPath("$.data.intent").value("traffic.forecast.query"))
                .andExpect(jsonPath("$.data.missingFields[0]").value("mealTime"))
                .andExpect(jsonPath("$.data.runId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void resumes_a_traffic_clarification_without_losing_the_original_date() throws Exception {
        mvc.perform(message(
                        "traffic-clarification-start-001",
                        "明天预计有多少人用餐？",
                        "CONV-ASSIST-TRAFFIC-RESUME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CLARIFICATION"))
                .andExpect(jsonPath("$.data.missingFields[0]").value("mealTime"));

        mvc.perform(message(
                        "traffic-clarification-answer-001",
                        "午餐",
                        "CONV-ASSIST-TRAFFIC-RESUME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("RESULT"))
                .andExpect(jsonPath("$.data.intent").value("traffic.forecast.query"))
                .andExpect(jsonPath("$.data.result.forecastDate").value("2026-08-22"))
                .andExpect(jsonPath("$.data.result.mealTime").value("LUNCH"))
                .andExpect(jsonPath("$.data.result.available").value(false))
                .andExpect(jsonPath("$.data.result.reason").value("NO_FORECAST_FACT"));
    }

    @Test
    void returns_a_deterministic_meal_prep_recommendation_without_creating_a_plan() throws Exception {
        jdbc.update(
                "INSERT INTO traffic_forecasts (school_id, canteen_id, forecast_date, meal_time, "
                        + "expected_diner_count, lower_bound, upper_bound, model_version, source, generated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "2026-08-22", "LUNCH", 850, 810, 880,
                "study-traffic-v1", "GENERATED_STUDY_FACT", "2026-08-21 09:00:00");
        jdbc.update(
                "INSERT INTO dishes (school_id, canteen_id, dish_id, name, category, status, version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "DISH-PREP-001", "番茄牛腩", "主菜", "ACTIVE", 1,
                SCHOOL_ID, CANTEEN_ID, "DISH-PREP-002", "宫保鸡丁", "主菜", "ACTIVE", 1);
        jdbc.update(
                "INSERT INTO daily_menus (school_id, canteen_id, menu_id, menu_date, meal_time, status, version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "M006", "2026-08-22", "LUNCH", "PUBLISHED", 1);
        jdbc.update(
                "INSERT INTO daily_menu_items (school_id, canteen_id, menu_id, dish_id, estimated_quantity, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?, ?), (?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "M006", "DISH-PREP-001", 100, 1,
                SCHOOL_ID, CANTEEN_ID, "M006", "DISH-PREP-002", 200, 2);

        int plansBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM procurement_plans WHERE school_id = ?", Integer.class, SCHOOL_ID);
        mvc.perform(message(
                        "meal-prep-message-001",
                        "分析 2026-08-22 午餐备餐应该准备多少份",
                        "CONV-ASSIST-MEAL-PREP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("RESULT"))
                .andExpect(jsonPath("$.data.intent").value("meal_plan.query"))
                .andExpect(jsonPath("$.data.runStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.available").value(true))
                .andExpect(jsonPath("$.data.result.sourceMenuId").value("M006"))
                .andExpect(jsonPath("$.data.result.totalRecommendedQuantity").value(850))
                .andExpect(jsonPath("$.data.result.items[0].recommendedQuantity").value(283))
                .andExpect(jsonPath("$.data.result.items[1].recommendedQuantity").value(567))
                .andExpect(jsonPath("$.data.message").value(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("未创建备餐计划"),
                                org.hamcrest.Matchers.containsString("未创建采购计划"))));
        assertEquals(
                plansBefore,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM procurement_plans WHERE school_id = ?", Integer.class,
                        SCHOOL_ID));
        assertEquals(
                "PUBLISHED",
                jdbc.queryForObject(
                        "SELECT status FROM daily_menus WHERE school_id = ? AND canteen_id = ? "
                                + "AND menu_id = ?", String.class,
                        SCHOOL_ID, CANTEEN_ID, "M006"));
    }

    @Test
    void previews_and_confirms_a_menu_publish_without_bypassing_domain_approval() throws Exception {
        jdbc.update(
                "INSERT INTO daily_menus (school_id, canteen_id, menu_id, menu_date, meal_time, "
                        + "status, version, submitted_by, decision_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "M002", "2026-08-18", "LUNCH",
                "APPROVED", 3, "USER-SUBMITTER", "USER-APPROVER");
        jdbc.update(
                "INSERT INTO daily_menu_items (school_id, canteen_id, menu_id, dish_id, "
                        + "estimated_quantity, sort_order) VALUES (?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "M002", "DISH-ASSIST-001", 120, 1);

        mvc.perform(message("publish-preview", "请发布 M002", "CONV-ASSIST-PUBLISH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.data.intent").value("menu.publish"))
                .andExpect(jsonPath("$.data.runStatus").value("WAITING_CONFIRMATION"))
                .andExpect(jsonPath("$.data.result.businessParameters.menuId")
                        .value("M002"));
        assertEquals(
                "WAITING_CONFIRMATION",
                jdbc.queryForObject(
                        "SELECT status FROM assistant_conversations WHERE conversation_id = ?",
                        String.class,
                        "CONV-ASSIST-PUBLISH"));
        assertEquals(
                "APPROVED",
                jdbc.queryForObject(
                        "SELECT status FROM daily_menus WHERE school_id = ? AND canteen_id = ? "
                        + "AND menu_id = ?",
                        String.class,
                        SCHOOL_ID, CANTEEN_ID, "M002"));

        mvc.perform(message("publish-unrelated", "查询 TRACE-ASSIST-001", "CONV-ASSIST-PUBLISH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.data.intent").value("menu.publish"))
                .andExpect(jsonPath("$.data.runStatus").value("WAITING_CONFIRMATION"));

        mvc.perform(message("publish-replan", "请发布 M003", "CONV-ASSIST-PUBLISH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.data.intent").value("menu.publish"))
                .andExpect(jsonPath("$.data.runStatus").value("WAITING_CONFIRMATION"));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM assistant_pending_actions WHERE conversation_id = ?",
                        Integer.class,
                        "CONV-ASSIST-PUBLISH"));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM agent_runs WHERE intent = 'menu.publish'",
                        Integer.class));

        mvc.perform(message("publish-confirm", "确认发布", "CONV-ASSIST-PUBLISH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("RESULT"))
                .andExpect(jsonPath("$.data.intent").value("menu.publish"))
                .andExpect(jsonPath("$.data.runStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.status").value("PUBLISHED"));
        assertEquals(
                "ACTIVE",
                jdbc.queryForObject(
                        "SELECT status FROM assistant_conversations WHERE conversation_id = ?",
                        String.class,
                        "CONV-ASSIST-PUBLISH"));
    }

    @Test
    void reconciles_a_pending_action_after_the_agent_run_is_cancelled_elsewhere() throws Exception {
        jdbc.update(
                "INSERT INTO daily_menus (school_id, canteen_id, menu_id, menu_date, meal_time, "
                        + "status, version, submitted_by, decision_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                SCHOOL_ID, CANTEEN_ID, "M004", "2026-08-19", "LUNCH",
                "APPROVED", 1, "USER-SUBMITTER", "USER-APPROVER");
        String preview = mvc.perform(message(
                        "external-cancel-preview",
                        "请发布 M004",
                        "CONV-ASSIST-EXTERNAL-CANCEL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CONFIRMATION_REQUIRED"))
                .andReturn().getResponse().getContentAsString();
        String runId = com.jayway.jsonpath.JsonPath.read(preview, "$.data.runId");
        long runVersion = jdbc.queryForObject(
                "SELECT version FROM agent_runs WHERE run_id = ?", Long.class, runId);

        mvc.perform(post("/api/v1/agent/runs/{runId}/cancel", runId)
                        .queryParam("schoolId", SCHOOL_ID)
                        .queryParam("canteenId", CANTEEN_ID)
                        .header("Idempotency-Key", "external-cancel-decision")
                        .header("X-Request-Id", "external-cancel-request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + runVersion + "}")
                        .requestAttr(AuthPrincipal.class.getName(), PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        mvc.perform(message(
                        "external-cancel-confirm",
                        "确认发布",
                        "CONV-ASSIST-EXTERNAL-CANCEL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("RESULT"))
                .andExpect(jsonPath("$.data.runStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.data.message").value(
                        org.hamcrest.Matchers.containsString("其他入口")));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM assistant_pending_actions WHERE conversation_id = ?",
                        Integer.class,
                        "CONV-ASSIST-EXTERNAL-CANCEL"));
        assertEquals(
                "ACTIVE",
                jdbc.queryForObject(
                        "SELECT status FROM assistant_conversations WHERE conversation_id = ?",
                        String.class,
                        "CONV-ASSIST-EXTERNAL-CANCEL"));
    }

    @Test
    void returns_the_owned_conversation_history_without_creating_a_new_turn() throws Exception {
        mvc.perform(message("history-message-001", "查询 TRACE-ASSIST-001", "CONV-ASSIST-HISTORY"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/assistant/conversations/{conversationId}/messages", "CONV-ASSIST-HISTORY")
                        .queryParam("schoolId", SCHOOL_ID)
                        .queryParam("canteenId", CANTEEN_ID)
                        .requestAttr(AuthPrincipal.class.getName(), PRINCIPAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value("CONV-ASSIST-HISTORY"))
                .andExpect(jsonPath("$.data.turns.length()").value(1))
                .andExpect(jsonPath("$.data.turns[0].userMessage").value("查询 TRACE-ASSIST-001"))
                .andExpect(jsonPath("$.data.turns[0].response.runId").isNotEmpty());
    }

    @Test
    void rejects_history_from_a_different_scope() throws Exception {
        mvc.perform(message("history-message-002", "查询 TRACE-ASSIST-001", "CONV-ASSIST-SCOPE"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/assistant/conversations/{conversationId}/messages", "CONV-ASSIST-SCOPE")
                        .queryParam("schoolId", "OTHER-SCHOOL")
                        .queryParam("canteenId", "OTHER-CANTEEN")
                        .requestAttr(AuthPrincipal.class.getName(), PRINCIPAL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
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
