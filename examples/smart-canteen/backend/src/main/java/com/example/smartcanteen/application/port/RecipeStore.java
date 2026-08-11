package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.IngredientRequirement;
import java.util.List;

/** Read seam for recipe requirements used by procurement planning. */
public interface RecipeStore {

    List<IngredientRequirement> findRecipe(String menuId);
}
