package com.example.smartcanteen.domain;

import java.util.Objects;

public record ScopeGrant(
        String assignmentId,
        String userId,
        ScopeGrantType type,
        String regionCode,
        String schoolId,
        String canteenId) {

    public ScopeGrant {
        assignmentId = required("assignmentId", assignmentId, 64);
        userId = required("userId", userId, 64);
        Objects.requireNonNull(type, "type");
        regionCode = optional(regionCode, 64);
        schoolId = optional(schoolId, 64);
        canteenId = optional(canteenId, 64);
        switch (type) {
            case REGION -> {
                if (regionCode == null || schoolId != null || canteenId != null) {
                    throw new IllegalArgumentException("region grant must contain only regionCode");
                }
            }
            case SCHOOL -> {
                if (regionCode != null || schoolId == null || canteenId != null) {
                    throw new IllegalArgumentException("school grant must contain only schoolId");
                }
            }
            case CANTEEN -> {
                if (regionCode != null || schoolId == null || canteenId == null) {
                    throw new IllegalArgumentException("canteen grant must contain schoolId and canteenId");
                }
            }
        }
    }

    private static String required(String name, String value, int maxLength) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(
                    name + " must be non-blank and at most " + maxLength + " characters");
        }
        return normalized;
    }

    private static String optional(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("scope value is too long");
        }
        return normalized;
    }
}
