package com.example.smartcanteen.domain;

import java.util.List;

public record Dish(
        String id,
        String name,
        String category,
        String description,
        String imageUrl,
        boolean active,
        long version,
        List<DishIngredient> ingredients) {

    public Dish {
        id = required(id, "dishId", 64);
        name = required(name, "name", 100);
        category = required(category, "category", 64);
        description = optional(description, "description", 1000);
        imageUrl = optional(imageUrl, "imageUrl", 500);
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
    }

    private static String required(String value, String name, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new IllegalArgumentException(name + " exceeds " + max + " characters");
        }
        return normalized;
    }

    private static String optional(String value, String name, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return required(value, name, max);
    }
}
