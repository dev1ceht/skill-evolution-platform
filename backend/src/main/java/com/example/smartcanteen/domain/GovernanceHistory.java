package com.example.smartcanteen.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record GovernanceHistory(
        String historyId,
        String entityType,
        String entityId,
        String action,
        String status,
        Map<String, Object> snapshot,
        String actorId,
        Instant occurredAt) {

    public GovernanceHistory {
        if (historyId == null || historyId.isBlank() || entityType == null || entityType.isBlank()
                || entityId == null || entityId.isBlank() || action == null || action.isBlank()
                || status == null || status.isBlank() || actorId == null || actorId.isBlank()
                || occurredAt == null) {
            throw new IllegalArgumentException("Governance history identity and actor are required");
        }
        snapshot = snapshot == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(snapshot));
    }
}
