package com.example.smartcanteen.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.infrastructure.MealOrderToolExecutor;
import com.example.smartcanteen.application.MealOrderService;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.MealOrder;
import com.example.smartcanteen.domain.MealOrderItem;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MealOrderToolExecutorTest {

    private final MealOrderService orders = mock(MealOrderService.class);
    private final MealOrderToolExecutor executor = new MealOrderToolExecutor(
            orders, new ObjectMapper().findAndRegisterModules());
    private final ExecutionContext context = new ExecutionContext(
            "REQ-MEAL-ORDER-001",
            "USER-DINER-001",
            "diner",
            new CanteenScope("SCHOOL-001", "CANTEEN-001"),
            Set.of(Role.DINER),
            Set.of("MEAL_ORDER_READ", "MEAL_ORDER_WRITE"));

    @Test
    void exposes_personal_query_create_and_cancel_tools() {
        assertThat(executor.supports("meal_order.query")).isTrue();
        assertThat(executor.supports("meal_order.create")).isTrue();
        assertThat(executor.supports("meal_order.cancel")).isTrue();
        assertThat(executor.supports("meal_order.pay")).isTrue();
        assertThat(executor.supports("payment.create")).isFalse();
    }

    @Test
    void delegates_query_with_actor_from_execution_context() {
        PageResult<MealOrder> expected = new PageResult<>(List.of(order()), 1, 100, 1);
        when(orders.listMine(
                        context.scope(), context.actorUserId(), null, 1, 100))
                .thenReturn(expected);

        var result = executor.execute("meal_order.query", context, "{}");

        assertThat(result.resultJson()).contains("MEAL-001").contains("DISH-001");
        verify(orders).listMine(context.scope(), context.actorUserId(), null, 1, 100);
    }

    @Test
    void parses_write_inputs_and_keeps_business_idempotency_key_explicit() {
        when(orders.create(
                        eq(context.scope()),
                        eq(context.actorUserId()),
                        eq("M001"),
                        eq(LocalDate.of(2026, 8, 21)),
                        eq("LUNCH"),
                        anyList(),
                        eq("AGENT-STEP-KEY")))
                .thenReturn(order());

        var result = executor.execute(
                "meal_order.create",
                context,
                """
                {"menuId":"M001","menuDate":"2026-08-21","mealTime":"LUNCH",
                 "items":[{"dishId":"DISH-001","quantity":2}],
                 "businessIdempotencyKey":"AGENT-STEP-KEY"}
                """);

        assertThat(result.resultJson()).contains("MEAL-001");
        verify(orders).create(
                eq(context.scope()),
                eq(context.actorUserId()),
                eq("M001"),
                eq(LocalDate.of(2026, 8, 21)),
                eq("LUNCH"),
                anyList(),
                eq("AGENT-STEP-KEY"));
    }

    @Test
    void rejects_unknown_fields_and_missing_business_idempotency_key() {
        assertThatThrownBy(() -> executor.execute(
                        "meal_order.query", context, "{\"actorUserId\":\"OTHER\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported meal order field");
        assertThatThrownBy(() -> executor.execute(
                        "meal_order.create", context,
                        "{\"menuId\":\"M001\",\"items\":[{\"dishId\":\"DISH-001\",\"quantity\":1}]}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("businessIdempotencyKey is required");
    }

    @Test
    void delegates_mock_payment_with_actor_from_execution_context() {
        when(orders.pay(
                        eq(context.scope()),
                        eq(context.actorUserId()),
                        eq("MEAL-001"),
                        eq("PAY-KEY-001")))
                .thenReturn(paidOrder());

        var result = executor.execute(
                "meal_order.pay",
                context,
                "{\"orderId\":\"MEAL-001\",\"businessIdempotencyKey\":\"PAY-KEY-001\"}");

        assertThat(result.resultJson()).contains("MEAL-001").contains("PAID");
        verify(orders).pay(
                context.scope(), context.actorUserId(), "MEAL-001", "PAY-KEY-001");
    }

    private static MealOrder order() {
        Instant now = Instant.parse("2026-08-21T03:00:00Z");
        return new MealOrder(
                "MEAL-001",
                "MO-001",
                "USER-DINER-001",
                "M001",
                LocalDate.of(2026, 8, 21),
                "LUNCH",
                "CREATED",
                "UNPAID",
                BigDecimal.ZERO,
                List.of(new MealOrderItem(
                        "DISH-001", "番茄鸡蛋", 1, BigDecimal.ZERO, BigDecimal.ZERO)),
                0,
                now,
                now);
    }

    private static MealOrder paidOrder() {
        Instant now = Instant.parse("2026-08-21T03:00:00Z");
        return new MealOrder(
                "MEAL-001",
                "MO-001",
                "USER-DINER-001",
                "M001",
                LocalDate.of(2026, 8, 21),
                "LUNCH",
                "CREATED",
                "PAID",
                BigDecimal.ZERO,
                List.of(new MealOrderItem(
                        "DISH-001", "番茄鸡蛋", 1, BigDecimal.ZERO, BigDecimal.ZERO)),
                1,
                now,
                now);
    }
}
