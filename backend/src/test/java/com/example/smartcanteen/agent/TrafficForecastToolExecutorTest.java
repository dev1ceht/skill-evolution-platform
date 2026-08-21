package com.example.smartcanteen.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.infrastructure.TrafficForecastToolExecutor;
import com.example.smartcanteen.application.TrafficForecastService;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.TrafficForecastResult;
import com.example.smartcanteen.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TrafficForecastToolExecutorTest {

    private final TrafficForecastService forecasts = mock(TrafficForecastService.class);
    private final TrafficForecastToolExecutor executor = new TrafficForecastToolExecutor(
            forecasts, new ObjectMapper().findAndRegisterModules());
    private final ExecutionContext context = new ExecutionContext(
            "REQ-TRAFFIC-001",
            "USER-TRAFFIC-001",
            "operator",
            new CanteenScope("SCHOOL-001", "CANTEEN-001"),
            Set.of(Role.CANTEEN_STAFF),
            Set.of("TRAFFIC_FORECAST_READ"));

    @Test
    void delegates_a_strict_forecast_query_with_the_server_scope() {
        TrafficForecastResult expected = new TrafficForecastResult(
                LocalDate.of(2026, 8, 22),
                "LUNCH",
                true,
                850L,
                810L,
                880L,
                "study-baseline-v1",
                "GENERATED_STUDY_FACT",
                Instant.parse("2026-08-21T01:00:00Z"),
                null);
        when(forecasts.forecast(
                        eq(context.scope()), eq(LocalDate.of(2026, 8, 22)), eq("LUNCH")))
                .thenReturn(expected);

        var result = executor.execute(
                "traffic.forecast.query",
                context,
                "{\"forecastDate\":\"2026-08-22\",\"mealTime\":\"lunch\"}");

        assertThat(result.resultJson())
                .contains("\"expectedDinerCount\":850")
                .contains("study-baseline-v1");
        verify(forecasts).forecast(context.scope(), LocalDate.of(2026, 8, 22), "LUNCH");
    }

    @Test
    void rejects_non_object_unknown_and_invalid_forecast_input() {
        assertThatThrownBy(() -> executor.execute(
                        "traffic.forecast.query", context, "[]"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("input must be an object");
        assertThatThrownBy(() -> executor.execute(
                        "traffic.forecast.query", context, "{\"page\":1}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported traffic forecast query field: page");
        assertThatThrownBy(() -> executor.execute(
                        "traffic.forecast.query",
                        context,
                        "{\"forecastDate\":\"tomorrow\",\"mealTime\":\"LUNCH\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("forecastDate must be YYYY-MM-DD");
    }
}
