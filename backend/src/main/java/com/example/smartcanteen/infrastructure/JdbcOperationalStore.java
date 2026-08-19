package com.example.smartcanteen.infrastructure;

import com.example.smartcanteen.application.IngredientQuantityConverter;
import com.example.smartcanteen.application.port.OperationalStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.DailyMenu;
import com.example.smartcanteen.domain.DailyMenuItem;
import com.example.smartcanteen.domain.Dish;
import com.example.smartcanteen.domain.DishIngredient;
import com.example.smartcanteen.domain.DashboardSummary;
import com.example.smartcanteen.domain.Ingredient;
import com.example.smartcanteen.domain.InventoryLine;
import com.example.smartcanteen.domain.Nutrition;
import com.example.smartcanteen.domain.OperationalLedgerRecord;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.domain.PurchaseOrder;
import com.example.smartcanteen.domain.PurchaseOrderItem;
import com.example.smartcanteen.domain.Supplier;
import com.example.smartcanteen.domain.TraceabilityResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcOperationalStore implements OperationalStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final IngredientQuantityConverter quantityConverter;

    public JdbcOperationalStore(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            IngredientQuantityConverter quantityConverter) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.quantityConverter = quantityConverter;
    }

    @Override
    public PageResult<Ingredient> listIngredients(
            CanteenScope scope, String keyword, String category, int page, int size) {
        requirePage(page, size);
        StringBuilder where = new StringBuilder(
                " WHERE school_id = ? AND canteen_id = ? ");
        List<Object> params = new ArrayList<>(List.of(scope.schoolId(), scope.canteenId()));
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (ingredient_id LIKE ? OR name LIKE ?)");
            String value = "%" + keyword.trim() + "%";
            params.add(value);
            params.add(value);
        }
        if (category != null && !category.isBlank()) {
            where.append(" AND category = ?");
            params.add(category.trim());
        }
        long total = count("SELECT COUNT(*) FROM ingredients" + where, params);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add(offset(page, size));
        List<Ingredient> records = jdbc.query(
                "SELECT * FROM ingredients" + where
                        + " ORDER BY name, ingredient_id LIMIT ? OFFSET ?",
                ingredientMapper(),
                pageParams.toArray());
        return new PageResult<>(records, page, size, total);
    }

    @Override
    public Optional<Ingredient> findIngredient(CanteenScope scope, String ingredientId) {
        return jdbc.query(
                        "SELECT * FROM ingredients WHERE school_id = ? AND canteen_id = ? "
                                + "AND ingredient_id = ?",
                        ingredientMapper(),
                        scope.schoolId(),
                        scope.canteenId(),
                        ingredientId)
                .stream()
                .findFirst();
    }

    @Override
    public void createIngredient(CanteenScope scope, Ingredient ingredient) {
        try {
            jdbc.update(
                    "INSERT INTO ingredients (school_id, canteen_id, ingredient_id, name, "
                            + "category, base_unit, specification, energy_kcal, protein_g, fat_g, "
                            + "carbohydrate_g, warning_threshold, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    scope.schoolId(),
                    scope.canteenId(),
                    ingredient.id(),
                    ingredient.name(),
                    ingredient.category(),
                    ingredient.baseUnit(),
                    ingredient.specification(),
                    ingredient.nutrition().energyKcal(),
                    ingredient.nutrition().proteinG(),
                    ingredient.nutrition().fatG(),
                    ingredient.nutrition().carbohydrateG(),
                    ingredient.warningThreshold(),
                    ingredient.active() ? "ACTIVE" : "DISABLED");
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("Ingredient ID or name already exists");
        }
    }

    @Override
    public void updateIngredient(CanteenScope scope, Ingredient ingredient) {
        int changed = jdbc.update(
                "UPDATE ingredients SET name = ?, category = ?, base_unit = ?, specification = ?, "
                        + "energy_kcal = ?, protein_g = ?, fat_g = ?, carbohydrate_g = ?, "
                        + "warning_threshold = ?, status = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE school_id = ? AND canteen_id = ? AND ingredient_id = ?",
                ingredient.name(),
                ingredient.category(),
                ingredient.baseUnit(),
                ingredient.specification(),
                ingredient.nutrition().energyKcal(),
                ingredient.nutrition().proteinG(),
                ingredient.nutrition().fatG(),
                ingredient.nutrition().carbohydrateG(),
                ingredient.warningThreshold(),
                ingredient.active() ? "ACTIVE" : "DISABLED",
                scope.schoolId(),
                scope.canteenId(),
                ingredient.id());
        if (changed != 1) {
            throw new IllegalArgumentException("Ingredient not found: " + ingredient.id());
        }
    }

    @Override
    public PageResult<Dish> listDishes(
            CanteenScope scope, String keyword, String category, int page, int size) {
        requirePage(page, size);
        StringBuilder where = new StringBuilder(
                " WHERE school_id = ? AND canteen_id = ? ");
        List<Object> params = new ArrayList<>(List.of(scope.schoolId(), scope.canteenId()));
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (dish_id LIKE ? OR name LIKE ?)");
            String value = "%" + keyword.trim() + "%";
            params.add(value);
            params.add(value);
        }
        if (category != null && !category.isBlank()) {
            where.append(" AND category = ?");
            params.add(category.trim());
        }
        long total = count("SELECT COUNT(*) FROM dishes" + where, params);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add(offset(page, size));
        List<Dish> records = jdbc.query(
                "SELECT * FROM dishes" + where + " ORDER BY name, dish_id LIMIT ? OFFSET ?",
                dishMapper(scope),
                pageParams.toArray());
        return new PageResult<>(records, page, size, total);
    }

    @Override
    public Optional<Dish> findDish(CanteenScope scope, String dishId) {
        return jdbc.query(
                        "SELECT * FROM dishes WHERE school_id = ? AND canteen_id = ? AND dish_id = ?",
                        dishMapper(scope),
                        scope.schoolId(),
                        scope.canteenId(),
                        dishId)
                .stream()
                .findFirst();
    }

    @Override
    public void createDish(CanteenScope scope, Dish dish) {
        try {
            jdbc.update(
                    "INSERT INTO dishes (school_id, canteen_id, dish_id, name, category, "
                            + "description, image_url, status, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)",
                    scope.schoolId(),
                    scope.canteenId(),
                    dish.id(),
                    dish.name(),
                    dish.category(),
                    dish.description(),
                    dish.imageUrl(),
                    dish.active() ? "ACTIVE" : "DISABLED");
            replaceDishIngredients(scope, dish);
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("Dish ID or name already exists");
        }
    }

    @Override
    public void updateDish(CanteenScope scope, Dish dish) {
        int changed = jdbc.update(
                "UPDATE dishes SET name = ?, category = ?, description = ?, image_url = ?, "
                        + "status = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE school_id = ? AND canteen_id = ? AND dish_id = ? AND version = ?",
                dish.name(),
                dish.category(),
                dish.description(),
                dish.imageUrl(),
                dish.active() ? "ACTIVE" : "DISABLED",
                scope.schoolId(),
                scope.canteenId(),
                dish.id(),
                dish.version());
        if (changed != 1) {
            throw new IllegalStateException("Dish was changed concurrently or not found: " + dish.id());
        }
        replaceDishIngredients(scope, dish);
    }

    private void replaceDishIngredients(CanteenScope scope, Dish dish) {
        jdbc.update(
                "DELETE FROM dish_ingredients WHERE school_id = ? AND canteen_id = ? AND dish_id = ?",
                scope.schoolId(),
                scope.canteenId(),
                dish.id());
        for (DishIngredient item : dish.ingredients()) {
            jdbc.update(
                    "INSERT INTO dish_ingredients (school_id, canteen_id, dish_id, ingredient_id, quantity, unit) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    scope.schoolId(),
                    scope.canteenId(),
                    dish.id(),
                    item.ingredientId(),
                    item.quantity(),
                    item.unit());
        }
    }

    @Override
    public PageResult<DailyMenu> listDailyMenus(
            CanteenScope scope, LocalDate from, LocalDate to, int page, int size) {
        requirePage(page, size);
        long total = count(
                "SELECT COUNT(*) FROM daily_menus WHERE school_id = ? AND canteen_id = ? "
                        + "AND menu_date BETWEEN ? AND ?",
                List.of(scope.schoolId(), scope.canteenId(), java.sql.Date.valueOf(from),
                        java.sql.Date.valueOf(to)));
        List<DailyMenu> records = jdbc.query(
                "SELECT * FROM daily_menus WHERE school_id = ? AND canteen_id = ? "
                        + "AND menu_date BETWEEN ? AND ? ORDER BY menu_date DESC, meal_time "
                        + "LIMIT ? OFFSET ?",
                dailyMenuMapper(scope),
                scope.schoolId(),
                scope.canteenId(),
                java.sql.Date.valueOf(from),
                java.sql.Date.valueOf(to),
                size,
                offset(page, size));
        return new PageResult<>(records, page, size, total);
    }

    @Override
    public Optional<DailyMenu> findDailyMenu(CanteenScope scope, String menuId) {
        return jdbc.query(
                        "SELECT * FROM daily_menus WHERE school_id = ? AND canteen_id = ? AND menu_id = ?",
                        dailyMenuMapper(scope),
                        scope.schoolId(),
                        scope.canteenId(),
                        menuId)
                .stream()
                .findFirst();
    }

    @Override
    public void saveDailyMenu(CanteenScope scope, DailyMenu menu, boolean create) {
        try {
            if (create) {
                jdbc.update(
                        "INSERT INTO daily_menus (school_id, canteen_id, menu_id, menu_date, meal_time, status, version) "
                                + "VALUES (?, ?, ?, ?, ?, 'DRAFT', 0)",
                        scope.schoolId(),
                        scope.canteenId(),
                        menu.id(),
                        java.sql.Date.valueOf(menu.menuDate()),
                        menu.mealTime());
            } else {
                int changed = jdbc.update(
                        "UPDATE daily_menus SET menu_date = ?, meal_time = ?, updated_at = CURRENT_TIMESTAMP, "
                                + "version = version + 1 WHERE school_id = ? AND canteen_id = ? AND menu_id = ? "
                                + "AND status IN ('DRAFT', 'REJECTED') AND version = ?",
                        java.sql.Date.valueOf(menu.menuDate()),
                        menu.mealTime(),
                        scope.schoolId(),
                        scope.canteenId(),
                        menu.id(),
                        menu.version());
                if (changed != 1) {
                    throw new IllegalStateException("Daily menu was changed concurrently: " + menu.id());
                }
            }
            jdbc.update(
                    "DELETE FROM daily_menu_items WHERE school_id = ? AND canteen_id = ? AND menu_id = ?",
                    scope.schoolId(),
                    scope.canteenId(),
                    menu.id());
            for (DailyMenuItem item : menu.items()) {
                jdbc.update(
                        "INSERT INTO daily_menu_items (school_id, canteen_id, menu_id, dish_id, estimated_quantity, sort_order) "
                                + "VALUES (?, ?, ?, ?, ?, ?)",
                        scope.schoolId(),
                        scope.canteenId(),
                        menu.id(),
                        item.dishId(),
                        item.estimatedQuantity(),
                        item.sortOrder());
            }
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("Daily menu ID or date/meal slot already exists");
        }
    }

    @Override
    public void submitDailyMenu(
            CanteenScope scope, String menuId, long expectedVersion, String actorUserId) {
        int changed = jdbc.update(
                "UPDATE daily_menus SET status = 'PENDING_APPROVAL', submitted_by = ?, "
                        + "decision_by = NULL, decision_comment = NULL, published_by = NULL, "
                        + "version = version + 1, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE school_id = ? AND canteen_id = ? AND menu_id = ? "
                        + "AND status IN ('DRAFT', 'REJECTED') AND version = ?",
                actorUserId,
                scope.schoolId(),
                scope.canteenId(),
                menuId,
                expectedVersion);
        if (changed != 1) {
            throw new IllegalStateException("Daily menu cannot be submitted: " + menuId);
        }
    }

    @Override
    public void decideDailyMenu(
            CanteenScope scope,
            String menuId,
            long expectedVersion,
            String decision,
            String comment,
            String actorUserId) {
        String status = "APPROVE".equals(decision) ? "APPROVED" : "REJECTED";
        int changed = jdbc.update(
                "UPDATE daily_menus SET status = ?, decision_by = ?, decision_comment = ?, "
                        + "version = version + 1, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE school_id = ? AND canteen_id = ? AND menu_id = ? "
                        + "AND status = 'PENDING_APPROVAL' AND version = ?",
                status,
                actorUserId,
                comment,
                scope.schoolId(),
                scope.canteenId(),
                menuId,
                expectedVersion);
        if (changed != 1) {
            throw new IllegalStateException("Daily menu cannot record a decision: " + menuId);
        }
    }

    @Override
    public void publishDailyMenu(
            CanteenScope scope, String menuId, long expectedVersion, String actorUserId) {
        int changed = jdbc.update(
                "UPDATE daily_menus SET status = 'PUBLISHED', published_by = ?, "
                        + "version = version + 1, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE school_id = ? AND canteen_id = ? "
                        + "AND menu_id = ? AND status = 'APPROVED' AND version = ?",
                actorUserId,
                scope.schoolId(),
                scope.canteenId(),
                menuId,
                expectedVersion);
        if (changed != 1) {
            throw new IllegalStateException("Daily menu cannot be published: " + menuId);
        }
    }

    @Override
    public PageResult<Supplier> listSuppliers(
            CanteenScope scope, String keyword, int page, int size) {
        requirePage(page, size);
        StringBuilder where = new StringBuilder(
                " WHERE school_id = ? AND canteen_id = ? ");
        List<Object> params = new ArrayList<>(List.of(scope.schoolId(), scope.canteenId()));
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (supplier_id LIKE ? OR name LIKE ?)");
            String value = "%" + keyword.trim() + "%";
            params.add(value);
            params.add(value);
        }
        long total = count("SELECT COUNT(*) FROM suppliers" + where, params);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add(offset(page, size));
        List<Supplier> records = jdbc.query(
                "SELECT * FROM suppliers" + where + " ORDER BY name, supplier_id LIMIT ? OFFSET ?",
                supplierMapper(),
                pageParams.toArray());
        return new PageResult<>(records, page, size, total);
    }

    @Override
    public Optional<Supplier> findSupplier(CanteenScope scope, String supplierId) {
        return jdbc.query(
                        "SELECT * FROM suppliers WHERE school_id = ? AND canteen_id = ? AND supplier_id = ?",
                        supplierMapper(),
                        scope.schoolId(),
                        scope.canteenId(),
                        supplierId)
                .stream()
                .findFirst();
    }

    @Override
    public void createSupplier(CanteenScope scope, Supplier supplier) {
        try {
            jdbc.update(
                    "INSERT INTO suppliers (school_id, canteen_id, supplier_id, name, contact_name, "
                            + "contact_phone, license_no, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    scope.schoolId(),
                    scope.canteenId(),
                    supplier.id(),
                    supplier.name(),
                    supplier.contactName(),
                    supplier.contactPhone(),
                    supplier.licenseNo(),
                    supplier.active() ? "ACTIVE" : "DISABLED");
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("Supplier ID or name already exists");
        }
    }

    @Override
    public PageResult<PurchaseOrder> listPurchaseOrders(
            CanteenScope scope, String status, int page, int size) {
        requirePage(page, size);
        StringBuilder where = new StringBuilder(
                " WHERE school_id = ? AND canteen_id = ? ");
        List<Object> params = new ArrayList<>(List.of(scope.schoolId(), scope.canteenId()));
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            params.add(status.trim().toUpperCase());
        }
        long total = count("SELECT COUNT(*) FROM purchase_orders" + where, params);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add(offset(page, size));
        List<PurchaseOrder> records = jdbc.query(
                "SELECT * FROM purchase_orders" + where
                        + " ORDER BY created_at DESC, order_no DESC LIMIT ? OFFSET ?",
                purchaseOrderMapper(scope),
                pageParams.toArray());
        return new PageResult<>(records, page, size, total);
    }

    @Override
    public Optional<PurchaseOrder> findPurchaseOrder(CanteenScope scope, String orderId) {
        return jdbc.query(
                        "SELECT * FROM purchase_orders WHERE school_id = ? AND canteen_id = ? AND order_id = ?",
                        purchaseOrderMapper(scope),
                        scope.schoolId(),
                        scope.canteenId(),
                        orderId)
                .stream()
                .findFirst();
    }

    @Override
    public PurchaseOrder createPurchaseOrder(
            CanteenScope scope, PurchaseOrder order, String idempotencyKey) {
        try {
            jdbc.update(
                    "INSERT INTO purchase_orders (school_id, canteen_id, order_id, order_no, supplier_id, "
                            + "order_type, status, expected_delivery_at, total_amount, remark, idempotency_key) "
                            + "VALUES (?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, ?, ?)",
                    scope.schoolId(),
                    scope.canteenId(),
                    order.id(),
                    order.orderNo(),
                    order.supplierId(),
                    order.orderType(),
                    timestamp(order.expectedDeliveryAt()),
                    order.totalAmount(),
                    order.remark(),
                    idempotencyKey);
            for (PurchaseOrderItem item : order.items()) {
                jdbc.update(
                        "INSERT INTO purchase_order_items (school_id, canteen_id, order_id, ingredient_id, "
                                + "quantity, unit, unit_price, amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        scope.schoolId(),
                        scope.canteenId(),
                        order.id(),
                        item.ingredientId(),
                        item.quantity(),
                        item.unit(),
                        item.unitPrice(),
                        item.amount().setScale(2, RoundingMode.HALF_UP));
            }
        } catch (DuplicateKeyException exception) {
            Optional<PurchaseOrder> existing = findPurchaseOrderByIdempotency(scope, idempotencyKey);
            if (existing.isPresent()) {
                PurchaseOrder value = existing.get();
                if (!samePurchaseOrder(value, order)) {
                    throw new IllegalArgumentException(
                        "Idempotency-Key was already used for a different purchase order");
                }
                return value;
            }
            throw new IllegalArgumentException("Purchase order ID or order number already exists");
        }
        return findPurchaseOrder(scope, order.id())
                .orElseThrow(() -> new IllegalStateException("Purchase order was not persisted"));
    }

    @Override
    public PurchaseOrder transitionPurchaseOrder(
            CanteenScope scope, String orderId, String targetStatus) {
        PurchaseOrder existing = findPurchaseOrder(scope, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found: " + orderId));
        if (!validTransition(existing.status(), targetStatus)) {
            throw new IllegalStateException(
                    "Purchase order cannot transition from " + existing.status() + " to " + targetStatus);
        }
        int changed = jdbc.update(
                "UPDATE purchase_orders SET status = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE school_id = ? AND canteen_id = ? AND order_id = ? AND status = ?",
                targetStatus,
                scope.schoolId(),
                scope.canteenId(),
                orderId,
                existing.status());
        if (changed != 1) {
            throw new IllegalStateException("Purchase order was changed concurrently: " + orderId);
        }
        return findPurchaseOrder(scope, orderId)
                .orElseThrow(() -> new IllegalStateException("Purchase order disappeared: " + orderId));
    }

    @Override
    public ReceiveResult receivePurchaseOrder(
            CanteenScope scope,
            String orderId,
            String idempotencyKey,
            List<ReceiveItem> items) {
        PurchaseOrder order = findPurchaseOrder(scope, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase order not found: " + orderId));
        Optional<ReceiveResult> previous = findReceiptByIdempotency(scope, idempotencyKey);
        if (previous.isPresent()) {
            if (!previous.get().orderId().equals(orderId)) {
                throw new IllegalArgumentException(
                        "Idempotency-Key was already used for a different purchase receipt");
            }
            // An empty body means "receive the remaining quantity". It is intentionally
            // accepted on an idempotent retry of an explicit receipt.
            if (items != null && !items.isEmpty()) {
                ensureSameReceipt(scope, previous.get(), order.supplierId(), items);
            }
            return previous.get();
        }
        if (!"CONFIRMED".equals(order.status())) {
            throw new IllegalStateException("Purchase order cannot be received in status " + order.status());
        }
        Map<String, PurchaseOrderItem> ordered = new HashMap<>();
        for (PurchaseOrderItem item : order.items()) {
            ordered.put(item.ingredientId(), item);
        }
        List<ReceiveItem> effectiveItems = items == null || items.isEmpty()
                ? remainingItems(scope, order)
                : List.copyOf(items);
        if (effectiveItems.isEmpty()) {
            throw new IllegalStateException("Purchase order has no remaining quantity: " + orderId);
        }
        Set<String> receivedIngredients = new HashSet<>();
        for (ReceiveItem item : effectiveItems) {
            PurchaseOrderItem orderItem = ordered.get(item.ingredientId());
            if (orderItem == null || !receivedIngredients.add(item.ingredientId())) {
                throw new IllegalArgumentException(
                        "Received ingredient is not a unique order line: " + item.ingredientId());
            }
            BigDecimal receivedBase = toIngredientBase(
                    scope, item.ingredientId(), item.quantity(), item.unit()).quantity();
            BigDecimal orderedBase = toIngredientBase(
                    scope, item.ingredientId(), orderItem.quantity(), orderItem.unit()).quantity();
            BigDecimal alreadyReceived = receivedQuantityForUpdate(
                    scope, orderId, item.ingredientId());
            if (receivedBase.compareTo(orderedBase.subtract(alreadyReceived)) > 0) {
                throw new IllegalArgumentException(
                        "Received quantity exceeds remaining ordered quantity for " + item.ingredientId());
            }
        }
        String receiptId = "RECEIPT-" + UUID.randomUUID();
        try {
            jdbc.update(
                    "INSERT INTO purchase_receipts (school_id, canteen_id, receipt_id, order_id, idempotency_key) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    scope.schoolId(),
                    scope.canteenId(),
                    receiptId,
                    orderId,
                    idempotencyKey);
        } catch (DuplicateKeyException exception) {
            ReceiveResult raced = findReceiptByIdempotency(scope, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Receipt idempotency race was not recoverable"));
            if (!raced.orderId().equals(orderId)) {
                throw new IllegalArgumentException(
                        "Idempotency-Key was already used for a different purchase receipt");
            }
            if (items != null && !items.isEmpty()) {
                ensureSameReceipt(scope, raced, order.supplierId(), items);
            }
            return raced;
        }
        List<String> traceCodes = new ArrayList<>();
        for (ReceiveItem item : effectiveItems) {
            IngredientData ingredient = findIngredientData(scope, item.ingredientId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown ingredient: " + item.ingredientId()));
            BaseAmount amount = toIngredientBase(
                    scope, item.ingredientId(), item.quantity(), item.unit());
            String batchId = "BATCH-" + UUID.randomUUID();
            String traceCode = "TRACE-" + UUID.randomUUID();
            jdbc.update(
                    "INSERT INTO inventory_batches (school_id, canteen_id, batch_id, order_id, ingredient_id, "
                            + "supplier_id, batch_no, quantity_base, base_unit, purchase_price, production_date, "
                            + "expiry_date, trace_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    scope.schoolId(),
                    scope.canteenId(),
                    batchId,
                    orderId,
                    item.ingredientId(),
                    order.supplierId(),
                    item.batchNo() == null || item.batchNo().isBlank()
                            ? batchId : item.batchNo(),
                    amount.quantity(),
                    amount.unit(),
                    item.purchasePrice(),
                    item.productionDate() == null ? null : java.sql.Date.valueOf(item.productionDate()),
                    item.expiryDate() == null ? null : java.sql.Date.valueOf(item.expiryDate()),
                    traceCode);
            jdbc.update(
                "INSERT INTO purchase_receipt_items (school_id, canteen_id, receipt_id, batch_id, ingredient_id, "
                            + "quantity_base, base_unit) VALUES (?, ?, ?, ?, ?, ?, ?)",
                    scope.schoolId(),
                    scope.canteenId(),
                    receiptId,
                    batchId,
                    item.ingredientId(),
                    amount.quantity(),
                    amount.unit());
            int updated = jdbc.update(
                    "UPDATE purchase_order_items SET received_quantity_base = received_quantity_base + ? "
                            + "WHERE school_id = ? AND canteen_id = ? AND order_id = ? AND ingredient_id = ?",
                    amount.quantity(),
                    scope.schoolId(),
                    scope.canteenId(),
                    orderId,
                    item.ingredientId());
            if (updated != 1) {
                throw new IllegalStateException("Purchase order line disappeared: " + item.ingredientId());
            }
            addInventory(scope, item.ingredientId(), ingredient, amount);
            jdbc.update(
                    "INSERT INTO traceability_records (school_id, canteen_id, trace_code, batch_id, order_id, "
                            + "ingredient_id, supplier_id, quantity_base, base_unit) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    scope.schoolId(),
                    scope.canteenId(),
                    traceCode,
                    batchId,
                    orderId,
                    item.ingredientId(),
                    order.supplierId(),
                    amount.quantity(),
                    amount.unit());
            traceCodes.add(traceCode);
        }
        boolean complete = true;
        for (PurchaseOrderItem item : order.items()) {
            BigDecimal orderedBase = toIngredientBase(
                    scope, item.ingredientId(), item.quantity(), item.unit()).quantity();
            if (receivedQuantityForUpdate(scope, orderId, item.ingredientId())
                    .compareTo(orderedBase) < 0) {
                complete = false;
                break;
            }
        }
        if (complete) {
            jdbc.update(
                    "UPDATE purchase_orders SET status = 'RECEIVED', updated_at = CURRENT_TIMESTAMP "
                            + "WHERE school_id = ? AND canteen_id = ? AND order_id = ? AND status = 'CONFIRMED'",
                    scope.schoolId(), scope.canteenId(), orderId);
        }
        return new ReceiveResult(orderId, receiptId, traceCodes);
    }

    @Override
    public ReceiveResult receiveInventory(
            CanteenScope scope,
            String idempotencyKey,
            String supplierId,
            ReceiveItem item) {
        Optional<ReceiveResult> previous = findReceiptByIdempotency(scope, idempotencyKey);
        if (previous.isPresent()) {
            ensureDirectReceipt(scope, idempotencyKey, previous.get());
            ensureSameReceipt(scope, previous.get(), supplierId, List.of(item));
            return previous.get();
        }
        IngredientData ingredient = findIngredientData(scope, item.ingredientId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown ingredient: " + item.ingredientId()));
        BaseAmount amount = toIngredientBase(scope, item.ingredientId(), item.quantity(), item.unit());
        String receiptId = "RECEIPT-" + UUID.randomUUID();
        try {
            jdbc.update(
                    "INSERT INTO purchase_receipts (school_id, canteen_id, receipt_id, order_id, idempotency_key) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    scope.schoolId(),
                    scope.canteenId(),
                    receiptId,
                    directReceiptOrderId(scope, idempotencyKey),
                    idempotencyKey);
        } catch (DuplicateKeyException exception) {
            ReceiveResult raced = findReceiptByIdempotency(scope, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "Receipt idempotency race was not recoverable"));
            ensureDirectReceipt(scope, idempotencyKey, raced);
            ensureSameReceipt(scope, raced, supplierId, List.of(item));
            return raced;
        }
        String batchId = "BATCH-" + UUID.randomUUID();
        String traceCode = "TRACE-" + UUID.randomUUID();
        String orderId = directReceiptOrderId(scope, idempotencyKey);
        jdbc.update(
                "INSERT INTO inventory_batches (school_id, canteen_id, batch_id, order_id, ingredient_id, "
                        + "supplier_id, batch_no, quantity_base, base_unit, purchase_price, production_date, "
                        + "expiry_date, trace_code) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                scope.schoolId(),
                scope.canteenId(),
                batchId,
                orderId,
                item.ingredientId(),
                supplierId,
                item.batchNo(),
                amount.quantity(),
                amount.unit(),
                item.purchasePrice(),
                item.productionDate() == null ? null : java.sql.Date.valueOf(item.productionDate()),
                item.expiryDate() == null ? null : java.sql.Date.valueOf(item.expiryDate()),
                traceCode);
        jdbc.update(
                "INSERT INTO purchase_receipt_items (school_id, canteen_id, receipt_id, batch_id, ingredient_id, "
                        + "quantity_base, base_unit) VALUES (?, ?, ?, ?, ?, ?, ?)",
                scope.schoolId(),
                scope.canteenId(),
                receiptId,
                batchId,
                item.ingredientId(),
                amount.quantity(),
                amount.unit());
        addInventory(scope, item.ingredientId(), ingredient, amount);
        jdbc.update(
                "INSERT INTO traceability_records (school_id, canteen_id, trace_code, batch_id, order_id, "
                        + "ingredient_id, supplier_id, quantity_base, base_unit) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                scope.schoolId(),
                scope.canteenId(),
                traceCode,
                batchId,
                orderId,
                item.ingredientId(),
                supplierId,
                amount.quantity(),
                amount.unit());
        return new ReceiveResult(orderId, receiptId, List.of(traceCode));
    }

    private static void ensureDirectReceipt(
            CanteenScope scope, String idempotencyKey, ReceiveResult stored) {
        if (!directReceiptOrderId(scope, idempotencyKey).equals(stored.orderId())) {
            throw new IllegalArgumentException(
                    "Idempotency-Key was already used for a different purchase receipt");
        }
    }

    private static String directReceiptOrderId(CanteenScope scope, String idempotencyKey) {
        UUID stable = UUID.nameUUIDFromBytes((scope.schoolId() + ":" + scope.canteenId() + ":"
                + idempotencyKey).getBytes(StandardCharsets.UTF_8));
        return "DIRECT-" + stable;
    }

    @Override
    public PageResult<InventoryLine> listInventory(
            CanteenScope scope, String keyword, boolean warningOnly, int page, int size) {
        requirePage(page, size);
        StringBuilder where = new StringBuilder(
                " WHERE i.school_id = ? AND i.canteen_id = ? ");
        List<Object> params = new ArrayList<>(List.of(scope.schoolId(), scope.canteenId()));
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (i.material_id LIKE ? OR g.name LIKE ?)");
            String value = "%" + keyword.trim() + "%";
            params.add(value);
            params.add(value);
        }
        if (warningOnly) {
            where.append(" AND i.quantity_base <= i.warning_threshold");
        }
        String from = " FROM inventory i LEFT JOIN ingredients g ON g.school_id = i.school_id "
                + "AND g.canteen_id = i.canteen_id AND g.ingredient_id = i.material_id";
        long total = count("SELECT COUNT(*)" + from + where, params);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add(offset(page, size));
        List<InventoryLine> records = jdbc.query(
                "SELECT i.material_id, g.name, g.category, i.quantity_base, i.base_unit, "
                        + "i.warning_threshold, i.last_update_time" + from + where
                        + " ORDER BY i.material_id LIMIT ? OFFSET ?",
                (result, row) -> new InventoryLine(
                        result.getString("material_id"),
                        result.getString("name"),
                        result.getString("category"),
                        result.getBigDecimal("quantity_base"),
                        result.getString("base_unit"),
                        result.getBigDecimal("warning_threshold"),
                        result.getBigDecimal("quantity_base")
                                .compareTo(result.getBigDecimal("warning_threshold")) <= 0,
                        instant(result.getTimestamp("last_update_time"))),
                pageParams.toArray());
        return new PageResult<>(records, page, size, total);
    }

    @Override
    public StockOutResult stockOut(
            CanteenScope scope,
            String idempotencyKey,
            String reason,
            List<StockOutItem> items) {
        Optional<StockOutResult> previous = findStockOutByIdempotency(scope, idempotencyKey);
        if (previous.isPresent()) {
            ensureSameStockOut(scope, previous.get(), reason, items);
            return previous.get();
        }
        String stockOutId = "STOCK-OUT-" + UUID.randomUUID();
        try {
            jdbc.update(
                    "INSERT INTO stock_out_records (school_id, canteen_id, stock_out_id, idempotency_key, reason) "
                            + "VALUES (?, ?, ?, ?, ?)",
                    scope.schoolId(),
                    scope.canteenId(),
                    stockOutId,
                    idempotencyKey,
                    reason);
        } catch (DuplicateKeyException exception) {
            StockOutResult raced = findStockOutByIdempotency(scope, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException("Stock-out idempotency race was not recoverable"));
            ensureSameStockOut(scope, raced, reason, items);
            return raced;
        }
        List<StockOutItem> persistedItems = new ArrayList<>();
        for (StockOutItem item : items) {
            IngredientData ingredient = findIngredientData(scope, item.ingredientId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown ingredient: " + item.ingredientId()));
            BaseAmount amount = toIngredientBase(
                    scope, item.ingredientId(), item.quantity(), item.unit());
            int changed = jdbc.update(
                    "UPDATE inventory SET quantity_base = quantity_base - ?, last_update_time = CURRENT_TIMESTAMP "
                            + "WHERE school_id = ? AND canteen_id = ? AND material_id = ? "
                            + "AND base_unit = ? AND quantity_base >= ?",
                    amount.quantity(),
                    scope.schoolId(),
                    scope.canteenId(),
                    item.ingredientId(),
                    amount.unit(),
                    amount.quantity());
            if (changed != 1) {
                Optional<String> unit = inventoryUnit(scope, item.ingredientId());
                if (unit.isPresent() && !unit.get().equals(amount.unit())) {
                    throw new IllegalArgumentException(
                            "Inventory unit mismatch for " + item.ingredientId());
                }
                throw new IllegalStateException("Insufficient inventory for " + item.ingredientId());
            }
            jdbc.update(
                "INSERT INTO stock_out_items (school_id, canteen_id, stock_out_id, ingredient_id, quantity_base, base_unit) "
                            + "VALUES (?, ?, ?, ?, ?, ?)",
                    scope.schoolId(),
                    scope.canteenId(),
                    stockOutId,
                    item.ingredientId(),
                    amount.quantity(),
                    amount.unit());
            persistedItems.add(new StockOutItem(item.ingredientId(), amount.quantity(), amount.unit()));
        }
        return new StockOutResult(stockOutId, persistedItems);
    }

    @Override
    public OperationalLedgerRecord saveLedgerRecord(
            CanteenScope scope, OperationalLedgerRecord record) {
        Integer configured = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_cycle_requirements WHERE school_id = ? AND canteen_id = ? "
                        + "AND cycle_id = ? AND ledger_code = ?",
                Integer.class,
                scope.schoolId(),
                scope.canteenId(),
                record.cycleId(),
                record.ledgerCode());
        if (!Integer.valueOf(1).equals(configured)) {
            throw new IllegalArgumentException(
                    "Ledger code is not configured for cycle: " + record.ledgerCode());
        }
        String content = writeJson(record.content());
        String photos = writeJson(record.photos());
        try {
            jdbc.update(
                    "INSERT INTO operational_ledger_records (school_id, canteen_id, record_id, cycle_id, ledger_code, "
                            + "record_time, recorder_id, content_json, photos_json, status, remark) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    scope.schoolId(),
                    scope.canteenId(),
                    record.id(),
                    record.cycleId(),
                    record.ledgerCode(),
                    timestamp(record.recordTime()),
                    record.recorderId(),
                    content,
                    photos,
                    record.status(),
                    record.remark());
        } catch (DuplicateKeyException exception) {
            OperationalLedgerRecord existing = findLedgerByCycleAndCode(
                    scope, record.cycleId(), record.ledgerCode())
                    .orElseThrow(() -> exception);
            if (!writeJson(existing.content()).equals(content)
                    || !writeJson(existing.photos()).equals(photos)) {
                throw new IllegalArgumentException(
                        "Ledger code already has a different record in this cycle");
            }
            return existing;
        }
        jdbc.update(
                "UPDATE ledger_cycle_requirements SET completed = TRUE, completed_at = COALESCE(completed_at, CURRENT_TIMESTAMP) "
                        + "WHERE school_id = ? AND canteen_id = ? AND cycle_id = ? AND ledger_code = ?",
                scope.schoolId(),
                scope.canteenId(),
                record.cycleId(),
                record.ledgerCode());
        syncLedgerAlert(scope, record.cycleId());
        return findLedgerById(scope, record.id())
                .orElseThrow(() -> new IllegalStateException("Ledger record was not persisted"));
    }

    @Override
    public PageResult<OperationalLedgerRecord> listLedgerRecords(
            CanteenScope scope,
            String cycleId,
            String ledgerCode,
            String status,
            Instant from,
            Instant to,
            int page,
            int size) {
        requirePage(page, size);
        StringBuilder where = new StringBuilder(
                " WHERE school_id = ? AND canteen_id = ? ");
        List<Object> params = new ArrayList<>(List.of(scope.schoolId(), scope.canteenId()));
        if (cycleId != null && !cycleId.isBlank()) {
            where.append(" AND cycle_id = ?");
            params.add(cycleId.trim());
        }
        if (ledgerCode != null && !ledgerCode.isBlank()) {
            where.append(" AND ledger_code = ?");
            params.add(ledgerCode.trim());
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            params.add(status.trim().toUpperCase());
        }
        if (from != null) {
            where.append(" AND record_time >= ?");
            params.add(timestamp(from));
        }
        if (to != null) {
            where.append(" AND record_time <= ?");
            params.add(timestamp(to));
        }
        long total = count("SELECT COUNT(*) FROM operational_ledger_records" + where, params);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add(offset(page, size));
        List<OperationalLedgerRecord> records = jdbc.query(
                "SELECT * FROM operational_ledger_records" + where
                        + " ORDER BY record_time DESC, record_id DESC LIMIT ? OFFSET ?",
                ledgerMapper(),
                pageParams.toArray());
        return new PageResult<>(records, page, size, total);
    }

    @Override
    public LedgerStats ledgerStats(CanteenScope scope, LocalDate from, LocalDate to) {
        Timestamp start = Timestamp.valueOf(from.atStartOfDay());
        Timestamp end = Timestamp.valueOf(to.plusDays(1).atStartOfDay().minusNanos(1));
        long expected = count(
                "SELECT COUNT(*) FROM ledger_cycle_requirements r JOIN ledger_cycles c "
                        + "ON c.school_id = r.school_id AND c.canteen_id = r.canteen_id AND c.id = r.cycle_id "
                        + "WHERE r.school_id = ? AND r.canteen_id = ? AND c.period_start <= ? AND c.period_end >= ?",
                List.of(scope.schoolId(), scope.canteenId(), java.sql.Date.valueOf(to),
                        java.sql.Date.valueOf(from)));
        long completed = count(
                "SELECT COUNT(*) FROM operational_ledger_records WHERE school_id = ? AND canteen_id = ? "
                        + "AND status = 'COMPLETED' AND record_time BETWEEN ? AND ?",
                List.of(scope.schoolId(), scope.canteenId(), start, end));
        return new LedgerStats(expected, completed, Math.max(0, expected - completed));
    }

    @Override
    public DashboardSummary dashboardSummary(CanteenScope scope, LocalDate date) {
        Timestamp start = Timestamp.valueOf(date.atStartOfDay());
        Timestamp end = Timestamp.valueOf(date.plusDays(1).atStartOfDay());
        long todayMenus = count(
                "SELECT COUNT(*) FROM daily_menus WHERE school_id = ? AND canteen_id = ? AND menu_date = ?",
                List.of(scope.schoolId(), scope.canteenId(), java.sql.Date.valueOf(date)));
        long publishedMenus = count(
                "SELECT COUNT(*) FROM daily_menus WHERE school_id = ? AND canteen_id = ? "
                        + "AND menu_date = ? AND status = 'PUBLISHED'",
                List.of(scope.schoolId(), scope.canteenId(), java.sql.Date.valueOf(date)));
        long pendingOrders = count(
                "SELECT COUNT(*) FROM purchase_orders WHERE school_id = ? AND canteen_id = ? "
                        + "AND status IN ('DRAFT', 'SUBMITTED', 'CONFIRMED')",
                List.of(scope.schoolId(), scope.canteenId()));
        long warnings = count(
                "SELECT COUNT(*) FROM inventory WHERE school_id = ? AND canteen_id = ? "
                        + "AND quantity_base <= warning_threshold",
                List.of(scope.schoolId(), scope.canteenId()));
        long openLedger = count(
                "SELECT COUNT(*) FROM ledger_alerts WHERE school_id = ? AND canteen_id = ? AND status = 'OPEN'",
                List.of(scope.schoolId(), scope.canteenId()));
        long openAlerts = count(
                "SELECT COUNT(*) FROM alert_records WHERE school_id = ? AND status = 'UNPROCESSED' "
                        + "AND (canteen_id = ? OR canteen_id IS NULL)",
                List.of(scope.schoolId(), scope.canteenId()));
        BigDecimal purchaseAmount = jdbc.queryForObject(
                "SELECT COALESCE(SUM(total_amount), 0) FROM purchase_orders WHERE school_id = ? AND canteen_id = ? "
                        + "AND created_at >= ? AND created_at < ? AND status <> 'CANCELLED'",
                BigDecimal.class,
                scope.schoolId(),
                scope.canteenId(),
                start,
                end);
        return new DashboardSummary(
                date,
                todayMenus,
                publishedMenus,
                pendingOrders,
                warnings,
                openLedger,
                openAlerts,
                purchaseAmount == null ? BigDecimal.ZERO : purchaseAmount);
    }

    @Override
    public Optional<TraceabilityResult> trace(CanteenScope scope, String traceCode) {
        return jdbc.query(
                        "SELECT t.trace_code, t.batch_id, t.order_id, t.ingredient_id, g.name ingredient_name, "
                                + "t.supplier_id, s.name supplier_name, t.quantity_base, t.base_unit, b.created_at "
                                + "FROM traceability_records t "
                                + "LEFT JOIN ingredients g ON g.school_id = t.school_id AND g.canteen_id = t.canteen_id "
                                + "AND g.ingredient_id = t.ingredient_id "
                                + "LEFT JOIN suppliers s ON s.school_id = t.school_id AND s.canteen_id = t.canteen_id "
                                + "AND s.supplier_id = t.supplier_id "
                                + "LEFT JOIN inventory_batches b ON b.school_id = t.school_id AND b.canteen_id = t.canteen_id "
                                + "AND b.batch_id = t.batch_id "
                                + "WHERE t.school_id = ? AND t.canteen_id = ? AND t.trace_code = ?",
                        (result, row) -> new TraceabilityResult(
                                result.getString("trace_code"),
                                result.getString("batch_id"),
                                result.getString("order_id"),
                                result.getString("ingredient_id"),
                                result.getString("ingredient_name"),
                                result.getString("supplier_id"),
                                result.getString("supplier_name"),
                                result.getBigDecimal("quantity_base"),
                                result.getString("base_unit"),
                                instant(result.getTimestamp("created_at"))),
                        scope.schoolId(),
                        scope.canteenId(),
                        traceCode)
                .stream()
                .findFirst();
    }

    private RowMapper<Ingredient> ingredientMapper() {
        return (result, row) -> new Ingredient(
                result.getString("ingredient_id"),
                result.getString("name"),
                result.getString("category"),
                result.getString("base_unit"),
                result.getString("specification"),
                new Nutrition(
                        result.getBigDecimal("energy_kcal"),
                        result.getBigDecimal("protein_g"),
                        result.getBigDecimal("fat_g"),
                        result.getBigDecimal("carbohydrate_g")),
                result.getBigDecimal("warning_threshold"),
                "ACTIVE".equals(result.getString("status")));
    }

    private RowMapper<Dish> dishMapper(CanteenScope scope) {
        return (result, row) -> new Dish(
                result.getString("dish_id"),
                result.getString("name"),
                result.getString("category"),
                result.getString("description"),
                result.getString("image_url"),
                "ACTIVE".equals(result.getString("status")),
                result.getLong("version"),
                jdbc.query(
                        "SELECT ingredient_id, quantity, unit FROM dish_ingredients "
                                + "WHERE school_id = ? AND canteen_id = ? AND dish_id = ? ORDER BY ingredient_id",
                        (ingredient, itemRow) -> new DishIngredient(
                                ingredient.getString("ingredient_id"),
                                ingredient.getBigDecimal("quantity"),
                                ingredient.getString("unit")),
                        scope.schoolId(),
                        scope.canteenId(),
                        result.getString("dish_id")));
    }

    private RowMapper<DailyMenu> dailyMenuMapper(CanteenScope scope) {
        return (result, row) -> new DailyMenu(
                result.getString("menu_id"),
                result.getDate("menu_date").toLocalDate(),
                result.getString("meal_time"),
                result.getString("status"),
                result.getLong("version"),
                jdbc.query(
                        "SELECT dish_id, estimated_quantity, sort_order FROM daily_menu_items "
                                + "WHERE school_id = ? AND canteen_id = ? AND menu_id = ? ORDER BY sort_order, dish_id",
                        (item, itemRow) -> new DailyMenuItem(
                                item.getString("dish_id"),
                                item.getBigDecimal("estimated_quantity"),
                                item.getInt("sort_order")),
                        scope.schoolId(),
                        scope.canteenId(),
                        result.getString("menu_id")),
                result.getString("submitted_by"),
                result.getString("decision_by"),
                result.getString("decision_comment"),
                result.getString("published_by"));
    }

    private RowMapper<Supplier> supplierMapper() {
        return (result, row) -> new Supplier(
                result.getString("supplier_id"),
                result.getString("name"),
                result.getString("contact_name"),
                result.getString("contact_phone"),
                result.getString("license_no"),
                "ACTIVE".equals(result.getString("status")));
    }

    private RowMapper<PurchaseOrder> purchaseOrderMapper(CanteenScope scope) {
        return (result, row) -> new PurchaseOrder(
                result.getString("order_id"),
                result.getString("order_no"),
                result.getString("supplier_id"),
                result.getString("order_type"),
                result.getString("status"),
                instant(result.getTimestamp("expected_delivery_at")),
                result.getBigDecimal("total_amount"),
                result.getString("remark"),
                instant(result.getTimestamp("created_at")),
                jdbc.query(
                        "SELECT ingredient_id, quantity, unit, unit_price, amount FROM purchase_order_items "
                                + "WHERE school_id = ? AND canteen_id = ? AND order_id = ? ORDER BY ingredient_id",
                        (item, itemRow) -> new PurchaseOrderItem(
                                item.getString("ingredient_id"),
                                item.getBigDecimal("quantity"),
                                item.getString("unit"),
                                item.getBigDecimal("unit_price"),
                                item.getBigDecimal("amount")),
                        scope.schoolId(),
                        scope.canteenId(),
                        result.getString("order_id")));
    }

    private RowMapper<OperationalLedgerRecord> ledgerMapper() {
        return (result, row) -> new OperationalLedgerRecord(
                result.getString("record_id"),
                result.getString("cycle_id"),
                result.getString("ledger_code"),
                instant(result.getTimestamp("record_time")),
                result.getString("recorder_id"),
                readMap(result.getString("content_json")),
                readStringList(result.getString("photos_json")),
                result.getString("status"),
                result.getString("remark"),
                instant(result.getTimestamp("created_at")));
    }

    private Optional<OperationalLedgerRecord> findLedgerById(CanteenScope scope, String recordId) {
        return jdbc.query(
                        "SELECT * FROM operational_ledger_records WHERE school_id = ? AND canteen_id = ? AND record_id = ?",
                        ledgerMapper(),
                        scope.schoolId(),
                        scope.canteenId(),
                        recordId)
                .stream()
                .findFirst();
    }

    private Optional<OperationalLedgerRecord> findLedgerByCycleAndCode(
            CanteenScope scope, String cycleId, String ledgerCode) {
        return jdbc.query(
                        "SELECT * FROM operational_ledger_records WHERE school_id = ? AND canteen_id = ? "
                                + "AND cycle_id = ? AND ledger_code = ?",
                        ledgerMapper(),
                        scope.schoolId(),
                        scope.canteenId(),
                        cycleId,
                        ledgerCode)
                .stream()
                .findFirst();
    }

    private Optional<PurchaseOrder> findPurchaseOrderByIdempotency(
            CanteenScope scope, String idempotencyKey) {
        return jdbc.query(
                        "SELECT * FROM purchase_orders WHERE school_id = ? AND canteen_id = ? AND idempotency_key = ?",
                        purchaseOrderMapper(scope),
                        scope.schoolId(),
                        scope.canteenId(),
                        idempotencyKey)
                .stream()
                .findFirst();
    }

    private boolean samePurchaseOrder(PurchaseOrder left, PurchaseOrder right) {
        if (!left.supplierId().equals(right.supplierId())
                || !left.orderType().equals(right.orderType())
                || left.totalAmount().compareTo(right.totalAmount()) != 0
                || !java.util.Objects.equals(
                        left.expectedDeliveryAt(), right.expectedDeliveryAt())
                || !java.util.Objects.equals(left.remark(), right.remark())
                || left.items().size() != right.items().size()) {
            return false;
        }
        Map<String, PurchaseOrderItem> expected = new HashMap<>();
        for (PurchaseOrderItem item : right.items()) {
            expected.put(item.ingredientId(), item);
        }
        for (PurchaseOrderItem item : left.items()) {
            PurchaseOrderItem other = expected.get(item.ingredientId());
            if (other == null
                    || !item.unit().equals(other.unit())
                    || item.quantity().compareTo(other.quantity()) != 0
                    || item.unitPrice().compareTo(other.unitPrice()) != 0) {
                return false;
            }
        }
        return true;
    }

    private void ensureSameStockOut(
            CanteenScope scope,
            StockOutResult stored,
            String requestedReason,
            List<StockOutItem> requested) {
        String storedReason = jdbc.query(
                        "SELECT reason FROM stock_out_records WHERE school_id = ? AND canteen_id = ? "
                                + "AND stock_out_id = ?",
                        (result, row) -> result.getString("reason"),
                        scope.schoolId(),
                        scope.canteenId(),
                        stored.stockOutId())
                .stream()
                .findFirst()
                .orElse(null);
        if (!java.util.Objects.equals(storedReason, requestedReason)) {
            throw new IllegalArgumentException(
                    "Idempotency-Key was already used for a different stock-out");
        }
        if (requested == null || requested.size() != stored.items().size()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key was already used for a different stock-out");
        }
        Map<String, StockOutItem> expected = new HashMap<>();
        for (StockOutItem item : stored.items()) {
            expected.put(item.ingredientId(), item);
        }
        for (StockOutItem item : requested) {
            findIngredientData(scope, item.ingredientId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown ingredient: " + item.ingredientId()));
            BaseAmount amount = toIngredientBase(
                    scope, item.ingredientId(), item.quantity(), item.unit());
            StockOutItem other = expected.get(item.ingredientId());
            if (other == null
                    || !other.unit().equals(amount.unit())
                    || other.quantity().compareTo(amount.quantity()) != 0) {
                throw new IllegalArgumentException(
                        "Idempotency-Key was already used for a different stock-out");
            }
        }
    }

    private Optional<ReceiveResult> findReceiptByIdempotency(
            CanteenScope scope, String idempotencyKey) {
        return jdbc.query(
                        "SELECT receipt_id, order_id FROM purchase_receipts WHERE school_id = ? AND canteen_id = ? "
                                + "AND idempotency_key = ?",
                        (result, row) -> new ReceiveResult(
                                result.getString("order_id"),
                                result.getString("receipt_id"),
                                jdbc.queryForList(
                                        "SELECT b.trace_code FROM purchase_receipt_items i "
                                                + "JOIN inventory_batches b ON b.school_id = i.school_id "
                                                + "AND b.canteen_id = i.canteen_id AND b.batch_id = i.batch_id "
                                                + "WHERE i.school_id = ? AND i.canteen_id = ? AND i.receipt_id = ? "
                                                + "ORDER BY b.trace_code",
                                        String.class,
                                        scope.schoolId(),
                                        scope.canteenId(),
                                        result.getString("receipt_id"))),
                        scope.schoolId(),
                        scope.canteenId(),
                        idempotencyKey)
                .stream()
                .findFirst();
    }

    private void ensureSameReceipt(
            CanteenScope scope,
            ReceiveResult stored,
            String expectedSupplierId,
            List<ReceiveItem> requested) {
        List<ReceiptItemSnapshot> persisted = jdbc.query(
                "SELECT i.ingredient_id, i.quantity_base, i.base_unit, b.batch_id, b.supplier_id, "
                        + "b.batch_no, b.purchase_price, b.production_date, b.expiry_date "
                        + "FROM purchase_receipt_items i "
                        + "JOIN inventory_batches b ON b.school_id = i.school_id "
                        + "AND b.canteen_id = i.canteen_id AND b.batch_id = i.batch_id "
                        + "WHERE i.school_id = ? AND i.canteen_id = ? AND i.receipt_id = ? "
                        + "ORDER BY i.ingredient_id",
                (result, row) -> new ReceiptItemSnapshot(
                        new ReceiveItem(
                                result.getString("ingredient_id"),
                                result.getBigDecimal("quantity_base"),
                                result.getString("base_unit"),
                                result.getString("batch_no"),
                                result.getBigDecimal("purchase_price"),
                                localDate(result.getDate("production_date")),
                                localDate(result.getDate("expiry_date"))),
                        result.getString("batch_id"),
                        result.getString("supplier_id")),
                scope.schoolId(),
                scope.canteenId(),
                stored.receiptId());
        if (requested == null || requested.size() != persisted.size()) {
            throw new IllegalArgumentException(
                    "Idempotency-Key was already used for a different purchase receipt");
        }
        Map<String, ReceiptItemSnapshot> expected = new HashMap<>();
        for (ReceiptItemSnapshot snapshot : persisted) {
            expected.put(snapshot.item().ingredientId(), snapshot);
        }
        Set<String> receivedIngredients = new HashSet<>();
        for (ReceiveItem item : requested) {
            if (!receivedIngredients.add(item.ingredientId())) {
                throw new IllegalArgumentException(
                        "Idempotency-Key was already used for a different purchase receipt");
            }
            findIngredientData(scope, item.ingredientId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown ingredient: " + item.ingredientId()));
            BaseAmount amount = toIngredientBase(
                    scope, item.ingredientId(), item.quantity(), item.unit());
            ReceiptItemSnapshot snapshot = expected.get(item.ingredientId());
            ReceiveItem saved = snapshot == null ? null : snapshot.item();
            boolean batchMatches = saved != null
                    && (item.batchNo() == null || item.batchNo().isBlank()
                            ? saved.batchNo().equals(snapshot.batchId())
                            : saved.batchNo().equals(item.batchNo()));
            if (saved == null
                    || (expectedSupplierId != null
                            && !expectedSupplierId.equals(snapshot.supplierId()))
                    || !saved.unit().equals(amount.unit())
                    || saved.quantity().compareTo(amount.quantity()) != 0
                    || !batchMatches
                    || saved.purchasePrice().compareTo(item.purchasePrice()) != 0
                    || !java.util.Objects.equals(saved.productionDate(), item.productionDate())
                    || !java.util.Objects.equals(saved.expiryDate(), item.expiryDate())) {
                throw new IllegalArgumentException(
                        "Idempotency-Key was already used for a different purchase receipt");
            }
        }
    }

    private record ReceiptItemSnapshot(
            ReceiveItem item, String batchId, String supplierId) {
    }

    private Optional<StockOutResult> findStockOutByIdempotency(
            CanteenScope scope, String idempotencyKey) {
        return jdbc.query(
                        "SELECT stock_out_id FROM stock_out_records WHERE school_id = ? AND canteen_id = ? "
                                + "AND idempotency_key = ?",
                        (result, row) -> new StockOutResult(
                                result.getString("stock_out_id"),
                                jdbc.query(
                                        "SELECT ingredient_id, quantity_base, base_unit FROM stock_out_items "
                                                + "WHERE school_id = ? AND canteen_id = ? AND stock_out_id = ? "
                                                + "ORDER BY ingredient_id",
                                        (item, itemRow) -> new StockOutItem(
                                                item.getString("ingredient_id"),
                                                item.getBigDecimal("quantity_base"),
                                                item.getString("base_unit")),
                                        scope.schoolId(),
                                        scope.canteenId(),
                                        result.getString("stock_out_id"))),
                        scope.schoolId(),
                        scope.canteenId(),
                        idempotencyKey)
                .stream()
                .findFirst();
    }

    private Optional<IngredientData> findIngredientData(CanteenScope scope, String ingredientId) {
        return jdbc.query(
                        "SELECT base_unit, warning_threshold FROM ingredients WHERE school_id = ? "
                                + "AND canteen_id = ? AND ingredient_id = ?",
                        (result, row) -> new IngredientData(
                                result.getString("base_unit"),
                                result.getBigDecimal("warning_threshold")),
                        scope.schoolId(),
                        scope.canteenId(),
                        ingredientId)
                .stream()
                .findFirst();
    }

    private List<ReceiveItem> remainingItems(CanteenScope scope, PurchaseOrder order) {
        List<ReceiveItem> remaining = new ArrayList<>();
        for (PurchaseOrderItem item : order.items()) {
            BigDecimal orderedBase = toIngredientBase(
                    scope, item.ingredientId(), item.quantity(), item.unit()).quantity();
            BigDecimal receivedBase = receivedQuantityForUpdate(
                    scope, order.id(), item.ingredientId());
            BigDecimal quantity = orderedBase.subtract(receivedBase);
            if (quantity.signum() > 0) {
                String baseUnit = toIngredientBase(
                        scope, item.ingredientId(), BigDecimal.ONE, item.unit()).unit();
                remaining.add(new ReceiveItem(
                        item.ingredientId(),
                        quantity,
                        baseUnit,
                        "BATCH-" + UUID.randomUUID(),
                        item.unitPrice(),
                        null,
                        null));
            }
        }
        return remaining;
    }

    private BigDecimal receivedQuantityForUpdate(
            CanteenScope scope, String orderId, String ingredientId) {
        return jdbc.query(
                        "SELECT received_quantity_base FROM purchase_order_items "
                                + "WHERE school_id = ? AND canteen_id = ? AND order_id = ? "
                                + "AND ingredient_id = ? FOR UPDATE",
                        (result, row) -> result.getBigDecimal("received_quantity_base"),
                        scope.schoolId(), scope.canteenId(), orderId, ingredientId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Purchase order line not found: " + ingredientId));
    }

    private BaseAmount toIngredientBase(
            CanteenScope scope, String ingredientId, BigDecimal quantity, String unit) {
        Ingredient ingredient = findIngredient(scope, ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown ingredient: " + ingredientId));
        var converted = quantityConverter.toBase(scope, ingredient, quantity, unit);
        return new BaseAmount(converted.quantity(), converted.unit());
    }

    private void addInventory(
            CanteenScope scope, String ingredientId, IngredientData ingredient, BaseAmount amount) {
        int changed = jdbc.update(
                "UPDATE inventory SET quantity_base = quantity_base + ?, warning_threshold = ?, "
                        + "last_update_time = CURRENT_TIMESTAMP WHERE school_id = ? AND canteen_id = ? "
                        + "AND material_id = ? AND base_unit = ?",
                amount.quantity(),
                ingredient.warningThreshold(),
                scope.schoolId(),
                scope.canteenId(),
                ingredientId,
                amount.unit());
        if (changed == 1) {
            return;
        }
        Optional<String> actualUnit = inventoryUnit(scope, ingredientId);
        if (actualUnit.isPresent() && !actualUnit.get().equals(amount.unit())) {
            throw new IllegalArgumentException("Inventory unit mismatch for " + ingredientId);
        }
        try {
            jdbc.update(
                    "INSERT INTO inventory (school_id, canteen_id, material_id, quantity_base, base_unit, "
                            + "warning_threshold, last_update_time) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                    scope.schoolId(),
                    scope.canteenId(),
                    ingredientId,
                    amount.quantity(),
                    amount.unit(),
                    ingredient.warningThreshold());
        } catch (DuplicateKeyException exception) {
            int retried = jdbc.update(
                    "UPDATE inventory SET quantity_base = quantity_base + ?, warning_threshold = ?, "
                            + "last_update_time = CURRENT_TIMESTAMP WHERE school_id = ? AND canteen_id = ? "
                            + "AND material_id = ? AND base_unit = ?",
                    amount.quantity(),
                    ingredient.warningThreshold(),
                    scope.schoolId(),
                    scope.canteenId(),
                    ingredientId,
                    amount.unit());
            if (retried != 1) {
                throw new IllegalArgumentException("Inventory unit mismatch for " + ingredientId);
            }
        }
    }

    private void syncLedgerAlert(CanteenScope scope, String cycleId) {
        long missing = count(
                "SELECT COUNT(*) FROM ledger_cycle_requirements WHERE school_id = ? AND canteen_id = ? "
                        + "AND cycle_id = ? AND completed = FALSE",
                List.of(scope.schoolId(), scope.canteenId(), cycleId));
        String status = missing == 0 ? "CLEARED" : "OPEN";
        jdbc.update(
                "UPDATE ledger_cycles SET status = ? WHERE school_id = ? AND canteen_id = ? AND id = ?",
                status,
                scope.schoolId(),
                scope.canteenId(),
                cycleId);
        if (missing == 0) {
            jdbc.update(
                    "UPDATE ledger_alerts SET status = 'CLEARED', cleared_at = COALESCE(cleared_at, CURRENT_TIMESTAMP) "
                            + "WHERE school_id = ? AND canteen_id = ? AND cycle_id = ?",
                    scope.schoolId(),
                    scope.canteenId(),
                    cycleId);
        }
    }

    private Optional<String> inventoryUnit(CanteenScope scope, String ingredientId) {
        return jdbc.query(
                        "SELECT base_unit FROM inventory WHERE school_id = ? AND canteen_id = ? AND material_id = ?",
                        (result, row) -> result.getString("base_unit"),
                        scope.schoolId(),
                        scope.canteenId(),
                        ingredientId)
                .stream()
                .findFirst();
    }

    private boolean validTransition(String current, String target) {
        if ("CANCELLED".equals(target)) {
            return !List.of("RECEIVED", "CANCELLED").contains(current);
        }
        return ("DRAFT".equals(current) && "SUBMITTED".equals(target))
                || ("SUBMITTED".equals(current) && "CONFIRMED".equals(target));
    }

    private Map<String, Object> readMap(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored ledger content is invalid JSON", exception);
        }
    }

    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored ledger photos are invalid JSON", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize operational JSON", exception);
        }
    }

    private long count(String sql, List<?> parameters) {
        Number value = jdbc.queryForObject(sql, Number.class, parameters.toArray());
        return value == null ? 0 : value.longValue();
    }

    private static int offset(int page, int size) {
        return Math.multiplyExact(page - 1, size);
    }

    private static void requirePage(int page, int size) {
        if (page < 1 || page > 1_000_000 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be 1..1000000 and size must be 1..100");
        }
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static LocalDate localDate(java.sql.Date value) {
        return value == null ? null : value.toLocalDate();
    }

    private record IngredientData(String baseUnit, BigDecimal warningThreshold) {
    }

    private record BaseAmount(BigDecimal quantity, String unit) {
    }
}
