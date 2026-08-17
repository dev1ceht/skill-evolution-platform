package com.example.smartcanteen.domain;

import java.time.LocalDate;
import java.util.List;

public record DailyMenu(
        String id,
        LocalDate menuDate,
        String mealTime,
        String status,
        long version,
        List<DailyMenuItem> items,
        String submittedBy,
        String decisionBy,
        String decisionComment,
        String publishedBy) {

    public DailyMenu(
            String id,
            LocalDate menuDate,
            String mealTime,
            String status,
            long version,
            List<DailyMenuItem> items) {
        this(id, menuDate, mealTime, status, version, items, null, null, null, null);
    }

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
        if (!List.of("DRAFT", "PENDING_APPROVAL", "APPROVED", "REJECTED", "PUBLISHED")
                .contains(status)) {
            throw new IllegalArgumentException("Unsupported daily menu status: " + status);
        }
        items = items == null ? List.of() : List.copyOf(items);
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
    }

    public DailyMenu withLifecycle(
            String nextStatus,
            String nextSubmittedBy,
            String nextDecisionBy,
            String nextDecisionComment,
            String nextPublishedBy,
            long nextVersion) {
        return new DailyMenu(
                id,
                menuDate,
                mealTime,
                nextStatus,
                nextVersion,
                items,
                nextSubmittedBy,
                nextDecisionBy,
                nextDecisionComment,
                nextPublishedBy);
    }
}
