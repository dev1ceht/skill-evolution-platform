package com.example.smartcanteen.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable snapshot of one compliance state change. */
public record ComplianceRecordHistory(
        String historyId,
        String recordId,
        String action,
        ComplianceRecordStatus status,
        Map<String, Object> snapshot,
        String actorId,
        Instant occurredAt) {

    public ComplianceRecordHistory {
        if (historyId == null || historyId.isBlank()
                || recordId == null || recordId.isBlank()
                || action == null || action.isBlank()
                || actorId == null || actorId.isBlank()
                || occurredAt == null) {
            throw new IllegalArgumentException("Compliance history identity and actor are required");
        }
        status = java.util.Objects.requireNonNull(status, "status is required");
        snapshot = snapshot == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(snapshot));
        historyId = historyId.trim();
        recordId = recordId.trim();
        action = action.trim();
        actorId = actorId.trim();
    }
}
