package com.example.smartcanteen.domain;

import java.time.Instant;
import java.util.Objects;

/** A personal rating submitted for one employee/student meal order. */
public record MealReview(
        String id,
        String actorUserId,
        String orderId,
        String orderNo,
        int rating,
        String content,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public MealReview {
        id = required("id", id, 64);
        actorUserId = required("actorUserId", actorUserId, 64);
        orderId = required("orderId", orderId, 64);
        orderNo = required("orderNo", orderNo, 64);
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("rating must be between 1 and 5");
        }
        content = optional("content", content, 2000);
        if (!"SUBMITTED".equals(status)) {
            throw new IllegalArgumentException("Unsupported meal review status: " + status);
        }
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (createdAt.isAfter(updatedAt)) {
            throw new IllegalArgumentException("createdAt cannot be after updatedAt");
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
