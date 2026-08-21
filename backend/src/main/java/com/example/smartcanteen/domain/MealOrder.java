package com.example.smartcanteen.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record MealOrder(
        String id,
        String orderNo,
        String actorUserId,
        String menuId,
        LocalDate mealDate,
        String mealTime,
        String status,
        String paymentStatus,
        BigDecimal totalAmount,
        List<MealOrderItem> items,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public MealOrder {
        id = required(id, "orderId", 64);
        orderNo = required(orderNo, "orderNo", 64);
        actorUserId = required(actorUserId, "actorUserId", 64);
        menuId = required(menuId, "menuId", 64);
        if (mealDate == null) {
            throw new IllegalArgumentException("mealDate is required");
        }
        mealTime = required(mealTime, "mealTime", 16).toUpperCase();
        if (!List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").contains(mealTime)) {
            throw new IllegalArgumentException("Unsupported mealTime: " + mealTime);
        }
        if (!List.of("CREATED", "CANCELLED").contains(status)) {
            throw new IllegalArgumentException("Unsupported meal order status: " + status);
        }
        if (!List.of("UNPAID", "PAID").contains(paymentStatus)) {
            throw new IllegalArgumentException("Unsupported meal order payment status: " + paymentStatus);
        }
        totalAmount = nonNegative(totalAmount, "totalAmount");
        items = items == null ? List.of() : List.copyOf(items);
        if (items.isEmpty()) {
            throw new IllegalArgumentException("Meal order must contain at least one item");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
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
