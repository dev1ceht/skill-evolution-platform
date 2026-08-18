package com.example.smartcanteen.infrastructure;

import com.example.smartcanteen.application.port.AuditStore;
import com.example.smartcanteen.domain.AuditLog;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAuditStore implements AuditStore {

    private final JdbcTemplate jdbc;

    public JdbcAuditStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    // Keep audit evidence atomic with the caller's Run transaction when one exists. The
    // application deliberately catches audit failures and records an event fallback, so the
    // interceptor must not mark that surrounding business transaction rollback-only.
    @Transactional(noRollbackFor = RuntimeException.class)
    public void append(AuditLog auditLog) {
        jdbc.update(
                "INSERT INTO audit_logs "
                        + "(audit_id, actor_user_id, action, resource_type, resource_id, school_id, "
                        + "canteen_id, outcome, detail, request_id, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                auditLog.auditId(), auditLog.actorUserId(), auditLog.action(), auditLog.resourceType(),
                auditLog.resourceId(), auditLog.schoolId(), auditLog.canteenId(), auditLog.outcome(),
                auditLog.detail(), auditLog.requestId(),
                auditLog.createdAt() == null ? Timestamp.from(Instant.now()) : Timestamp.from(auditLog.createdAt()));
    }

    @Override
    public List<AuditLog> listRecent(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return jdbc.query(
                "SELECT audit_id, actor_user_id, action, resource_type, resource_id, school_id, "
                        + "canteen_id, outcome, detail, request_id, created_at "
                        + "FROM audit_logs ORDER BY created_at DESC, audit_id DESC LIMIT ?",
                (result, row) -> new AuditLog(
                        result.getString("audit_id"),
                        result.getString("actor_user_id"),
                        result.getString("action"),
                        result.getString("resource_type"),
                        result.getString("resource_id"),
                        result.getString("school_id"),
                        result.getString("canteen_id"),
                        result.getString("outcome"),
                        result.getString("detail"),
                        result.getString("request_id"),
                        result.getTimestamp("created_at").toInstant()),
                safeLimit);
    }
}
