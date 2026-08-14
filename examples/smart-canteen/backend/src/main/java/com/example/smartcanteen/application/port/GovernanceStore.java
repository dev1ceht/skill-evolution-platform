package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.CanteenShowcase;
import com.example.smartcanteen.domain.CanteenShowcaseStatus;
import com.example.smartcanteen.domain.GovernanceHistory;
import com.example.smartcanteen.domain.MealSuspension;
import com.example.smartcanteen.domain.MealSuspensionStatus;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.domain.SupplierComplaint;
import com.example.smartcanteen.domain.SupplierComplaintStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persistence seam for the small, auditable school-operation workflows. */
public interface GovernanceStore {

    PageResult<CanteenShowcase> listShowcases(CanteenScope scope, String status, int page, int size);

    Optional<CanteenShowcase> findShowcase(CanteenScope scope, String showcaseId);

    CanteenShowcase createShowcase(CanteenScope scope, CanteenShowcase showcase, String actorId);

    CanteenShowcase updateShowcase(CanteenScope scope, CanteenShowcase showcase, String actorId);

    CanteenShowcase transitionShowcase(
            CanteenScope scope,
            String showcaseId,
            long expectedVersion,
            CanteenShowcaseStatus status,
            String reviewRemark,
            String actorId);

    PageResult<MealSuspension> listMealSuspensions(
            CanteenScope scope, LocalDate from, LocalDate to, String status, int page, int size);

    Optional<MealSuspension> findMealSuspension(CanteenScope scope, String suspensionId);

    MealSuspension createMealSuspension(CanteenScope scope, MealSuspension suspension, String actorId);

    MealSuspension transitionMealSuspension(
            CanteenScope scope,
            String suspensionId,
            long expectedVersion,
            MealSuspensionStatus status,
            String reviewRemark,
            String actorId);

    Map<String, Long> mealSuspensionStats(CanteenScope scope, LocalDate from, LocalDate to);

    PageResult<SupplierComplaint> listComplaints(
            CanteenScope scope, String status, String supplierId, int page, int size);

    Optional<SupplierComplaint> findComplaint(CanteenScope scope, String complaintId);

    SupplierComplaint createComplaint(CanteenScope scope, SupplierComplaint complaint, String actorId);

    SupplierComplaint transitionComplaint(
            CanteenScope scope,
            String complaintId,
            long expectedVersion,
            SupplierComplaintStatus status,
            String reply,
            String actorId);

    List<GovernanceHistory> history(CanteenScope scope, String entityType, String entityId);
}
