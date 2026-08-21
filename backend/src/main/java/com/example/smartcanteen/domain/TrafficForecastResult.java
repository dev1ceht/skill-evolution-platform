package com.example.smartcanteen.domain;

import java.time.Instant;
import java.time.LocalDate;

/** Assistant-facing result that makes missing forecast facts explicit. */
public record TrafficForecastResult(
        LocalDate forecastDate,
        String mealTime,
        boolean available,
        Long expectedDinerCount,
        Long lowerBound,
        Long upperBound,
        String modelVersion,
        String source,
        Instant generatedAt,
        String reason) {

    public TrafficForecastResult {
        if (forecastDate == null) {
            throw new IllegalArgumentException("forecastDate is required");
        }
        mealTime = TrafficForecast.normalizeMealTime(mealTime);
        if (available) {
            if (expectedDinerCount == null || lowerBound == null || upperBound == null
                    || modelVersion == null || source == null || generatedAt == null) {
                throw new IllegalArgumentException("Available forecast must contain all facts");
            }
            if (reason != null && !reason.isBlank()) {
                throw new IllegalArgumentException("Available forecast cannot contain a reason");
            }
        } else if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Unavailable forecast must contain a reason");
        }
    }

    public static TrafficForecastResult fromFact(TrafficForecast fact) {
        if (fact == null) {
            throw new IllegalArgumentException("forecast fact is required");
        }
        return new TrafficForecastResult(
                fact.forecastDate(),
                fact.mealTime(),
                true,
                fact.expectedDinerCount(),
                fact.lowerBound(),
                fact.upperBound(),
                fact.modelVersion(),
                fact.source(),
                fact.generatedAt(),
                null);
    }

    public static TrafficForecastResult unavailable(
            LocalDate forecastDate, String mealTime, String reason) {
        return new TrafficForecastResult(
                forecastDate, mealTime, false, null, null, null, null, null, null, reason);
    }
}
