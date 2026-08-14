package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.application.port.AlertCenter;
import com.example.smartcanteen.application.port.ComplianceStore;
import com.example.smartcanteen.domain.AlertRecord;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.ComplianceCategory;
import com.example.smartcanteen.domain.ComplianceRecord;
import com.example.smartcanteen.domain.ComplianceRecordStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ComplianceRecordServiceTest {

    private static final CanteenScope SCOPE = new CanteenScope("SCHOOL-P3", "CANTEEN-P3");

    @Test
    void only_submitted_records_can_be_reviewed_and_each_transition_is_versioned() {
        ComplianceStore store = mock(ComplianceStore.class);
        AlertCenter alerts = mock(AlertCenter.class);
        ComplianceRecordService service = new ComplianceRecordService(store, alerts);
        ComplianceRecord draft = record(ComplianceRecordStatus.DRAFT, 0);
        ComplianceRecord submitted = record(ComplianceRecordStatus.SUBMITTED, 1);
        ComplianceRecord approved = record(ComplianceRecordStatus.APPROVED, 2);
        when(store.find(SCOPE, draft.id())).thenReturn(Optional.of(draft), Optional.of(submitted));
        when(store.transition(eq(SCOPE), eq(draft.id()), eq(0L), eq(ComplianceRecordStatus.SUBMITTED),
                any(), eq("STAFF-P3"))).thenReturn(submitted);
        when(store.transition(eq(SCOPE), eq(draft.id()), eq(1L), eq(ComplianceRecordStatus.APPROVED),
                eq("已核验"), eq("ADMIN-P3"))).thenReturn(approved);

        Assertions.assertThat(service.submit(SCOPE, draft.id(), 0, "STAFF-P3")).isEqualTo(submitted);
        Assertions.assertThat(service.review(
                SCOPE, draft.id(), 1, ComplianceRecordStatus.APPROVED, "已核验", "ADMIN-P3"))
                .isEqualTo(approved);
        verify(store).transition(eq(SCOPE), eq(draft.id()), eq(0L), eq(ComplianceRecordStatus.SUBMITTED),
                any(), eq("STAFF-P3"));
        verify(store).transition(eq(SCOPE), eq(draft.id()), eq(1L), eq(ComplianceRecordStatus.APPROVED),
                eq("已核验"), eq("ADMIN-P3"));
    }

    @Test
    void expiry_scan_reports_a_stable_alert_for_each_approved_record() {
        ComplianceStore store = mock(ComplianceStore.class);
        AlertCenter alerts = mock(AlertCenter.class);
        ComplianceRecordService service = new ComplianceRecordService(store, alerts);
        ComplianceRecord expiring = record(ComplianceRecordStatus.APPROVED, 2);
        AlertRecord alert = mock(AlertRecord.class);
        when(store.expiring(SCOPE, LocalDate.of(2026, 8, 14), LocalDate.of(2026, 9, 13)))
                .thenReturn(List.of(expiring));
        when(alerts.report(any())).thenReturn(alert);

        Assertions.assertThat(service.scanExpiry(
                SCOPE, LocalDate.of(2026, 8, 14), 30)).containsExactly(alert);
        ArgumentCaptor<com.example.smartcanteen.domain.AlertReport> report =
                ArgumentCaptor.forClass(com.example.smartcanteen.domain.AlertReport.class);
        verify(alerts).report(report.capture());
        Assertions.assertThat(report.getValue().source().name()).isEqualTo("COMPLIANCE");
        Assertions.assertThat(report.getValue().thirdWarnId()).contains(expiring.id(), "2026-08-20");
    }

    @Test
    void draft_cannot_be_reviewed_directly() {
        ComplianceStore store = mock(ComplianceStore.class);
        ComplianceRecordService service = new ComplianceRecordService(store, mock(AlertCenter.class));
        ComplianceRecord draft = record(ComplianceRecordStatus.DRAFT, 0);
        when(store.find(SCOPE, draft.id())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.review(
                SCOPE, draft.id(), 0, ComplianceRecordStatus.APPROVED, "", "ADMIN-P3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUBMITTED");
    }

    private static ComplianceRecord record(ComplianceRecordStatus status, long version) {
        return new ComplianceRecord(
                "COMPLIANCE-P3-001",
                ComplianceCategory.LICENSE,
                "CANTEEN",
                "CANTEEN-P3",
                "阶段3食堂",
                "食品经营许可证",
                "LIC-P3-001",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 8, 20),
                List.of("https://example.test/license.pdf"),
                status,
                status == ComplianceRecordStatus.REJECTED ? "补充材料" : null,
                version,
                Instant.EPOCH,
                Instant.EPOCH,
                null,
                null,
                null);
    }
}
