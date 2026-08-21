package com.example.smartcanteen.infrastructure;

import com.example.smartcanteen.application.port.DinerComplaintStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.DinerComplaint;
import com.example.smartcanteen.domain.PageResult;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDinerComplaintStore implements DinerComplaintStore {

    private final JdbcTemplate jdbc;

    public JdbcDinerComplaintStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<DinerComplaint> listMine(
            CanteenScope scope, String actorUserId, String status, int page, int size) {
        requirePage(page, size);
        StringBuilder where = new StringBuilder(
                " WHERE school_id = ? AND canteen_id = ? AND actor_user_id = ?");
        List<Object> params = new ArrayList<>(List.of(
                scope.schoolId(), scope.canteenId(), actorUserId));
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            params.add(status);
        }
        long total = count("SELECT COUNT(*) FROM diner_complaints" + where, params);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add(offset(page, size));
        List<DinerComplaint> records = jdbc.query(
                "SELECT * FROM diner_complaints" + where
                        + " ORDER BY created_at DESC, complaint_id DESC LIMIT ? OFFSET ?",
                complaintMapper(), pageParams.toArray());
        return new PageResult<>(records, page, size, total);
    }

    @Override
    public DinerComplaint create(
            CanteenScope scope,
            DinerComplaint complaint,
            String idempotencyKey,
            String requestHash) {
        Optional<DinerComplaint> existing = findByIdempotency(
                scope, complaint.actorUserId(), idempotencyKey);
        if (existing.isPresent()) {
            ensureSameRequest(scope, existing.get(), requestHash);
            return existing.get();
        }
        try {
            jdbc.update(
                    "INSERT INTO diner_complaints (school_id, canteen_id, complaint_id, actor_user_id, "
                            + "category, subject, description, related_order_id, status, reply, "
                            + "idempotency_key, request_hash, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    scope.schoolId(), scope.canteenId(), complaint.id(), complaint.actorUserId(),
                    complaint.category(), complaint.subject(), complaint.description(),
                    complaint.relatedOrderId(), complaint.status(), complaint.reply(), idempotencyKey,
                    requestHash, complaint.version());
        } catch (DuplicateKeyException exception) {
            DinerComplaint replay = findByIdempotency(
                            scope, complaint.actorUserId(), idempotencyKey)
                    .orElseThrow(() -> exception);
            ensureSameRequest(scope, replay, requestHash);
            return replay;
        }
        return findByIdempotency(scope, complaint.actorUserId(), idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Diner complaint was not persisted"));
    }

    @Override
    public Optional<DinerComplaint> findByIdempotency(
            CanteenScope scope, String actorUserId, String idempotencyKey) {
        return jdbc.query(
                        "SELECT * FROM diner_complaints WHERE school_id = ? AND canteen_id = ? "
                                + "AND actor_user_id = ? AND idempotency_key = ?",
                        complaintMapper(), scope.schoolId(), scope.canteenId(), actorUserId,
                        idempotencyKey)
                .stream()
                .findFirst();
    }

    private void ensureSameRequest(
            CanteenScope scope, DinerComplaint existing, String requestHash) {
        String storedHash = jdbc.queryForObject(
                "SELECT request_hash FROM diner_complaints WHERE school_id = ? AND canteen_id = ? "
                        + "AND complaint_id = ?",
                String.class, scope.schoolId(), scope.canteenId(), existing.id());
        if (!requestHash.equals(storedHash)) {
            throw new IllegalArgumentException(
                    "Idempotency-Key was already used for a different diner complaint");
        }
    }

    private RowMapper<DinerComplaint> complaintMapper() {
        return (result, row) -> new DinerComplaint(
                result.getString("complaint_id"),
                result.getString("actor_user_id"),
                result.getString("category"),
                result.getString("subject"),
                result.getString("description"),
                result.getString("related_order_id"),
                result.getString("status"),
                result.getString("reply"),
                result.getLong("version"),
                instant(result.getTimestamp("created_at")),
                instant(result.getTimestamp("updated_at")));
    }

    private long count(String sql, List<?> parameters) {
        Number value = jdbc.queryForObject(sql, Number.class, parameters.toArray());
        return value == null ? 0 : value.longValue();
    }

    private static int offset(int page, int size) {
        return Math.multiplyExact(page - 1, size);
    }

    private static void requirePage(int page, int size) {
        if (page < 1 || page > 1_000_000 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be 1..1000000 and size must be 1..100");
        }
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
