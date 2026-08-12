package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.IngredientRequirement;
import com.example.smartcanteen.domain.CanteenScope;
import java.util.List;

/** Read seam for recipe requirements used by procurement planning. */
public interface RecipeStore {

    List<IngredientRequirement> findRecipe(String menuId);

    /** Replace the complete recipe for a menu in one transaction. */
    default void replaceRecipe(
            CanteenScope scope,
            String menuId,
            List<IngredientRequirement> requirements) {
        throw new UnsupportedOperationException("Scoped recipe replacement is not supported");
    }

    default List<IngredientRequirement> findRecipe(CanteenScope scope, String menuId) {
        return findRecipe(menuId);
    }
}
