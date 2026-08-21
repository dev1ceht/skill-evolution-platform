package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.OperationalStore;
import com.example.smartcanteen.application.port.ProcurementPlanStore;
import com.example.smartcanteen.domain.BaseQuantity;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.DailyMenu;
import com.example.smartcanteen.domain.DailyMenuItem;
import com.example.smartcanteen.domain.Dish;
import com.example.smartcanteen.domain.DishIngredient;
import com.example.smartcanteen.domain.Ingredient;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.domain.ProcurementPlan;
import com.example.smartcanteen.domain.ProcurementPlanItem;
import com.example.smartcanteen.domain.ProcurementPlanStatus;
import com.example.smartcanteen.domain.ProcurementGapAnalysis;
import com.example.smartcanteen.domain.ProcurementGapItem;
import com.example.smartcanteen.domain.PurchaseOrder;
import com.example.smartcanteen.domain.PurchaseOrderItem;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcurementPlanService {

    private final ProcurementPlanStore plans;
    private final IngredientQuantityConverter quantities;
    private final ProcurementOperationsService orders;
    private final OperationalStore operationalStore;

    public ProcurementPlanService(
            ProcurementPlanStore plans,
            IngredientQuantityConverter quantities,
            ProcurementOperationsService orders,
            OperationalStore operationalStore) {
        this.plans = plans;
        this.quantities = quantities;
        this.orders = orders;
        this.operationalStore = operationalStore;
    }

    @Transactional(readOnly = true)
    public PageResult<ProcurementPlan> list(
            CanteenScope scope, String status, int page, int size) {
        if (status != null && !status.isBlank()) {
            ProcurementPlanStatus.valueOf(status.trim().toUpperCase());
        }
        return plans.list(scope, status, page, size);
    }

    @Transactional(readOnly = true)
    public ProcurementPlan find(CanteenScope scope, String planId) {
        requireIdentifier("planId", planId, 64);
        return plans.find(scope, planId)
                .orElseThrow(() -> new IllegalArgumentException("Procurement plan not found: " + planId));
    }

    /**
     * Compares a published menu's deterministic recipe demand with current inventory and open
     * purchase-order snapshots. This method deliberately does not persist a procurement plan.
     */
    @Transactional(readOnly = true)
    public ProcurementGapAnalysis analyzeGap(
            CanteenScope scope, LocalDate menuDate, String mealTime) {
        if (scope == null || menuDate == null) {
            throw new IllegalArgumentException("scope and menuDate are required");
        }
        String normalizedMealTime = normalizeMealTime(mealTime);
        List<DailyMenu> menus = plans.findPublishedMenus(scope, menuDate, menuDate).stream()
                .filter(menu -> normalizedMealTime == null
                        || normalizedMealTime.equals(menu.mealTime()))
                .toList();
        if (menus.isEmpty()) {
            return ProcurementGapAnalysis.of(menuDate, normalizedMealTime, List.of(), List.of());
        }
        Map<String, BigDecimal> inventory = plans.inventorySnapshot(scope);
        Map<String, BigDecimal> openOrders = plans.openOrderSnapshot(scope);
        Map<String, RequirementAccumulator> requirements = calculateRequirements(scope, menus);
        List<ProcurementGapItem> items = sortedRequirements(requirements).stream()
                .map(requirement -> requirement.toGapItem(
                        inventory.getOrDefault(requirement.ingredient.id(), BigDecimal.ZERO),
                        openOrders.getOrDefault(requirement.ingredient.id(), BigDecimal.ZERO)))
                .toList();
        return ProcurementGapAnalysis.of(
                menuDate,
                normalizedMealTime,
                menus.stream().map(DailyMenu::id).toList(),
                items);
    }

    @Transactional
    public ProcurementPlan generate(
            CanteenScope scope,
            LocalDate periodStart,
            LocalDate periodEnd,
            String idempotencyKey) {
        requireIdentifier("Idempotency-Key", idempotencyKey, 128);
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("periodEnd cannot be before periodStart");
        }
        ProcurementPlan existing = plans.findByIdempotencyKey(scope, idempotencyKey).orElse(null);
        if (existing != null) {
            if (!existing.periodStart().equals(periodStart)
                    || !existing.periodEnd().equals(periodEnd)) {
                throw new IllegalArgumentException(
                        "Idempotency-Key was already used for a different planning period");
            }
            return existing;
        }
        List<DailyMenu> menus = plans.findPublishedMenus(scope, periodStart, periodEnd);
        if (menus.isEmpty()) {
            throw new IllegalStateException("No published menus exist in the requested period");
        }
        Map<String, RequirementAccumulator> requirements = calculateRequirements(scope, menus);
        Map<String, BigDecimal> inventory = plans.inventorySnapshot(scope);
        Map<String, BigDecimal> openOrders = plans.openOrderSnapshot(scope);
        List<ProcurementPlanItem> items = sortedRequirements(requirements).stream()
                .map(requirement -> requirement.toPlanItem(
                        inventory.getOrDefault(requirement.ingredient.id(), BigDecimal.ZERO),
                        openOrders.getOrDefault(requirement.ingredient.id(), BigDecimal.ZERO)))
                .toList();
        ProcurementPlan plan = new ProcurementPlan(
                "PLAN-" + UUID.randomUUID(),
                "PLAN" + UUID.randomUUID(),
                periodStart,
                periodEnd,
                ProcurementPlanStatus.DRAFT,
                0,
                Instant.now(),
                menus.stream().map(DailyMenu::id).toList(),
                items,
                List.of());
        return plans.create(scope, plan, idempotencyKey);
    }

    private Map<String, RequirementAccumulator> calculateRequirements(
            CanteenScope scope, List<DailyMenu> menus) {
        Map<String, RequirementAccumulator> requirements = new LinkedHashMap<>();
        for (DailyMenu menu : menus) {
            for (DailyMenuItem menuItem : menu.items()) {
                Dish dish = plans.findDish(scope, menuItem.dishId())
                        .filter(Dish::active)
                        .orElseThrow(() -> new IllegalStateException(
                                "Published menu references an unavailable dish: " + menuItem.dishId()));
                for (DishIngredient recipe : dish.ingredients()) {
                    Ingredient ingredient = plans.findIngredient(scope, recipe.ingredientId())
                            .filter(Ingredient::active)
                            .orElseThrow(() -> new IllegalStateException(
                                    "Dish references an unavailable ingredient: " + recipe.ingredientId()));
                    BaseQuantity perServing = quantities.toBase(
                            scope, ingredient, recipe.quantity(), recipe.unit());
                    requirements.computeIfAbsent(
                                    ingredient.id(), id -> new RequirementAccumulator(
                                            ingredient, perServing.unit()))
                            .add(perServing.quantity().multiply(menuItem.estimatedQuantity()));
                }
            }
        }
        return requirements;
    }

    private static List<RequirementAccumulator> sortedRequirements(
            Map<String, RequirementAccumulator> requirements) {
        return requirements.values().stream()
                .sorted(Comparator.comparing(requirement -> requirement.ingredient.id()))
                .toList();
    }

    private static String normalizeMealTime(String mealTime) {
        if (mealTime == null || mealTime.isBlank()) {
            return null;
        }
        String normalized = mealTime.trim().toUpperCase(java.util.Locale.ROOT);
        if (!List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported mealTime: " + mealTime);
        }
        return normalized;
    }

    @Transactional
    public ProcurementPlan adjust(
            CanteenScope scope,
            String planId,
            long expectedVersion,
            List<PlanAdjustment> adjustments) {
        ProcurementPlan current = find(scope, planId);
        if (current.status() != ProcurementPlanStatus.DRAFT) {
            throw new IllegalStateException("Only a DRAFT procurement plan can be adjusted");
        }
        if (adjustments == null || adjustments.isEmpty()) {
            throw new IllegalArgumentException("At least one procurement plan item is required");
        }
        Map<String, ProcurementPlanItem> original = new HashMap<>();
        current.items().forEach(item -> original.put(item.ingredientId(), item));
        Set<String> seen = new HashSet<>();
        List<ProcurementPlanItem> updated = new ArrayList<>();
        for (PlanAdjustment adjustment : adjustments) {
            if (!seen.add(adjustment.ingredientId())) {
                throw new IllegalArgumentException(
                        "Duplicate procurement plan ingredient: " + adjustment.ingredientId());
            }
            ProcurementPlanItem item = original.get(adjustment.ingredientId());
            if (item == null) {
                throw new IllegalArgumentException(
                        "Ingredient is not in procurement plan: " + adjustment.ingredientId());
            }
            Ingredient ingredient = plans.findIngredient(scope, item.ingredientId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Ingredient not found: " + item.ingredientId()));
            BigDecimal adjustedBase = quantities.toBase(
                            scope, ingredient, adjustment.quantity(), adjustment.unit())
                    .quantity();
            updated.add(new ProcurementPlanItem(
                    item.ingredientId(),
                    item.requiredBaseQuantity(),
                    item.inventoryBaseQuantity(),
                    item.openOrderBaseQuantity(),
                    item.shortageBaseQuantity(),
                    adjustedBase,
                    item.baseUnit()));
        }
        if (seen.size() != original.size()) {
            throw new IllegalArgumentException(
                    "Adjustments must include every procurement plan item");
        }
        return plans.updateItems(scope, planId, expectedVersion, updated);
    }

    @Transactional
    public ProcurementPlan confirm(CanteenScope scope, String planId) {
        return plans.transition(scope, planId, ProcurementPlanStatus.CONFIRMED.name());
    }

    @Transactional
    public ProcurementPlan cancel(CanteenScope scope, String planId) {
        return plans.transition(scope, planId, ProcurementPlanStatus.CANCELLED.name());
    }

    @Transactional
    public PurchaseOrder convertToOrder(
            CanteenScope scope,
            String planId,
            String idempotencyKey,
            String supplierId,
            String orderType,
            Instant expectedDeliveryAt,
            String remark,
            List<OrderLine> requestedItems) {
        requireIdentifier("Idempotency-Key", idempotencyKey, 128);
        ProcurementPlan plan = find(scope, planId);
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new IllegalArgumentException("Purchase order items are required");
        }
        String normalizedSupplierId = requireIdentifier("supplierId", supplierId, 64);
        String normalizedOrderType = normalizeOrderType(orderType);
        String normalizedRemark = normalizeRemark(remark);
        List<PurchaseOrderItem> orderItems = toOrderItems(scope, plan, requestedItems);
        String payloadHash = orderPayloadHash(
                normalizedSupplierId,
                normalizedOrderType,
                expectedDeliveryAt,
                normalizedRemark,
                orderItems);
        ProcurementPlanStore.PlanOrder linked = plans.findOrderByPlan(scope, planId).orElse(null);
        if (linked != null) {
            if (!linked.idempotencyKey().equals(idempotencyKey)) {
                throw new IllegalStateException("Procurement plan has already been converted to an order");
            }
            PurchaseOrder existing = operationalStore.findPurchaseOrder(scope, linked.orderId())
                    .orElseThrow(() -> new IllegalStateException("Linked purchase order was not found"));
            if ((linked.payloadHash() != null && !linked.payloadHash().equals(payloadHash))
                    || !payloadHash.equals(orderPayloadHash(
                            existing.supplierId(),
                            existing.orderType(),
                            existing.expectedDeliveryAt(),
                            existing.remark(),
                            existing.items()))) {
                throw new IllegalArgumentException(
                        "Idempotency-Key was already used for a different procurement order");
            }
            if (linked.payloadHash() == null) {
                plans.recordOrderPayloadHash(scope, planId, idempotencyKey, payloadHash);
            }
            return existing;
        }
        ProcurementPlanStore.PlanOrder sameKey = plans.findOrderByIdempotencyKey(scope, idempotencyKey)
                .orElse(null);
        if (sameKey != null && !sameKey.planId().equals(planId)) {
            throw new IllegalArgumentException(
                    "Idempotency-Key was already used for a different procurement order");
        }
        if (plan.status() != ProcurementPlanStatus.CONFIRMED) {
            throw new IllegalStateException("Only a CONFIRMED procurement plan can create an order");
        }
        PurchaseOrder order = orders.createOrder(
                scope,
                null,
                null,
                normalizedSupplierId,
                normalizedOrderType,
                expectedDeliveryAt,
                normalizedRemark,
                idempotencyKey,
                orderItems);
        plans.linkOrder(
                scope, planId, plan.version(), order.id(), idempotencyKey, payloadHash);
        return order;
    }

    private List<PurchaseOrderItem> toOrderItems(
            CanteenScope scope, ProcurementPlan plan, List<OrderLine> requestedItems) {
        Map<String, ProcurementPlanItem> planned = new HashMap<>();
        plan.items().forEach(item -> planned.put(item.ingredientId(), item));
        Set<String> seen = new HashSet<>();
        List<PurchaseOrderItem> orderItems = new ArrayList<>();
        for (OrderLine requested : requestedItems) {
            if (!seen.add(requested.ingredientId())) {
                throw new IllegalArgumentException(
                        "Duplicate ingredient in purchase order: " + requested.ingredientId());
            }
            ProcurementPlanItem planItem = planned.get(requested.ingredientId());
            if (planItem == null) {
                throw new IllegalArgumentException(
                        "Ingredient is not in procurement plan: " + requested.ingredientId());
            }
            Ingredient ingredient = plans.findIngredient(scope, requested.ingredientId())
                    .filter(Ingredient::active)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown or disabled ingredient: " + requested.ingredientId()));
            BigDecimal requestedBase = quantities.toBase(
                            scope, ingredient, requested.quantity(), requested.unit())
                    .quantity();
            if (requestedBase.compareTo(planItem.plannedBaseQuantity()) > 0) {
                throw new IllegalArgumentException(
                        "Order quantity exceeds planned quantity for " + requested.ingredientId());
            }
            orderItems.add(new PurchaseOrderItem(
                    requested.ingredientId(), requested.quantity(), requested.unit(),
                    requested.unitPrice(), null));
        }
        return orderItems;
    }

    private static String normalizeOrderType(String value) {
        String normalized = requireIdentifier("orderType", value, 16).toUpperCase(java.util.Locale.ROOT);
        if (!List.of("ONLINE", "OFFLINE").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported orderType: " + value);
        }
        return normalized;
    }

    private static String normalizeRemark(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String orderPayloadHash(
            String supplierId,
            String orderType,
            Instant expectedDeliveryAt,
            String remark,
            List<PurchaseOrderItem> items) {
        StringBuilder canonical = new StringBuilder();
        appendToken(canonical, supplierId);
        appendToken(canonical, orderType);
        appendToken(canonical, expectedDeliveryAt == null ? null : expectedDeliveryAt.toString());
        appendToken(canonical, remark);
        items.stream()
                .sorted(Comparator.comparing(PurchaseOrderItem::ingredientId))
                .forEach(item -> {
                    appendToken(canonical, item.ingredientId());
                    appendToken(canonical, item.quantity().stripTrailingZeros().toPlainString());
                    appendToken(canonical, item.unit());
                    appendToken(canonical, item.unitPrice().stripTrailingZeros().toPlainString());
                });
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte value : digest) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void appendToken(StringBuilder target, String value) {
        String normalized = value == null ? "<null>" : value;
        target.append(normalized.length()).append(':').append(normalized).append('|');
    }

    public record PlanAdjustment(String ingredientId, BigDecimal quantity, String unit) {
        public PlanAdjustment {
            if (ingredientId == null || ingredientId.isBlank()) {
                throw new IllegalArgumentException("ingredientId is required");
            }
            if (quantity == null || quantity.signum() < 0) {
                throw new IllegalArgumentException("quantity must be non-negative");
            }
            if (unit == null || unit.isBlank()) {
                throw new IllegalArgumentException("unit is required");
            }
        }
    }

    public record OrderLine(
            String ingredientId, BigDecimal quantity, String unit, BigDecimal unitPrice) {
        public OrderLine {
            if (ingredientId == null || ingredientId.isBlank()) {
                throw new IllegalArgumentException("ingredientId is required");
            }
            if (quantity == null || quantity.signum() <= 0) {
                throw new IllegalArgumentException("quantity must be positive");
            }
            if (unit == null || unit.isBlank()) {
                throw new IllegalArgumentException("unit is required");
            }
            if (unitPrice == null || unitPrice.signum() < 0) {
                throw new IllegalArgumentException("unitPrice must be non-negative");
            }
        }
    }

    private static final class RequirementAccumulator {
        private final Ingredient ingredient;
        private final String baseUnit;
        private BigDecimal required = BigDecimal.ZERO;

        private RequirementAccumulator(Ingredient ingredient, String baseUnit) {
            this.ingredient = ingredient;
            this.baseUnit = baseUnit;
        }

        private void add(BigDecimal quantity) {
            required = required.add(quantity);
        }

        private ProcurementPlanItem toPlanItem(
                BigDecimal inventory, BigDecimal openOrders) {
            BigDecimal available = inventory.add(openOrders);
            BigDecimal shortage = required.subtract(available).max(BigDecimal.ZERO);
            return new ProcurementPlanItem(
                    ingredient.id(), required, inventory, openOrders,
                    shortage, shortage, baseUnit);
        }

        private ProcurementGapItem toGapItem(
                BigDecimal inventory, BigDecimal openOrders) {
            BigDecimal available = inventory.add(openOrders);
            BigDecimal shortage = required.subtract(available).max(BigDecimal.ZERO);
            return new ProcurementGapItem(
                    ingredient.id(),
                    ingredient.name(),
                    ingredient.category(),
                    required,
                    inventory,
                    openOrders,
                    shortage,
                    baseUnit);
        }
    }

    private static String requireIdentifier(String label, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(label + " exceeds " + maxLength + " characters");
        }
        return value.trim();
    }
}
