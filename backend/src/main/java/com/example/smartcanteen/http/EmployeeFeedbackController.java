package com.example.smartcanteen.http;

import com.example.smartcanteen.application.DinerComplaintService;
import com.example.smartcanteen.application.MealReviewService;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.DinerComplaint;
import com.example.smartcanteen.domain.MealReview;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
import com.example.smartcanteen.security.RoleAccess;
import com.example.smartcanteen.security.ScopeAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class EmployeeFeedbackController {

    private final MealReviewService reviews;
    private final DinerComplaintService complaints;
    private final ScopeAccess scopes;
    private final RoleAccess roles;

    public EmployeeFeedbackController(
            MealReviewService reviews,
            DinerComplaintService complaints,
            ScopeAccess scopes,
            RoleAccess roles) {
        this.reviews = reviews;
        this.complaints = complaints;
        this.scopes = scopes;
        this.roles = roles;
    }

    @GetMapping("/meal-reviews")
    public ApiResponse<OperationalController.PageView<MealReview>> listReviews(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireConsumerAccess(request);
        PageResult<MealReview> result = reviews.listMine(
                scopes.require(request, schoolId, canteenId), actorUserId(request), page, size);
        return ApiResponse.ok(OperationalController.PageView.from(result));
    }

    @PostMapping("/meal-reviews")
    public ApiResponse<MealReview> createReview(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody CreateMealReviewRequest body) {
        requireConsumerAccess(request);
        return ApiResponse.ok(reviews.create(
                scopes.require(request, schoolId, canteenId),
                actorUserId(request),
                body.orderId(),
                body.rating(),
                body.content(),
                idempotencyKey));
    }

    @GetMapping("/diner-complaints")
    public ApiResponse<OperationalController.PageView<DinerComplaint>> listComplaints(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireConsumerAccess(request);
        PageResult<DinerComplaint> result = complaints.listMine(
                scopes.require(request, schoolId, canteenId), actorUserId(request), status, page, size);
        return ApiResponse.ok(OperationalController.PageView.from(result));
    }

    @PostMapping("/diner-complaints")
    public ApiResponse<DinerComplaint> createComplaint(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody CreateDinerComplaintRequest body) {
        requireConsumerAccess(request);
        return ApiResponse.ok(complaints.create(
                scopes.require(request, schoolId, canteenId),
                actorUserId(request),
                body.category(),
                body.subject(),
                body.description(),
                body.relatedOrderId(),
                idempotencyKey));
    }

    private void requireConsumerAccess(HttpServletRequest request) {
        roles.requireAny(
                request,
                Role.DINER,
                Role.SYSTEM_ADMIN,
                Role.SCHOOL_ADMIN,
                Role.CANTEEN_STAFF);
    }

    private static String actorUserId(HttpServletRequest request) {
        Object value = request.getAttribute(AuthPrincipal.class.getName());
        if (value instanceof AuthPrincipal principal) {
            return principal.userId();
        }
        throw new IllegalStateException("Authenticated actor is required");
    }

    public record CreateMealReviewRequest(
            @NotBlank String orderId,
            @Min(1) @Max(5) int rating,
            String content) {
    }

    public record CreateDinerComplaintRequest(
            @NotBlank String category,
            @NotBlank String subject,
            @NotBlank String description,
            String relatedOrderId) {
    }
}
