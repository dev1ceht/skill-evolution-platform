package com.example.smartcanteen.domain;

import java.time.Instant;
import java.util.List;

public record CanteenShowcase(
        String id,
        String title,
        String content,
        List<String> photos,
        CanteenShowcaseStatus status,
        String previousVersionId,
        long version,
        Instant createdAt,
        Instant updatedAt,
        String reviewRemark,
        Instant reviewedAt,
        String reviewedBy,
        Instant publishedAt) {

    public CanteenShowcase {
        id = required("id", id, 64);
        title = required("title", title, 200);
        content = required("content", content, 5000);
        photos = normalizePhotos(photos);
        status = java.util.Objects.requireNonNull(status, "status is required");
        previousVersionId = optional("previousVersionId", previousVersionId, 64);
        reviewRemark = optional("reviewRemark", reviewRemark, 1000);
        reviewedBy = optional("reviewedBy", reviewedBy, 64);
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
    }

    private static List<String> normalizePhotos(List<String> values) {
        if (values == null) {
            return List.of();
        }
        if (values.size() > 9) {
            throw new IllegalArgumentException("At most 9 showcase photos are allowed");
        }
        return values.stream().map(value -> required("photo", value, 500)).toList();
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
