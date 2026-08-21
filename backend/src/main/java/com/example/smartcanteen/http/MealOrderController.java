package com.example.smartcanteen.http;

import com.example.smartcanteen.application.MealOrderService;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.DinerMenu;
import com.example.smartcanteen.domain.MealOrder;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
import com.example.smartcanteen.security.RoleAccess;
import com.example.smartcanteen.security.ScopeAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MealOrderController {

    private final MealOrderService orders;
    private final ScopeAccess scopes;
    private final RoleAccess roles;

    public MealOrderController(MealOrderService orders, ScopeAccess scopes, RoleAccess roles) {
        this.orders = orders;
        this.scopes = scopes;
        this.roles = roles;
    }

    @GetMapping("/diner/menus")
    public ApiResponse<OperationalController.PageView<DinerMenu>> listMenus(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) String mealTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireConsumerAccess(request);
        PageResult<DinerMenu> result = orders.listPublishedMenus(
                scopes.require(request, schoolId, canteenId), date, mealTime, page, size);
        return ApiResponse.ok(OperationalController.PageView.from(result));
    }

    @GetMapping("/meal-orders")
    public ApiResponse<OperationalController.PageView<MealOrder>> listMine(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireConsumerAccess(request);
        CanteenScope scope = scopes.require(request, schoolId, canteenId);
        PageResult<MealOrder> result = orders.listMine(
                scope, actorUserId(request), status, page, size);
        return ApiResponse.ok(OperationalController.PageView.from(result));
    }

    @PostMapping("/meal-orders")
    public ApiResponse<MealOrder> create(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody CreateMealOrderRequest body) {
        requireConsumerAccess(request);
        CanteenScope scope = scopes.require(request, schoolId, canteenId);
        return ApiResponse.ok(orders.create(
                scope,
                actorUserId(request),
                body.menuId(),
                body.menuDate(),
                body.mealTime(),
                body.items().stream()
                        .map(item -> new MealOrderService.RequestedItem(item.dishId(), item.quantity()))
                        .toList(),
                idempotencyKey));
    }

    @PostMapping("/meal-orders/{orderId}/cancel")
    public ApiResponse<MealOrder> cancel(
            HttpServletRequest request,
            @PathVariable String orderId,
            @RequestParam String schoolId,
            @RequestParam String canteenId) {
        requireConsumerAccess(request);
        return ApiResponse.ok(orders.cancel(
                scopes.require(request, schoolId, canteenId), actorUserId(request), orderId));
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

    public record CreateMealOrderRequest(
            String menuId,
            LocalDate menuDate,
            String mealTime,
            @NotEmpty List<@Valid MealOrderItemRequest> items) {

        public CreateMealOrderRequest {
            if ((menuId == null || menuId.isBlank()) && menuDate == null) {
                throw new IllegalArgumentException("menuId or menuDate is required");
            }
        }
    }

    public record MealOrderItemRequest(
            @NotBlank String dishId,
            @NotNull @DecimalMin(value = "1") @DecimalMax(value = "20") Integer quantity) {
    }
}
