package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.DailyMenu;
import com.example.smartcanteen.domain.Dish;
import com.example.smartcanteen.domain.Ingredient;
import com.example.smartcanteen.domain.ProcurementPlan;
import com.example.smartcanteen.domain.ProcurementPlanItem;
import com.example.smartcanteen.domain.PageResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Persistence and read seam for the recipe-driven procurement plan aggregate. */
public interface ProcurementPlanStore {

    List<DailyMenu> findPublishedMenus(
            CanteenScope scope, LocalDate periodStart, LocalDate periodEnd);

    Optional<Dish> findDish(CanteenScope scope, String dishId);

    Optional<Ingredient> findIngredient(CanteenScope scope, String ingredientId);

    Map<String, BigDecimal> inventorySnapshot(CanteenScope scope);

    Map<String, BigDecimal> openOrderSnapshot(CanteenScope scope);

    PageResult<ProcurementPlan> list(
            CanteenScope scope, String status, int page, int size);

    Optional<ProcurementPlan> find(CanteenScope scope, String planId);

    Optional<ProcurementPlan> findByIdempotencyKey(
            CanteenScope scope, String idempotencyKey);

    ProcurementPlan create(
            CanteenScope scope, ProcurementPlan plan, String idempotencyKey);

    ProcurementPlan updateItems(
            CanteenScope scope,
            String planId,
            long expectedVersion,
            List<ProcurementPlanItem> items);

    ProcurementPlan transition(
            CanteenScope scope, String planId, String targetStatus);

    Optional<PlanOrder> findOrderByPlan(CanteenScope scope, String planId);

    Optional<PlanOrder> findOrderByIdempotencyKey(
            CanteenScope scope, String idempotencyKey);

    ProcurementPlan linkOrder(
            CanteenScope scope,
            String planId,
            long expectedVersion,
            String orderId,
            String idempotencyKey,
            String payloadHash);

    void recordOrderPayloadHash(
            CanteenScope scope, String planId, String idempotencyKey, String payloadHash);

    record PlanOrder(String planId, String orderId, String idempotencyKey, String payloadHash) {
        public PlanOrder(String planId, String orderId, String idempotencyKey) {
            this(planId, orderId, idempotencyKey, null);
        }
    }
}
