package com.example.smartcanteen.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record DinerMenu(
        String id,
        LocalDate menuDate,
        String mealTime,
        List<DinerMenuItem> items) {

    public DinerMenu {
        id = required(id, "menuId", 64);
        if (menuDate == null) {
            throw new IllegalArgumentException("menuDate is required");
        }
        mealTime = required(mealTime, "mealTime", 16).toUpperCase();
        if (!List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").contains(mealTime)) {
            throw new IllegalArgumentException("Unsupported mealTime: " + mealTime);
        }
        items = items == null ? List.of() : List.copyOf(items);
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
}
