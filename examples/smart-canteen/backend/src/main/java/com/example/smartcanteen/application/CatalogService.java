package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.OperationalStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.Dish;
import com.example.smartcanteen.domain.DishIngredient;
import com.example.smartcanteen.domain.Ingredient;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.domain.UnitConverter;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

    private final OperationalStore store;
    private final UnitConverter units;

    public CatalogService(OperationalStore store, UnitConverter units) {
        this.store = store;
        this.units = units;
    }

    @Transactional(readOnly = true)
    public PageResult<Ingredient> listIngredients(
            CanteenScope scope, String keyword, String category, int page, int size) {
        return store.listIngredients(scope, keyword, category, page, size);
    }

    @Transactional
    public Ingredient saveIngredient(CanteenScope scope, Ingredient ingredient, boolean create) {
        validateIngredientUnit(ingredient.baseUnit());
        if (create) {
            store.createIngredient(scope, ingredient);
        } else {
            store.updateIngredient(scope, ingredient);
        }
        return store.findIngredient(scope, ingredient.id())
                .orElseThrow(() -> new IllegalStateException("Ingredient was not persisted"));
    }

    @Transactional(readOnly = true)
    public PageResult<Dish> listDishes(
            CanteenScope scope, String keyword, String category, int page, int size) {
        return store.listDishes(scope, keyword, category, page, size);
    }

    @Transactional
    public Dish saveDish(CanteenScope scope, Dish dish, boolean create) {
        if (dish.ingredients().isEmpty()) {
            throw new IllegalArgumentException("A dish must contain at least one ingredient");
        }
        Set<String> ingredientIds = new HashSet<>();
        for (DishIngredient recipe : dish.ingredients()) {
            if (!ingredientIds.add(recipe.ingredientId())) {
                throw new IllegalArgumentException(
                        "Dish contains duplicate ingredient: " + recipe.ingredientId());
            }
            Ingredient ingredient = store.findIngredient(scope, recipe.ingredientId())
                    .filter(Ingredient::active)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown or disabled ingredient: " + recipe.ingredientId()));
            String recipeBaseUnit = units.convert(BigDecimal.ONE, recipe.unit()).unit();
            String ingredientBaseUnit = units.convert(BigDecimal.ONE, ingredient.baseUnit()).unit();
            if (!recipeBaseUnit.equals(ingredientBaseUnit)) {
                throw new IllegalArgumentException(
                        "Recipe unit is incompatible with ingredient " + recipe.ingredientId());
            }
        }
        if (create) {
            store.createDish(scope, dish);
        } else {
            store.updateDish(scope, dish);
        }
        return store.findDish(scope, dish.id())
                .orElseThrow(() -> new IllegalStateException("Dish was not persisted"));
    }

    public static String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private void validateIngredientUnit(String unit) {
        units.convert(BigDecimal.ONE, unit);
    }
}
