package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.IngredientUnit;
import java.util.List;
import java.util.Optional;

public interface IngredientUnitStore {

    List<IngredientUnit> listIngredientUnits(CanteenScope scope, String ingredientId);

    Optional<IngredientUnit> findIngredientUnit(
            CanteenScope scope, String ingredientId, String unitCode);

    void replaceIngredientUnits(
            CanteenScope scope, String ingredientId, List<IngredientUnit> units);
}
