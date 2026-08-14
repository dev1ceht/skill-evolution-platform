package com.example.smartcanteen.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record Ingredient(
        String id,
        String name,
        String category,
        String baseUnit,
        String specification,
        Nutrition nutrition,
        BigDecimal warningThreshold,
        boolean active) {

    public Ingredient {
        id = required(id, "ingredientId", 64);
        name = required(name, "name", 100);
        category = required(category, "category", 64);
        baseUnit = required(baseUnit, "baseUnit", 16).toLowerCase();
        specification = optional(specification, "specification", 100);
        nutrition = Objects.requireNonNullElse(nutrition, Nutrition.zero());
        warningThreshold = nonNegative(warningThreshold, "warningThreshold");
    }

    private static String required(String value, String name, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new IllegalArgumentException(name + " exceeds " + max + " characters");
        }
        return normalized;
    }

    private static String optional(String value, String name, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return required(value, name, max);
    }

    private static BigDecimal nonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }
}
