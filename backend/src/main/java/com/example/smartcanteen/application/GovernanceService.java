package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.GovernanceStore;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** State-machine facade for content publishing, meal suspension and complaints. */
@Service
public class GovernanceService {

    public static final String SHOWCASE = "SHOWCASE";
    public static final String MEAL_SUSPENSION = "MEAL_SUSPENSION";
    public static final String SUPPLIER_COMPLAINT = "SUPPLIER_COMPLAINT";

    private final GovernanceStore store;

    public GovernanceService(GovernanceStore store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public PageResult<CanteenShowcase> listShowcases(
            CanteenScope scope, String status, int page, int size) {
        return store.listShowcases(scope, status, page, size);
    }

    @Transactional(readOnly = true)
    public Optional<CanteenShowcase> findShowcase(CanteenScope scope, String id) {
        return store.findShowcase(scope, id);
    }

    @Transactional
    public CanteenShowcase createShowcase(
            CanteenScope scope, CanteenShowcase showcase, String actorId) {
        if (showcase.status() != CanteenShowcaseStatus.DRAFT) {
            throw new IllegalArgumentException("A new showcase must start as DRAFT");
        }
        return store.createShowcase(scope, showcase, actor(actorId));
    }

    @Transactional
    public CanteenShowcase updateShowcase(
            CanteenScope scope, CanteenShowcase showcase, String actorId) {
        CanteenShowcase current = currentShowcase(scope, showcase.id());
        if (current.status() == CanteenShowcaseStatus.PUBLISHED
                || current.status() == CanteenShowcaseStatus.REVOKED) {
            throw new IllegalArgumentException("Published showcase content is immutable; create a new version");
        }
        if (current.status() != CanteenShowcaseStatus.DRAFT
                && current.status() != CanteenShowcaseStatus.REJECTED) {
            throw new IllegalArgumentException("Only DRAFT or REJECTED showcases can be edited");
        }
        if (showcase.status() != current.status()) {
            throw new IllegalArgumentException("Showcase status cannot be changed by update");
        }
        return store.updateShowcase(scope, showcase, actor(actorId));
    }

    @Transactional
    public CanteenShowcase submitShowcase(
            CanteenScope scope, String id, long expectedVersion, String actorId) {
        CanteenShowcase current = currentShowcase(scope, id);
        requireOneOf(current.status(), "Only DRAFT or REJECTED showcases can be submitted",
                CanteenShowcaseStatus.DRAFT, CanteenShowcaseStatus.REJECTED);
        return store.transitionShowcase(
                scope, id, expectedVersion, CanteenShowcaseStatus.SUBMITTED,
                current.reviewRemark(), actor(actorId));
    }

    @Transactional
    public CanteenShowcase reviewShowcase(
            CanteenScope scope,
            String id,
            long expectedVersion,
            CanteenShowcaseStatus targetStatus,
            String reviewRemark,
            String actorId) {
        if (targetStatus != CanteenShowcaseStatus.APPROVED
                && targetStatus != CanteenShowcaseStatus.REJECTED) {
            throw new IllegalArgumentException("Showcase review target must be APPROVED or REJECTED");
        }
        requireOneOf(currentShowcase(scope, id).status(),
                "Only SUBMITTED showcases can be reviewed", CanteenShowcaseStatus.SUBMITTED);
        requireText(reviewRemark, "reviewRemark");
        return store.transitionShowcase(
                scope, id, expectedVersion, targetStatus, reviewRemark, actor(actorId));
    }

    @Transactional
    public CanteenShowcase publishShowcase(
            CanteenScope scope, String id, long expectedVersion, String actorId) {
        requireOneOf(currentShowcase(scope, id).status(),
                "Only APPROVED showcases can be published", CanteenShowcaseStatus.APPROVED);
        return store.transitionShowcase(
                scope, id, expectedVersion, CanteenShowcaseStatus.PUBLISHED, null, actor(actorId));
    }

    @Transactional
    public CanteenShowcase revokeShowcase(
            CanteenScope scope, String id, long expectedVersion, String actorId) {
        requireOneOf(currentShowcase(scope, id).status(),
                "Only PUBLISHED showcases can be revoked", CanteenShowcaseStatus.PUBLISHED);
        return store.transitionShowcase(
                scope, id, expectedVersion, CanteenShowcaseStatus.REVOKED, null, actor(actorId));
    }

    @Transactional
    public CanteenShowcase createShowcaseVersion(
            CanteenScope scope, String previousId, CanteenShowcase draft, String actorId) {
        CanteenShowcase current = currentShowcase(scope, previousId);
        requireOneOf(current.status(), "Only published showcase content can be versioned",
                CanteenShowcaseStatus.PUBLISHED, CanteenShowcaseStatus.REVOKED);
        if (!previousId.equals(draft.previousVersionId())
                || draft.status() != CanteenShowcaseStatus.DRAFT) {
            throw new IllegalArgumentException("New showcase version must reference the previous published version");
        }
        return store.createShowcase(scope, draft, actor(actorId));
    }

    @Transactional(readOnly = true)
    public List<GovernanceHistory> showcaseHistory(CanteenScope scope, String id) {
        currentShowcase(scope, id);
        return store.history(scope, SHOWCASE, id);
    }

    @Transactional(readOnly = true)
    public PageResult<MealSuspension> listMealSuspensions(
            CanteenScope scope, LocalDate from, LocalDate to, String status, int page, int size) {
        return store.listMealSuspensions(scope, from, to, status, page, size);
    }

    @Transactional
    public MealSuspension createMealSuspension(
            CanteenScope scope, MealSuspension suspension, String actorId) {
        if (suspension.status() != MealSuspensionStatus.SUBMITTED) {
            throw new IllegalArgumentException("A new meal suspension must start as SUBMITTED");
        }
        return store.createMealSuspension(scope, suspension, actor(actorId));
    }

    @Transactional
    public MealSuspension reviewMealSuspension(
            CanteenScope scope,
            String id,
            long expectedVersion,
            MealSuspensionStatus targetStatus,
            String reviewRemark,
            String actorId) {
        if (targetStatus != MealSuspensionStatus.APPROVED
                && targetStatus != MealSuspensionStatus.REJECTED) {
            throw new IllegalArgumentException("Meal suspension review target must be APPROVED or REJECTED");
        }
        requireOneOf(currentMealSuspension(scope, id).status(),
                "Only SUBMITTED meal suspensions can be reviewed", MealSuspensionStatus.SUBMITTED);
        requireText(reviewRemark, "reviewRemark");
        return store.transitionMealSuspension(
                scope, id, expectedVersion, targetStatus, reviewRemark, actor(actorId));
    }

    @Transactional
    public MealSuspension cancelMealSuspension(
            CanteenScope scope, String id, long expectedVersion, String actorId) {
        requireOneOf(currentMealSuspension(scope, id).status(),
                "Only SUBMITTED or APPROVED meal suspensions can be cancelled",
                MealSuspensionStatus.SUBMITTED, MealSuspensionStatus.APPROVED);
        return store.transitionMealSuspension(
                scope, id, expectedVersion, MealSuspensionStatus.CANCELLED, null, actor(actorId));
    }

    @Transactional(readOnly = true)
    public Map<String, Long> mealSuspensionStats(CanteenScope scope, LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new IllegalArgumentException("to cannot be before from");
        }
        return store.mealSuspensionStats(scope, from, to);
    }

    @Transactional
    public SupplierComplaint createComplaint(
            CanteenScope scope, SupplierComplaint complaint, String actorId) {
        if (complaint.status() != SupplierComplaintStatus.SUBMITTED) {
            throw new IllegalArgumentException("A new supplier complaint must start as SUBMITTED");
        }
        return store.createComplaint(scope, complaint, actor(actorId));
    }

    @Transactional
    public SupplierComplaint reviewComplaint(
            CanteenScope scope,
            String id,
            long expectedVersion,
            SupplierComplaintStatus targetStatus,
            String reply,
            String actorId) {
        if (targetStatus != SupplierComplaintStatus.ACCEPTED
                && targetStatus != SupplierComplaintStatus.REJECTED) {
            throw new IllegalArgumentException("Complaint review target must be ACCEPTED or REJECTED");
        }
        requireOneOf(currentComplaint(scope, id).status(),
                "Only SUBMITTED complaints can be reviewed", SupplierComplaintStatus.SUBMITTED);
        return store.transitionComplaint(
                scope, id, expectedVersion, targetStatus, reply, actor(actorId));
    }

    @Transactional
    public SupplierComplaint processComplaint(
            CanteenScope scope, String id, long expectedVersion, String actorId) {
        requireOneOf(currentComplaint(scope, id).status(),
                "Only ACCEPTED complaints can enter processing", SupplierComplaintStatus.ACCEPTED);
        return store.transitionComplaint(
                scope, id, expectedVersion, SupplierComplaintStatus.PROCESSING, null, actor(actorId));
    }

    @Transactional
    public SupplierComplaint replyComplaint(
            CanteenScope scope, String id, long expectedVersion, String reply, String actorId) {
        requireOneOf(currentComplaint(scope, id).status(),
                "Only PROCESSING complaints can be replied to", SupplierComplaintStatus.PROCESSING);
        requireText(reply, "reply");
        return store.transitionComplaint(
                scope, id, expectedVersion, SupplierComplaintStatus.REPLIED, reply, actor(actorId));
    }

    @Transactional
    public SupplierComplaint closeComplaint(
            CanteenScope scope, String id, long expectedVersion, String actorId) {
        SupplierComplaint current = currentComplaint(scope, id);
        requireOneOf(current.status(),
                "Only REPLIED complaints can be closed", SupplierComplaintStatus.REPLIED);
        return store.transitionComplaint(
                scope, id, expectedVersion, SupplierComplaintStatus.CLOSED,
                current.reply(), actor(actorId));
    }

    @Transactional(readOnly = true)
    public List<GovernanceHistory> complaintHistory(CanteenScope scope, String id) {
        currentComplaint(scope, id);
        return store.history(scope, SUPPLIER_COMPLAINT, id);
    }

    @Transactional(readOnly = true)
    public List<GovernanceHistory> mealSuspensionHistory(CanteenScope scope, String id) {
        currentMealSuspension(scope, id);
        return store.history(scope, MEAL_SUSPENSION, id);
    }

    @Transactional(readOnly = true)
    public PageResult<SupplierComplaint> listComplaints(
            CanteenScope scope, String status, String supplierId, int page, int size) {
        return store.listComplaints(scope, status, supplierId, page, size);
    }

    @Transactional(readOnly = true)
    public Optional<SupplierComplaint> findComplaint(CanteenScope scope, String id) {
        return store.findComplaint(scope, id);
    }

    private CanteenShowcase currentShowcase(CanteenScope scope, String id) {
        return store.findShowcase(scope, id)
                .orElseThrow(() -> new IllegalArgumentException("Showcase not found: " + id));
    }

    private MealSuspension currentMealSuspension(CanteenScope scope, String id) {
        return store.findMealSuspension(scope, id)
                .orElseThrow(() -> new IllegalArgumentException("Meal suspension not found: " + id));
    }

    private SupplierComplaint currentComplaint(CanteenScope scope, String id) {
        return store.findComplaint(scope, id)
                .orElseThrow(() -> new IllegalArgumentException("Supplier complaint not found: " + id));
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static <T> void requireOneOf(T actual, String message, T... allowed) {
        for (T value : allowed) {
            if (value == actual) {
                return;
            }
        }
        throw new IllegalArgumentException(message);
    }

    private static String actor(String actorId) {
        return actorId == null || actorId.isBlank() ? "SYSTEM" : actorId.trim();
    }
}
