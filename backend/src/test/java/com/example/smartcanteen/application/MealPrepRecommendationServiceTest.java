package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.application.port.OperationalStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.DailyMenu;
import com.example.smartcanteen.domain.DailyMenuItem;
import com.example.smartcanteen.domain.Dish;
import com.example.smartcanteen.domain.MealPrepItem;
import com.example.smartcanteen.domain.MealPrepRecommendation;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.domain.TrafficForecastResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class MealPrepRecommendationServiceTest {

    @Test
    void allocates_the_forecast_count_by_menu_estimate_using_largest_remainder() {
        CanteenScope scope = new CanteenScope("SCHOOL-001", "CANTEEN-001");
        LocalDate menuDate = LocalDate.of(2026, 8, 22);
        DailyMenu menu = new DailyMenu(
                "M822",
                menuDate,
                "LUNCH",
                "PUBLISHED",
                1,
                List.of(
                        new DailyMenuItem("DISH-A", java.math.BigDecimal.valueOf(100), 1),
                        new DailyMenuItem("DISH-B", java.math.BigDecimal.valueOf(200), 2)));
        DailyMenuService menus = mock(DailyMenuService.class);
        OperationalStore operational = mock(OperationalStore.class);
        TrafficForecastService forecasts = mock(TrafficForecastService.class);
        when(menus.listPublished(scope, menuDate, menuDate, "LUNCH", 1, 100))
                .thenReturn(new PageResult<>(List.of(menu), 1, 100, 1));
        when(operational.findDish(eq(scope), eq("DISH-A")))
                .thenReturn(java.util.Optional.of(dish("DISH-A", "番茄炒蛋")));
        when(operational.findDish(eq(scope), eq("DISH-B")))
                .thenReturn(java.util.Optional.of(dish("DISH-B", "宫保鸡丁")));
        when(forecasts.forecast(scope, menuDate, "LUNCH"))
                .thenReturn(new TrafficForecastResult(
                        menuDate,
                        "LUNCH",
                        true,
                        850L,
                        810L,
                        880L,
                        "study-baseline-v1",
                        "GENERATED_STUDY_FACT",
                        Instant.parse("2026-08-21T01:00:00Z"),
                        null));

        MealPrepRecommendation result = new MealPrepRecommendationService(
                menus, operational, forecasts).recommend(scope, menuDate, "LUNCH");

        assertThat(result.available()).isTrue();
        assertThat(result.sourceMenuId()).isEqualTo("M822");
        assertThat(result.totalRecommendedQuantity()).isEqualTo(850);
        assertThat(result.allocationMethod())
                .isEqualTo("PROPORTIONAL_MENU_ESTIMATE_LARGEST_REMAINDER");
        assertThat(result.items()).extracting(MealPrepItem::recommendedQuantity)
                .containsExactly(283L, 567L);
        assertThat(result.items()).extracting(MealPrepItem::dishName)
                .containsExactly("番茄炒蛋", "宫保鸡丁");
    }

    @Test
    void does_not_guess_when_the_menu_has_no_forecast_fact() {
        CanteenScope scope = new CanteenScope("SCHOOL-001", "CANTEEN-001");
        LocalDate menuDate = LocalDate.of(2026, 8, 23);
        DailyMenuService menus = mock(DailyMenuService.class);
        OperationalStore operational = mock(OperationalStore.class);
        TrafficForecastService forecasts = mock(TrafficForecastService.class);
        when(forecasts.forecast(scope, menuDate, "LUNCH"))
                .thenReturn(TrafficForecastResult.unavailable(
                        menuDate, "LUNCH", "NO_FORECAST_FACT"));

        MealPrepRecommendation result = new MealPrepRecommendationService(
                menus, operational, forecasts).recommend(scope, menuDate, "LUNCH");

        assertThat(result.available()).isFalse();
        assertThat(result.items()).isEmpty();
        assertThat(result.totalRecommendedQuantity()).isZero();
        assertThat(result.reason()).isEqualTo("NO_FORECAST_FACT");
    }

    @Test
    void does_not_recommend_when_the_forecast_exists_but_the_published_menu_is_missing() {
        CanteenScope scope = new CanteenScope("SCHOOL-001", "CANTEEN-001");
        LocalDate menuDate = LocalDate.of(2026, 8, 24);
        DailyMenuService menus = mock(DailyMenuService.class);
        OperationalStore operational = mock(OperationalStore.class);
        TrafficForecastService forecasts = mock(TrafficForecastService.class);
        when(forecasts.forecast(scope, menuDate, "LUNCH"))
                .thenReturn(new TrafficForecastResult(
                        menuDate,
                        "LUNCH",
                        true,
                        850L,
                        810L,
                        880L,
                        "study-baseline-v1",
                        "GENERATED_STUDY_FACT",
                        Instant.parse("2026-08-21T01:00:00Z"),
                        null));
        when(menus.listPublished(scope, menuDate, menuDate, "LUNCH", 1, 100))
                .thenReturn(new PageResult<>(List.of(), 1, 100, 0));

        MealPrepRecommendation result = new MealPrepRecommendationService(
                menus, operational, forecasts).recommend(scope, menuDate, "LUNCH");

        assertThat(result.available()).isFalse();
        assertThat(result.items()).isEmpty();
        assertThat(result.totalRecommendedQuantity()).isZero();
        assertThat(result.reason()).isEqualTo("NO_PUBLISHED_MENU");
        verify(operational, never()).findDish(eq(scope), org.mockito.ArgumentMatchers.anyString());
    }

    private static Dish dish(String id, String name) {
        return new Dish(id, name, "主菜", null, null, true, 1, List.of());
    }
}
