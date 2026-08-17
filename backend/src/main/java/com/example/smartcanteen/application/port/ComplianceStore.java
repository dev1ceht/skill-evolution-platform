package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.ComplianceRecord;
import com.example.smartcanteen.domain.ComplianceRecordHistory;
import com.example.smartcanteen.domain.ComplianceRecordStatus;
import com.example.smartcanteen.domain.PageResult;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence seam for auditable compliance records and expiry candidates. */
public interface ComplianceStore {

    PageResult<ComplianceRecord> list(
            CanteenScope scope,
            String category,
            String status,
            LocalDate expiringBy,
            int page,
            int size);

    Optional<ComplianceRecord> find(CanteenScope scope, String recordId);

    ComplianceRecord create(CanteenScope scope, ComplianceRecord record, String actorId);

    ComplianceRecord update(CanteenScope scope, ComplianceRecord record, String actorId);

    ComplianceRecord transition(
            CanteenScope scope,
            String recordId,
            long expectedVersion,
            ComplianceRecordStatus status,
            String reviewRemark,
            String actorId);

    List<ComplianceRecordHistory> history(CanteenScope scope, String recordId);

    List<ComplianceRecord> expiring(CanteenScope scope, LocalDate asOf, LocalDate until);

    List<CanteenScope> listScopes();
}
