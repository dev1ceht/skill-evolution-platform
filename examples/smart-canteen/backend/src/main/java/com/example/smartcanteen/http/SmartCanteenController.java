package com.example.smartcanteen.http;

import com.example.smartcanteen.application.SmartCanteenWorkflow;
import com.example.smartcanteen.application.SmartCanteenWorkflow.ReceiptResult;
import com.example.smartcanteen.domain.LedgerAlert;
import com.example.smartcanteen.domain.Menu;
import com.example.smartcanteen.domain.ProcurementItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SmartCanteenController {

    private final SmartCanteenWorkflow workflow;

    public SmartCanteenController(SmartCanteenWorkflow workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/menus/{menuId}/submit")
    public ApiResponse<MenuView> submit(@PathVariable String menuId) {
        return ApiResponse.ok(MenuView.from(workflow.submitMenu(menuId)));
    }

    @PostMapping("/menu-approvals/{menuId}/decision")
    public ApiResponse<MenuView> decide(
            @PathVariable String menuId,
            @Valid @RequestBody ApprovalDecision request) {
        return ApiResponse.ok(MenuView.from(
                workflow.decideMenu(menuId, request.decision(), request.comment())));
    }

    @PostMapping("/procurement-plans/generate")
    public ApiResponse<ProcurementPlanView> generate(
            @Valid @RequestBody GenerateProcurement request) {
        return ApiResponse.ok(new ProcurementPlanView(
                request.menuId(), workflow.generateProcurement(request.menuId())));
    }

    @PostMapping("/inventory/receipts")
    public ApiResponse<ReceiptResult> receive(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InventoryReceipt request) {
        return ApiResponse.ok(workflow.receive(
                idempotencyKey, request.materialId(), request.quantity(), request.unit()));
    }

    @PostMapping("/ledger-records")
    public ApiResponse<LedgerAlert> completeLedger(@Valid @RequestBody LedgerRecord request) {
        return ApiResponse.ok(workflow.completeLedger(request.ledgerCode()));
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
}
