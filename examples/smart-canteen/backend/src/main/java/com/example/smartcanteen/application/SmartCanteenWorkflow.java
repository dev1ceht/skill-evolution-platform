package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.LedgerMonitoring;
import com.example.smartcanteen.application.port.SmartCanteenStore;
import com.example.smartcanteen.application.port.SmartCanteenStore.ReceiptCommand;
import com.example.smartcanteen.application.port.SmartCanteenStore.StoredReceipt;
import com.example.smartcanteen.domain.BaseQuantity;
import com.example.smartcanteen.domain.LedgerAlert;
import com.example.smartcanteen.domain.LedgerCode;
import com.example.smartcanteen.domain.LedgerCycleRequest;
import com.example.smartcanteen.domain.LedgerRecordCommand;
import com.example.smartcanteen.domain.LedgerScope;
import com.example.smartcanteen.domain.Menu;
import com.example.smartcanteen.domain.MenuStatus;
import com.example.smartcanteen.domain.ProcurementItem;
import com.example.smartcanteen.domain.ProcurementService;
import com.example.smartcanteen.domain.UnitConverter;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SmartCanteenWorkflow {

    private final SmartCanteenStore store;
    private final UnitConverter unitConverter = new UnitConverter();
    private final ProcurementService procurementService = new ProcurementService(unitConverter);
    // Compatibility scope for the original unscoped endpoints. V2 seeds it
    // from the V1 ledger requirements; new callers must provide an explicit scope.
    private static final LedgerScope DEFAULT_LEDGER_SCOPE =
            new LedgerScope("SCHOOL-001", "CANTEEN-001", "CYCLE-001");
    private final LedgerMonitoring ledgerMonitoring;

    public SmartCanteenWorkflow(SmartCanteenStore store, LedgerMonitoring ledgerMonitoring) {
        this.store = store;
        this.ledgerMonitoring = ledgerMonitoring;
    }

    @Transactional
    public Menu submitMenu(String menuId) {
        requireIdentifier("menuId", menuId, 64);
        Menu menu = requireMenu(menuId);
        menu.submit();
        store.saveMenu(menu);
        return menu;
    }

    @Transactional
    public Menu decideMenu(String menuId, String decision, String comment) {
        requireIdentifier("menuId", menuId, 64);
        Menu menu = requireMenu(menuId);
        if ("APPROVE".equalsIgnoreCase(decision)) {
            menu.approve(comment);
        } else if ("REJECT".equalsIgnoreCase(decision)) {
            menu.reject(comment);
        } else {
            throw new IllegalArgumentException("Unsupported approval decision: " + decision);
        }
        store.saveMenu(menu);
        return menu;
    }

    @Transactional(readOnly = true)
    public List<ProcurementItem> generateProcurement(String menuId) {
        requireIdentifier("menuId", menuId, 64);
        Menu menu = requireMenu(menuId);
        if (menu.status() != MenuStatus.APPROVED) {
            throw new IllegalStateException("Only approved menus can generate procurement plans");
        }
        return procurementService.calculateShortages(
                store.findRecipe(menuId), store.inventorySnapshot());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceiptResult receive(
            String idempotencyKey,
            String materialId,
            BigDecimal quantity,
            String unit) {
        requireIdentifier("Idempotency-Key", idempotencyKey, 128);
        requireIdentifier("materialId", materialId, 64);
        requireIdentifier("unit", unit, 16);
        BaseQuantity received = unitConverter.convert(quantity, unit);
        StoredReceipt stored = store.receiveOnce(new ReceiptCommand(
                idempotencyKey,
                materialId,
                quantity,
                unit,
                received.quantity(),
                received.unit()));
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
    public LedgerAlert completeLedger(LedgerRecordCommand command) {
        return ledgerMonitoring.completeLedger(command);
    }

    @Transactional(readOnly = true)
    public LedgerAlert currentLedgerAlert() {
        return currentLedgerAlert(DEFAULT_LEDGER_SCOPE);
    }

    @Transactional(readOnly = true)
    public LedgerAlert currentLedgerAlert(LedgerScope scope) {
        return ledgerMonitoring.current(scope);
    }

    @Transactional
    public LedgerAlert startLedgerCycle(LedgerCycleRequest request) {
        return ledgerMonitoring.startCycle(request);
    }

    private Menu requireMenu(String menuId) {
        return store.findMenu(menuId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown menu: " + menuId));
    }

    private static void requireIdentifier(String label, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(label + " exceeds " + maxLength + " characters");
        }
    }

    public record ReceiptResult(String materialId, BigDecimal quantityBase, String baseUnit) {
    }
}
