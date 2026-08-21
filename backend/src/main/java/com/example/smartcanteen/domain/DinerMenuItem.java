package com.example.smartcanteen.domain;

import java.util.Objects;

public record DinerMenuItem(
        String dishId,
        String name,
        String category,
        String description,
        String imageUrl) {

    public DinerMenuItem {
        dishId = required(dishId, "dishId", 64);
        name = required(name, "name", 100);
        category = required(category, "category", 64);
        description = optional(description, "description", 1000);
        imageUrl = optional(imageUrl, "imageUrl", 500);
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

    private static String optional(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return required(value, field, max);
    }
}
