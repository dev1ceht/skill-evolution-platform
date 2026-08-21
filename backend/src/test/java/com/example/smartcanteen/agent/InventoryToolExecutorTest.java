package com.example.smartcanteen.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.infrastructure.InventoryToolExecutor;
import com.example.smartcanteen.application.ProcurementOperationsService;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.InventoryLine;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InventoryToolExecutorTest {

    private final ProcurementOperationsService procurement = mock(ProcurementOperationsService.class);
    private final InventoryToolExecutor executor = new InventoryToolExecutor(
            procurement, new ObjectMapper().findAndRegisterModules());
    private final ExecutionContext context = new ExecutionContext(
            "REQ-INVENTORY-001",
            "USER-INVENTORY-001",
            "operator",
            new CanteenScope("SCHOOL-001", "CANTEEN-001"),
            Set.of(Role.CANTEEN_STAFF),
            Set.of("INVENTORY_READ"));

    @Test
    void supports_only_the_read_only_inventory_query_tool() {
        assertThat(executor.supports("inventory.query")).isTrue();
        assertThat(executor.supports("inventory.receive")).isFalse();
        assertThat(executor.supports("inventory.stock-out")).isFalse();
    }

    @Test
    void delegates_keyword_and_warning_filter_with_the_server_scope() {
        PageResult<InventoryLine> expected = new PageResult<>(
                List.of(new InventoryLine(
                        "ING-001",
                        "西兰花",
                        "蔬菜",
                        new BigDecimal("19"),
                        "kg",
                        new BigDecimal("20"),
                        true,
                        Instant.parse("2026-08-21T01:00:00Z"))),
                1,
                100,
                1);
        when(procurement.listInventory(
                        eq(context.scope()), eq("西兰花"), eq(true), eq(1), eq(100)))
                .thenReturn(expected);

        var result = executor.execute(
                "inventory.query",
                context,
                "{\"keyword\":\"西兰花\",\"warningOnly\":true}");

        assertThat(result.resultJson())
                .contains("ING-001")
                .contains("西兰花")
                .contains("\"warning\":true");
        verify(procurement).listInventory(
                context.scope(), "西兰花", true, 1, 100);
    }

    @Test
    void queries_all_inventory_when_no_filter_is_provided() {
        PageResult<InventoryLine> expected = new PageResult<>(List.of(), 1, 100, 0);
        when(procurement.listInventory(
                        eq(context.scope()), eq(null), eq(false), eq(1), eq(100)))
                .thenReturn(expected);

        var result = executor.execute("inventory.query", context, "{}");

        assertThat(result.resultJson()).contains("\"records\":[]");
        verify(procurement).listInventory(context.scope(), null, false, 1, 100);
    }

    @Test
    void materializes_all_inventory_pages_for_the_assistant_result() {
        PageResult<InventoryLine> firstPage = new PageResult<>(
                List.of(inventoryLine("ING-001", "西兰花")), 1, 100, 2);
        PageResult<InventoryLine> secondPage = new PageResult<>(
                List.of(inventoryLine("ING-002", "土豆")), 2, 100, 2);
        when(procurement.listInventory(
                        eq(context.scope()), eq(null), eq(false), eq(1), eq(100)))
                .thenReturn(firstPage);
        when(procurement.listInventory(
                        eq(context.scope()), eq(null), eq(false), eq(2), eq(100)))
                .thenReturn(secondPage);

        var result = executor.execute("inventory.query", context, "{}");

        assertThat(result.resultJson())
                .contains("ING-001")
                .contains("ING-002")
                .contains("\"total\":2");
        verify(procurement).listInventory(context.scope(), null, false, 1, 100);
        verify(procurement).listInventory(context.scope(), null, false, 2, 100);
    }

    @Test
    void rejects_a_non_boolean_warning_filter() {
        assertThatThrownBy(() -> executor.execute(
                        "inventory.query", context, "{\"warningOnly\":\"true\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("warningOnly must be boolean");
    }

    @Test
    void rejects_non_object_and_unknown_inventory_input() {
        assertThatThrownBy(() -> executor.execute("inventory.query", context, "[]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("input must be an object");
        assertThatThrownBy(() -> executor.execute(
                        "inventory.query", context, "{\"page\":1}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported inventory query field: page");
    }

    private static InventoryLine inventoryLine(String ingredientId, String name) {
        return new InventoryLine(
                ingredientId,
                name,
                "蔬菜",
                new BigDecimal("19"),
                "kg",
                new BigDecimal("20"),
                true,
                Instant.parse("2026-08-21T01:00:00Z"));
    }
}
