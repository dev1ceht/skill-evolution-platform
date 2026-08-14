package com.example.smartcanteen.domain;

import java.util.Objects;

public record Canteen(
        String id,
        String schoolId,
        String name,
        String address,
        String regionCode,
        boolean active) {

    public Canteen {
        id = required("id", id, 64);
        schoolId = required("schoolId", schoolId, 64);
        name = required("name", name, 200);
        address = optional(address, 255);
        regionCode = required("regionCode", regionCode, 64);
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
            throw new IllegalArgumentException("address must be at most " + maxLength + " characters");
        }
        return normalized;
    }
}
