package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.smartcanteen.application.port.InventoryStore;
import com.example.smartcanteen.application.port.MenuStore;
import com.example.smartcanteen.application.port.ProcurementPlanning;
import com.example.smartcanteen.application.port.RecipeStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.IngredientRequirement;
import com.example.smartcanteen.domain.Menu;
import com.example.smartcanteen.domain.ProcurementService;
import com.example.smartcanteen.domain.UnitConverter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ScopedProcurementPlanningModuleTest {

    @Test
    void procurement_reads_recipe_and_inventory_from_the_requested_canteen() {
        CanteenScope north = new CanteenScope("SCHOOL-SCOPE", "CANTEEN-NORTH");
        CanteenScope south = new CanteenScope("SCHOOL-SCOPE", "CANTEEN-SOUTH");
        Menu northMenu = approvedMenu("MENU-SHARED");
        Menu southMenu = approvedMenu("MENU-SHARED");
        ScopedData data = new ScopedData();
        data.menus.put(north, northMenu);
        data.menus.put(south, southMenu);
        data.recipes.put(north, List.of(
                new IngredientRequirement("EGG", new BigDecimal("12"), "count")));
        data.recipes.put(south, List.of(
                new IngredientRequirement("EGG", new BigDecimal("3"), "count")));
        data.inventory.put(north, Map.of("EGG", new BigDecimal("5")));
        data.inventory.put(south, Map.of("EGG", new BigDecimal("5")));

        ProcurementPlanning module = new ProcurementPlanningService(
                data, data, data, new ProcurementService(new UnitConverter()));

        assertThat(module.generate(north, "MENU-SHARED").get(0).shortageBaseQuantity())
                .isEqualByComparingTo("7");
        assertThat(module.generate(south, "MENU-SHARED")).isEmpty();
    }

    private static Menu approvedMenu(String id) {
        Menu menu = new Menu(id);
        menu.submit();
        menu.approve("approved");
        return menu;
    }

    private static final class ScopedData implements MenuStore, RecipeStore, InventoryStore {

        private final Map<CanteenScope, Menu> menus = new java.util.HashMap<>();
        private final Map<CanteenScope, List<IngredientRequirement>> recipes = new java.util.HashMap<>();
        private final Map<CanteenScope, Map<String, BigDecimal>> inventory = new java.util.HashMap<>();

        @Override
        public Optional<Menu> findMenu(String menuId) {
            throw new UnsupportedOperationException("scope is required");
        }

        @Override
        public void saveMenu(Menu menu) {
            throw new UnsupportedOperationException("scope is required");
        }

        @Override
        public Optional<Menu> findMenu(CanteenScope scope, String menuId) {
            return Optional.ofNullable(menus.get(scope));
        }

        @Override
        public List<IngredientRequirement> findRecipe(String menuId) {
            throw new UnsupportedOperationException("scope is required");
        }

        @Override
        public List<IngredientRequirement> findRecipe(CanteenScope scope, String menuId) {
            return recipes.getOrDefault(scope, List.of());
        }

        @Override
        public Map<String, BigDecimal> inventorySnapshot() {
            throw new UnsupportedOperationException("scope is required");
        }

        @Override
        public StoredReceipt receiveOnce(ReceiptCommand command) {
            throw new UnsupportedOperationException("not used by planning");
        }

        @Override
        public Map<String, BigDecimal> inventorySnapshot(CanteenScope scope) {
            return inventory.getOrDefault(scope, Map.of());
        }
    }
}
