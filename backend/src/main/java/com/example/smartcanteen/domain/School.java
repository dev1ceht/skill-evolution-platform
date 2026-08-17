package com.example.smartcanteen.domain;

import java.util.Objects;

public record School(String id, String name, String regionCode, boolean active) {

    public School {
        id = required("id", id, 64);
        name = required("name", name, 200);
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
}
