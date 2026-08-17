package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.IngredientRequirement;
import java.util.List;

/** Public use-case interface for importing a menu recipe before approval. */
public interface RecipeImport {

    RecipeResult importRecipe(
            CanteenScope scope,
            String menuId,
            List<IngredientRequirement> requirements);

    record RecipeResult(String menuId, List<IngredientRequirement> requirements) {
        public RecipeResult {
            requirements = List.copyOf(requirements);
        }
    }
}
