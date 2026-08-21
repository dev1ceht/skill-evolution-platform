package com.example.smartcanteen.domain;

import java.math.BigDecimal;
import java.util.Objects;

public record MealOrderItem(
        String dishId,
        String dishName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal amount) {

    public MealOrderItem {
        dishId = required(dishId, "dishId", 64);
        dishName = required(dishName, "dishName", 100);
        if (quantity < 1 || quantity > 20) {
            throw new IllegalArgumentException("quantity must be between 1 and 20");
        }
        unitPrice = nonNegative(unitPrice, "unitPrice");
        amount = nonNegative(amount, "amount");
    }

    private static String required(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        String normalized = value.trim();
        if (normalized.isBlank() || normalized.length() > max) {
            throw new IllegalArgumentException(field + " must be non-blank and at most " + max
                    + " characters");
        }
        return normalized;
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return value;
    }
}
