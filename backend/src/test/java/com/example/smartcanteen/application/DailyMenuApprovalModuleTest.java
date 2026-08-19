package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.smartcanteen.application.port.OperationalStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.DailyMenu;
import com.example.smartcanteen.domain.DailyMenuItem;
import com.example.smartcanteen.domain.Dish;
import com.example.smartcanteen.domain.DishIngredient;
import com.example.smartcanteen.domain.Ingredient;
import com.example.smartcanteen.domain.PageResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DailyMenuApprovalModuleTest {

    private static final CanteenScope SCOPE = new CanteenScope("SCHOOL-MENU", "CANTEEN-MENU");

    @Test
    void canonical_daily_menu_requires_domain_approval_before_publish() {
        FakeOperationalStore store = new FakeOperationalStore();
        DailyMenuService service = new DailyMenuService(store);
        DailyMenu draft = new DailyMenu(
                "M003",
                LocalDate.of(2026, 8, 17),
                "LUNCH",
                "DRAFT",
                0,
                List.of(new DailyMenuItem("DISH-001", BigDecimal.TEN, 0)));
        store.menu = draft;

        DailyMenu submitted = service.submitForApproval(SCOPE, draft.id(), 0, "USER-SUBMIT");
        assertThat(submitted.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(submitted.submittedBy()).isEqualTo("USER-SUBMIT");

        DailyMenu approved = service.recordDecision(
                SCOPE, draft.id(), 1, "APPROVE", "checked", "USER-APPROVER");
        assertThat(approved.status()).isEqualTo("APPROVED");
        assertThat(approved.decisionBy()).isEqualTo("USER-APPROVER");

        DailyMenu published = service.publish(SCOPE, draft.id(), 2, "USER-PUBLISH");
        assertThat(published.status()).isEqualTo("PUBLISHED");
        assertThat(published.publishedBy()).isEqualTo("USER-PUBLISH");
        assertThatThrownBy(() -> service.publish(SCOPE, draft.id(), 3, "USER-PUBLISH"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("published again");
    }

    @Test
    void rejected_menu_cannot_be_published_or_edited() {
        FakeOperationalStore store = new FakeOperationalStore();
        DailyMenuService service = new DailyMenuService(store);
        store.menu = new DailyMenu(
                "M004",
                LocalDate.of(2026, 8, 17),
                "DINNER",
                "PENDING_APPROVAL",
                4,
                List.of(new DailyMenuItem("DISH-001", BigDecimal.ONE, 0)),
                "USER-SUBMIT",
                null,
                null,
                null);

        DailyMenu rejected = service.recordDecision(
                SCOPE, "M004", 4, "REJECT", "missing allergen note", "USER-APPROVER");
        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThatThrownBy(() -> service.publish(SCOPE, "M004", 5, "USER-PUBLISH"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("domain approval");
    }

    @Test
    void submitter_cannot_approve_or_publish_the_same_menu() {
        FakeOperationalStore store = new FakeOperationalStore();
        DailyMenuService service = new DailyMenuService(store);
        store.menu = new DailyMenu(
                "M005",
                LocalDate.of(2026, 8, 17),
                "LUNCH",
                "PENDING_APPROVAL",
                1,
                List.of(new DailyMenuItem("DISH-001", BigDecimal.ONE, 0)),
                "SAME-USER",
                null,
                null,
                null);

        assertThatThrownBy(() -> service.recordDecision(
                SCOPE, "M005", 1, "APPROVE", "", "SAME-USER"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("submitter");
    }

    @Test
    void submit_preflight_checks_recipe_ingredients_and_unique_meal_slot() {
        FakeOperationalStore store = new FakeOperationalStore();
        DailyMenuService service = new DailyMenuService(store);
        DailyMenu draft = new DailyMenu(
                "M006",
                LocalDate.of(2026, 8, 17),
                "LUNCH",
                "DRAFT",
                0,
                List.of(new DailyMenuItem("DISH-001", BigDecimal.ONE, 0)));
        store.menu = draft;

        store.recipeIngredientActive = false;
        assertThatThrownBy(() -> service.validateForSubmit(SCOPE, draft.id(), 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recipe ingredient");

        store.recipeIngredientActive = true;
        store.dailyMenus = List.of(new DailyMenu(
                "M007",
                draft.menuDate(),
                draft.mealTime(),
                "DRAFT",
                0,
                List.of(new DailyMenuItem("DISH-001", BigDecimal.ONE, 0))));
        assertThatThrownBy(() -> service.validateForSubmit(SCOPE, draft.id(), 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    private static final class FakeOperationalStore implements OperationalStore {

        private DailyMenu menu;
        private List<DailyMenu> dailyMenus = List.of();
        private boolean recipeIngredientActive = true;

        @Override
        public Optional<DailyMenu> findDailyMenu(CanteenScope scope, String menuId) {
            return Optional.ofNullable(menu);
        }

        @Override
        public void submitDailyMenu(CanteenScope scope, String menuId, long expectedVersion, String actorUserId) {
            menu = menu.withLifecycle("PENDING_APPROVAL", actorUserId, menu.decisionBy(),
                    menu.decisionComment(), menu.publishedBy(), menu.version() + 1);
        }

        @Override
        public void decideDailyMenu(
                CanteenScope scope,
                String menuId,
                long expectedVersion,
                String decision,
                String comment,
                String actorUserId) {
            menu = menu.withLifecycle(
                    decision.equals("APPROVE") ? "APPROVED" : "REJECTED",
                    menu.submittedBy(),
                    actorUserId,
                    comment,
                    menu.publishedBy(),
                    menu.version() + 1);
        }

        @Override
        public void publishDailyMenu(
                CanteenScope scope,
                String menuId,
                long expectedVersion,
                String actorUserId) {
            menu = menu.withLifecycle(
                    "PUBLISHED", menu.submittedBy(), menu.decisionBy(), menu.decisionComment(),
                    actorUserId, menu.version() + 1);
        }

        @Override
        public PageResult<Dish> listDishes(CanteenScope scope, String keyword, String category, int page, int size) {
            return new PageResult<>(List.of(), page, size, 0);
        }

        @Override
        public Optional<Dish> findDish(CanteenScope scope, String dishId) {
            return Optional.of(new Dish(
                    "DISH-001", "Dish", "MAIN", null, null, true, 0,
                    List.of(new DishIngredient("ING-001", BigDecimal.ONE, "kg"))));
        }

        @Override
        public Optional<Ingredient> findIngredient(CanteenScope scope, String ingredientId) {
            return Optional.of(new Ingredient(
                    "ING-001", "Ingredient", "VEGETABLE", "kg", null, null, BigDecimal.ZERO,
                    recipeIngredientActive));
        }

        @Override public PageResult listIngredients(CanteenScope s, String k, String c, int p, int z) { return empty(p, z); }
        @Override public void createIngredient(CanteenScope s, com.example.smartcanteen.domain.Ingredient i) { }
        @Override public void updateIngredient(CanteenScope s, com.example.smartcanteen.domain.Ingredient i) { }
        @Override public void createDish(CanteenScope s, Dish d) { }
        @Override public void updateDish(CanteenScope s, Dish d) { }
        @Override public PageResult<DailyMenu> listDailyMenus(CanteenScope s, LocalDate f, LocalDate t, int p, int z) {
            return new PageResult<>(dailyMenus, p, z, dailyMenus.size());
        }
        @Override public void saveDailyMenu(CanteenScope s, DailyMenu m, boolean c) { menu = m; }
        @Override public PageResult listSuppliers(CanteenScope s, String k, int p, int z) { return empty(p, z); }
        @Override public Optional findSupplier(CanteenScope s, String id) { return Optional.empty(); }
        @Override public void createSupplier(CanteenScope s, com.example.smartcanteen.domain.Supplier v) { }
        @Override public PageResult listPurchaseOrders(CanteenScope s, String st, int p, int z) { return empty(p, z); }
        @Override public Optional findPurchaseOrder(CanteenScope s, String id) { return Optional.empty(); }
        @Override public com.example.smartcanteen.domain.PurchaseOrder createPurchaseOrder(CanteenScope s, com.example.smartcanteen.domain.PurchaseOrder o, String k) { return o; }
        @Override public com.example.smartcanteen.domain.PurchaseOrder transitionPurchaseOrder(CanteenScope s, String id, String st) { throw new UnsupportedOperationException(); }
        @Override public ReceiveResult receivePurchaseOrder(CanteenScope s, String id, String k, List<ReceiveItem> i) { throw new UnsupportedOperationException(); }
        @Override public ReceiveResult receiveInventory(CanteenScope s, String k, String supplierId, ReceiveItem i) { throw new UnsupportedOperationException(); }
        @Override public PageResult listInventory(CanteenScope s, String k, boolean w, int p, int z) { return empty(p, z); }
        @Override public StockOutResult stockOut(CanteenScope s, String k, String r, List<StockOutItem> i) { throw new UnsupportedOperationException(); }
        @Override public com.example.smartcanteen.domain.OperationalLedgerRecord saveLedgerRecord(CanteenScope s, com.example.smartcanteen.domain.OperationalLedgerRecord r) { return r; }
        @Override public PageResult listLedgerRecords(CanteenScope s, String c, String l, String st, java.time.Instant f, java.time.Instant t, int p, int z) { return empty(p, z); }
        @Override public LedgerStats ledgerStats(CanteenScope s, LocalDate f, LocalDate t) { return new LedgerStats(0, 0, 0); }
        @Override public com.example.smartcanteen.domain.DashboardSummary dashboardSummary(CanteenScope s, LocalDate d) { throw new UnsupportedOperationException(); }
        @Override public Optional<com.example.smartcanteen.domain.TraceabilityResult> trace(CanteenScope s, String c) { return Optional.empty(); }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private static PageResult empty(int page, int size) { return new PageResult(List.of(), page, size, 0); }
    }
}
