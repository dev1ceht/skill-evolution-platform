package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.OperationalStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.Ingredient;
import com.example.smartcanteen.domain.InventoryLine;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.domain.PurchaseOrder;
import com.example.smartcanteen.domain.PurchaseOrderItem;
import com.example.smartcanteen.domain.Supplier;
import com.example.smartcanteen.domain.UnitConverter;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcurementOperationsService {

    private final OperationalStore store;
    private final UnitConverter units;

    public ProcurementOperationsService(OperationalStore store, UnitConverter units) {
        this.store = store;
        this.units = units;
    }

    @Transactional(readOnly = true)
    public PageResult<Supplier> listSuppliers(CanteenScope scope, String keyword, int page, int size) {
        return store.listSuppliers(scope, keyword, page, size);
    }

    @Transactional
    public Supplier createSupplier(CanteenScope scope, Supplier supplier) {
        store.createSupplier(scope, supplier);
        return store.findSupplier(scope, supplier.id())
                .orElseThrow(() -> new IllegalStateException("Supplier was not persisted"));
    }

    @Transactional(readOnly = true)
    public PageResult<PurchaseOrder> listOrders(
            CanteenScope scope, String status, int page, int size) {
        return store.listPurchaseOrders(scope, status, page, size);
    }

    @Transactional(readOnly = true)
    public PageResult<InventoryLine> listInventory(
            CanteenScope scope, String keyword, boolean warningOnly, int page, int size) {
        return store.listInventory(scope, keyword, warningOnly, page, size);
    }

    @Transactional
    public PurchaseOrder createOrder(
            CanteenScope scope,
            String id,
            String orderNo,
            String supplierId,
            String orderType,
            Instant expectedDeliveryAt,
            String remark,
            String idempotencyKey,
            List<PurchaseOrderItem> items) {
        require(idempotencyKey, "Idempotency-Key");
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("At least one purchase order item is required");
        }
        store.findSupplier(scope, supplierId)
                .filter(Supplier::active)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or disabled supplier"));
        Set<String> ingredientIds = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseOrderItem item : items) {
            if (!ingredientIds.add(item.ingredientId())) {
                throw new IllegalArgumentException("Duplicate ingredient in purchase order");
            }
            Ingredient ingredient = store.findIngredient(scope, item.ingredientId())
                    .filter(Ingredient::active)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown or disabled ingredient: " + item.ingredientId()));
            ensureCompatibleUnit(ingredient, item.unit());
            total = total.add(item.quantity().multiply(item.unitPrice()));
        }
        PurchaseOrder order = new PurchaseOrder(
                id == null || id.isBlank() ? "PO-" + UUID.randomUUID() : id,
                orderNo == null || orderNo.isBlank() ? "PO" + UUID.randomUUID() : orderNo,
                supplierId,
                orderType,
                "DRAFT",
                expectedDeliveryAt,
                total.setScale(2, java.math.RoundingMode.HALF_UP),
                remark,
                Instant.now(),
                items);
        return store.createPurchaseOrder(scope, order, idempotencyKey);
    }

    @Transactional
    public PurchaseOrder transition(CanteenScope scope, String orderId, String targetStatus) {
        return store.transitionPurchaseOrder(scope, orderId, normalizeStatus(targetStatus));
    }

    @Transactional
    public OperationalStore.ReceiveResult receive(
            CanteenScope scope,
            String orderId,
            String idempotencyKey,
            List<OperationalStore.ReceiveItem> requestedItems) {
        require(idempotencyKey, "Idempotency-Key");
        PurchaseOrder order = store.findPurchaseOrder(scope, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found: " + orderId));
        List<OperationalStore.ReceiveItem> items = requestedItems == null || requestedItems.isEmpty()
                ? order.items().stream()
                        .map(item -> new OperationalStore.ReceiveItem(
                                item.ingredientId(),
                                item.quantity(),
                                item.unit(),
                                "BATCH-" + UUID.randomUUID(),
                                item.unitPrice(),
                                null,
                                null))
                        .toList()
                : requestedItems;
        for (OperationalStore.ReceiveItem item : items) {
            Ingredient ingredient = store.findIngredient(scope, item.ingredientId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown ingredient: " + item.ingredientId()));
            ensureCompatibleUnit(ingredient, item.unit());
            if (item.quantity() == null || item.quantity().signum() <= 0) {
                throw new IllegalArgumentException("Received quantity must be positive");
            }
            if (item.purchasePrice() == null || item.purchasePrice().signum() < 0) {
                throw new IllegalArgumentException("Received purchasePrice must be non-negative");
            }
        }
        return store.receivePurchaseOrder(scope, orderId, idempotencyKey, items);
    }

    @Transactional
    public OperationalStore.StockOutResult stockOut(
            CanteenScope scope,
            String idempotencyKey,
            String reason,
            List<OperationalStore.StockOutItem> items) {
        require(idempotencyKey, "Idempotency-Key");
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("At least one stock-out item is required");
        }
        Set<String> ingredientIds = new HashSet<>();
        for (OperationalStore.StockOutItem item : items) {
            if (!ingredientIds.add(item.ingredientId())) {
                throw new IllegalArgumentException(
                        "Duplicate ingredient in stock-out: " + item.ingredientId());
            }
            Ingredient ingredient = store.findIngredient(scope, item.ingredientId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown ingredient: " + item.ingredientId()));
            ensureCompatibleUnit(ingredient, item.unit());
            if (item.quantity() == null || item.quantity().signum() <= 0) {
                throw new IllegalArgumentException("Stock-out quantity must be positive");
            }
        }
        return store.stockOut(scope, idempotencyKey, reason, items);
    }

    private void ensureCompatibleUnit(Ingredient ingredient, String unit) {
        String expected = units.convert(BigDecimal.ONE, ingredient.baseUnit()).unit();
        String actual = units.convert(BigDecimal.ONE, unit).unit();
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(
                    "Unit " + unit + " is incompatible with ingredient " + ingredient.id());
        }
    }

    private static String normalizeStatus(String value) {
        require(value, "status");
        String status = value.trim().toUpperCase();
        if (!List.of("SUBMITTED", "CONFIRMED", "CANCELLED").contains(status)) {
            throw new IllegalArgumentException("Unsupported purchase order transition: " + value);
        }
        return status;
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
