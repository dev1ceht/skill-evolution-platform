package com.example.smartcanteen.application;

import com.example.smartcanteen.domain.BaseQuantity;
import com.example.smartcanteen.domain.IngredientRequirement;
import com.example.smartcanteen.domain.LedgerAlert;
import com.example.smartcanteen.domain.LedgerAlertService;
import com.example.smartcanteen.domain.LedgerCode;
import com.example.smartcanteen.domain.Menu;
import com.example.smartcanteen.domain.ProcurementItem;
import com.example.smartcanteen.domain.ProcurementService;
import com.example.smartcanteen.domain.UnitConverter;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class SmartCanteenWorkflow {

    private final Map<String, Menu> menus = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> inventory = new ConcurrentHashMap<>();
    private final Map<String, List<IngredientRequirement>> recipes = new HashMap<>();
    private final Map<String, ReceiptResult> processedReceipts = new ConcurrentHashMap<>();
    private final UnitConverter unitConverter = new UnitConverter();
    private final ProcurementService procurementService = new ProcurementService(unitConverter);
    private final LedgerAlertService ledgerAlerts =
            new LedgerAlertService(Set.of(LedgerCode.PURCHASE_ACCEPTANCE));

    public SmartCanteenWorkflow() {
        menus.put("MENU-001", new Menu("MENU-001"));
        recipes.put("MENU-001", List.of(
                new IngredientRequirement("FLOUR", new BigDecimal("2"), "kg"),
                new IngredientRequirement("EGG", new BigDecimal("12"), "count")));
        inventory.put("FLOUR", new BigDecimal("500"));
        inventory.put("EGG", new BigDecimal("20"));
    }

    public synchronized Menu submitMenu(String menuId) {
        Menu menu = requireMenu(menuId);
        menu.submit();
        return menu;
    }

    public synchronized Menu decideMenu(String menuId, String decision, String comment) {
        Menu menu = requireMenu(menuId);
        if ("APPROVE".equalsIgnoreCase(decision)) {
            menu.approve(comment);
        } else if ("REJECT".equalsIgnoreCase(decision)) {
            menu.reject(comment);
        } else {
            throw new IllegalArgumentException("Unsupported approval decision: " + decision);
        }
        return menu;
    }

    public synchronized List<ProcurementItem> generateProcurement(String menuId) {
        Menu menu = requireMenu(menuId);
        if (!"APPROVED".equals(menu.status().name())) {
            throw new IllegalStateException("Only approved menus can generate procurement plans");
        }
        return procurementService.calculateShortages(
                recipes.getOrDefault(menuId, List.of()), Map.copyOf(inventory));
    }

    public synchronized ReceiptResult receive(
            String idempotencyKey,
            String materialId,
            BigDecimal quantity,
            String unit) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }
        ReceiptResult previous = processedReceipts.get(idempotencyKey);
        if (previous != null) {
            return previous;
        }
        BaseQuantity received = unitConverter.convert(quantity, unit);
        BigDecimal updated = inventory.merge(materialId, received.quantity(), BigDecimal::add);
        ReceiptResult result = new ReceiptResult(materialId, updated, received.unit());
        processedReceipts.put(idempotencyKey, result);
        return result;
    }

    public LedgerAlert completeLedger(String ledgerCode) {
        return ledgerAlerts.complete(LedgerCode.from(ledgerCode));
    }

    public LedgerAlert currentLedgerAlert() {
        return ledgerAlerts.current();
    }

    private Menu requireMenu(String menuId) {
        Menu menu = menus.get(menuId);
        if (menu == null) {
            throw new IllegalArgumentException("Unknown menu: " + menuId);
        }
        return menu;
    }

    public record ReceiptResult(String materialId, BigDecimal quantityBase, String baseUnit) {
    }
}
