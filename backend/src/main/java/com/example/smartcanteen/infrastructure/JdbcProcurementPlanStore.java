package com.example.smartcanteen.infrastructure;

import com.example.smartcanteen.application.IngredientQuantityConverter;
import com.example.smartcanteen.application.port.ProcurementPlanStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.DailyMenu;
import com.example.smartcanteen.domain.DailyMenuItem;
import com.example.smartcanteen.domain.Dish;
import com.example.smartcanteen.domain.DishIngredient;
import com.example.smartcanteen.domain.Ingredient;
import com.example.smartcanteen.domain.Nutrition;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.domain.ProcurementPlan;
import com.example.smartcanteen.domain.ProcurementPlanItem;
import com.example.smartcanteen.domain.ProcurementPlanStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcProcurementPlanStore implements ProcurementPlanStore {

    private final JdbcTemplate jdbc;
    private final IngredientQuantityConverter quantityConverter;

    public JdbcProcurementPlanStore(
            JdbcTemplate jdbc, IngredientQuantityConverter quantityConverter) {
        this.jdbc = jdbc;
        this.quantityConverter = quantityConverter;
    }

    @Override
    public List<DailyMenu> findPublishedMenus(
            CanteenScope scope, LocalDate periodStart, LocalDate periodEnd) {
        return jdbc.query(
                "SELECT menu_id, menu_date, meal_time, status, version FROM daily_menus "
                        + "WHERE school_id = ? AND canteen_id = ? AND status = 'PUBLISHED' "
                        + "AND menu_date BETWEEN ? AND ? ORDER BY menu_date, meal_time, menu_id",
                (result, row) -> new DailyMenu(
                        result.getString("menu_id"),
                        result.getDate("menu_date").toLocalDate(),
                        result.getString("meal_time"),
                        result.getString("status"),
                        result.getLong("version"),
                        jdbc.query(
                                "SELECT dish_id, estimated_quantity, sort_order FROM daily_menu_items "
                                        + "WHERE school_id = ? AND canteen_id = ? AND menu_id = ? "
                                        + "ORDER BY sort_order, dish_id",
                                (item, itemRow) -> new DailyMenuItem(
                                        item.getString("dish_id"),
                                        item.getBigDecimal("estimated_quantity"),
                                        item.getInt("sort_order")),
                                scope.schoolId(),
                                scope.canteenId(),
                                result.getString("menu_id"))),
                scope.schoolId(),
                scope.canteenId(),
                java.sql.Date.valueOf(periodStart),
                java.sql.Date.valueOf(periodEnd));
    }

    @Override
    public Optional<Dish> findDish(CanteenScope scope, String dishId) {
        return jdbc.query(
                        "SELECT dish_id, name, category, description, image_url, status, version "
                                + "FROM dishes WHERE school_id = ? AND canteen_id = ? AND dish_id = ?",
                        (result, row) -> new Dish(
                                result.getString("dish_id"),
                                result.getString("name"),
                                result.getString("category"),
                                result.getString("description"),
                                result.getString("image_url"),
                                "ACTIVE".equals(result.getString("status")),
                                result.getLong("version"),
                                jdbc.query(
                                        "SELECT ingredient_id, quantity, unit FROM dish_ingredients "
                                                + "WHERE school_id = ? AND canteen_id = ? AND dish_id = ? "
                                                + "ORDER BY ingredient_id",
                                        (item, itemRow) -> new DishIngredient(
                                                item.getString("ingredient_id"),
                                                item.getBigDecimal("quantity"),
                                                item.getString("unit")),
                                        scope.schoolId(),
                                        scope.canteenId(),
                                        dishId)),
                        scope.schoolId(), scope.canteenId(), dishId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<Ingredient> findIngredient(CanteenScope scope, String ingredientId) {
        return jdbc.query(
                        "SELECT ingredient_id, name, category, base_unit, specification, energy_kcal, "
                                + "protein_g, fat_g, carbohydrate_g, warning_threshold, status "
                                + "FROM ingredients WHERE school_id = ? AND canteen_id = ? "
                                + "AND ingredient_id = ?",
                        (result, row) -> new Ingredient(
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
                                "ACTIVE".equals(result.getString("status"))),
                        scope.schoolId(), scope.canteenId(), ingredientId)
                .stream()
                .findFirst();
    }

    @Override
    public Map<String, BigDecimal> inventorySnapshot(CanteenScope scope) {
        Map<String, BigDecimal> snapshot = new HashMap<>();
        jdbc.query(
                "SELECT material_id, quantity_base FROM inventory "
                        + "WHERE school_id = ? AND canteen_id = ?",
                (result, row) -> {
                    snapshot.put(result.getString("material_id"),
                            result.getBigDecimal("quantity_base"));
                    return null;
                },
                scope.schoolId(), scope.canteenId());
        return Map.copyOf(snapshot);
    }

    @Override
    public Map<String, BigDecimal> openOrderSnapshot(CanteenScope scope) {
        Map<String, BigDecimal> snapshot = new HashMap<>();
        jdbc.query(
                "SELECT i.ingredient_id, i.quantity, i.unit, i.received_quantity_base "
                        + "FROM purchase_order_items i JOIN purchase_orders o ON "
                        + "o.school_id = i.school_id AND o.canteen_id = i.canteen_id AND o.order_id = i.order_id "
                        + "WHERE i.school_id = ? AND i.canteen_id = ? "
                        // A draft order is not a supplier commitment and must not suppress
                        // the next plan's shortage. Submitted/confirmed orders are open.
                        + "AND o.status IN ('SUBMITTED', 'CONFIRMED')",
                (result, row) -> {
                    String ingredientId = result.getString("ingredient_id");
                    Ingredient ingredient = findIngredient(scope, ingredientId)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Order references unknown ingredient: " + ingredientId));
                    BigDecimal ordered = quantityConverter.toBase(
                                    scope,
                                    ingredient,
                                    result.getBigDecimal("quantity"),
                                    result.getString("unit"))
                            .quantity();
                    BigDecimal received = result.getBigDecimal("received_quantity_base");
                    BigDecimal open = ordered.subtract(received == null ? BigDecimal.ZERO : received);
                    if (open.signum() > 0) {
                        snapshot.merge(ingredientId, open, BigDecimal::add);
                    }
                    return null;
                },
                scope.schoolId(), scope.canteenId());
        return Map.copyOf(snapshot);
    }

    @Override
    public PageResult<ProcurementPlan> list(
            CanteenScope scope, String status, int page, int size) {
        requirePage(page, size);
        StringBuilder where = new StringBuilder(
                " WHERE school_id = ? AND canteen_id = ? ");
        List<Object> params = new ArrayList<>(List.of(scope.schoolId(), scope.canteenId()));
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            params.add(status.trim().toUpperCase());
        }
        long total = count("SELECT COUNT(*) FROM procurement_plans" + where, params);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add(offset(page, size));
        List<String> ids = jdbc.query(
                "SELECT plan_id FROM procurement_plans" + where
                        + " ORDER BY period_start DESC, plan_no DESC LIMIT ? OFFSET ?",
                (result, row) -> result.getString("plan_id"),
                pageParams.toArray());
        return new PageResult<>(
                ids.stream().map(id -> find(scope, id).orElseThrow()).toList(),
                page, size, total);
    }

    @Override
    public Optional<ProcurementPlan> find(CanteenScope scope, String planId) {
        return jdbc.query(
                        "SELECT plan_id, plan_no, period_start, period_end, status, version, created_at "
                                + "FROM procurement_plans WHERE school_id = ? AND canteen_id = ? AND plan_id = ?",
                        (result, row) -> readPlan(scope, result.getString("plan_id"),
                                result.getString("plan_no"),
                                result.getDate("period_start").toLocalDate(),
                                result.getDate("period_end").toLocalDate(),
                                result.getString("status"), result.getLong("version"),
                                instant(result.getTimestamp("created_at"))),
                        scope.schoolId(), scope.canteenId(), planId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ProcurementPlan> findByIdempotencyKey(
            CanteenScope scope, String idempotencyKey) {
        return jdbc.query(
                        "SELECT plan_id FROM procurement_plans WHERE school_id = ? AND canteen_id = ? "
                                + "AND idempotency_key = ?",
                        (result, row) -> result.getString("plan_id"),
                        scope.schoolId(), scope.canteenId(), idempotencyKey)
                .stream()
                .findFirst()
                .flatMap(id -> find(scope, id));
    }

    @Override
    public ProcurementPlan create(
            CanteenScope scope, ProcurementPlan plan, String idempotencyKey) {
        try {
            jdbc.update(
                    "INSERT INTO procurement_plans (school_id, canteen_id, plan_id, plan_no, "
                            + "period_start, period_end, status, idempotency_key, version) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)",
                    scope.schoolId(), scope.canteenId(), plan.id(), plan.planNo(),
                    java.sql.Date.valueOf(plan.periodStart()),
                    java.sql.Date.valueOf(plan.periodEnd()), plan.status().name(), idempotencyKey);
            for (String menuId : plan.sourceMenuIds()) {
                jdbc.update(
                        "INSERT INTO procurement_plan_menus (school_id, canteen_id, plan_id, menu_id) "
                                + "VALUES (?, ?, ?, ?)",
                        scope.schoolId(), scope.canteenId(), plan.id(), menuId);
            }
            insertItems(scope, plan.id(), plan.items());
        } catch (DuplicateKeyException exception) {
            ProcurementPlan existing = findByIdempotencyKey(scope, idempotencyKey).orElse(null);
            if (existing != null && samePlan(existing, plan)) {
                return existing;
            }
            throw new IllegalArgumentException(
                    "Idempotency-Key was already used for a different procurement plan");
        }
        return find(scope, plan.id())
                .orElseThrow(() -> new IllegalStateException("Procurement plan was not persisted"));
    }

    @Override
    public ProcurementPlan updateItems(
            CanteenScope scope,
            String planId,
            long expectedVersion,
            List<ProcurementPlanItem> items) {
        int changed = jdbc.update(
                "UPDATE procurement_plans SET version = version + 1, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE school_id = ? AND canteen_id = ? AND plan_id = ? "
                        + "AND status = 'DRAFT' AND version = ?",
                scope.schoolId(), scope.canteenId(), planId, expectedVersion);
        if (changed != 1) {
            throw new IllegalStateException(
                    "Procurement plan was changed, confirmed, cancelled or not found: " + planId);
        }
        jdbc.update(
                "DELETE FROM procurement_plan_items WHERE school_id = ? AND canteen_id = ? AND plan_id = ?",
                scope.schoolId(), scope.canteenId(), planId);
        insertItems(scope, planId, items);
        return find(scope, planId)
                .orElseThrow(() -> new IllegalStateException("Procurement plan disappeared: " + planId));
    }

    @Override
    public ProcurementPlan transition(
            CanteenScope scope, String planId, String targetStatus) {
        ProcurementPlan existing = find(scope, planId)
                .orElseThrow(() -> new IllegalArgumentException("Procurement plan not found: " + planId));
        if (!validTransition(existing.status(), targetStatus)) {
            throw new IllegalStateException(
                    "Procurement plan cannot transition from " + existing.status() + " to " + targetStatus);
        }
        int changed = jdbc.update(
                "UPDATE procurement_plans SET status = ?, version = version + 1, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE school_id = ? AND canteen_id = ? "
                        + "AND plan_id = ? AND status = ? AND version = ?",
                targetStatus, scope.schoolId(), scope.canteenId(), planId,
                existing.status().name(), existing.version());
        if (changed != 1) {
            throw new IllegalStateException("Procurement plan was changed concurrently: " + planId);
        }
        return find(scope, planId).orElseThrow();
    }

    @Override
    public Optional<PlanOrder> findOrderByPlan(CanteenScope scope, String planId) {
        return jdbc.query(
                        "SELECT plan_id, order_id, idempotency_key, payload_hash FROM procurement_plan_orders "
                                + "WHERE school_id = ? AND canteen_id = ? AND plan_id = ?",
                        (result, row) -> new PlanOrder(
                                result.getString("plan_id"),
                                result.getString("order_id"),
                                result.getString("idempotency_key"),
                                result.getString("payload_hash")),
                        scope.schoolId(), scope.canteenId(), planId)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<PlanOrder> findOrderByIdempotencyKey(
            CanteenScope scope, String idempotencyKey) {
        return jdbc.query(
                        "SELECT plan_id, order_id, idempotency_key, payload_hash FROM procurement_plan_orders "
                                + "WHERE school_id = ? AND canteen_id = ? AND idempotency_key = ?",
                        (result, row) -> new PlanOrder(
                                result.getString("plan_id"),
                                result.getString("order_id"),
                                result.getString("idempotency_key"),
                                result.getString("payload_hash")),
                        scope.schoolId(), scope.canteenId(), idempotencyKey)
                .stream()
                .findFirst();
    }

    @Override
    public ProcurementPlan linkOrder(
            CanteenScope scope,
            String planId,
            long expectedVersion,
            String orderId,
            String idempotencyKey,
            String payloadHash) {
        try {
            jdbc.update(
                    "INSERT INTO procurement_plan_orders (school_id, canteen_id, plan_id, order_id, "
                            + "idempotency_key, payload_hash) VALUES (?, ?, ?, ?, ?, ?)",
                    scope.schoolId(), scope.canteenId(), planId, orderId, idempotencyKey, payloadHash);
        } catch (DuplicateKeyException exception) {
            PlanOrder existing = findOrderByPlan(scope, planId).orElse(null);
            if (existing != null && existing.orderId().equals(orderId)
                    && existing.idempotencyKey().equals(idempotencyKey)) {
                return find(scope, planId).orElseThrow();
            }
            throw new IllegalArgumentException("Procurement plan already has a different order");
        }
        int changed = jdbc.update(
                "UPDATE procurement_plans SET status = 'CONVERTED', version = version + 1, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE school_id = ? AND canteen_id = ? "
                        + "AND plan_id = ? AND status = 'CONFIRMED' AND version = ?",
                scope.schoolId(), scope.canteenId(), planId, expectedVersion);
        if (changed != 1) {
            throw new IllegalStateException(
                    "Only an unchanged CONFIRMED procurement plan can be converted: " + planId);
        }
        return find(scope, planId).orElseThrow();
    }

    @Override
    public void recordOrderPayloadHash(
            CanteenScope scope, String planId, String idempotencyKey, String payloadHash) {
        jdbc.update(
                "UPDATE procurement_plan_orders SET payload_hash = ? WHERE school_id = ? "
                        + "AND canteen_id = ? AND plan_id = ? AND idempotency_key = ? "
                        + "AND payload_hash IS NULL",
                payloadHash, scope.schoolId(), scope.canteenId(), planId, idempotencyKey);
    }

    private ProcurementPlan readPlan(
            CanteenScope scope,
            String planId,
            String planNo,
            LocalDate periodStart,
            LocalDate periodEnd,
            String status,
            long version,
            Instant createdAt) {
        List<String> menuIds = jdbc.query(
                "SELECT menu_id FROM procurement_plan_menus WHERE school_id = ? AND canteen_id = ? "
                        + "AND plan_id = ? ORDER BY menu_id",
                (result, row) -> result.getString("menu_id"),
                scope.schoolId(), scope.canteenId(), planId);
        List<ProcurementPlanItem> items = jdbc.query(
                "SELECT ingredient_id, required_quantity_base, inventory_quantity_base, "
                        + "open_order_quantity_base, shortage_quantity_base, planned_quantity_base, base_unit "
                        + "FROM procurement_plan_items WHERE school_id = ? AND canteen_id = ? AND plan_id = ? "
                        + "ORDER BY ingredient_id",
                (result, row) -> new ProcurementPlanItem(
                        result.getString("ingredient_id"),
                        result.getBigDecimal("required_quantity_base"),
                        result.getBigDecimal("inventory_quantity_base"),
                        result.getBigDecimal("open_order_quantity_base"),
                        result.getBigDecimal("shortage_quantity_base"),
                        result.getBigDecimal("planned_quantity_base"),
                        result.getString("base_unit")),
                scope.schoolId(), scope.canteenId(), planId);
        List<String> orderIds = jdbc.query(
                "SELECT order_id FROM procurement_plan_orders WHERE school_id = ? AND canteen_id = ? "
                        + "AND plan_id = ? ORDER BY order_id",
                (result, row) -> result.getString("order_id"),
                scope.schoolId(), scope.canteenId(), planId);
        return new ProcurementPlan(
                planId, planNo, periodStart, periodEnd,
                ProcurementPlanStatus.valueOf(status), version, createdAt,
                menuIds, items, orderIds);
    }

    private void insertItems(
            CanteenScope scope, String planId, List<ProcurementPlanItem> items) {
        for (ProcurementPlanItem item : items) {
            jdbc.update(
                    "INSERT INTO procurement_plan_items (school_id, canteen_id, plan_id, ingredient_id, "
                            + "required_quantity_base, inventory_quantity_base, open_order_quantity_base, "
                            + "shortage_quantity_base, planned_quantity_base, base_unit) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    scope.schoolId(), scope.canteenId(), planId, item.ingredientId(),
                    item.requiredBaseQuantity(), item.inventoryBaseQuantity(),
                    item.openOrderBaseQuantity(), item.shortageBaseQuantity(),
                    item.plannedBaseQuantity(), item.baseUnit());
        }
    }

    private boolean samePlan(ProcurementPlan left, ProcurementPlan right) {
        if (!left.periodStart().equals(right.periodStart())
                || !left.periodEnd().equals(right.periodEnd())
                || !new HashSet<>(left.sourceMenuIds()).equals(new HashSet<>(right.sourceMenuIds()))
                || left.items().size() != right.items().size()) {
            return false;
        }
        Map<String, ProcurementPlanItem> expected = new HashMap<>();
        right.items().forEach(item -> expected.put(item.ingredientId(), item));
        for (ProcurementPlanItem item : left.items()) {
            ProcurementPlanItem other = expected.get(item.ingredientId());
            if (other == null
                    || item.requiredBaseQuantity().compareTo(other.requiredBaseQuantity()) != 0
                    || item.inventoryBaseQuantity().compareTo(other.inventoryBaseQuantity()) != 0
                    || item.openOrderBaseQuantity().compareTo(other.openOrderBaseQuantity()) != 0
                    || item.shortageBaseQuantity().compareTo(other.shortageBaseQuantity()) != 0
                    || item.plannedBaseQuantity().compareTo(other.plannedBaseQuantity()) != 0
                    || !item.baseUnit().equals(other.baseUnit())) {
                return false;
            }
        }
        return true;
    }

    private boolean validTransition(ProcurementPlanStatus current, String target) {
        if ("CANCELLED".equals(target)) {
            return current == ProcurementPlanStatus.DRAFT
                    || current == ProcurementPlanStatus.CONFIRMED;
        }
        return current == ProcurementPlanStatus.DRAFT && "CONFIRMED".equals(target);
    }

    private long count(String sql, List<?> params) {
        Number value = jdbc.queryForObject(sql, Number.class, params.toArray());
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

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
