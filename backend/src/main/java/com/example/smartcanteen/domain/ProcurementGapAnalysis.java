package com.example.smartcanteen.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Read-only result of comparing published-menu recipe demand with supply snapshots. */
public record ProcurementGapAnalysis(
        LocalDate menuDate,
        String mealTime,
        List<String> sourceMenuIds,
        List<ProcurementGapItem> items,
        long shortageCount) {

    public ProcurementGapAnalysis {
        if (menuDate == null) {
            throw new IllegalArgumentException("menuDate is required");
        }
        if (mealTime != null) {
            String normalizedMealTime = mealTime.trim().toUpperCase(Locale.ROOT);
            if (!List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK")
                    .contains(normalizedMealTime)) {
                throw new IllegalArgumentException("Unsupported mealTime: " + mealTime);
            }
            mealTime = normalizedMealTime;
        }
        sourceMenuIds = sourceMenuIds == null ? List.of() : List.copyOf(sourceMenuIds);
        items = items == null ? List.of() : List.copyOf(items);
        long calculatedShortageCount = items.stream()
                .filter(item -> item.shortageBaseQuantity().signum() > 0)
                .count();
        if (shortageCount != calculatedShortageCount) {
            throw new IllegalArgumentException("shortageCount does not match gap items");
        }
    }

    public static ProcurementGapAnalysis of(
            LocalDate menuDate,
            String mealTime,
            List<String> sourceMenuIds,
            List<ProcurementGapItem> items) {
        List<ProcurementGapItem> safeItems = items == null ? List.of() : List.copyOf(items);
        long shortageCount = safeItems.stream()
                .filter(item -> item.shortageBaseQuantity().signum() > 0)
                .count();
        return new ProcurementGapAnalysis(
                menuDate, mealTime, sourceMenuIds, safeItems, shortageCount);
    }
}
