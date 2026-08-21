package com.example.smartcanteen.domain;

import java.time.Instant;
import java.util.Set;

/** A personal complaint submitted by an employee/student to the current canteen scope. */
public record DinerComplaint(
        String id,
        String actorUserId,
        String category,
        String subject,
        String description,
        String relatedOrderId,
        String status,
        String reply,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    private static final Set<String> CATEGORIES = Set.of(
            "FOOD_QUALITY", "SERVICE", "HYGIENE", "QUEUE", "PAYMENT", "OTHER");

    public DinerComplaint {
        id = required("id", id, 64);
        actorUserId = required("actorUserId", actorUserId, 64);
        category = required("category", category, 32).toUpperCase(java.util.Locale.ROOT);
        if (!CATEGORIES.contains(category)) {
            throw new IllegalArgumentException("Unsupported complaint category: " + category);
        }
        subject = required("subject", subject, 120);
        description = required("description", description, 2000);
        relatedOrderId = optional("relatedOrderId", relatedOrderId, 64);
        if (!"SUBMITTED".equals(status)) {
            throw new IllegalArgumentException("Unsupported diner complaint status: " + status);
        }
        reply = optional("reply", reply, 3000);
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
        if (createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("Complaint timestamps are required");
        }
        if (createdAt.isAfter(updatedAt)) {
            throw new IllegalArgumentException("createdAt cannot be after updatedAt");
        }
    }

    public static boolean isSupportedCategory(String category) {
        return category != null && CATEGORIES.contains(category.trim().toUpperCase(java.util.Locale.ROOT));
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
