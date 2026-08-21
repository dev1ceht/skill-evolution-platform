package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.application.port.TrafficForecastStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.TrafficForecast;
import com.example.smartcanteen.domain.TrafficForecastResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TrafficForecastServiceTest {

    @Test
    void returns_the_versioned_forecast_fact_without_recomputing_it() {
        CanteenScope scope = new CanteenScope("SCHOOL-001", "CANTEEN-001");
        LocalDate forecastDate = LocalDate.of(2026, 8, 22);
        TrafficForecastStore store = mock(TrafficForecastStore.class);
        TrafficForecast fact = new TrafficForecast(
                forecastDate,
                "LUNCH",
                850,
                810,
                880,
                "study-baseline-v1",
                "GENERATED_STUDY_FACT",
                Instant.parse("2026-08-21T01:00:00Z"));
        when(store.find(eq(scope), eq(forecastDate), eq("LUNCH")))
                .thenReturn(Optional.of(fact));

        TrafficForecastResult result = new TrafficForecastService(store)
                .forecast(scope, forecastDate, "LUNCH");

        assertThat(result.available()).isTrue();
        assertThat(result.expectedDinerCount()).isEqualTo(850);
        assertThat(result.lowerBound()).isEqualTo(810);
        assertThat(result.upperBound()).isEqualTo(880);
        assertThat(result.modelVersion()).isEqualTo("study-baseline-v1");
        assertThat(result.source()).isEqualTo("GENERATED_STUDY_FACT");
        assertThat(result.reason()).isNull();
    }

    @Test
    void does_not_guess_when_no_forecast_fact_exists() {
        CanteenScope scope = new CanteenScope("SCHOOL-001", "CANTEEN-001");
        LocalDate forecastDate = LocalDate.of(2026, 8, 23);
        TrafficForecastStore store = mock(TrafficForecastStore.class);
        when(store.find(eq(scope), eq(forecastDate), eq("LUNCH")))
                .thenReturn(Optional.empty());

        TrafficForecastResult result = new TrafficForecastService(store)
                .forecast(scope, forecastDate, "LUNCH");

        assertThat(result.available()).isFalse();
        assertThat(result.expectedDinerCount()).isNull();
        assertThat(result.reason()).isEqualTo("NO_FORECAST_FACT");
    }
}
