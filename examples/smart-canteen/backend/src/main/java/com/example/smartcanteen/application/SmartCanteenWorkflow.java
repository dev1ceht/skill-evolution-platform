package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.LedgerMonitoring;
import com.example.smartcanteen.application.port.InventoryReceiving;
import com.example.smartcanteen.application.port.MenuApproval;
import com.example.smartcanteen.application.port.ProcurementPlanning;
import com.example.smartcanteen.application.port.RecipeImport;
import com.example.smartcanteen.domain.LedgerAlert;
import com.example.smartcanteen.domain.LedgerCode;
import com.example.smartcanteen.domain.LedgerCycleRequest;
import com.example.smartcanteen.domain.LedgerRecordCommand;
import com.example.smartcanteen.domain.LedgerScope;
import com.example.smartcanteen.domain.Menu;
import com.example.smartcanteen.domain.IngredientRequirement;
import com.example.smartcanteen.domain.ProcurementItem;
import com.example.smartcanteen.domain.CanteenScope;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SmartCanteenWorkflow {

    private final MenuApproval menuApproval;
    private final ProcurementPlanning procurementPlanning;
    private final InventoryReceiving inventoryReceiving;
    private final RecipeImport recipeImport;
    // Compatibility scope for the original unscoped endpoints. V2 seeds it
    // from the V1 ledger requirements; new callers must provide an explicit scope.
    private static final LedgerScope DEFAULT_LEDGER_SCOPE =
            new LedgerScope("SCHOOL-001", "CANTEEN-001", "CYCLE-001");
    private final LedgerMonitoring ledgerMonitoring;

    public SmartCanteenWorkflow(
            MenuApproval menuApproval,
            ProcurementPlanning procurementPlanning,
            InventoryReceiving inventoryReceiving,
            RecipeImport recipeImport,
            LedgerMonitoring ledgerMonitoring) {
        this.menuApproval = menuApproval;
        this.procurementPlanning = procurementPlanning;
        this.inventoryReceiving = inventoryReceiving;
        this.recipeImport = recipeImport;
        this.ledgerMonitoring = ledgerMonitoring;
    }

    @Transactional
    public Menu submitMenu(String menuId) {
        return submitMenu(CanteenScope.DEFAULT, menuId);
    }

    @Transactional
    public Menu submitMenu(CanteenScope scope, String menuId) {
        return menuApproval.submit(scope, menuId);
    }

    @Transactional
    public Menu decideMenu(String menuId, String decision, String comment) {
        return decideMenu(CanteenScope.DEFAULT, menuId, decision, comment);
    }

    @Transactional
    public Menu decideMenu(
            CanteenScope scope, String menuId, String decision, String comment) {
        return menuApproval.decide(scope, menuId, decision, comment);
    }

    @Transactional
    public RecipeImport.RecipeResult importRecipe(
            CanteenScope scope,
            String menuId,
            List<IngredientRequirement> requirements) {
        return recipeImport.importRecipe(scope, menuId, requirements);
    }

    @Transactional(readOnly = true)
    public List<ProcurementItem> generateProcurement(String menuId) {
        return generateProcurement(CanteenScope.DEFAULT, menuId);
    }

    @Transactional(readOnly = true)
    public List<ProcurementItem> generateProcurement(CanteenScope scope, String menuId) {
        return procurementPlanning.generate(scope, menuId);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceiptResult receive(
            String idempotencyKey,
            String materialId,
            BigDecimal quantity,
            String unit) {
        return receive(CanteenScope.DEFAULT, idempotencyKey, materialId, quantity, unit);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceiptResult receive(
            CanteenScope scope,
            String idempotencyKey,
            String materialId,
            BigDecimal quantity,
            String unit) {
        InventoryReceiving.ReceiptResult stored = inventoryReceiving.receive(
                scope,
                idempotencyKey, materialId, quantity, unit);
        return new ReceiptResult(
                stored.materialId(), stored.quantityBase(), stored.baseUnit());
    }

    @Transactional
    public LedgerAlert completeLedger(String ledgerCode) {
        return completeLedger(new LedgerRecordCommand(
                DEFAULT_LEDGER_SCOPE,
                LedgerCode.from(ledgerCode)));
    }

    @Transactional
    public LedgerAlert completeLedger(CanteenScope scope, String ledgerCode) {
        return completeLedger(new LedgerRecordCommand(
                new LedgerScope(scope.schoolId(), scope.canteenId(), "CYCLE-001"),
                LedgerCode.from(ledgerCode)));
    }

    @Transactional
    public LedgerAlert completeLedger(LedgerRecordCommand command) {
        return ledgerMonitoring.completeLedger(command);
    }

    @Transactional(readOnly = true)
    public LedgerAlert currentLedgerAlert() {
        return currentLedgerAlert(DEFAULT_LEDGER_SCOPE);
    }

    @Transactional(readOnly = true)
    public LedgerAlert currentLedgerAlert(CanteenScope scope) {
        return currentLedgerAlert(new LedgerScope(
                scope.schoolId(), scope.canteenId(), "CYCLE-001"));
    }

    @Transactional(readOnly = true)
    public LedgerAlert currentLedgerAlert(LedgerScope scope) {
        return ledgerMonitoring.current(scope);
    }

    @Transactional
    public LedgerAlert startLedgerCycle(LedgerCycleRequest request) {
        return ledgerMonitoring.startCycle(request);
    }

    public record ReceiptResult(String materialId, BigDecimal quantityBase, String baseUnit) {
    }
}
