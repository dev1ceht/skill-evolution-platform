package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.smartcanteen.application.port.IngredientUnitStore;
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
import com.example.smartcanteen.domain.UnitConverter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProcurementPlanServiceModuleTest {

    @Test
    void aggregates_published_menus_and_persists_inventory_order_snapshot() {
        CanteenScope scope = new CanteenScope("SCHOOL-MODULE", "CANTEEN-MODULE");
        Ingredient rice = new Ingredient(
                "RICE", "Rice", "staple", "g", null, Nutrition.zero(), BigDecimal.ZERO, true);
        Dish dish = new Dish(
                "DISH-RICE", "Rice dish", "staple", null, null, true, 0,
                List.of(new DishIngredient("RICE", new BigDecimal("100"), "g")));
        DailyMenu menu = new DailyMenu(
                "M002", LocalDate.of(2026, 8, 14), "LUNCH", "PUBLISHED", 0,
                List.of(new DailyMenuItem("DISH-RICE", new BigDecimal("100"), 0)));
        FakePlanStore store = new FakePlanStore(menu, dish, rice);
        IngredientQuantityConverter converter = new IngredientQuantityConverter(
                new IngredientUnitStore() {
                    @Override
                    public List<com.example.smartcanteen.domain.IngredientUnit> listIngredientUnits(
                            CanteenScope ignored, String ingredientId) {
                        return List.of();
                    }

                    @Override
                    public Optional<com.example.smartcanteen.domain.IngredientUnit> findIngredientUnit(
                            CanteenScope ignored, String ingredientId, String unitCode) {
                        return Optional.empty();
                    }

                    @Override
                    public void replaceIngredientUnits(
                            CanteenScope ignored,
                            String ingredientId,
                            List<com.example.smartcanteen.domain.IngredientUnit> units) {
                        throw new UnsupportedOperationException("not used by planning");
                    }
                },
                new UnitConverter());
        ProcurementPlanService service = new ProcurementPlanService(
                store, converter, null, null);

        ProcurementPlan plan = service.generate(
                scope, LocalDate.of(2026, 8, 14), LocalDate.of(2026, 8, 14), "PLAN-MODULE-1");

        assertThat(plan.items()).singleElement().satisfies(item -> {
            assertThat(item.ingredientId()).isEqualTo("RICE");
            assertThat(item.requiredBaseQuantity()).isEqualByComparingTo("10000");
            assertThat(item.inventoryBaseQuantity()).isEqualByComparingTo("1000");
            assertThat(item.openOrderBaseQuantity()).isEqualByComparingTo("2000");
            assertThat(item.shortageBaseQuantity()).isEqualByComparingTo("7000");
            assertThat(item.plannedBaseQuantity()).isEqualByComparingTo("7000");
        });
        assertThat(store.persisted).isSameAs(plan);
    }

    private static final class FakePlanStore implements ProcurementPlanStore {

        private final DailyMenu menu;
        private final Dish dish;
        private final Ingredient ingredient;
        private final Map<String, ProcurementPlan> plans = new HashMap<>();
        private ProcurementPlan persisted;

        private FakePlanStore(DailyMenu menu, Dish dish, Ingredient ingredient) {
            this.menu = menu;
            this.dish = dish;
            this.ingredient = ingredient;
        }

        @Override
        public List<DailyMenu> findPublishedMenus(
                CanteenScope scope, LocalDate periodStart, LocalDate periodEnd) {
            return List.of(menu);
        }

        @Override
        public Optional<Dish> findDish(CanteenScope scope, String dishId) {
            return Optional.of(dish);
        }

        @Override
        public Optional<Ingredient> findIngredient(CanteenScope scope, String ingredientId) {
            return Optional.of(ingredient);
        }

        @Override
        public Map<String, BigDecimal> inventorySnapshot(CanteenScope scope) {
            return Map.of("RICE", new BigDecimal("1000"));
        }

        @Override
        public Map<String, BigDecimal> openOrderSnapshot(CanteenScope scope) {
            return Map.of("RICE", new BigDecimal("2000"));
        }

        @Override
        public PageResult<ProcurementPlan> list(
                CanteenScope scope, String status, int page, int size) {
            return new PageResult<>(List.copyOf(plans.values()), page, size, plans.size());
        }

        @Override
        public Optional<ProcurementPlan> find(CanteenScope scope, String planId) {
            return Optional.ofNullable(plans.get(planId));
        }

        @Override
        public Optional<ProcurementPlan> findByIdempotencyKey(
                CanteenScope scope, String idempotencyKey) {
            return Optional.empty();
        }

        @Override
        public ProcurementPlan create(
                CanteenScope scope, ProcurementPlan plan, String idempotencyKey) {
            persisted = plan;
            plans.put(plan.id(), plan);
            return plan;
        }

        @Override
        public ProcurementPlan updateItems(
                CanteenScope scope, String planId, long expectedVersion,
                List<ProcurementPlanItem> items) {
            throw new UnsupportedOperationException("not used by planning generation");
        }

        @Override
        public ProcurementPlan transition(
                CanteenScope scope, String planId, String targetStatus) {
            throw new UnsupportedOperationException("not used by planning generation");
        }

        @Override
        public Optional<PlanOrder> findOrderByPlan(CanteenScope scope, String planId) {
            return Optional.empty();
        }

        @Override
        public Optional<PlanOrder> findOrderByIdempotencyKey(
                CanteenScope scope, String idempotencyKey) {
            return Optional.empty();
        }

        @Override
        public ProcurementPlan linkOrder(
                CanteenScope scope, String planId, long expectedVersion,
                String orderId, String idempotencyKey, String payloadHash) {
            throw new UnsupportedOperationException("not used by planning generation");
        }

        @Override
        public void recordOrderPayloadHash(
                CanteenScope scope, String planId, String idempotencyKey, String payloadHash) {
            throw new UnsupportedOperationException("not used by planning generation");
        }
    }
}
