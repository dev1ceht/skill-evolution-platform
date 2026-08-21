package com.example.smartcanteen.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.smartcanteen.assistant.application.RuleBasedAssistantIntentResolver;
import com.example.smartcanteen.assistant.domain.AssistantClarification;
import com.example.smartcanteen.assistant.domain.AssistantPendingAction;
import com.example.smartcanteen.assistant.domain.AssistantResolution;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuleBasedAssistantIntentResolverTest {

    private final RuleBasedAssistantIntentResolver resolver =
            new RuleBasedAssistantIntentResolver();

    @Test
    void resolves_a_traceability_message_with_a_trace_code() {
        AssistantResolution result = resolver.resolve("请查询 TRACE-001 的食品溯源信息");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.TRACEABILITY_QUERY);
        assertThat(result.intent()).isEqualTo("traceability.query");
        assertThat(result.traceCode()).isEqualTo("TRACE-001");
        assertThat(result.missingFields()).isEmpty();
    }

    @Test
    void asks_for_the_trace_code_when_the_user_requests_traceability_without_one() {
        AssistantResolution result = resolver.resolve("帮我查一下这批食材的溯源");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.CLARIFICATION);
        assertThat(result.missingFields()).containsExactly("traceCode");
        assertThat(result.message()).contains("溯源码");
    }

    @Test
    void does_not_treat_a_menu_id_as_a_traceability_code() {
        AssistantResolution result = resolver.resolve("查询 M001 的食品溯源");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.CLARIFICATION);
        assertThat(result.missingFields()).containsExactly("traceCode");
    }

    @Test
    void explains_the_supported_capability_for_an_unrelated_message() {
        AssistantResolution result = resolver.resolve("帮我安排明天的采购");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.UNSUPPORTED);
        assertThat(result.intent()).isNull();
        assertThat(result.message()).contains("食品溯源");
    }

    @Test
    void resolves_a_menu_query_with_a_menu_id() {
        AssistantResolution result = resolver.resolve("请查询 M001 的午餐菜单");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.MENU_QUERY);
        assertThat(result.intent()).isEqualTo("menu.query");
        assertThat(result.menuId()).isEqualTo("M001");
    }

    @Test
    void resolves_today_menu_query_without_requiring_a_menu_id() {
        AssistantResolution result = resolver.resolve("今天有什么菜？");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.MENU_QUERY);
        assertThat(result.intent()).isEqualTo("menu.query");
        assertThat(result.menuId()).isNull();
        assertThat(result.parameters()).containsEntry("menuDate", LocalDate.now().toString());
    }

    @Test
    void resolves_a_date_and_meal_time_menu_query() {
        AssistantResolution result = resolver.resolve("查询 2026-08-17 午餐菜单");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.MENU_QUERY);
        assertThat(result.menuId()).isNull();
        assertThat(result.parameters())
                .containsEntry("menuDate", "2026-08-17")
                .containsEntry("mealTime", "LUNCH");
    }

    @Test
    void accepts_an_iso_date_outside_the_current_century() {
        AssistantResolution result = resolver.resolve("查询 1999-08-17 的菜单");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.MENU_QUERY);
        assertThat(result.parameters()).containsEntry("menuDate", "1999-08-17");
    }

    @Test
    void resolves_a_menu_publish_request_without_executing_it() {
        AssistantResolution result = resolver.resolve("请发布 M001");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.MENU_PUBLISH_REQUEST);
        assertThat(result.intent()).isEqualTo("menu.publish");
        assertThat(result.menuId()).isEqualTo("M001");
    }

    @Test
    void rejects_a_procurement_publish_request_before_menu_publish_matching() {
        AssistantResolution result = resolver.resolve("请发布采购计划");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.UNSUPPORTED);
        assertThat(result.intent()).isNull();
        assertThat(result.menuId()).isNull();
        assertThat(result.message()).contains("采购");
    }

    @Test
    void asks_for_a_menu_id_when_publish_request_is_missing_one() {
        AssistantResolution result = resolver.resolve("请发布今天的菜单");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.CLARIFICATION);
        assertThat(result.intent()).isEqualTo("menu.publish");
        assertThat(result.missingFields()).containsExactly("menuId");
    }

    @Test
    void resolves_a_menu_id_as_the_answer_to_a_pending_menu_publish_clarification() {
        AssistantClarification pending = new AssistantClarification(
                "CONV-001",
                "menu.publish",
                "请发布今天的菜单",
                java.util.List.of("menuId"),
                Instant.parse("2026-08-17T05:00:00Z"),
                Instant.parse("2026-08-17T05:00:00Z"));

        AssistantResolution result = resolver.resolve("M001", Optional.of(pending));

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.MENU_PUBLISH_REQUEST);
        assertThat(result.intent()).isEqualTo("menu.publish");
        assertThat(result.menuId()).isEqualTo("M001");
    }

    @Test
    void resolves_a_trace_code_as_the_answer_to_a_pending_clarification() {
        AssistantClarification pending = new AssistantClarification(
                "CONV-001",
                "traceability.query",
                "帮我查一下这批食材的溯源",
                java.util.List.of("traceCode"),
                Instant.parse("2026-08-17T05:00:00Z"),
                Instant.parse("2026-08-17T05:00:00Z"));

        AssistantResolution result = resolver.resolve("TRACE-001", Optional.of(pending));

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.TRACEABILITY_QUERY);
        assertThat(result.traceCode()).isEqualTo("TRACE-001");
    }

    @Test
    void does_not_consume_a_new_unsupported_request_as_a_pending_answer() {
        AssistantClarification pending = new AssistantClarification(
                "CONV-001",
                "traceability.query",
                "帮我查一下这批食材的溯源",
                java.util.List.of("traceCode"),
                Instant.parse("2026-08-17T05:00:00Z"),
                Instant.parse("2026-08-17T05:00:00Z"));

        AssistantResolution result = resolver.resolve("帮我安排明天的采购", Optional.of(pending));

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.UNSUPPORTED);
        assertThat(result.message()).contains("食品溯源");
    }

    @Test
    void resolves_confirmation_and_cancellation_for_a_pending_menu_publish() {
        AssistantPendingAction pending = new AssistantPendingAction(
                        "CONV-001",
                        "menu.publish",
                        "RUN-001",
                        0,
                        "M001",
                        3,
                        "a".repeat(64),
                        Instant.parse("2026-08-17T05:00:00Z"),
                        Instant.parse("2026-08-17T05:00:00Z"));

        AssistantResolution confirm = resolver.resolve(
                "确认发布", Optional.empty(), Optional.of(pending));
        AssistantResolution cancel = resolver.resolve(
                "取消", Optional.empty(), Optional.of(pending));

        assertThat(confirm.type()).isEqualTo(AssistantResolution.Type.CONFIRM_PENDING_ACTION);
        assertThat(confirm.intent()).isEqualTo("menu.publish");
        assertThat(cancel.type()).isEqualTo(AssistantResolution.Type.CANCEL_PENDING_ACTION);
        assertThat(cancel.intent()).isEqualTo("menu.publish");
    }

    @Test
    void resolves_a_procurement_plan_write_with_an_explicit_period() {
        AssistantResolution result = resolver.resolve(
                "生成采购计划 2026-08-18 至 2026-08-24");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.WRITE_REQUEST);
        assertThat(result.intent()).isEqualTo("procurement.plan.generate");
        assertThat(result.parameters())
                .containsEntry("periodStart", "2026-08-18")
                .containsEntry("periodEnd", "2026-08-24");
    }

    @Test
    void requires_a_confirmed_plan_before_creating_a_purchase_order() {
        AssistantResolution result = resolver.resolve(
                "创建采购订单，供应商 SUP-001，食材 ING-001 10 kg，单价 8");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.CLARIFICATION);
        assertThat(result.intent()).isEqualTo("procurement.order.create");
        assertThat(result.missingFields()).containsExactly("planId");
    }

    @Test
    void resolves_a_manual_inventory_receipt_with_traceability_coordinates() {
        AssistantResolution result = resolver.resolve(
                "库存入库 ING-001，供应商 SUP-001，2 kg，批次 BATCH-001，采购价 8");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.WRITE_REQUEST);
        assertThat(result.intent()).isEqualTo("inventory.receive");
        assertThat(result.parameters())
                .containsEntry("materialId", "ING-001")
                .containsEntry("supplierId", "SUP-001")
                .containsEntry("batchNo", "BATCH-001")
                .containsEntry("purchasePrice", "8");
    }

    @Test
    void resolves_an_inventory_stock_out_write_and_keeps_the_reason() {
        AssistantResolution result = resolver.resolve(
                "库存出库 ING-001 2 kg，原因 午餐备料");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.WRITE_REQUEST);
        assertThat(result.intent()).isEqualTo("inventory.stock-out");
        assertThat(result.parameters())
                .containsEntry("ingredientId", "ING-001")
                .containsEntry("quantity", "2")
                .containsEntry("unit", "kg")
                .containsEntry("reason", "午餐备料");
    }

    @Test
    void resolves_a_general_inventory_query_for_operations_staff() {
        AssistantResolution result = resolver.resolve("查询库存");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.INVENTORY_QUERY);
        assertThat(result.intent()).isEqualTo("inventory.query");
        assertThat(result.parameters()).isEmpty();
    }

    @Test
    void resolves_a_low_inventory_query_as_a_deterministic_warning_filter() {
        AssistantResolution result = resolver.resolve("哪些食材库存不足？");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.INVENTORY_QUERY);
        assertThat(result.intent()).isEqualTo("inventory.query");
        assertThat(result.parameters()).containsEntry("warningOnly", "true");
    }

    @Test
    void keeps_an_ingredient_name_as_the_inventory_keyword() {
        AssistantResolution result = resolver.resolve("西兰花还剩多少？");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.INVENTORY_QUERY);
        assertThat(result.intent()).isEqualTo("inventory.query");
        assertThat(result.parameters()).containsEntry("keyword", "西兰花");
        assertThat(result.parameters()).containsEntry("warningOnly", "false");
    }

    @Test
    void keeps_an_ingredient_name_when_the_inventory_query_is_filtered_to_warnings() {
        AssistantResolution result = resolver.resolve("西兰花库存不足吗？");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.INVENTORY_QUERY);
        assertThat(result.parameters())
                .containsEntry("keyword", "西兰花")
                .containsEntry("warningOnly", "true");
    }

    @Test
    void resolves_a_tomorrow_menu_ingredient_gap_query() {
        AssistantResolution result = resolver.resolve("检查一下明天的菜单有没有原材料不足");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.PROCUREMENT_GAP_QUERY);
        assertThat(result.intent()).isEqualTo("procurement.gap.query");
        assertThat(result.parameters())
                .containsEntry("menuDate", LocalDate.now().plusDays(1).toString());
    }

    @Test
    void keeps_the_meal_time_when_resolving_a_menu_ingredient_gap_query() {
        AssistantResolution result = resolver.resolve("明天午餐有哪些食材缺口？");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.PROCUREMENT_GAP_QUERY);
        assertThat(result.parameters())
                .containsEntry("menuDate", LocalDate.now().plusDays(1).toString())
                .containsEntry("mealTime", "LUNCH");
    }

    @Test
    void resolves_a_tomorrow_lunch_traffic_forecast_query() {
        AssistantResolution result = resolver.resolve("明天午餐预计有多少人用餐？");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.TRAFFIC_FORECAST_QUERY);
        assertThat(result.intent()).isEqualTo("traffic.forecast.query");
        assertThat(result.parameters())
                .containsEntry("forecastDate", LocalDate.now().plusDays(1).toString())
                .containsEntry("mealTime", "LUNCH");
    }

    @Test
    void resolves_a_tomorrow_lunch_meal_prep_recommendation_query() {
        AssistantResolution result = resolver.resolve("明天午餐应该备多少份？");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.MEAL_PLAN_QUERY);
        assertThat(result.intent()).isEqualTo("meal_plan.query");
        assertThat(result.parameters())
                .containsEntry("menuDate", LocalDate.now().plusDays(1).toString())
                .containsEntry("mealTime", "LUNCH");
    }

    @Test
    void asks_for_the_meal_time_when_meal_prep_request_has_only_a_date() {
        AssistantResolution result = resolver.resolve("明天应该备多少份？");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.CLARIFICATION);
        assertThat(result.intent()).isEqualTo("meal_plan.query");
        assertThat(result.missingFields()).containsExactly("mealTime");
    }

    @Test
    void merges_a_meal_time_answer_with_the_original_traffic_forecast_date() {
        AssistantClarification pending = new AssistantClarification(
                "CONV-TRAFFIC-001",
                "traffic.forecast.query",
                "明天预计有多少人用餐？",
                java.util.List.of("mealTime"),
                Instant.parse("2026-08-17T05:00:00Z"),
                Instant.parse("2026-08-17T05:00:00Z"));

        AssistantResolution result = resolver.resolve("午餐", Optional.of(pending));

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.TRAFFIC_FORECAST_QUERY);
        assertThat(result.parameters())
                .containsEntry("forecastDate", LocalDate.now().plusDays(1).toString())
                .containsEntry("mealTime", "LUNCH");
    }

    @Test
    void asks_for_an_alert_id_before_allowing_disposal() {
        AssistantResolution result = resolver.resolve("处置预警，说明已整改");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.CLARIFICATION);
        assertThat(result.intent()).isEqualTo("alert.dispose");
        assertThat(result.missingFields()).containsExactly("warnId");
    }
}
