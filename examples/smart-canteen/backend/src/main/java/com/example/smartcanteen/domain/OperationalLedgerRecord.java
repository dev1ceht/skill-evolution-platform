package com.example.smartcanteen.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record OperationalLedgerRecord(
        String id,
        String cycleId,
        String ledgerCode,
        Instant recordTime,
        String recorderId,
        Map<String, Object> content,
        List<String> photos,
        String status,
        String remark,
        Instant createdAt) {

    public OperationalLedgerRecord {
        if (id == null || id.isBlank() || cycleId == null || cycleId.isBlank()
                || ledgerCode == null || ledgerCode.isBlank()) {
            throw new IllegalArgumentException("recordId, cycleId and ledgerCode are required");
        }
        recordTime = recordTime == null ? Instant.now() : recordTime;
        content = content == null ? Map.of() : Map.copyOf(content);
        photos = photos == null ? List.of() : List.copyOf(photos);
        status = status == null || status.isBlank() ? "COMPLETED" : status;
    }
}
