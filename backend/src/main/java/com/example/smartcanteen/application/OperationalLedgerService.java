package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.OperationalStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.OperationalLedgerRecord;
import com.example.smartcanteen.domain.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationalLedgerService {

    private final OperationalStore store;
    private final ObjectMapper objectMapper;

    public OperationalLedgerService(OperationalStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OperationalLedgerRecord save(
            CanteenScope scope,
            String recordId,
            String cycleId,
            String ledgerCode,
            Instant recordTime,
            String recorderId,
            Map<String, Object> content,
            List<String> photos,
            String remark) {
        require(cycleId, "cycleId");
        require(ledgerCode, "ledgerCode");
        Map<String, Object> normalizedContent = content == null ? Map.of() : content;
        if (normalizedContent.size() > 100) {
            throw new IllegalArgumentException("content contains too many fields");
        }
        serialize(normalizedContent);
        List<String> normalizedPhotos = photos == null ? List.of() : List.copyOf(photos);
        if (normalizedPhotos.size() > 9) {
            throw new IllegalArgumentException("At most 9 ledger photos are allowed");
        }
        for (String photo : normalizedPhotos) {
            if (photo == null || photo.isBlank() || photo.length() > 500) {
                throw new IllegalArgumentException("Each ledger photo must be a valid URL reference");
            }
        }
        OperationalLedgerRecord record = new OperationalLedgerRecord(
                recordId == null || recordId.isBlank() ? "LEDGER-" + UUID.randomUUID() : recordId,
                cycleId,
                ledgerCode,
                recordTime == null ? Instant.now() : recordTime,
                recorderId,
                normalizedContent,
                normalizedPhotos,
                "COMPLETED",
                remark,
                Instant.now());
        return store.saveLedgerRecord(scope, record);
    }

    @Transactional(readOnly = true)
    public PageResult<OperationalLedgerRecord> list(
            CanteenScope scope,
            String cycleId,
            String ledgerCode,
            String status,
            Instant from,
            Instant to,
            int page,
            int size) {
        return store.listLedgerRecords(scope, cycleId, ledgerCode, status, from, to, page, size);
    }

    @Transactional(readOnly = true)
    public OperationalStore.LedgerStats stats(CanteenScope scope, LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now() : from;
        LocalDate end = to == null ? start : to;
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }
        return store.ledgerStats(scope, start, end);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("content must be valid JSON", exception);
        }
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
