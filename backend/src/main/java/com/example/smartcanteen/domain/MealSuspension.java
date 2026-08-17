package com.example.smartcanteen.domain;

import java.time.Instant;
import java.time.LocalDate;

public record MealSuspension(
        String id,
        LocalDate mealDate,
        MealPeriod mealPeriod,
        String reason,
        MealSuspensionStatus status,
        String reviewRemark,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant reviewedAt,
        String reviewedBy) {

    public MealSuspension {
        id = required("id", id, 64);
        mealDate = java.util.Objects.requireNonNull(mealDate, "mealDate is required");
        mealPeriod = java.util.Objects.requireNonNull(mealPeriod, "mealPeriod is required");
        reason = required("reason", reason, 1000);
        status = java.util.Objects.requireNonNull(status, "status is required");
        reviewRemark = optional("reviewRemark", reviewRemark, 1000);
        reviewedBy = optional("reviewedBy", reviewedBy, 64);
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
    }

    private static String required(String name, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return normalized;
    }

    private static String optional(String name, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return required(name, value, maxLength);
    }
}
