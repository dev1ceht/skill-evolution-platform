package com.example.smartcanteen.domain;

import java.time.LocalDate;
import java.util.List;

/** Read-only recommendation that allocates a forecast fact across a published menu. */
public record MealPrepRecommendation(
        LocalDate menuDate,
        String mealTime,
        boolean available,
        String sourceMenuId,
        TrafficForecastResult forecast,
        String allocationMethod,
        List<MealPrepItem> items,
        long totalRecommendedQuantity,
        String reason) {

    public MealPrepRecommendation {
        if (menuDate == null) {
            throw new IllegalArgumentException("menuDate is required");
        }
        mealTime = TrafficForecast.normalizeMealTime(mealTime);
        items = items == null ? List.of() : List.copyOf(items);
        if (totalRecommendedQuantity < 0) {
            throw new IllegalArgumentException("totalRecommendedQuantity cannot be negative");
        }
        if (available) {
            if (sourceMenuId == null || sourceMenuId.isBlank()) {
                throw new IllegalArgumentException("Available recommendation requires sourceMenuId");
            }
            if (forecast == null || !forecast.available()) {
                throw new IllegalArgumentException("Available recommendation requires a forecast");
            }
            if (allocationMethod == null || allocationMethod.isBlank()) {
                throw new IllegalArgumentException("allocationMethod is required");
            }
            if (items.isEmpty() || totalRecommendedQuantity <= 0) {
                throw new IllegalArgumentException("Available recommendation requires items");
            }
            long calculatedTotal = items.stream()
                    .mapToLong(MealPrepItem::recommendedQuantity)
                    .sum();
            if (calculatedTotal != totalRecommendedQuantity) {
                throw new IllegalArgumentException(
                        "totalRecommendedQuantity does not match recommendation items");
            }
            if (reason != null && !reason.isBlank()) {
                throw new IllegalArgumentException("Available recommendation cannot contain a reason");
            }
        } else {
            if (!items.isEmpty() || totalRecommendedQuantity != 0) {
                throw new IllegalArgumentException(
                        "Unavailable recommendation cannot contain quantities");
            }
            if (reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Unavailable recommendation requires a reason");
            }
        }
    }

    public static MealPrepRecommendation unavailable(
            LocalDate menuDate,
            String mealTime,
            TrafficForecastResult forecast,
            String reason) {
        return new MealPrepRecommendation(
                menuDate, mealTime, false, null, forecast, null, List.of(), 0, reason);
    }
}
