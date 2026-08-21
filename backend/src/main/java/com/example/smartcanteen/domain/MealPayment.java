package com.example.smartcanteen.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** A deterministic payment fact for the study environment. */
public record MealPayment(
        String id,
        String actorUserId,
        String orderId,
        BigDecimal amount,
        String method,
        String status,
        String idempotencyKey,
        String requestHash,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public MealPayment {
        id = required("id", id, 64);
        actorUserId = required("actorUserId", actorUserId, 64);
        orderId = required("orderId", orderId, 64);
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
        if (!"STUDY_MOCK".equals(method)) {
            throw new IllegalArgumentException("Only STUDY_MOCK payment is supported");
        }
        if (!"SUCCEEDED".equals(status)) {
            throw new IllegalArgumentException("Unsupported meal payment status: " + status);
        }
        idempotencyKey = required("idempotencyKey", idempotencyKey, 128);
        requestHash = required("requestHash", requestHash, 64);
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
}
