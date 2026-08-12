package com.example.smartcanteen.http;

import com.example.smartcanteen.application.SmartCanteenWorkflow;
import com.example.smartcanteen.application.SmartCanteenWorkflow.ReceiptResult;
import com.example.smartcanteen.domain.LedgerAlert;
import com.example.smartcanteen.domain.LedgerCode;
import com.example.smartcanteen.domain.LedgerCycleRequest;
import com.example.smartcanteen.domain.LedgerRecordCommand;
import com.example.smartcanteen.domain.LedgerScope;
import com.example.smartcanteen.domain.Menu;
import com.example.smartcanteen.domain.ProcurementItem;
import com.example.smartcanteen.domain.CanteenScope;
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

    public SmartCanteenController(SmartCanteenWorkflow workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/menus/{menuId}/submit")
    public ApiResponse<MenuView> submit(
            @PathVariable String menuId,
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String canteenId) {
        return ApiResponse.ok(MenuView.from(workflow.submitMenu(
                scope(schoolId, canteenId), menuId)));
    }

    @PostMapping("/menu-approvals/{menuId}/decision")
    public ApiResponse<MenuView> decide(
            @PathVariable String menuId,
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String canteenId,
            @Valid @RequestBody ApprovalDecision request) {
        return ApiResponse.ok(MenuView.from(
                workflow.decideMenu(
                        scope(schoolId, canteenId),
                        menuId,
                        request.decision(),
                        request.comment())));
    }

    @PostMapping("/procurement-plans/generate")
    public ApiResponse<ProcurementPlanView> generate(
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String canteenId,
            @Valid @RequestBody GenerateProcurement request) {
        return ApiResponse.ok(new ProcurementPlanView(
                request.menuId(), workflow.generateProcurement(
                        scope(schoolId, canteenId), request.menuId())));
    }

    @PostMapping("/inventory/receipts")
    public ApiResponse<ReceiptResult> receive(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam(required = false) String schoolId,
            @RequestParam(required = false) String canteenId,
            @Valid @RequestBody InventoryReceipt request) {
        return ApiResponse.ok(workflow.receive(
                scope(schoolId, canteenId),
                idempotencyKey, request.materialId(), request.quantity(), request.unit()));
    }

    @PostMapping("/ledger-records")
    public ApiResponse<LedgerAlert> completeLedger(@Valid @RequestBody LedgerRecord request) {
        return ApiResponse.ok(workflow.completeLedger(request.ledgerCode()));
    }

    @PostMapping("/ledger-cycles")
    public ApiResponse<LedgerAlert> startLedgerCycle(
            @Valid @RequestBody StartLedgerCycle request) {
        LedgerScope scope = new LedgerScope(request.schoolId(), request.canteenId(), request.cycleId());
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
            @PathVariable String cycleId,
            @Valid @RequestBody ScopedLedgerRecord request) {
        return ApiResponse.ok(workflow.completeLedger(new LedgerRecordCommand(
                new LedgerScope(request.schoolId(), request.canteenId(), cycleId),
                LedgerCode.from(request.ledgerCode()))));
    }

    @GetMapping("/ledger-cycles/{cycleId}/alerts/current")
    public ApiResponse<LedgerAlert> currentScopedLedgerAlert(
            @PathVariable String cycleId,
            @RequestParam String schoolId,
            @RequestParam String canteenId) {
        return ApiResponse.ok(workflow.currentLedgerAlert(
                new LedgerScope(schoolId, canteenId, cycleId)));
    }

    @GetMapping("/ledger-alerts/current")
    public ApiResponse<LedgerAlert> currentLedgerAlert() {
        return ApiResponse.ok(workflow.currentLedgerAlert());
    }

    public record MenuView(String id, String status, String decisionComment) {
        static MenuView from(Menu menu) {
            return new MenuView(menu.id(), menu.status().name(), menu.decisionComment());
        }
    }

    public record ApprovalDecision(@NotBlank String decision, @NotBlank String comment) {
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

    private static CanteenScope scope(String schoolId, String canteenId) {
        if ((schoolId == null) != (canteenId == null)) {
            throw new IllegalArgumentException(
                    "schoolId and canteenId must be provided together");
        }
        return new CanteenScope(
                schoolId == null ? CanteenScope.DEFAULT.schoolId() : schoolId,
                canteenId == null ? CanteenScope.DEFAULT.canteenId() : canteenId);
    }
}
