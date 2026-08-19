package com.example.smartcanteen.infrastructure;

import com.example.smartcanteen.application.port.AlertCenter;
import com.example.smartcanteen.application.port.AlertStore;
import com.example.smartcanteen.domain.AlertDisposal;
import com.example.smartcanteen.domain.AlertQuery;
import com.example.smartcanteen.domain.AlertRecord;
import com.example.smartcanteen.domain.AlertReport;
import com.example.smartcanteen.domain.AlertSource;
import com.example.smartcanteen.domain.AlertStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAlertStore implements AlertStore {

    private final JdbcTemplate jdbc;

    public JdbcAlertStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AlertRecord report(AlertReport report) {
        try {
            jdbc.update(
                    """
                    INSERT INTO alert_records (
                        warn_id, source, third_warn_id, school_id, school_name,
                        area_code, device_id, device_name, canteen_id,
                        warn_happen_time, alarm_event_id, warn_full_pic, warn_content,
                        status, process_status
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'UNPROCESSED', 0)
                    """,
                    report.warnId(),
                    report.source().name(),
                    report.thirdWarnId(),
                    report.schoolId(),
                    report.schoolName(),
                    report.areaCode(),
                    report.deviceId(),
                    report.deviceName(),
                    report.canteenId(),
                    Timestamp.from(report.warnHappenTime()),
                    report.alarmEventId(),
                    report.warnFullPic(),
                    report.warnContent());
        } catch (DuplicateKeyException duplicate) {
            AlertRecord existing = findByWarnId(report.warnId()).orElseThrow(() -> duplicate);
            if (!existing.matches(report)) {
                throw new IllegalArgumentException(
                        "thirdWarnId was already used for a different alert payload");
            }
            return existing;
        }
        return findByWarnId(report.warnId())
                .orElseThrow(() -> new IllegalStateException("Alert was not persisted"));
    }

    @Override
    public Optional<AlertRecord> find(String warnId) {
        return findByWarnId(warnId);
    }

    @Override
    public AlertRecord dispose(String warnId, AlertDisposal disposal) {
        AlertRecord existing = findByWarnId(warnId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + warnId));
        if (existing.hasSameDisposal(disposal)) {
            return existing;
        }
        if (existing.status() == AlertStatus.PROCESSED) {
            throw new IllegalArgumentException(
                    "Processed alert cannot be changed: " + warnId);
        }
        int changed = jdbc.update(
                """
                UPDATE alert_records
                SET status = ?, process_status = ?, process_time = ?,
                    process_user = ?, process_content = ?, process_file = ?
                WHERE warn_id = ? AND status = 'UNPROCESSED'
                """,
                disposal.processStatus() == 1 ? AlertStatus.PROCESSED.name()
                        : AlertStatus.UNPROCESSED.name(),
                disposal.processStatus(),
                timestamp(disposal.processTime()),
                disposal.processUser(),
                disposal.processContent(),
                disposal.processFile(),
                warnId);
        if (changed != 1) {
            AlertRecord current = findByWarnId(warnId)
                    .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + warnId));
            if (current.hasSameDisposal(disposal)) {
                return current;
            }
            throw new IllegalArgumentException("Alert was changed concurrently: " + warnId);
        }
        return findByWarnId(warnId)
                .orElseThrow(() -> new IllegalStateException("Alert disposal was not persisted"));
    }

    @Override
    public AlertRecord dispose(String warnId, AlertDisposal disposal, String idempotencyKey) {
        AlertRecord existing = findByWarnId(warnId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + warnId));
        String payloadHash = disposalHash(disposal);
        Optional<StoredDisposal> previous = findDisposalByWarnId(existing.warnId());
        if (previous.isPresent()) {
            if (!previous.get().idempotencyKey().equals(idempotencyKey)
                    || !previous.get().payloadHash().equals(payloadHash)) {
                throw new IllegalArgumentException(
                        "Idempotency-Key was already used for a different alert disposal");
            }
            return existing;
        }
        try {
            jdbc.update(
                    "INSERT INTO alert_disposal_idempotency "
                            + "(school_id, canteen_id, warn_id, idempotency_key, payload_hash) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    existing.schoolId(),
                    existing.canteenId(),
                    existing.warnId(),
                    idempotencyKey,
                    payloadHash);
        } catch (DuplicateKeyException duplicate) {
            StoredDisposal raced = findDisposalByWarnId(existing.warnId()).orElse(null);
            if (raced != null
                    && raced.idempotencyKey().equals(idempotencyKey)
                    && raced.payloadHash().equals(payloadHash)) {
                return findByWarnId(warnId).orElse(existing);
            }
            throw new IllegalArgumentException(
                    "Idempotency-Key was already used for a different alert disposal", duplicate);
        }
        // Record the idempotency evidence before applying the state transition so future Agent
        // retries cannot bypass the durable guard.
        return dispose(warnId, disposal);
    }

    @Override
    public AlertCenter.AlertPage query(AlertQuery query) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> parameters = new ArrayList<>();
        if (query.schoolId() != null) {
            where.append(" AND school_id = ?");
            parameters.add(query.schoolId());
        }
        if (query.canteenId() != null) {
            where.append(" AND canteen_id = ?");
            parameters.add(query.canteenId());
        }
        if (query.source() != null) {
            where.append(" AND source = ?");
            parameters.add(query.source().name());
        }
        if (query.status() != null) {
            where.append(" AND status = ?");
            parameters.add(query.status().name());
        }
        if (query.alarmEventId() != null) {
            where.append(" AND alarm_event_id = ?");
            parameters.add(query.alarmEventId());
        }
        if (query.deviceName() != null) {
            where.append(" AND device_name = ?");
            parameters.add(query.deviceName());
        }
        if (query.startDate() != null) {
            where.append(" AND warn_happen_time >= ?");
            parameters.add(Timestamp.from(query.startDate()));
        }
        if (query.endDate() != null) {
            where.append(" AND warn_happen_time <= ?");
            parameters.add(Timestamp.from(query.endDate()));
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM alert_records" + where,
                Long.class,
                parameters.toArray());
        int offset = (query.pageNum() - 1) * query.pageSize();
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(query.pageSize());
        pageParameters.add(offset);
        List<AlertRecord> records = jdbc.query(
                "SELECT * FROM alert_records" + where
                        + " ORDER BY warn_happen_time DESC, created_at DESC"
                        + " LIMIT ? OFFSET ?",
                this::mapRecord,
                pageParameters.toArray());
        return new AlertCenter.AlertPage(
                records, query.pageNum(), query.pageSize(), total == null ? 0 : total);
    }

    private Optional<AlertRecord> findByWarnId(String warnId) {
        return jdbc.query(
                        "SELECT * FROM alert_records WHERE warn_id = ?",
                        this::mapRecord,
                        warnId)
                .stream()
                .findFirst();
    }

    private Optional<StoredDisposal> findDisposalByWarnId(String warnId) {
        return jdbc.query(
                        "SELECT idempotency_key, payload_hash FROM alert_disposal_idempotency "
                                + "WHERE warn_id = ?",
                        (result, row) -> new StoredDisposal(
                                result.getString("idempotency_key"),
                                result.getString("payload_hash")),
                        warnId)
                .stream()
                .findFirst();
    }

    private static String disposalHash(AlertDisposal disposal) {
        String payload = disposal.processStatus() + "\n"
                + String.valueOf(disposal.processTime()) + "\n"
                + String.valueOf(disposal.processUser()) + "\n"
                + String.valueOf(disposal.processContent()) + "\n"
                + String.valueOf(disposal.processFile());
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record StoredDisposal(String idempotencyKey, String payloadHash) {
    }

    private AlertRecord mapRecord(ResultSet result, int row) throws SQLException {
        return new AlertRecord(
                result.getString("warn_id"),
                AlertSource.from(result.getString("source")),
                result.getString("third_warn_id"),
                result.getString("school_id"),
                result.getString("school_name"),
                result.getString("area_code"),
                result.getString("device_id"),
                result.getString("device_name"),
                result.getString("canteen_id"),
                instant(result.getTimestamp("warn_happen_time")),
                result.getString("alarm_event_id"),
                result.getString("warn_full_pic"),
                result.getString("warn_content"),
                AlertStatus.from(result.getString("status")),
                instant(result.getTimestamp("created_at")),
                instant(result.getTimestamp("process_time")),
                result.getString("process_user"),
                result.getString("process_content"),
                result.getString("process_file"));
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
