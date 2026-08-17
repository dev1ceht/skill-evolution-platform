package com.example.smartcanteen.domain;

import java.math.BigDecimal;

public record PurchaseOrderItem(
        String ingredientId,
        BigDecimal quantity,
        String unit,
        BigDecimal unitPrice,
        BigDecimal amount) {

    public PurchaseOrderItem {
        if (ingredientId == null || ingredientId.isBlank()) {
            throw new IllegalArgumentException("ingredientId is required");
        }
        ingredientId = ingredientId.trim();
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("Purchase quantity must be positive");
        }
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("unit is required");
        }
        unit = unit.trim().toLowerCase();
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must be non-negative");
        }
        amount = amount == null ? quantity.multiply(unitPrice) : amount;
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
    }
}
