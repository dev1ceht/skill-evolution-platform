package com.example.smartcanteen.domain;

import java.util.Arrays;
import java.util.List;

/**
 * Canonical categories used to classify dishes in the single-canteen menu catalog.
 */
public enum DishCategory {
    STAPLE("主食"),
    MEAT("荤菜"),
    VEGETARIAN("素菜"),
    MEAT_AND_VEGETABLE("荤素搭配"),
    SOUP("汤羹"),
    STIR_FRY("炒菜"),
    SNACK("小吃"),
    DRINK("饮品"),
    BREAKFAST("早餐"),
    OTHER("其他");

    private final String label;

    DishCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static List<String> labels() {
        return Arrays.stream(values()).map(DishCategory::label).toList();
    }

    public static String require(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("category is required");
        }
        String normalized = value.trim();
        return Arrays.stream(values())
                .filter(category -> category.label.equals(normalized))
                .findFirst()
                .map(DishCategory::label)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported dish category: " + value));
    }
}
