package com.example.smartcanteen.domain;

import java.math.BigDecimal;

/** A food-specific business unit and its multiplier to the ingredient base unit. */
public record IngredientUnit(
        String unitCode,
        String baseUnit,
        BigDecimal toBaseFactor,
        boolean active) {

    public IngredientUnit {
        unitCode = required(unitCode, "unitCode", 16).toLowerCase();
        baseUnit = required(baseUnit, "baseUnit", 16).toLowerCase();
        if (toBaseFactor == null || toBaseFactor.signum() <= 0) {
            throw new IllegalArgumentException("toBaseFactor must be positive");
        }
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
}
