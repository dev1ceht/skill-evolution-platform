package com.example.smartcanteen.http;

import com.example.smartcanteen.application.SmartCanteenWorkflow;
import com.example.smartcanteen.application.port.RecipeImport;
import com.example.smartcanteen.application.SmartCanteenWorkflow.ReceiptResult;
import com.example.smartcanteen.domain.LedgerAlert;
import com.example.smartcanteen.domain.LedgerCode;
import com.example.smartcanteen.domain.LedgerCycleRequest;
import com.example.smartcanteen.domain.LedgerRecordCommand;
import com.example.smartcanteen.domain.LedgerScope;
import com.example.smartcanteen.domain.IngredientRequirement;
import com.example.smartcanteen.domain.Menu;
import com.example.smartcanteen.domain.ProcurementItem;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
import com.example.smartcanteen.security.ScopeAccess;
import com.example.smartcanteen.security.RoleAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletRequest;
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
public class SmartCanteenController {

    private final SmartCanteenWorkflow workflow;
    private final ScopeAccess scopes;
    private final RoleAccess roles;

    public SmartCanteenController(
            SmartCanteenWorkflow workflow, ScopeAccess scopes, RoleAccess roles) {
        this.workflow = workflow;
        this.scopes = scopes;
        this.roles = roles;
    }

    @PostMapping("/menus/{menuId}/submit")
    public ApiResponse<MenuView> submit(
            HttpServletRequest request,
            @PathVariable String menuId,
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String canteenId) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(MenuView.from(workflow.submitMenu(
                scope(request, schoolId, canteenId), menuId)));
    }

    @PostMapping("/menu-approvals/{menuId}/decision")
    public ApiResponse<MenuView> decide(
            HttpServletRequest request,
            @PathVariable String menuId,
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String canteenId,
            @Valid @RequestBody ApprovalDecision body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(MenuView.from(
                workflow.decideMenu(
                        scope(request, schoolId, canteenId),
                        menuId,
                        body.decision(),
                body.comment())));
    }

    @PostMapping("/menus/{menuId}/recipe")
    public ApiResponse<RecipeView> importRecipe(
            HttpServletRequest request,
            @PathVariable String menuId,
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String canteenId,
            @Valid @RequestBody RecipeImportRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        RecipeImport.RecipeResult result = workflow.importRecipe(
                scope(request, schoolId, canteenId),
                menuId,
                body.requirements().stream()
                        .map(item -> new IngredientRequirement(
                                item.materialId(), item.quantity(), item.unit()))
                        .toList());
        return ApiResponse.ok(RecipeView.from(result));
    }

    @PostMapping("/procurement-plans/generate")
    public ApiResponse<ProcurementPlanView> generate(
            HttpServletRequest request,
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String canteenId,
            @Valid @RequestBody GenerateProcurement body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(new ProcurementPlanView(
                body.menuId(), workflow.generateProcurement(
                scope(request, schoolId, canteenId), body.menuId())));
    }

    @PostMapping("/inventory/receipts")
    public ApiResponse<ReceiptResult> receive(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String canteenId,
            @Valid @RequestBody InventoryReceipt body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(workflow.receive(
                scope(request, schoolId, canteenId),
                idempotencyKey, body.materialId(), body.quantity(), body.unit()));
    }

    @PostMapping("/ledger-records")
    public ApiResponse<LedgerAlert> completeLedger(
            HttpServletRequest servletRequest,
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String canteenId,
            @Valid @RequestBody LedgerRecord request) {
        roles.requireAny(servletRequest, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(workflow.completeLedger(
                scope(servletRequest, schoolId, canteenId), request.ledgerCode()));
    }

    @PostMapping("/ledger-cycles")
    public ApiResponse<LedgerAlert> startLedgerCycle(
            HttpServletRequest servletRequest,
            @Valid @RequestBody StartLedgerCycle request) {
        roles.requireAny(servletRequest, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        CanteenScope canteenScope = scopes.require(
                servletRequest, request.schoolId(), request.canteenId());
        LedgerScope scope = new LedgerScope(
                canteenScope.schoolId(), canteenScope.canteenId(), request.cycleId());
        Set<LedgerCode> codes = request.ledgerCodes().stream()
                .map(LedgerCode::from)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LocalDate periodStart = request.periodStart() == null
                ? LocalDate.now()
                : request.periodStart();
        LocalDate periodEnd = request.periodEnd() == null
                ? periodStart
                : request.periodEnd();
        return ApiResponse.ok(workflow.startLedgerCycle(
                new LedgerCycleRequest(scope, codes, periodStart, periodEnd)));
    }

    @PostMapping("/ledger-cycles/{cycleId}/records")
    public ApiResponse<LedgerAlert> completeScopedLedger(
            HttpServletRequest servletRequest,
            @PathVariable String cycleId,
            @Valid @RequestBody ScopedLedgerRecord request) {
        roles.requireAny(servletRequest, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        CanteenScope canteenScope = scopes.require(
                servletRequest, request.schoolId(), request.canteenId());
        return ApiResponse.ok(workflow.completeLedger(new LedgerRecordCommand(
                new LedgerScope(canteenScope.schoolId(), canteenScope.canteenId(), cycleId),
                LedgerCode.from(request.ledgerCode()))));
    }

    @GetMapping("/ledger-cycles/{cycleId}/alerts/current")
    public ApiResponse<LedgerAlert> currentScopedLedgerAlert(
            HttpServletRequest servletRequest,
            @PathVariable String cycleId,
            @RequestParam String schoolId,
            @RequestParam String canteenId) {
        roles.requireReader(servletRequest);
        CanteenScope canteenScope = scopes.require(servletRequest, schoolId, canteenId);
        return ApiResponse.ok(workflow.currentLedgerAlert(
                new LedgerScope(canteenScope.schoolId(), canteenScope.canteenId(), cycleId)));
    }

    @GetMapping("/ledger-alerts/current")
    public ApiResponse<LedgerAlert> currentLedgerAlert(
            HttpServletRequest servletRequest,
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String canteenId) {
        roles.requireReader(servletRequest);
        return ApiResponse.ok(workflow.currentLedgerAlert(
                scope(servletRequest, schoolId, canteenId)));
    }

    public record MenuView(String id, String status, String decisionComment) {
        static MenuView from(Menu menu) {
            return new MenuView(menu.id(), menu.status().name(), menu.decisionComment());
        }
    }

    public record ApprovalDecision(@NotBlank String decision, @NotBlank String comment) {
    }

    public record RecipeImportRequest(
            @NotEmpty List<@Valid RecipeRequirement> requirements) {
    }

    public record RecipeRequirement(
            @NotBlank String materialId,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
            @NotBlank String unit) {
    }

    public record RecipeView(String menuId, List<RecipeRequirement> requirements) {

        static RecipeView from(RecipeImport.RecipeResult result) {
            return new RecipeView(
                    result.menuId(),
                    result.requirements().stream()
                            .map(item -> new RecipeRequirement(
                                    item.materialId(), item.quantity(), item.unit()))
                            .toList());
        }
    }

    public record GenerateProcurement(@NotBlank String menuId) {
    }

    public record ProcurementPlanView(String menuId, List<ProcurementItem> items) {
    }

    public record InventoryReceipt(
            @NotBlank String materialId,
            @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal quantity,
            @NotBlank String unit) {
    }

    public record LedgerRecord(@NotBlank String ledgerCode) {
    }

    public record StartLedgerCycle(
            @NotBlank String schoolId,
            @NotBlank String canteenId,
            @NotBlank String cycleId,
            @NotEmpty List<@NotBlank String> ledgerCodes,
            LocalDate periodStart,
            LocalDate periodEnd) {
    }

    public record ScopedLedgerRecord(
            @NotBlank String schoolId,
            @NotBlank String canteenId,
            @NotBlank String ledgerCode) {
    }

    private CanteenScope scope(
            HttpServletRequest request, String schoolId, String canteenId) {
        if ((schoolId == null) != (canteenId == null)) {
            throw new IllegalArgumentException(
                    "schoolId and canteenId must be provided together");
        }
        if (schoolId != null) {
            return scopes.require(request, schoolId, canteenId);
        }
        Object value = request.getAttribute(AuthPrincipal.class.getName());
        if (value instanceof AuthPrincipal principal
                && principal.schoolId() != null && principal.canteenId() != null) {
            return scopes.require(request, principal.schoolId(), principal.canteenId());
        }
        return scopes.require(
                request, CanteenScope.DEFAULT.schoolId(), CanteenScope.DEFAULT.canteenId());
    }
}
