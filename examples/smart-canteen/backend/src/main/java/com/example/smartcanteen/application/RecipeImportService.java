package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.MenuStore;
import com.example.smartcanteen.application.port.RecipeImport;
import com.example.smartcanteen.application.port.RecipeStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.IngredientRequirement;
import com.example.smartcanteen.domain.Menu;
import com.example.smartcanteen.domain.MenuStatus;
import com.example.smartcanteen.domain.UnitConverter;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecipeImportService implements RecipeImport {

    private final MenuStore menus;
    private final RecipeStore recipes;
    private final UnitConverter units;

    public RecipeImportService(MenuStore menus, RecipeStore recipes, UnitConverter units) {
        this.menus = menus;
        this.recipes = recipes;
        this.units = units;
    }

    @Override
    @Transactional
    public RecipeResult importRecipe(
            CanteenScope scope,
            String menuId,
            List<IngredientRequirement> requirements) {
        requireIdentifier("menuId", menuId, 64);
        if (requirements == null || requirements.isEmpty()) {
            throw new IllegalArgumentException("At least one recipe requirement is required");
        }

        Menu menu = menus.findMenu(scope, menuId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown menu: " + menuId));
        if (menu.status() != MenuStatus.DRAFT) {
            throw new IllegalStateException("Only DRAFT menus can receive a recipe import");
        }

        Set<String> materialIds = new HashSet<>();
        List<IngredientRequirement> validated = requirements.stream()
                .map(requirement -> validateRequirement(requirement, materialIds))
                .toList();
        recipes.replaceRecipe(scope, menuId, validated);
        return new RecipeResult(menuId, validated);
    }

    private IngredientRequirement validateRequirement(
            IngredientRequirement requirement,
            Set<String> materialIds) {
        if (requirement == null) {
            throw new IllegalArgumentException("Recipe requirement is required");
        }
        requireIdentifier("materialId", requirement.materialId(), 64);
        if (!materialIds.add(requirement.materialId())) {
            throw new IllegalArgumentException(
                    "Recipe contains duplicate material: " + requirement.materialId());
        }
        BigDecimal quantity = requirement.quantity();
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("Recipe quantity must be positive");
        }
        requireIdentifier("unit", requirement.unit(), 16);
        units.convert(quantity, requirement.unit());
        return requirement;
    }

    private static void requireIdentifier(String label, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(label + " exceeds " + maxLength + " characters");
        }
    }
}
