package com.example.smartcanteen.domain;

import java.util.Arrays;
import java.util.List;

/**
 * Canonical categories used to classify ingredients in the single-canteen catalog.
 */
public enum IngredientCategory {
    VEGETABLE("蔬菜"),
    MEAT_AND_POULTRY("肉禽"),
    EGG_AND_DAIRY("蛋奶"),
    AQUATIC("水产"),
    STAPLE("主食"),
    SOY_PRODUCT("豆制品"),
    SEASONING("调味品"),
    DRY_GOODS("干货"),
    FRUIT("水果"),
    SEMI_FINISHED("半成品"),
    OTHER("其他");

    private final String label;

    IngredientCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static List<String> labels() {
        return Arrays.stream(values()).map(IngredientCategory::label).toList();
    }

    public static String require(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("category is required");
        }
        String normalized = value.trim();
        return Arrays.stream(values())
                .filter(category -> category.label.equals(normalized))
                .findFirst()
                .map(IngredientCategory::label)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported ingredient category: " + value));
    }
}
