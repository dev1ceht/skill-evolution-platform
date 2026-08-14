package com.example.smartcanteen.domain;

import java.math.BigDecimal;

public record ProcurementPlanItem(
        String ingredientId,
        BigDecimal requiredBaseQuantity,
        BigDecimal inventoryBaseQuantity,
        BigDecimal openOrderBaseQuantity,
        BigDecimal shortageBaseQuantity,
        BigDecimal plannedBaseQuantity,
        String baseUnit) {

    public ProcurementPlanItem {
        if (ingredientId == null || ingredientId.isBlank()) {
            throw new IllegalArgumentException("ingredientId is required");
        }
        ingredientId = ingredientId.trim();
        requiredBaseQuantity = positive(requiredBaseQuantity, "requiredBaseQuantity");
        inventoryBaseQuantity = nonNegative(inventoryBaseQuantity, "inventoryBaseQuantity");
        openOrderBaseQuantity = nonNegative(openOrderBaseQuantity, "openOrderBaseQuantity");
        shortageBaseQuantity = nonNegative(shortageBaseQuantity, "shortageBaseQuantity");
        plannedBaseQuantity = nonNegative(plannedBaseQuantity, "plannedBaseQuantity");
        if (baseUnit == null || baseUnit.isBlank()) {
            throw new IllegalArgumentException("baseUnit is required");
        }
        baseUnit = baseUnit.trim().toLowerCase();
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
