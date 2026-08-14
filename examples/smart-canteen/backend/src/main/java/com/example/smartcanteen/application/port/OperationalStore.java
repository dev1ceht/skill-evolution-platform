package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.DailyMenu;
import com.example.smartcanteen.domain.Dish;
import com.example.smartcanteen.domain.DashboardSummary;
import com.example.smartcanteen.domain.Ingredient;
import com.example.smartcanteen.domain.InventoryLine;
import com.example.smartcanteen.domain.OperationalLedgerRecord;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.domain.PurchaseOrder;
import com.example.smartcanteen.domain.Supplier;
import com.example.smartcanteen.domain.TraceabilityResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence port for the phase-5 operational slice. */
public interface OperationalStore {

    PageResult<Ingredient> listIngredients(
            CanteenScope scope, String keyword, String category, int page, int size);

    Optional<Ingredient> findIngredient(CanteenScope scope, String ingredientId);

    void createIngredient(CanteenScope scope, Ingredient ingredient);

    void updateIngredient(CanteenScope scope, Ingredient ingredient);

    PageResult<Dish> listDishes(
            CanteenScope scope, String keyword, String category, int page, int size);

    Optional<Dish> findDish(CanteenScope scope, String dishId);

    void createDish(CanteenScope scope, Dish dish);

    void updateDish(CanteenScope scope, Dish dish);

    PageResult<DailyMenu> listDailyMenus(
            CanteenScope scope, LocalDate from, LocalDate to, int page, int size);

    Optional<DailyMenu> findDailyMenu(CanteenScope scope, String menuId);

    void saveDailyMenu(CanteenScope scope, DailyMenu menu, boolean create);

    void publishDailyMenu(CanteenScope scope, String menuId);

    PageResult<Supplier> listSuppliers(CanteenScope scope, String keyword, int page, int size);

    Optional<Supplier> findSupplier(CanteenScope scope, String supplierId);

    void createSupplier(CanteenScope scope, Supplier supplier);

    PageResult<PurchaseOrder> listPurchaseOrders(
            CanteenScope scope, String status, int page, int size);

    Optional<PurchaseOrder> findPurchaseOrder(CanteenScope scope, String orderId);

    PurchaseOrder createPurchaseOrder(
            CanteenScope scope,
            PurchaseOrder order,
            String idempotencyKey);

    PurchaseOrder transitionPurchaseOrder(
            CanteenScope scope, String orderId, String targetStatus);

    ReceiveResult receivePurchaseOrder(
            CanteenScope scope,
            String orderId,
            String idempotencyKey,
            List<ReceiveItem> items);

    PageResult<InventoryLine> listInventory(
            CanteenScope scope, String keyword, boolean warningOnly, int page, int size);

    StockOutResult stockOut(
            CanteenScope scope,
            String idempotencyKey,
            String reason,
            List<StockOutItem> items);

    OperationalLedgerRecord saveLedgerRecord(
            CanteenScope scope, OperationalLedgerRecord record);

    PageResult<OperationalLedgerRecord> listLedgerRecords(
            CanteenScope scope,
            String cycleId,
            String ledgerCode,
            String status,
            Instant from,
            Instant to,
            int page,
            int size);

    LedgerStats ledgerStats(CanteenScope scope, LocalDate from, LocalDate to);

    DashboardSummary dashboardSummary(CanteenScope scope, LocalDate date);

    Optional<TraceabilityResult> trace(CanteenScope scope, String traceCode);

    record ReceiveItem(
            String ingredientId,
            BigDecimal quantity,
            String unit,
            String batchNo,
            BigDecimal purchasePrice,
            LocalDate productionDate,
            LocalDate expiryDate) {
    }

    record ReceiveResult(String orderId, String receiptId, List<String> traceCodes) {
        public ReceiveResult {
            traceCodes = traceCodes == null ? List.of() : List.copyOf(traceCodes);
        }
    }

    record StockOutItem(String ingredientId, BigDecimal quantity, String unit) {
    }

    record StockOutResult(String stockOutId, List<StockOutItem> items) {
        public StockOutResult {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    record LedgerStats(long expected, long completed, long missing) {
    }
}
