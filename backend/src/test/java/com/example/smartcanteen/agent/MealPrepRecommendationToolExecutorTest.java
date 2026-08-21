package com.example.smartcanteen.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.infrastructure.MealPrepRecommendationToolExecutor;
import com.example.smartcanteen.application.MealPrepRecommendationService;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.MealPrepRecommendation;
import com.example.smartcanteen.domain.TrafficForecastResult;
import com.example.smartcanteen.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MealPrepRecommendationToolExecutorTest {

    private final MealPrepRecommendationService mealPlans = mock(MealPrepRecommendationService.class);
    private final MealPrepRecommendationToolExecutor executor =
            new MealPrepRecommendationToolExecutor(
                    mealPlans, new ObjectMapper().findAndRegisterModules());
    private final ExecutionContext context = new ExecutionContext(
            "REQ-MEAL-001",
            "USER-MEAL-001",
            "operator",
            new CanteenScope("SCHOOL-001", "CANTEEN-001"),
            Set.of(Role.CANTEEN_STAFF),
            Set.of("MEAL_PLAN_ANALYSIS_READ"));

    @Test
    void delegates_a_meal_prep_query_with_the_server_scope() {
        LocalDate date = LocalDate.of(2026, 8, 22);
        MealPrepRecommendation expected = MealPrepRecommendation.unavailable(
                date,
                "LUNCH",
                TrafficForecastResult.unavailable(date, "LUNCH", "NO_PUBLISHED_MENU"),
                "NO_PUBLISHED_MENU");
        when(mealPlans.recommend(eq(context.scope()), eq(date), eq("LUNCH")))
                .thenReturn(expected);

        var result = executor.execute(
                "meal_plan.query",
                context,
                "{\"menuDate\":\"2026-08-22\",\"mealTime\":\"LUNCH\"}");

        assertThat(result.resultJson())
                .contains("\"available\":false")
                .contains("NO_PUBLISHED_MENU");
        verify(mealPlans).recommend(context.scope(), date, "LUNCH");
    }

    @Test
    void rejects_unknown_and_invalid_meal_prep_input() {
        assertThatThrownBy(() -> executor.execute(
                        "meal_plan.query", context, "{\"menuDate\":\"2026-08-22\",\"page\":1}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported meal prep query field: page");
        assertThatThrownBy(() -> executor.execute(
                        "meal_plan.query",
                        context,
                        "{\"menuDate\":\"2026-08-22\",\"mealTime\":\"MIDNIGHT\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mealTime must be BREAKFAST, LUNCH, DINNER or SNACK");
    }
}
