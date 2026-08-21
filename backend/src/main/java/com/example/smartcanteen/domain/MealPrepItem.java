package com.example.smartcanteen.domain;

import java.math.BigDecimal;

/** One dish allocation in a deterministic meal-prep recommendation. */
public record MealPrepItem(
        String dishId,
        String dishName,
        BigDecimal currentPlannedQuantity,
        long recommendedQuantity,
        int sortOrder) {

    public MealPrepItem {
        dishId = required(dishId, "dishId", 64);
        dishName = required(dishName, "dishName", 100);
        if (currentPlannedQuantity == null || currentPlannedQuantity.signum() <= 0) {
            throw new IllegalArgumentException("currentPlannedQuantity must be positive");
        }
        if (recommendedQuantity < 0) {
            throw new IllegalArgumentException("recommendedQuantity cannot be negative");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder cannot be negative");
        }
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
