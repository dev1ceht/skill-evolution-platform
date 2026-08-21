package com.example.smartcanteen.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Versioned forecast fact supplied by a forecasting service or study dataset. */
public record TrafficForecast(
        LocalDate forecastDate,
        String mealTime,
        long expectedDinerCount,
        long lowerBound,
        long upperBound,
        String modelVersion,
        String source,
        Instant generatedAt) {

    public TrafficForecast {
        if (forecastDate == null) {
            throw new IllegalArgumentException("forecastDate is required");
        }
        mealTime = normalizeMealTime(mealTime);
        if (expectedDinerCount <= 0) {
            throw new IllegalArgumentException("expectedDinerCount must be positive");
        }
        if (lowerBound < 0 || lowerBound > expectedDinerCount) {
            throw new IllegalArgumentException("lowerBound must be between zero and expected count");
        }
        if (upperBound < expectedDinerCount) {
            throw new IllegalArgumentException("upperBound cannot be below expected count");
        }
        modelVersion = required(modelVersion, "modelVersion", 64);
        source = required(source, "source", 64);
        if (generatedAt == null) {
            throw new IllegalArgumentException("generatedAt is required");
        }
    }

    public static String normalizeMealTime(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("mealTime is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported mealTime: " + value);
        }
        return normalized;
    }

    private static String required(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }
}
