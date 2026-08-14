package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.AlertCenter;
import com.example.smartcanteen.application.port.ComplianceStore;
import com.example.smartcanteen.domain.AlertReport;
import com.example.smartcanteen.domain.AlertSource;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.ComplianceRecord;
import com.example.smartcanteen.domain.ComplianceRecordHistory;
import com.example.smartcanteen.domain.ComplianceRecordStatus;
import com.example.smartcanteen.domain.PageResult;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns compliance state transitions and turns expiry facts into AlertCenter records. */
@Service
public class ComplianceRecordService {

    private final ComplianceStore store;
    private final AlertCenter alerts;

    public ComplianceRecordService(ComplianceStore store, AlertCenter alerts) {
        this.store = store;
        this.alerts = alerts;
    }

    @Transactional(readOnly = true)
    public PageResult<ComplianceRecord> list(
            CanteenScope scope,
            String category,
            String status,
            Integer expiringWithinDays,
            int page,
            int size) {
        LocalDate expiringBy = null;
        if (expiringWithinDays != null) {
            if (expiringWithinDays < 0 || expiringWithinDays > 3650) {
                throw new IllegalArgumentException("expiringWithinDays must be between 0 and 3650");
            }
            expiringBy = LocalDate.now().plusDays(expiringWithinDays);
        }
        return store.list(scope, category, status, expiringBy, page, size);
    }

    @Transactional(readOnly = true)
    public Optional<ComplianceRecord> find(CanteenScope scope, String recordId) {
        return store.find(scope, recordId);
    }

    @Transactional
    public ComplianceRecord create(CanteenScope scope, ComplianceRecord record, String actorId) {
        if (record.status() != ComplianceRecordStatus.DRAFT) {
            throw new IllegalArgumentException("A new compliance record must start as DRAFT");
        }
        return store.create(scope, record, actor(actorId));
    }

    @Transactional
    public ComplianceRecord update(CanteenScope scope, ComplianceRecord record, String actorId) {
        ComplianceRecord current = current(scope, record.id());
        if (current.status() != ComplianceRecordStatus.DRAFT
                && current.status() != ComplianceRecordStatus.REJECTED) {
            throw new IllegalArgumentException("Only DRAFT or REJECTED compliance records can be edited");
        }
        if (record.status() != current.status()) {
            throw new IllegalArgumentException("Record status cannot be changed by update");
        }
        return store.update(scope, record, actor(actorId));
    }

    @Transactional
    public ComplianceRecord submit(
            CanteenScope scope, String recordId, long expectedVersion, String actorId) {
        ComplianceRecord current = current(scope, recordId);
        if (current.status() != ComplianceRecordStatus.DRAFT
                && current.status() != ComplianceRecordStatus.REJECTED) {
            throw new IllegalArgumentException("Only DRAFT or REJECTED compliance records can be submitted");
        }
        return store.transition(
                scope,
                recordId,
                expectedVersion,
                ComplianceRecordStatus.SUBMITTED,
                current.reviewRemark(),
                actor(actorId));
    }

    @Transactional
    public ComplianceRecord review(
            CanteenScope scope,
            String recordId,
            long expectedVersion,
            ComplianceRecordStatus targetStatus,
            String reviewRemark,
            String actorId) {
        if (targetStatus != ComplianceRecordStatus.APPROVED
                && targetStatus != ComplianceRecordStatus.REJECTED) {
            throw new IllegalArgumentException("Review target must be APPROVED or REJECTED");
        }
        ComplianceRecord current = current(scope, recordId);
        if (current.status() != ComplianceRecordStatus.SUBMITTED) {
            throw new IllegalArgumentException("Only SUBMITTED compliance records can be reviewed");
        }
        if (reviewRemark == null || reviewRemark.isBlank()) {
            throw new IllegalArgumentException("reviewRemark is required");
        }
        return store.transition(
                scope,
                recordId,
                expectedVersion,
                targetStatus,
                reviewRemark,
                actor(actorId));
    }

    @Transactional(readOnly = true)
    public List<ComplianceRecordHistory> history(CanteenScope scope, String recordId) {
        current(scope, recordId);
        return store.history(scope, recordId);
    }

    @Transactional
    public List<com.example.smartcanteen.domain.AlertRecord> scanExpiry(
            CanteenScope scope, LocalDate asOf, int windowDays) {
        LocalDate date = asOf == null ? LocalDate.now() : asOf;
        if (windowDays < 0 || windowDays > 3650) {
            throw new IllegalArgumentException("windowDays must be between 0 and 3650");
        }
        return store.expiring(scope, date, date.plusDays(windowDays)).stream()
                .filter(record -> record.status() == ComplianceRecordStatus.APPROVED)
                .map(record -> alerts.report(toAlert(scope, record)))
                .toList();
    }

    /** Daily safety net; the manual endpoint uses the same idempotent path for deterministic tests. */
    @Scheduled(cron = "${smart-canteen.compliance.expiry-scan-cron:0 0 2 * * *}")
    @Transactional
    public void scanAllScopes() {
        LocalDate today = LocalDate.now();
        for (CanteenScope scope : store.listScopes()) {
            scanExpiry(scope, today, 30);
        }
    }

    private AlertReport toAlert(CanteenScope scope, ComplianceRecord record) {
        String thirdWarnId = "COMPLIANCE-EXPIRY-" + record.id() + "-" + record.validTo();
        String attachment = record.attachmentRefs().isEmpty()
                ? null
                : record.attachmentRefs().get(0);
        return new AlertReport(
                AlertSource.COMPLIANCE,
                thirdWarnId,
                scope.schoolId(),
                null,
                null,
                null,
                null,
                record.validTo().atStartOfDay(ZoneOffset.UTC).toInstant(),
                "COMPLIANCE_EXPIRY",
                attachment,
                "合规档案到期：" + record.title() + "（" + record.validTo() + "）",
                scope.canteenId());
    }

    private ComplianceRecord current(CanteenScope scope, String recordId) {
        return store.find(scope, recordId)
                .orElseThrow(() -> new IllegalArgumentException("Compliance record not found: " + recordId));
    }

    private static String actor(String actorId) {
        return actorId == null || actorId.isBlank() ? "SYSTEM" : actorId.trim();
    }
}
