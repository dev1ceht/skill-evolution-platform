package com.example.smartcanteen.http;

import com.example.smartcanteen.application.ProcurementPlanService;
import com.example.smartcanteen.application.ProcurementPlanService.OrderLine;
import com.example.smartcanteen.application.ProcurementPlanService.PlanAdjustment;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.domain.ProcurementPlan;
import com.example.smartcanteen.domain.PurchaseOrder;
import com.example.smartcanteen.security.Role;
import com.example.smartcanteen.security.RoleAccess;
import com.example.smartcanteen.security.ScopeAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/procurement-plans")
public class ProcurementPlanController {

    private final ProcurementPlanService plans;
    private final ScopeAccess scopes;
    private final RoleAccess roles;

    public ProcurementPlanController(
            ProcurementPlanService plans, ScopeAccess scopes, RoleAccess roles) {
        this.plans = plans;
        this.scopes = scopes;
        this.roles = roles;
    }

    @GetMapping
    public ApiResponse<PageView<ProcurementPlan>> list(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        roles.requireReader(request);
        return ApiResponse.ok(PageView.from(plans.list(
                scopes.require(request, schoolId, canteenId), status, page, size)));
    }

    @GetMapping("/{planId}")
    public ApiResponse<ProcurementPlan> find(
            HttpServletRequest request,
            @PathVariable String planId,
            @RequestParam String schoolId,
            @RequestParam String canteenId) {
        roles.requireReader(request);
        return ApiResponse.ok(plans.find(
                scopes.require(request, schoolId, canteenId), planId));
    }

    @PostMapping("/generate-range")
    public ApiResponse<ProcurementPlan> generate(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody GenerateRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(plans.generate(
                scopes.require(request, schoolId, canteenId),
                body.periodStart(), body.periodEnd(), idempotencyKey));
    }

    @PutMapping("/{planId}/items")
    public ApiResponse<ProcurementPlan> adjust(
            HttpServletRequest request,
            @PathVariable String planId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody AdjustRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(plans.adjust(
                scopes.require(request, schoolId, canteenId),
                planId,
                body.version(),
                body.items().stream()
                        .map(item -> new PlanAdjustment(
                                item.ingredientId(), item.quantity(), item.unit()))
                        .toList()));
    }

    @PostMapping("/{planId}/confirm")
    public ApiResponse<ProcurementPlan> confirm(
            HttpServletRequest request,
            @PathVariable String planId,
            @RequestParam String schoolId,
            @RequestParam String canteenId) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN);
        return ApiResponse.ok(plans.confirm(
                scopes.require(request, schoolId, canteenId), planId));
    }

    @PostMapping("/{planId}/cancel")
    public ApiResponse<ProcurementPlan> cancel(
            HttpServletRequest request,
            @PathVariable String planId,
            @RequestParam String schoolId,
            @RequestParam String canteenId) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN);
        return ApiResponse.ok(plans.cancel(
                scopes.require(request, schoolId, canteenId), planId));
    }

    @PostMapping("/{planId}/purchase-orders")
    public ApiResponse<PurchaseOrder> convertToOrder(
            HttpServletRequest request,
            @PathVariable String planId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody CreateOrderRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(plans.convertToOrder(
                scopes.require(request, schoolId, canteenId),
                planId,
                idempotencyKey,
                body.supplierId(),
                body.orderType(),
                body.expectedDeliveryAt(),
                body.remark(),
                body.items().stream()
                        .map(item -> new OrderLine(
                                item.ingredientId(), item.quantity(), item.unit(), item.unitPrice()))
                        .toList()));
    }

    public record GenerateRequest(
            @NotNull LocalDate periodStart,
            @NotNull LocalDate periodEnd) {
    }

    public record AdjustRequest(
            long version,
            @NotEmpty List<@Valid PlanItemRequest> items) {
    }

    public record PlanItemRequest(
            @NotBlank String ingredientId,
            @NotNull @DecimalMin(value = "0", inclusive = true) BigDecimal quantity,
            @NotBlank String unit) {
    }

    public record CreateOrderRequest(
            @NotBlank String supplierId,
            @NotBlank String orderType,
            Instant expectedDeliveryAt,
            String remark,
            @NotEmpty List<@Valid OrderItemRequest> items) {
    }

    public record OrderItemRequest(
            @NotBlank String ingredientId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotBlank String unit,
            @NotNull @DecimalMin("0") BigDecimal unitPrice) {
    }

    public record PageView<T>(long total, long pages, int current, int size, List<T> records) {
        static <T> PageView<T> from(PageResult<T> page) {
            return new PageView<>(
                    page.total(), page.pages(), page.current(), page.size(), page.records());
        }
    }
}
