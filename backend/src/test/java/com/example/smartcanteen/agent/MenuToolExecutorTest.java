package com.example.smartcanteen.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.infrastructure.MenuToolExecutor;
import com.example.smartcanteen.application.DailyMenuService;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.DailyMenu;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MenuToolExecutorTest {

    private final DailyMenuService menus = mock(DailyMenuService.class);
    private final MenuToolExecutor executor = new MenuToolExecutor(
            menus, new ObjectMapper().registerModule(new JavaTimeModule()));
    private final ExecutionContext context = new ExecutionContext(
            "REQ-MENU-001",
            "USER-MENU-001",
            "menu-user",
            new CanteenScope("SCHOOL-001", "CANTEEN-001"),
            Set.of(Role.DINER),
            Set.of("MENU_READ"));

    @Test
    void supports_menu_query_without_turning_menu_writes_into_read_tools() {
        assertThat(executor.supports("menu.query")).isTrue();
        assertThat(executor.supports("menu.publish")).isTrue();
        assertThat(executor.supports("inventory.query")).isFalse();
    }

    @Test
    void delegates_date_query_to_published_menu_service() {
        LocalDate date = LocalDate.of(2026, 8, 17);
        PageResult<DailyMenu> expected = new PageResult<>(
                List.of(new DailyMenu("M001", date, "LUNCH", "PUBLISHED", 2, List.of())),
                1,
                100,
                1);
        when(menus.listPublished(
                        eq(context.scope()), eq(date), eq(date), eq("LUNCH"), eq(1), eq(100)))
                .thenReturn(expected);

        var result = executor.execute(
                "menu.query",
                context,
                "{\"menuDate\":\"2026-08-17\",\"mealTime\":\"LUNCH\"}");

        assertThat(result.resultJson()).contains("M001").contains("PUBLISHED");
        verify(menus).listPublished(
                context.scope(), date, date, "LUNCH", 1, 100);
    }

    @Test
    void delegates_menu_id_query_to_published_menu_service() {
        DailyMenu expected = new DailyMenu(
                "M001", LocalDate.of(2026, 8, 17), "LUNCH", "PUBLISHED", 2, List.of());
        when(menus.getPublished(context.scope(), "M001")).thenReturn(expected);

        var result = executor.execute("menu.query", context, "{\"menuId\":\"M001\"}");

        assertThat(result.resultJson()).contains("M001").contains("PUBLISHED");
        verify(menus).getPublished(context.scope(), "M001");
    }

    @Test
    void rejects_a_menu_query_without_an_id_or_date() {
        assertThatThrownBy(() -> executor.execute("menu.query", context, "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("menuId or menuDate");
    }

    @Test
    void rejects_a_menu_query_with_an_invalid_date() {
        assertThatThrownBy(() -> executor.execute(
                        "menu.query", context, "{\"menuDate\":\"2026-99-17\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("menuDate must be YYYY-MM-DD");
    }

    @Test
    void rejects_a_menu_query_with_both_id_and_date() {
        assertThatThrownBy(() -> executor.execute(
                        "menu.query", context,
                        "{\"menuId\":\"M001\",\"menuDate\":\"2026-08-17\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot contain both menuId and menuDate");
    }

    @Test
    void rejects_a_menu_id_query_with_a_meal_time_filter() {
        assertThatThrownBy(() -> executor.execute(
                        "menu.query", context,
                        "{\"menuId\":\"M001\",\"mealTime\":\"LUNCH\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot contain menuId and mealTime");
    }
}
