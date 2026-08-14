package com.example.smartcanteen.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ProcurementPlan(
        String id,
        String planNo,
        LocalDate periodStart,
        LocalDate periodEnd,
        ProcurementPlanStatus status,
        long version,
        Instant createdAt,
        List<String> sourceMenuIds,
        List<ProcurementPlanItem> items,
        List<String> orderIds) {

    public ProcurementPlan {
        id = required(id, "planId", 64);
        planNo = required(planNo, "planNo", 64);
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("Invalid procurement plan period");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version cannot be negative");
        }
        sourceMenuIds = sourceMenuIds == null ? List.of() : List.copyOf(sourceMenuIds);
        items = items == null ? List.of() : List.copyOf(items);
        orderIds = orderIds == null ? List.of() : List.copyOf(orderIds);
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
}
