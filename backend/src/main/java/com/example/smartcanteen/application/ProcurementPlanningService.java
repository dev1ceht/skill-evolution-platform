package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.InventoryStore;
import com.example.smartcanteen.application.port.MenuStore;
import com.example.smartcanteen.application.port.ProcurementPlanning;
import com.example.smartcanteen.application.port.RecipeStore;
import com.example.smartcanteen.domain.Menu;
import com.example.smartcanteen.domain.MenuStatus;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.ProcurementItem;
import com.example.smartcanteen.domain.ProcurementService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcurementPlanningService implements ProcurementPlanning {

    private final MenuStore menus;
    private final RecipeStore recipes;
    private final InventoryStore inventory;
    private final ProcurementService procurement;

    public ProcurementPlanningService(
            MenuStore menus,
            RecipeStore recipes,
            InventoryStore inventory,
            ProcurementService procurement) {
        this.menus = menus;
        this.recipes = recipes;
        this.inventory = inventory;
        this.procurement = procurement;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcurementItem> generate(String menuId) {
        return generate(CanteenScope.DEFAULT, menuId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcurementItem> generate(CanteenScope scope, String menuId) {
        requireIdentifier("menuId", menuId, 64);
        Menu menu = menus.findMenu(scope, menuId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown menu: " + menuId));
        if (menu.status() != MenuStatus.APPROVED) {
            throw new IllegalStateException("Only approved menus can generate procurement plans");
        }
        return procurement.calculateShortages(
                recipes.findRecipe(scope, menuId), inventory.inventorySnapshot(scope));
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
