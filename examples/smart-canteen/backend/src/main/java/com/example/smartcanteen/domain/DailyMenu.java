package com.example.smartcanteen.domain;

import java.time.LocalDate;
import java.util.List;

public record DailyMenu(
        String id,
        LocalDate menuDate,
        String mealTime,
        String status,
        long version,
        List<DailyMenuItem> items) {

    public DailyMenu {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("menuId is required");
        }
        id = id.trim();
        if (menuDate == null) {
            throw new IllegalArgumentException("menuDate is required");
        }
        if (mealTime == null || mealTime.isBlank()) {
            throw new IllegalArgumentException("mealTime is required");
        }
        mealTime = mealTime.trim().toUpperCase();
        if (!List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").contains(mealTime)) {
            throw new IllegalArgumentException("Unsupported mealTime: " + mealTime);
        }
        if (!List.of("DRAFT", "PUBLISHED").contains(status)) {
            throw new IllegalArgumentException("Unsupported daily menu status: " + status);
        }
        items = items == null ? List.of() : List.copyOf(items);
    }
}
