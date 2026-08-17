package com.example.smartcanteen.http;

import com.example.smartcanteen.application.GovernanceService;
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
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.ScopeAccess;
import com.example.smartcanteen.security.Role;
import com.example.smartcanteen.security.RoleAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GovernanceController {

    private final GovernanceService service;
    private final ScopeAccess scopes;
    private final RoleAccess roles;

    public GovernanceController(
            GovernanceService service,
            ScopeAccess scopes,
            RoleAccess roles) {
        this.service = service;
        this.scopes = scopes;
        this.roles = roles;
    }

    @GetMapping("/canteen-showcases")
    public ApiResponse<OperationalController.PageView<CanteenShowcase>> listShowcases(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        roles.requireReader(request);
        return ApiResponse.ok(OperationalController.PageView.from(service.listShowcases(
                scopes.require(request, schoolId, canteenId), status, page, size)));
    }

    @GetMapping("/canteen-showcases/{showcaseId}")
    public ApiResponse<CanteenShowcase> findShowcase(
            HttpServletRequest request,
            @PathVariable String showcaseId,
            @RequestParam String schoolId,
            @RequestParam String canteenId) {
        roles.requireReader(request);
        return ApiResponse.ok(service.findShowcase(scopes.require(request, schoolId, canteenId), showcaseId)
                .orElseThrow(() -> new IllegalArgumentException("Showcase not found: " + showcaseId)));
    }

    @PostMapping("/canteen-showcases")
    public ApiResponse<CanteenShowcase> createShowcase(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody ShowcaseRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(service.createShowcase(
                scopes.require(request, schoolId, canteenId),
                body.toDomain(body.showcaseId(), 0, CanteenShowcaseStatus.DRAFT, null),
                actor(request)));
    }

    @PutMapping("/canteen-showcases/{showcaseId}")
    public ApiResponse<CanteenShowcase> updateShowcase(
            HttpServletRequest request,
            @PathVariable String showcaseId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody ShowcaseRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(service.updateShowcase(
                scopes.require(request, schoolId, canteenId),
                body.toDomain(showcaseId, body.version(), body.status()),
                actor(request)));
    }

    @PostMapping("/canteen-showcases/{showcaseId}/submit")
    public ApiResponse<CanteenShowcase> submitShowcase(
            HttpServletRequest request,
            @PathVariable String showcaseId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody VersionRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(service.submitShowcase(
                scopes.require(request, schoolId, canteenId), showcaseId, body.version(), actor(request)));
    }

    @PostMapping("/canteen-showcases/{showcaseId}/review")
    public ApiResponse<CanteenShowcase> reviewShowcase(
            HttpServletRequest request,
            @PathVariable String showcaseId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody ReviewRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN);
        return ApiResponse.ok(service.reviewShowcase(
                scopes.require(request, schoolId, canteenId), showcaseId, body.version(),
                body.status(), body.reviewRemark(), actor(request)));
    }

    @PostMapping("/canteen-showcases/{showcaseId}/publish")
    public ApiResponse<CanteenShowcase> publishShowcase(
            HttpServletRequest request,
            @PathVariable String showcaseId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody VersionRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN);
        return ApiResponse.ok(service.publishShowcase(
                scopes.require(request, schoolId, canteenId), showcaseId, body.version(), actor(request)));
    }

    @PostMapping("/canteen-showcases/{showcaseId}/revoke")
    public ApiResponse<CanteenShowcase> revokeShowcase(
            HttpServletRequest request,
            @PathVariable String showcaseId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody VersionRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN);
        return ApiResponse.ok(service.revokeShowcase(
                scopes.require(request, schoolId, canteenId), showcaseId, body.version(), actor(request)));
    }

    @PostMapping("/canteen-showcases/{showcaseId}/versions")
    public ApiResponse<CanteenShowcase> createShowcaseVersion(
            HttpServletRequest request,
            @PathVariable String showcaseId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody ShowcaseRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        String versionId = body.showcaseId() == null || body.showcaseId().isBlank()
                ? "SHOWCASE-" + UUID.randomUUID() : body.showcaseId();
        return ApiResponse.ok(service.createShowcaseVersion(
                scopes.require(request, schoolId, canteenId), showcaseId,
                body.toDomain(versionId, 0, CanteenShowcaseStatus.DRAFT, showcaseId), actor(request)));
    }

    @GetMapping("/canteen-showcases/{showcaseId}/history")
    public ApiResponse<List<GovernanceHistory>> showcaseHistory(
            HttpServletRequest request,
            @PathVariable String showcaseId,
            @RequestParam String schoolId,
            @RequestParam String canteenId) {
        roles.requireReader(request);
        return ApiResponse.ok(service.showcaseHistory(
                scopes.require(request, schoolId, canteenId), showcaseId));
    }

    @GetMapping("/meal-suspensions")
    public ApiResponse<OperationalController.PageView<MealSuspension>> listMealSuspensions(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        roles.requireReader(request);
        return ApiResponse.ok(OperationalController.PageView.from(service.listMealSuspensions(
                scopes.require(request, schoolId, canteenId), from, to, status, page, size)));
    }

    @PostMapping("/meal-suspensions")
    public ApiResponse<MealSuspension> createMealSuspension(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody MealSuspensionRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(service.createMealSuspension(
                scopes.require(request, schoolId, canteenId),
                body.toDomain(body.suspensionId()), actor(request)));
    }

    @PostMapping("/meal-suspensions/{suspensionId}/review")
    public ApiResponse<MealSuspension> reviewMealSuspension(
            HttpServletRequest request,
            @PathVariable String suspensionId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody MealReviewRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN);
        return ApiResponse.ok(service.reviewMealSuspension(
                scopes.require(request, schoolId, canteenId), suspensionId, body.version(),
                body.status(), body.reviewRemark(), actor(request)));
    }

    @PostMapping("/meal-suspensions/{suspensionId}/cancel")
    public ApiResponse<MealSuspension> cancelMealSuspension(
            HttpServletRequest request,
            @PathVariable String suspensionId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody VersionRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(service.cancelMealSuspension(
                scopes.require(request, schoolId, canteenId), suspensionId, body.version(), actor(request)));
    }

    @GetMapping("/meal-suspensions/stats")
    public ApiResponse<Map<String, Long>> mealSuspensionStats(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to) {
        roles.requireReader(request);
        return ApiResponse.ok(service.mealSuspensionStats(
                scopes.require(request, schoolId, canteenId), from, to));
    }

    @GetMapping("/meal-suspensions/{suspensionId}/history")
    public ApiResponse<List<GovernanceHistory>> mealSuspensionHistory(
            HttpServletRequest request,
            @PathVariable String suspensionId,
            @RequestParam String schoolId,
            @RequestParam String canteenId) {
        roles.requireReader(request);
        return ApiResponse.ok(service.mealSuspensionHistory(
                scopes.require(request, schoolId, canteenId), suspensionId));
    }

    @GetMapping("/supplier-complaints")
    public ApiResponse<OperationalController.PageView<SupplierComplaint>> listComplaints(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String supplierId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        roles.requireReader(request);
        return ApiResponse.ok(OperationalController.PageView.from(service.listComplaints(
                scopes.require(request, schoolId, canteenId), status, supplierId, page, size)));
    }

    @GetMapping("/supplier-complaints/{complaintId}")
    public ApiResponse<SupplierComplaint> findComplaint(
            HttpServletRequest request,
            @PathVariable String complaintId,
            @RequestParam String schoolId,
            @RequestParam String canteenId) {
        roles.requireReader(request);
        return ApiResponse.ok(service.findComplaint(scopes.require(request, schoolId, canteenId), complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Supplier complaint not found: " + complaintId)));
    }

    @PostMapping("/supplier-complaints")
    public ApiResponse<SupplierComplaint> createComplaint(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody ComplaintRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(service.createComplaint(
                scopes.require(request, schoolId, canteenId), body.toDomain(body.complaintId(), actor(request)),
                actor(request)));
    }

    @PostMapping("/supplier-complaints/{complaintId}/review")
    public ApiResponse<SupplierComplaint> reviewComplaint(
            HttpServletRequest request,
            @PathVariable String complaintId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody ComplaintReviewRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN);
        return ApiResponse.ok(service.reviewComplaint(
                scopes.require(request, schoolId, canteenId), complaintId, body.version(),
                body.status(), body.note(), actor(request)));
    }

    @PostMapping("/supplier-complaints/{complaintId}/process")
    public ApiResponse<SupplierComplaint> processComplaint(
            HttpServletRequest request,
            @PathVariable String complaintId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody VersionRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN);
        return ApiResponse.ok(service.processComplaint(
                scopes.require(request, schoolId, canteenId), complaintId, body.version(), actor(request)));
    }

    @PostMapping("/supplier-complaints/{complaintId}/reply")
    public ApiResponse<SupplierComplaint> replyComplaint(
            HttpServletRequest request,
            @PathVariable String complaintId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody ComplaintReplyRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN);
        return ApiResponse.ok(service.replyComplaint(
                scopes.require(request, schoolId, canteenId), complaintId, body.version(),
                body.reply(), actor(request)));
    }

    @PostMapping("/supplier-complaints/{complaintId}/close")
    public ApiResponse<SupplierComplaint> closeComplaint(
            HttpServletRequest request,
            @PathVariable String complaintId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody VersionRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN);
        return ApiResponse.ok(service.closeComplaint(
                scopes.require(request, schoolId, canteenId), complaintId, body.version(), actor(request)));
    }

    @GetMapping("/supplier-complaints/{complaintId}/history")
    public ApiResponse<List<GovernanceHistory>> complaintHistory(
            HttpServletRequest request,
            @PathVariable String complaintId,
            @RequestParam String schoolId,
            @RequestParam String canteenId) {
        roles.requireReader(request);
        return ApiResponse.ok(service.complaintHistory(
                scopes.require(request, schoolId, canteenId), complaintId));
    }

    private static String actor(HttpServletRequest request) {
        Object value = request.getAttribute(AuthPrincipal.class.getName());
        return value instanceof AuthPrincipal principal ? principal.userId() : "SYSTEM";
    }

    public record VersionRequest(long version) {
    }

    public record ReviewRequest(
            long version,
            @NotNull CanteenShowcaseStatus status,
            @NotBlank String reviewRemark) {
    }

    public record ShowcaseRequest(
            String showcaseId,
            @NotBlank String title,
            @NotBlank String content,
            List<String> photos,
            CanteenShowcaseStatus status,
            String previousVersionId,
            long version) {

        CanteenShowcase toDomain(String id, long expectedVersion) {
            return toDomain(id, expectedVersion, status, previousVersionId);
        }

        CanteenShowcase toDomain(
                String id,
                long expectedVersion,
                CanteenShowcaseStatus fallbackStatus) {
            return toDomain(id, expectedVersion, fallbackStatus, previousVersionId);
        }

        CanteenShowcase toDomain(
                String id,
                long expectedVersion,
                CanteenShowcaseStatus fallbackStatus,
                String previousId) {
            return new CanteenShowcase(
                    id == null || id.isBlank() ? "SHOWCASE-" + UUID.randomUUID() : id,
                    title,
                    content,
                    photos,
                    status == null ? fallbackStatus : status,
                    previousId,
                    expectedVersion,
                    Instant.EPOCH,
                    Instant.EPOCH,
                    null,
                    null,
                    null,
                    null);
        }
    }

    public record MealSuspensionRequest(
            String suspensionId,
            @NotNull LocalDate mealDate,
            @NotNull MealPeriod mealPeriod,
            @NotBlank String reason) {

        MealSuspension toDomain(String id) {
            return new MealSuspension(
                    id == null || id.isBlank() ? "SUSPENSION-" + UUID.randomUUID() : id,
                    mealDate,
                    mealPeriod,
                    reason,
                    MealSuspensionStatus.SUBMITTED,
                    null,
                    0,
                    Instant.EPOCH,
                    Instant.EPOCH,
                    null,
                    null);
        }
    }

    public record MealReviewRequest(
            long version,
            @NotNull MealSuspensionStatus status,
            @NotBlank String reviewRemark) {
    }

    public record ComplaintRequest(
            String complaintId,
            @NotBlank String supplierId,
            @NotBlank String subject,
            @NotBlank String description,
            List<String> attachmentRefs,
            LocalDate deadline) {

        SupplierComplaint toDomain(String id, String actorId) {
            return new SupplierComplaint(
                    id == null || id.isBlank() ? "COMPLAINT-" + UUID.randomUUID() : id,
                    supplierId,
                    subject,
                    description,
                    attachmentRefs,
                    deadline,
                    SupplierComplaintStatus.SUBMITTED,
                    null,
                    0,
                    actorId,
                    null,
                    Instant.EPOCH,
                    Instant.EPOCH,
                    null,
                    null);
        }
    }

    public record ComplaintReviewRequest(
            long version,
            @NotNull SupplierComplaintStatus status,
            String note) {
    }

    public record ComplaintReplyRequest(long version, @NotBlank String reply) {
    }
}
