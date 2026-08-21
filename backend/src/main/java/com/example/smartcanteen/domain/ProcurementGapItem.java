package com.example.smartcanteen.domain;

import java.math.BigDecimal;
import java.util.Locale;

/** Deterministic, read-only ingredient shortage fact for a published menu. */
public record ProcurementGapItem(
        String ingredientId,
        String ingredientName,
        String category,
        BigDecimal requiredBaseQuantity,
        BigDecimal inventoryBaseQuantity,
        BigDecimal openOrderBaseQuantity,
        BigDecimal shortageBaseQuantity,
        String baseUnit) {

    public ProcurementGapItem {
        ingredientId = requiredText(ingredientId, "ingredientId", 64);
        ingredientName = requiredText(ingredientName, "ingredientName", 100);
        category = requiredText(category, "category", 64);
        requiredBaseQuantity = positive(requiredBaseQuantity, "requiredBaseQuantity");
        inventoryBaseQuantity = nonNegative(inventoryBaseQuantity, "inventoryBaseQuantity");
        openOrderBaseQuantity = nonNegative(openOrderBaseQuantity, "openOrderBaseQuantity");
        shortageBaseQuantity = nonNegative(shortageBaseQuantity, "shortageBaseQuantity");
        baseUnit = requiredText(baseUnit, "baseUnit", 16).toLowerCase(Locale.ROOT);

        BigDecimal calculatedShortage = requiredBaseQuantity
                .subtract(inventoryBaseQuantity.add(openOrderBaseQuantity))
                .max(BigDecimal.ZERO);
        if (calculatedShortage.compareTo(shortageBaseQuantity) != 0) {
            throw new IllegalArgumentException(
                    "shortageBaseQuantity must equal required quantity minus available quantity");
        }
    }

    private static String requiredText(String value, String name, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private static BigDecimal positive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static BigDecimal nonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }
}
