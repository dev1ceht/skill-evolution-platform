package com.example.smartcanteen.infrastructure;

import com.example.smartcanteen.application.port.GovernanceStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.CanteenShowcase;
import com.example.smartcanteen.domain.CanteenShowcaseStatus;
import com.example.smartcanteen.domain.GovernanceHistory;
import com.example.smartcanteen.domain.MealPeriod;
import com.example.smartcanteen.domain.MealSuspension;
import com.example.smartcanteen.domain.MealSuspensionStatus;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.domain.SupplierComplaint;
import com.example.smartcanteen.domain.SupplierComplaintStatus;
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
public class JdbcGovernanceStore implements GovernanceStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public JdbcGovernanceStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public PageResult<CanteenShowcase> listShowcases(
            CanteenScope scope, String status, int page, int size) {
        requirePage(page, size);
        StringBuilder where = new StringBuilder(" WHERE school_id = ? AND canteen_id = ?");
        List<Object> params = new ArrayList<>(List.of(scope.schoolId(), scope.canteenId()));
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            params.add(status.trim().toUpperCase(java.util.Locale.ROOT));
        }
        long total = count("SELECT COUNT(*) FROM canteen_showcases" + where, params);
        return new PageResult<>(jdbc.query(
                "SELECT * FROM canteen_showcases" + where
                        + " ORDER BY updated_at DESC, showcase_id DESC LIMIT ? OFFSET ?",
                this::mapShowcase,
                withPage(params, page, size).toArray()), page, size, total);
    }

    @Override
    public Optional<CanteenShowcase> findShowcase(CanteenScope scope, String showcaseId) {
        return jdbc.query(
                        "SELECT * FROM canteen_showcases WHERE school_id = ? AND canteen_id = ?"
                                + " AND showcase_id = ?",
                        this::mapShowcase,
                        scope.schoolId(),
                        scope.canteenId(),
                        showcaseId)
                .stream()
                .findFirst();
    }

    @Override
    public CanteenShowcase createShowcase(
            CanteenScope scope, CanteenShowcase showcase, String actorId) {
        Instant now = Instant.now();
        try {
            jdbc.update(
                    "INSERT INTO canteen_showcases (school_id, canteen_id, showcase_id, title, content,"
                            + " photos_json, status, previous_version_id, version, created_at, updated_at,"
                            + " review_remark, reviewed_at, reviewed_by, published_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?)",
                    scope.schoolId(),
                    scope.canteenId(),
                    showcase.id(),
                    showcase.title(),
                    showcase.content(),
                    writeJson(showcase.photos()),
                    showcase.status().name(),
                    showcase.previousVersionId(),
                    Timestamp.from(now),
                    Timestamp.from(now),
                    showcase.reviewRemark(),
                    timestamp(showcase.reviewedAt()),
                    showcase.reviewedBy(),
                    timestamp(showcase.publishedAt()));
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("Showcase already exists: " + showcase.id(), exception);
        }
        CanteenShowcase persisted = findShowcase(scope, showcase.id())
                .orElseThrow(() -> new IllegalStateException("Showcase was not persisted"));
        appendHistory(scope, "SHOWCASE", persisted.id(), persisted.status().name(), "CREATED",
                snapshot(persisted), actorId);
        return persisted;
    }

    @Override
    public CanteenShowcase updateShowcase(
            CanteenScope scope, CanteenShowcase showcase, String actorId) {
        int changed = jdbc.update(
                "UPDATE canteen_showcases SET title = ?, content = ?, photos_json = ?,"
                        + " review_remark = ?, version = version + 1, updated_at = ?"
                        + " WHERE school_id = ? AND canteen_id = ? AND showcase_id = ? AND version = ?",
                showcase.title(),
                showcase.content(),
                writeJson(showcase.photos()),
                showcase.reviewRemark(),
                Timestamp.from(Instant.now()),
                scope.schoolId(),
                scope.canteenId(),
                showcase.id(),
                showcase.version());
        if (changed != 1) {
            throw new IllegalArgumentException("Showcase was changed concurrently: " + showcase.id());
        }
        CanteenShowcase persisted = findShowcase(scope, showcase.id())
                .orElseThrow(() -> new IllegalStateException("Showcase disappeared"));
        appendHistory(scope, "SHOWCASE", persisted.id(), persisted.status().name(), "UPDATED",
                snapshot(persisted), actorId);
        return persisted;
    }

    @Override
    public CanteenShowcase transitionShowcase(
            CanteenScope scope,
            String showcaseId,
            long expectedVersion,
            CanteenShowcaseStatus status,
            String reviewRemark,
            String actorId) {
        Instant now = Instant.now();
        int changed;
        if (status == CanteenShowcaseStatus.APPROVED || status == CanteenShowcaseStatus.REJECTED) {
            changed = jdbc.update(
                    "UPDATE canteen_showcases SET status = ?, review_remark = ?, reviewed_at = ?,"
                            + " reviewed_by = ?, version = version + 1, updated_at = ?"
                            + " WHERE school_id = ? AND canteen_id = ? AND showcase_id = ? AND version = ?",
                    status.name(),
                    reviewRemark,
                    Timestamp.from(now),
                    actorId,
                    Timestamp.from(now),
                    scope.schoolId(),
                    scope.canteenId(),
                    showcaseId,
                    expectedVersion);
        } else if (status == CanteenShowcaseStatus.PUBLISHED) {
            changed = jdbc.update(
                    "UPDATE canteen_showcases SET status = ?, published_at = ?, version = version + 1,"
                            + " updated_at = ? WHERE school_id = ? AND canteen_id = ? AND showcase_id = ?"
                            + " AND version = ?",
                    status.name(),
                    Timestamp.from(now),
                    Timestamp.from(now),
                    scope.schoolId(),
                    scope.canteenId(),
                    showcaseId,
                    expectedVersion);
        } else {
            changed = jdbc.update(
                    "UPDATE canteen_showcases SET status = ?, review_remark = ?, version = version + 1,"
                            + " updated_at = ? WHERE school_id = ? AND canteen_id = ? AND showcase_id = ?"
                            + " AND version = ?",
                    status.name(),
                    reviewRemark,
                    Timestamp.from(now),
                    scope.schoolId(),
                    scope.canteenId(),
                    showcaseId,
                    expectedVersion);
        }
        if (changed != 1) {
            throw new IllegalArgumentException("Showcase was changed concurrently: " + showcaseId);
        }
        CanteenShowcase persisted = findShowcase(scope, showcaseId)
                .orElseThrow(() -> new IllegalStateException("Showcase disappeared"));
        appendHistory(scope, "SHOWCASE", persisted.id(), persisted.status().name(), status.name(),
                snapshot(persisted), actorId);
        return persisted;
    }

    @Override
    public PageResult<MealSuspension> listMealSuspensions(
            CanteenScope scope, LocalDate from, LocalDate to, String status, int page, int size) {
        requirePage(page, size);
        StringBuilder where = new StringBuilder(" WHERE school_id = ? AND canteen_id = ?");
        List<Object> params = new ArrayList<>(List.of(scope.schoolId(), scope.canteenId()));
        if (from != null) {
            where.append(" AND meal_date >= ?");
            params.add(java.sql.Date.valueOf(from));
        }
        if (to != null) {
            where.append(" AND meal_date <= ?");
            params.add(java.sql.Date.valueOf(to));
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            params.add(status.trim().toUpperCase(java.util.Locale.ROOT));
        }
        long total = count("SELECT COUNT(*) FROM meal_suspensions" + where, params);
        return new PageResult<>(jdbc.query(
                "SELECT * FROM meal_suspensions" + where
                        + " ORDER BY meal_date DESC, meal_period LIMIT ? OFFSET ?",
                this::mapSuspension,
                withPage(params, page, size).toArray()), page, size, total);
    }

    @Override
    public Optional<MealSuspension> findMealSuspension(CanteenScope scope, String suspensionId) {
        return jdbc.query(
                        "SELECT * FROM meal_suspensions WHERE school_id = ? AND canteen_id = ?"
                                + " AND suspension_id = ?",
                        this::mapSuspension,
                        scope.schoolId(),
                        scope.canteenId(),
                        suspensionId)
                .stream()
                .findFirst();
    }

    @Override
    public MealSuspension createMealSuspension(
            CanteenScope scope, MealSuspension suspension, String actorId) {
        Instant now = Instant.now();
        try {
            jdbc.update(
                    "INSERT INTO meal_suspensions (school_id, canteen_id, suspension_id, meal_date,"
                            + " meal_period, reason, status, review_remark, version, created_at, updated_at,"
                            + " reviewed_at, reviewed_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)",
                    scope.schoolId(),
                    scope.canteenId(),
                    suspension.id(),
                    java.sql.Date.valueOf(suspension.mealDate()),
                    suspension.mealPeriod().name(),
                    suspension.reason(),
                    suspension.status().name(),
                    suspension.reviewRemark(),
                    Timestamp.from(now),
                    Timestamp.from(now),
                    timestamp(suspension.reviewedAt()),
                    suspension.reviewedBy());
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("Meal suspension already exists for this meal slot", exception);
        }
        MealSuspension persisted = findMealSuspension(scope, suspension.id())
                .orElseThrow(() -> new IllegalStateException("Meal suspension was not persisted"));
        appendHistory(scope, "MEAL_SUSPENSION", persisted.id(), persisted.status().name(), "CREATED",
                snapshot(persisted), actorId);
        return persisted;
    }

    @Override
    public MealSuspension transitionMealSuspension(
            CanteenScope scope,
            String suspensionId,
            long expectedVersion,
            MealSuspensionStatus status,
            String reviewRemark,
            String actorId) {
        Instant now = Instant.now();
        int changed;
        if (status == MealSuspensionStatus.APPROVED || status == MealSuspensionStatus.REJECTED) {
            changed = jdbc.update(
                    "UPDATE meal_suspensions SET status = ?, review_remark = ?, reviewed_at = ?,"
                            + " reviewed_by = ?, version = version + 1, updated_at = ?"
                            + " WHERE school_id = ? AND canteen_id = ? AND suspension_id = ? AND version = ?",
                    status.name(),
                    reviewRemark,
                    Timestamp.from(now),
                    actorId,
                    Timestamp.from(now),
                    scope.schoolId(),
                    scope.canteenId(),
                    suspensionId,
                    expectedVersion);
        } else {
            changed = jdbc.update(
                    "UPDATE meal_suspensions SET status = ?, version = version + 1, updated_at = ?"
                            + " WHERE school_id = ? AND canteen_id = ? AND suspension_id = ? AND version = ?",
                    status.name(),
                    Timestamp.from(now),
                    scope.schoolId(),
                    scope.canteenId(),
                    suspensionId,
                    expectedVersion);
        }
        if (changed != 1) {
            throw new IllegalArgumentException("Meal suspension was changed concurrently: " + suspensionId);
        }
        MealSuspension persisted = findMealSuspension(scope, suspensionId)
                .orElseThrow(() -> new IllegalStateException("Meal suspension disappeared"));
        appendHistory(scope, "MEAL_SUSPENSION", persisted.id(), persisted.status().name(), status.name(),
                snapshot(persisted), actorId);
        return persisted;
    }

    @Override
    public Map<String, Long> mealSuspensionStats(
            CanteenScope scope, LocalDate from, LocalDate to) {
        StringBuilder sql = new StringBuilder(
                "SELECT status, COUNT(*) AS total FROM meal_suspensions"
                        + " WHERE school_id = ? AND canteen_id = ?");
        List<Object> params = new ArrayList<>(List.of(scope.schoolId(), scope.canteenId()));
        if (from != null) {
            sql.append(" AND meal_date >= ?");
            params.add(java.sql.Date.valueOf(from));
        }
        if (to != null) {
            sql.append(" AND meal_date <= ?");
            params.add(java.sql.Date.valueOf(to));
        }
        sql.append(" GROUP BY status ORDER BY status");
        Map<String, Long> stats = new LinkedHashMap<>();
        jdbc.query(sql.toString(),
                (org.springframework.jdbc.core.RowCallbackHandler) result -> stats.put(
                        result.getString("status"), result.getLong("total")),
                params.toArray());
        return stats;
    }

    @Override
    public PageResult<SupplierComplaint> listComplaints(
            CanteenScope scope, String status, String supplierId, int page, int size) {
        requirePage(page, size);
        StringBuilder where = new StringBuilder(" WHERE school_id = ? AND canteen_id = ?");
        List<Object> params = new ArrayList<>(List.of(scope.schoolId(), scope.canteenId()));
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            params.add(status.trim().toUpperCase(java.util.Locale.ROOT));
        }
        if (supplierId != null && !supplierId.isBlank()) {
            where.append(" AND supplier_id = ?");
            params.add(supplierId.trim());
        }
        long total = count("SELECT COUNT(*) FROM supplier_complaints" + where, params);
        return new PageResult<>(jdbc.query(
                "SELECT * FROM supplier_complaints" + where
                        + " ORDER BY updated_at DESC, complaint_id DESC LIMIT ? OFFSET ?",
                this::mapComplaint,
                withPage(params, page, size).toArray()), page, size, total);
    }

    @Override
    public Optional<SupplierComplaint> findComplaint(CanteenScope scope, String complaintId) {
        return jdbc.query(
                        "SELECT * FROM supplier_complaints WHERE school_id = ? AND canteen_id = ?"
                                + " AND complaint_id = ?",
                        this::mapComplaint,
                        scope.schoolId(),
                        scope.canteenId(),
                        complaintId)
                .stream()
                .findFirst();
    }

    @Override
    public SupplierComplaint createComplaint(
            CanteenScope scope, SupplierComplaint complaint, String actorId) {
        Instant now = Instant.now();
        try {
            jdbc.update(
                    "INSERT INTO supplier_complaints (school_id, canteen_id, complaint_id, supplier_id,"
                            + " subject, description, attachment_refs_json, deadline, status, reply, version,"
                            + " created_by, assigned_to, created_at, updated_at, accepted_at, closed_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?)",
                    scope.schoolId(),
                    scope.canteenId(),
                    complaint.id(),
                    complaint.supplierId(),
                    complaint.subject(),
                    complaint.description(),
                    writeJson(complaint.attachmentRefs()),
                    sqlDate(complaint.deadline()),
                    complaint.status().name(),
                    complaint.reply(),
                    complaint.createdBy(),
                    complaint.assignedTo(),
                    Timestamp.from(now),
                    Timestamp.from(now),
                    timestamp(complaint.acceptedAt()),
                    timestamp(complaint.closedAt()));
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("Supplier complaint already exists: " + complaint.id(), exception);
        }
        SupplierComplaint persisted = findComplaint(scope, complaint.id())
                .orElseThrow(() -> new IllegalStateException("Supplier complaint was not persisted"));
        appendHistory(scope, "SUPPLIER_COMPLAINT", persisted.id(), persisted.status().name(), "CREATED",
                snapshot(persisted), actorId);
        return persisted;
    }

    @Override
    public SupplierComplaint transitionComplaint(
            CanteenScope scope,
            String complaintId,
            long expectedVersion,
            SupplierComplaintStatus status,
            String reply,
            String actorId) {
        Instant now = Instant.now();
        String assignedTo = status == SupplierComplaintStatus.ACCEPTED ? actorId : null;
        int changed;
        if (status == SupplierComplaintStatus.ACCEPTED) {
            changed = jdbc.update(
                    "UPDATE supplier_complaints SET status = ?, assigned_to = COALESCE(assigned_to, ?),"
                            + " accepted_at = ?, version = version + 1, updated_at = ?"
                            + " WHERE school_id = ? AND canteen_id = ? AND complaint_id = ? AND version = ?",
                    status.name(),
                    assignedTo,
                    Timestamp.from(now),
                    Timestamp.from(now),
                    scope.schoolId(),
                    scope.canteenId(),
                    complaintId,
                    expectedVersion);
        } else if (status == SupplierComplaintStatus.REPLIED) {
            changed = jdbc.update(
                    "UPDATE supplier_complaints SET status = ?, reply = ?, version = version + 1,"
                            + " updated_at = ? WHERE school_id = ? AND canteen_id = ? AND complaint_id = ?"
                            + " AND version = ?",
                    status.name(),
                    reply,
                    Timestamp.from(now),
                    scope.schoolId(),
                    scope.canteenId(),
                    complaintId,
                    expectedVersion);
        } else if (status == SupplierComplaintStatus.CLOSED) {
            changed = jdbc.update(
                    "UPDATE supplier_complaints SET status = ?, reply = COALESCE(?, reply), closed_at = ?,"
                            + " version = version + 1, updated_at = ?"
                            + " WHERE school_id = ? AND canteen_id = ? AND complaint_id = ? AND version = ?",
                    status.name(),
                    reply,
                    Timestamp.from(now),
                    Timestamp.from(now),
                    scope.schoolId(),
                    scope.canteenId(),
                    complaintId,
                    expectedVersion);
        } else {
            changed = jdbc.update(
                    "UPDATE supplier_complaints SET status = ?, version = version + 1, updated_at = ?"
                            + " WHERE school_id = ? AND canteen_id = ? AND complaint_id = ? AND version = ?",
                    status.name(),
                    Timestamp.from(now),
                    scope.schoolId(),
                    scope.canteenId(),
                    complaintId,
                    expectedVersion);
        }
        if (changed != 1) {
            throw new IllegalArgumentException("Supplier complaint was changed concurrently: " + complaintId);
        }
        SupplierComplaint persisted = findComplaint(scope, complaintId)
                .orElseThrow(() -> new IllegalStateException("Supplier complaint disappeared"));
        appendHistory(scope, "SUPPLIER_COMPLAINT", persisted.id(), persisted.status().name(), status.name(),
                snapshot(persisted), actorId);
        return persisted;
    }

    @Override
    public List<GovernanceHistory> history(CanteenScope scope, String entityType, String entityId) {
        return jdbc.query(
                "SELECT * FROM governance_history WHERE school_id = ? AND canteen_id = ?"
                        + " AND entity_type = ? AND entity_id = ? ORDER BY occurred_at, history_id",
                this::mapHistory,
                scope.schoolId(),
                scope.canteenId(),
                entityType,
                entityId);
    }

    private void appendHistory(
            CanteenScope scope,
            String entityType,
            String entityId,
            String status,
            String action,
            Map<String, Object> snapshot,
            String actorId) {
        jdbc.update(
                "INSERT INTO governance_history (school_id, canteen_id, history_id, entity_type, entity_id,"
                        + " action, status, snapshot_json, actor_id, occurred_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                scope.schoolId(),
                scope.canteenId(),
                "GOV-HISTORY-" + UUID.randomUUID(),
                entityType,
                entityId,
                action,
                status,
                writeJson(snapshot),
                actorId,
                Timestamp.from(Instant.now()));
    }

    private CanteenShowcase mapShowcase(ResultSet result, int row) throws SQLException {
        return new CanteenShowcase(
                result.getString("showcase_id"),
                result.getString("title"),
                result.getString("content"),
                readJson(result.getString("photos_json"), new TypeReference<>() {
                }),
                CanteenShowcaseStatus.valueOf(result.getString("status")),
                result.getString("previous_version_id"),
                result.getLong("version"),
                instant(result.getTimestamp("created_at")),
                instant(result.getTimestamp("updated_at")),
                result.getString("review_remark"),
                instantOrNull(result.getTimestamp("reviewed_at")),
                result.getString("reviewed_by"),
                instantOrNull(result.getTimestamp("published_at")));
    }

    private MealSuspension mapSuspension(ResultSet result, int row) throws SQLException {
        return new MealSuspension(
                result.getString("suspension_id"),
                result.getDate("meal_date").toLocalDate(),
                MealPeriod.valueOf(result.getString("meal_period")),
                result.getString("reason"),
                MealSuspensionStatus.valueOf(result.getString("status")),
                result.getString("review_remark"),
                result.getLong("version"),
                instant(result.getTimestamp("created_at")),
                instant(result.getTimestamp("updated_at")),
                instantOrNull(result.getTimestamp("reviewed_at")),
                result.getString("reviewed_by"));
    }

    private SupplierComplaint mapComplaint(ResultSet result, int row) throws SQLException {
        return new SupplierComplaint(
                result.getString("complaint_id"),
                result.getString("supplier_id"),
                result.getString("subject"),
                result.getString("description"),
                readJson(result.getString("attachment_refs_json"), new TypeReference<>() {
                }),
                localDate(result.getDate("deadline")),
                SupplierComplaintStatus.valueOf(result.getString("status")),
                result.getString("reply"),
                result.getLong("version"),
                result.getString("created_by"),
                result.getString("assigned_to"),
                instant(result.getTimestamp("created_at")),
                instant(result.getTimestamp("updated_at")),
                instantOrNull(result.getTimestamp("accepted_at")),
                instantOrNull(result.getTimestamp("closed_at")));
    }

    private GovernanceHistory mapHistory(ResultSet result, int row) throws SQLException {
        return new GovernanceHistory(
                result.getString("history_id"),
                result.getString("entity_type"),
                result.getString("entity_id"),
                result.getString("action"),
                result.getString("status"),
                readJson(result.getString("snapshot_json"), new TypeReference<>() {
                }),
                result.getString("actor_id"),
                instant(result.getTimestamp("occurred_at")));
    }

    private static Map<String, Object> snapshot(CanteenShowcase value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value.id());
        result.put("title", value.title());
        result.put("content", value.content());
        result.put("photos", value.photos());
        result.put("status", value.status().name());
        put(result, "previousVersionId", value.previousVersionId());
        result.put("version", value.version());
        put(result, "reviewRemark", value.reviewRemark());
        put(result, "reviewedBy", value.reviewedBy());
        return result;
    }

    private static Map<String, Object> snapshot(MealSuspension value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value.id());
        result.put("mealDate", value.mealDate());
        result.put("mealPeriod", value.mealPeriod().name());
        result.put("reason", value.reason());
        result.put("status", value.status().name());
        put(result, "reviewRemark", value.reviewRemark());
        result.put("version", value.version());
        put(result, "reviewedBy", value.reviewedBy());
        return result;
    }

    private static Map<String, Object> snapshot(SupplierComplaint value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", value.id());
        result.put("supplierId", value.supplierId());
        result.put("subject", value.subject());
        result.put("description", value.description());
        result.put("attachmentRefs", value.attachmentRefs());
        put(result, "deadline", value.deadline());
        result.put("status", value.status().name());
        put(result, "reply", value.reply());
        result.put("version", value.version());
        result.put("createdBy", value.createdBy());
        put(result, "assignedTo", value.assignedTo());
        return result;
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
            throw new IllegalStateException("Invalid governance JSON", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Governance record contains invalid JSON", exception);
        }
    }

    private long count(String sql, List<Object> params) {
        Number value = jdbc.queryForObject(sql, Number.class, params.toArray());
        return value == null ? 0 : value.longValue();
    }

    private static List<Object> withPage(List<Object> params, int page, int size) {
        List<Object> values = new ArrayList<>(params);
        values.add(size);
        values.add((page - 1) * size);
        return values;
    }

    private static void requirePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be positive and size must be between 1 and 100");
        }
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? Instant.EPOCH : value.toInstant();
    }

    private static Instant instantOrNull(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static java.sql.Date sqlDate(LocalDate value) {
        return value == null ? null : java.sql.Date.valueOf(value);
    }

    private static LocalDate localDate(java.sql.Date value) {
        return value == null ? null : value.toLocalDate();
    }
}
