package com.example.smartcanteen.infrastructure;

import com.example.smartcanteen.application.port.ComplianceStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.ComplianceCategory;
import com.example.smartcanteen.domain.ComplianceRecord;
import com.example.smartcanteen.domain.ComplianceRecordHistory;
import com.example.smartcanteen.domain.ComplianceRecordStatus;
import com.example.smartcanteen.domain.PageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcComplianceStore implements ComplianceStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcComplianceStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResult<ComplianceRecord> list(
            CanteenScope scope,
            String category,
            String status,
            LocalDate expiringBy,
            int page,
            int size) {
        requirePage(page, size);
        StringBuilder where = new StringBuilder(" WHERE school_id = ? AND canteen_id = ?");
        List<Object> params = new ArrayList<>(List.of(scope.schoolId(), scope.canteenId()));
        if (category != null && !category.isBlank()) {
            where.append(" AND category = ?");
            params.add(category.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            params.add(status.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (expiringBy != null) {
            where.append(" AND valid_to <= ?");
            params.add(java.sql.Date.valueOf(expiringBy));
        }
        long total = count("SELECT COUNT(*) FROM compliance_records" + where, params);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add((page - 1) * size);
        List<ComplianceRecord> records = jdbc.query(
                "SELECT * FROM compliance_records" + where
                        + " ORDER BY valid_to, record_id LIMIT ? OFFSET ?",
                this::mapRecord,
                pageParams.toArray());
        return new PageResult<>(records, page, size, total);
    }

    @Override
    public Optional<ComplianceRecord> find(CanteenScope scope, String recordId) {
        return jdbc.query(
                        "SELECT * FROM compliance_records WHERE school_id = ? AND canteen_id = ?"
                                + " AND record_id = ?",
                        this::mapRecord,
                        scope.schoolId(),
                        scope.canteenId(),
                        recordId)
                .stream()
                .findFirst();
    }

    @Override
    public ComplianceRecord create(CanteenScope scope, ComplianceRecord record, String actorId) {
        Instant now = Instant.now();
        try {
            jdbc.update(
                    "INSERT INTO compliance_records (school_id, canteen_id, record_id, category, subject_type,"
                            + " subject_id, subject_name, title, credential_no, valid_from, valid_to,"
                            + " attachment_refs_json, status, review_remark, version, created_at, updated_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, 0, ?, ?)",
                    scope.schoolId(),
                    scope.canteenId(),
                    record.id(),
                    record.category().name(),
                    record.subjectType(),
                    record.subjectId(),
                    record.subjectName(),
                    record.title(),
                    record.credentialNo(),
                    java.sql.Date.valueOf(record.validFrom()),
                    java.sql.Date.valueOf(record.validTo()),
                    writeJson(record.attachmentRefs()),
                    record.reviewRemark(),
                    Timestamp.from(now),
                    Timestamp.from(now));
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("Compliance record already exists: " + record.id(), exception);
        }
        ComplianceRecord persisted = find(scope, record.id())
                .orElseThrow(() -> new IllegalStateException("Compliance record was not persisted"));
        appendHistory(scope, persisted, "CREATED", actorId);
        return persisted;
    }

    @Override
    public ComplianceRecord update(CanteenScope scope, ComplianceRecord record, String actorId) {
        int changed = jdbc.update(
                "UPDATE compliance_records SET category = ?, subject_type = ?, subject_id = ?,"
                        + " subject_name = ?, title = ?, credential_no = ?, valid_from = ?, valid_to = ?,"
                        + " attachment_refs_json = ?, review_remark = ?, version = version + 1, updated_at = ?"
                        + " WHERE school_id = ? AND canteen_id = ? AND record_id = ? AND version = ?",
                record.category().name(),
                record.subjectType(),
                record.subjectId(),
                record.subjectName(),
                record.title(),
                record.credentialNo(),
                java.sql.Date.valueOf(record.validFrom()),
                java.sql.Date.valueOf(record.validTo()),
                writeJson(record.attachmentRefs()),
                record.reviewRemark(),
                Timestamp.from(Instant.now()),
                scope.schoolId(),
                scope.canteenId(),
                record.id(),
                record.version());
        if (changed != 1) {
            throw new IllegalArgumentException(
                    "Compliance record was changed concurrently: " + record.id());
        }
        ComplianceRecord persisted = find(scope, record.id())
                .orElseThrow(() -> new IllegalStateException("Compliance record disappeared"));
        appendHistory(scope, persisted, "UPDATED", actorId);
        return persisted;
    }

    @Override
    public ComplianceRecord transition(
            CanteenScope scope,
            String recordId,
            long expectedVersion,
            ComplianceRecordStatus status,
            String reviewRemark,
            String actorId) {
        Instant now = Instant.now();
        int changed;
        if (status == ComplianceRecordStatus.SUBMITTED) {
            changed = jdbc.update(
                    "UPDATE compliance_records SET status = ?, review_remark = ?, submitted_at = ?,"
                            + " version = version + 1, updated_at = ?"
                            + " WHERE school_id = ? AND canteen_id = ? AND record_id = ? AND version = ?",
                    status.name(),
                    reviewRemark,
                    Timestamp.from(now),
                    Timestamp.from(now),
                    scope.schoolId(),
                    scope.canteenId(),
                    recordId,
                    expectedVersion);
        } else {
            changed = jdbc.update(
                    "UPDATE compliance_records SET status = ?, review_remark = ?, reviewed_at = ?,"
                            + " reviewed_by = ?, version = version + 1, updated_at = ?"
                            + " WHERE school_id = ? AND canteen_id = ? AND record_id = ? AND version = ?",
                    status.name(),
                    reviewRemark,
                    Timestamp.from(now),
                    actorId,
                    Timestamp.from(now),
                    scope.schoolId(),
                    scope.canteenId(),
                    recordId,
                    expectedVersion);
        }
        if (changed != 1) {
            throw new IllegalArgumentException(
                    "Compliance record was changed concurrently: " + recordId);
        }
        ComplianceRecord persisted = find(scope, recordId)
                .orElseThrow(() -> new IllegalStateException("Compliance record disappeared"));
        appendHistory(scope, persisted, status.name(), actorId);
        return persisted;
    }

    @Override
    public List<ComplianceRecordHistory> history(CanteenScope scope, String recordId) {
        return jdbc.query(
                "SELECT * FROM compliance_record_history WHERE school_id = ? AND canteen_id = ?"
                        + " AND record_id = ? ORDER BY occurred_at, history_id",
                this::mapHistory,
                scope.schoolId(),
                scope.canteenId(),
                recordId);
    }

    @Override
    public List<ComplianceRecord> expiring(CanteenScope scope, LocalDate asOf, LocalDate until) {
        if (until.isBefore(asOf)) {
            throw new IllegalArgumentException("until cannot be before asOf");
        }
        return jdbc.query(
                "SELECT * FROM compliance_records WHERE school_id = ? AND canteen_id = ?"
                        + " AND status = 'APPROVED' AND valid_to <= ? ORDER BY valid_to, record_id",
                this::mapRecord,
                scope.schoolId(),
                scope.canteenId(),
                java.sql.Date.valueOf(until));
    }

    @Override
    public List<CanteenScope> listScopes() {
        return jdbc.query(
                "SELECT school_id, id FROM canteens ORDER BY school_id, id",
                (result, row) -> new CanteenScope(
                        result.getString("school_id"), result.getString("id")));
    }

    private void appendHistory(
            CanteenScope scope,
            ComplianceRecord record,
            String action,
            String actorId) {
        jdbc.update(
                "INSERT INTO compliance_record_history (school_id, canteen_id, history_id, record_id, action,"
                        + " status, snapshot_json, actor_id, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                scope.schoolId(),
                scope.canteenId(),
                "HISTORY-" + UUID.randomUUID(),
                record.id(),
                action,
                record.status().name(),
                writeJson(snapshot(record)),
                actorId,
                Timestamp.from(Instant.now()));
    }

    private ComplianceRecord mapRecord(ResultSet result, int row) throws SQLException {
        return new ComplianceRecord(
                result.getString("record_id"),
                ComplianceCategory.valueOf(result.getString("category")),
                result.getString("subject_type"),
                result.getString("subject_id"),
                result.getString("subject_name"),
                result.getString("title"),
                result.getString("credential_no"),
                result.getDate("valid_from").toLocalDate(),
                result.getDate("valid_to").toLocalDate(),
                readJson(result.getString("attachment_refs_json"), new TypeReference<>() {
                }),
                ComplianceRecordStatus.valueOf(result.getString("status")),
                result.getString("review_remark"),
                result.getLong("version"),
                instant(result.getTimestamp("created_at")),
                instant(result.getTimestamp("updated_at")),
                instantOrNull(result.getTimestamp("submitted_at")),
                instantOrNull(result.getTimestamp("reviewed_at")),
                result.getString("reviewed_by"));
    }

    private ComplianceRecordHistory mapHistory(ResultSet result, int row) throws SQLException {
        return new ComplianceRecordHistory(
                result.getString("history_id"),
                result.getString("record_id"),
                result.getString("action"),
                ComplianceRecordStatus.valueOf(result.getString("status")),
                readJson(result.getString("snapshot_json"), new TypeReference<>() {
                }),
                result.getString("actor_id"),
                instant(result.getTimestamp("occurred_at")));
    }

    private static Map<String, Object> snapshot(ComplianceRecord record) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", record.id());
        snapshot.put("category", record.category().name());
        snapshot.put("subjectType", record.subjectType());
        snapshot.put("subjectId", record.subjectId());
        snapshot.put("subjectName", record.subjectName());
        snapshot.put("title", record.title());
        put(snapshot, "credentialNo", record.credentialNo());
        snapshot.put("validFrom", record.validFrom());
        snapshot.put("validTo", record.validTo());
        snapshot.put("attachmentRefs", record.attachmentRefs());
        snapshot.put("status", record.status().name());
        put(snapshot, "reviewRemark", record.reviewRemark());
        snapshot.put("version", record.version());
        put(snapshot, "submittedAt", record.submittedAt());
        put(snapshot, "reviewedAt", record.reviewedAt());
        put(snapshot, "reviewedBy", record.reviewedBy());
        return snapshot;
    }

    private static void put(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private <T> T readJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid compliance JSON", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Compliance record contains invalid JSON", exception);
        }
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    private static Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private long count(String sql, List<Object> params) {
        Number value = jdbc.queryForObject(sql, Number.class, params.toArray());
        return value == null ? 0 : value.longValue();
    }

    private static void requirePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be positive and size must be between 1 and 100");
        }
    }
}
