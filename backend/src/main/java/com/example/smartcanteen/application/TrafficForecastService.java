package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.TrafficForecastStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.TrafficForecast;
import com.example.smartcanteen.domain.TrafficForecastResult;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads forecast facts; it deliberately does not implement a forecasting algorithm. */
@Service
public class TrafficForecastService {

    private final TrafficForecastStore forecasts;

    public TrafficForecastService(TrafficForecastStore forecasts) {
        this.forecasts = Objects.requireNonNull(forecasts, "forecasts");
    }

    @Transactional(readOnly = true)
    public TrafficForecastResult forecast(
            CanteenScope scope, LocalDate forecastDate, String mealTime) {
        if (scope == null || forecastDate == null) {
            throw new IllegalArgumentException("scope and forecastDate are required");
        }
        String normalizedMealTime = TrafficForecast.normalizeMealTime(mealTime);
        return forecasts.find(scope, forecastDate, normalizedMealTime)
                .map(TrafficForecastResult::fromFact)
                .orElseGet(() -> TrafficForecastResult.unavailable(
                        forecastDate, normalizedMealTime, "NO_FORECAST_FACT"));
    }
}
