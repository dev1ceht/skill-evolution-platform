package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.smartcanteen.application.port.InventoryStore;
import com.example.smartcanteen.application.port.MenuStore;
import com.example.smartcanteen.application.port.ProcurementPlanning;
import com.example.smartcanteen.application.port.RecipeStore;
import com.example.smartcanteen.domain.IngredientRequirement;
import com.example.smartcanteen.domain.Menu;
import com.example.smartcanteen.domain.ProcurementService;
import com.example.smartcanteen.domain.UnitConverter;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProcurementPlanningModuleTest {

    @Test
    void procurement_module_requires_an_approved_menu_and_calculates_shortage() {
        Menu menu = new Menu("MENU-MODULE-002");
        menu.submit();
        menu.approve("approved for module test");

        MenuStore menus = new MenuStore() {
            @Override
            public Optional<Menu> findMenu(String menuId) {
                return Optional.of(menu);
            }

            @Override
            public void saveMenu(Menu ignored) {
                throw new UnsupportedOperationException("not used by planning");
            }
        };
        RecipeStore recipes = menuId -> List.of(
                new IngredientRequirement("EGG", new BigDecimal("12"), "count"));
        InventoryStore inventory = new InventoryStore() {
            @Override
            public Map<String, BigDecimal> inventorySnapshot() {
                return Map.of("EGG", new BigDecimal("5"));
            }

            @Override
            public StoredReceipt receiveOnce(ReceiptCommand command) {
                throw new UnsupportedOperationException("not used by planning");
            }
        };
        ProcurementPlanning module = new ProcurementPlanningService(
                menus, recipes, inventory, new ProcurementService(new UnitConverter()));

        assertThat(module.generate("MENU-MODULE-002"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.materialId()).isEqualTo("EGG");
                    assertThat(item.shortageBaseQuantity()).isEqualByComparingTo("7");
                    assertThat(item.baseUnit()).isEqualTo("count");
                });
    }
}
