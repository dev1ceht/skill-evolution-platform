package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.application.port.GovernanceStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.CanteenShowcase;
import com.example.smartcanteen.domain.CanteenShowcaseStatus;
import com.example.smartcanteen.domain.MealPeriod;
import com.example.smartcanteen.domain.MealSuspension;
import com.example.smartcanteen.domain.MealSuspensionStatus;
import com.example.smartcanteen.domain.SupplierComplaint;
import com.example.smartcanteen.domain.SupplierComplaintStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class GovernanceServiceTest {

    private static final CanteenScope SCOPE = new CanteenScope("SCHOOL-P3", "CANTEEN-P3");

    @Test
    void published_showcase_is_immutable_and_supplier_complaint_follows_a_linear_workflow() {
        GovernanceStore store = mock(GovernanceStore.class);
        GovernanceService service = new GovernanceService(store);
        CanteenShowcase published = showcase(CanteenShowcaseStatus.PUBLISHED, 2);
        when(store.findShowcase(SCOPE, published.id())).thenReturn(Optional.of(published));

        assertThatThrownBy(() -> service.updateShowcase(SCOPE, published, "STAFF-P3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("immutable");

        SupplierComplaint submitted = complaint(SupplierComplaintStatus.SUBMITTED, 0, null);
        SupplierComplaint accepted = complaint(SupplierComplaintStatus.ACCEPTED, 1, null);
        SupplierComplaint processing = complaint(SupplierComplaintStatus.PROCESSING, 2, null);
        SupplierComplaint replied = complaint(SupplierComplaintStatus.REPLIED, 3, "已回复");
        SupplierComplaint closed = complaint(SupplierComplaintStatus.CLOSED, 4, "已回复");
        when(store.findComplaint(SCOPE, submitted.id()))
                .thenReturn(Optional.of(submitted), Optional.of(accepted), Optional.of(processing),
                        Optional.of(replied));
        when(store.transitionComplaint(eq(SCOPE), eq(submitted.id()), eq(0L),
                eq(SupplierComplaintStatus.ACCEPTED), any(), eq("ADMIN-P3"))).thenReturn(accepted);
        when(store.transitionComplaint(eq(SCOPE), eq(submitted.id()), eq(1L),
                eq(SupplierComplaintStatus.PROCESSING), any(), eq("ADMIN-P3"))).thenReturn(processing);
        when(store.transitionComplaint(eq(SCOPE), eq(submitted.id()), eq(2L),
                eq(SupplierComplaintStatus.REPLIED), eq("已回复"), eq("ADMIN-P3"))).thenReturn(replied);
        when(store.transitionComplaint(eq(SCOPE), eq(submitted.id()), eq(3L),
                eq(SupplierComplaintStatus.CLOSED), eq("已回复"), eq("ADMIN-P3"))).thenReturn(closed);

        Assertions.assertThat(service.reviewComplaint(
                SCOPE, submitted.id(), 0, SupplierComplaintStatus.ACCEPTED, null, "ADMIN-P3"))
                .isEqualTo(accepted);
        Assertions.assertThat(service.processComplaint(SCOPE, submitted.id(), 1, "ADMIN-P3"))
                .isEqualTo(processing);
        Assertions.assertThat(service.replyComplaint(
                SCOPE, submitted.id(), 2, "已回复", "ADMIN-P3"))
                .isEqualTo(replied);
        Assertions.assertThat(service.closeComplaint(SCOPE, submitted.id(), 3, "ADMIN-P3"))
                .isEqualTo(closed);
        verify(store).transitionComplaint(eq(SCOPE), eq(submitted.id()), eq(3L),
                eq(SupplierComplaintStatus.CLOSED), eq("已回复"), eq("ADMIN-P3"));
    }

    @Test
    void approved_meal_suspension_can_be_cancelled_with_optimistic_version() {
        GovernanceStore store = mock(GovernanceStore.class);
        GovernanceService service = new GovernanceService(store);
        MealSuspension approved = suspension(MealSuspensionStatus.APPROVED, 1);
        MealSuspension cancelled = suspension(MealSuspensionStatus.CANCELLED, 2);
        when(store.findMealSuspension(SCOPE, approved.id())).thenReturn(Optional.of(approved));
        when(store.transitionMealSuspension(
                SCOPE, approved.id(), 1, MealSuspensionStatus.CANCELLED, null, "ADMIN-P3"))
                .thenReturn(cancelled);

        Assertions.assertThat(service.cancelMealSuspension(
                SCOPE, approved.id(), 1, "ADMIN-P3")).isEqualTo(cancelled);
        verify(store).transitionMealSuspension(
                SCOPE, approved.id(), 1, MealSuspensionStatus.CANCELLED, null, "ADMIN-P3");
    }

    private static CanteenShowcase showcase(CanteenShowcaseStatus status, long version) {
        return new CanteenShowcase(
                "SHOWCASE-P3-001", "阶段3食堂风采", "公开展示内容", List.of(), status, null,
                version, Instant.EPOCH, Instant.EPOCH, "已发布", Instant.EPOCH, "ADMIN-P3", Instant.EPOCH);
    }

    private static SupplierComplaint complaint(
            SupplierComplaintStatus status, long version, String reply) {
        return new SupplierComplaint(
                "COMPLAINT-P3-001", "SUP-P3-001", "到货质量问题", "发现包装破损", List.of(),
                null, status, reply, version, "STAFF-P3", "ADMIN-P3", Instant.EPOCH, Instant.EPOCH,
                Instant.EPOCH, status == SupplierComplaintStatus.CLOSED ? Instant.EPOCH : null);
    }

    private static MealSuspension suspension(MealSuspensionStatus status, long version) {
        return new MealSuspension(
                "SUSPENSION-P3-001", java.time.LocalDate.of(2026, 8, 20), MealPeriod.LUNCH,
                "设备维护", status, null, version, Instant.EPOCH, Instant.EPOCH, null, null);
    }
}
