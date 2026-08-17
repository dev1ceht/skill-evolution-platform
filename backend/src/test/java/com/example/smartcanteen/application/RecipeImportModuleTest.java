package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.smartcanteen.application.port.MenuStore;
import com.example.smartcanteen.application.port.RecipeImport;
import com.example.smartcanteen.application.port.RecipeStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.IngredientRequirement;
import com.example.smartcanteen.domain.Menu;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RecipeImportModuleTest {

    @Test
    void imports_and_replaces_recipe_requirements_for_a_draft_menu() {
        CanteenScope scope = new CanteenScope("SCHOOL-RECIPE", "CANTEEN-RECIPE");
        Menu menu = new Menu("MENU-RECIPE");
        InMemoryRecipeData data = new InMemoryRecipeData(scope, menu);
        RecipeImport module = new RecipeImportService(
                data,
                data,
                new com.example.smartcanteen.domain.UnitConverter());

        RecipeImport.RecipeResult first = module.importRecipe(
                scope,
                menu.id(),
                List.of(new IngredientRequirement("FLOUR", new BigDecimal("2"), "kg")));
        RecipeImport.RecipeResult replacement = module.importRecipe(
                scope,
                menu.id(),
                List.of(new IngredientRequirement("EGG", new BigDecimal("12"), "count")));

        assertThat(first.requirements()).extracting(IngredientRequirement::materialId)
                .containsExactly("FLOUR");
        assertThat(replacement.requirements()).extracting(IngredientRequirement::materialId)
                .containsExactly("EGG");
        assertThat(data.findRecipe(scope, menu.id())).extracting(IngredientRequirement::materialId)
                .containsExactly("EGG");
    }

    @Test
    void only_draft_menus_can_receive_a_recipe_import() {
        CanteenScope scope = new CanteenScope("SCHOOL-RECIPE", "CANTEEN-RECIPE");
        Menu menu = new Menu("MENU-APPROVED");
        menu.submit();
        menu.approve("ready");
        InMemoryRecipeData data = new InMemoryRecipeData(scope, menu);
        RecipeImport module = new RecipeImportService(
                data,
                data,
                new com.example.smartcanteen.domain.UnitConverter());

        assertThatThrownBy(() -> module.importRecipe(
                scope,
                menu.id(),
                List.of(new IngredientRequirement("EGG", new BigDecimal("1"), "count"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void rejects_duplicate_materials_in_one_recipe() {
        CanteenScope scope = new CanteenScope("SCHOOL-RECIPE", "CANTEEN-RECIPE");
        Menu menu = new Menu("MENU-DUPLICATE");
        InMemoryRecipeData data = new InMemoryRecipeData(scope, menu);
        RecipeImport module = new RecipeImportService(
                data,
                data,
                new com.example.smartcanteen.domain.UnitConverter());

        assertThatThrownBy(() -> module.importRecipe(
                scope,
                menu.id(),
                List.of(
                        new IngredientRequirement("EGG", new BigDecimal("1"), "count"),
                        new IngredientRequirement("EGG", new BigDecimal("2"), "count"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate material");
    }

    @Test
    void rejects_blank_or_unsupported_units_at_the_module_boundary() {
        CanteenScope scope = new CanteenScope("SCHOOL-RECIPE", "CANTEEN-RECIPE");
        InMemoryRecipeData data = new InMemoryRecipeData(scope, new Menu("MENU-UNIT"));
        RecipeImport module = new RecipeImportService(
                data,
                data,
                new com.example.smartcanteen.domain.UnitConverter());

        assertThatThrownBy(() -> module.importRecipe(
                scope,
                "MENU-UNIT",
                List.of(new IngredientRequirement("EGG", new BigDecimal("1"), ""))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unit is required");
    }

    private static final class InMemoryRecipeData implements MenuStore, RecipeStore {

        private final CanteenScope scope;
        private final Menu menu;
        private final Map<String, List<IngredientRequirement>> recipes = new HashMap<>();

        private InMemoryRecipeData(CanteenScope scope, Menu menu) {
            this.scope = scope;
            this.menu = menu;
        }

        @Override
        public Optional<Menu> findMenu(String menuId) {
            throw new UnsupportedOperationException("scope is required");
        }

        @Override
        public void saveMenu(Menu menu) {
            throw new UnsupportedOperationException("not used by recipe import");
        }

        @Override
        public Optional<Menu> findMenu(CanteenScope requestedScope, String menuId) {
            return scope.equals(requestedScope) && menu.id().equals(menuId)
                    ? Optional.of(menu)
                    : Optional.empty();
        }

        @Override
        public List<IngredientRequirement> findRecipe(String menuId) {
            throw new UnsupportedOperationException("scope is required");
        }

        @Override
        public void replaceRecipe(
                CanteenScope requestedScope,
                String menuId,
                List<IngredientRequirement> requirements) {
            recipes.put(key(requestedScope, menuId), List.copyOf(new ArrayList<>(requirements)));
        }

        @Override
        public List<IngredientRequirement> findRecipe(
                CanteenScope requestedScope,
                String menuId) {
            return recipes.getOrDefault(key(requestedScope, menuId), List.of());
        }

        private String key(CanteenScope requestedScope, String menuId) {
            return requestedScope.schoolId() + ":" + requestedScope.canteenId() + ":" + menuId;
        }
    }
}
