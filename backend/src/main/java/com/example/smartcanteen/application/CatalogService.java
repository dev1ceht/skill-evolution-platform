package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.OperationalStore;
import com.example.smartcanteen.application.port.IngredientUnitStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.Dish;
import com.example.smartcanteen.domain.DishIngredient;
import com.example.smartcanteen.domain.Ingredient;
import com.example.smartcanteen.domain.IngredientUnit;
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
    private final IngredientQuantityConverter quantityConverter;
    private final IngredientUnitStore ingredientUnits;

    public CatalogService(
            OperationalStore store,
            IngredientQuantityConverter quantityConverter,
            IngredientUnitStore ingredientUnits) {
        this.store = store;
        this.quantityConverter = quantityConverter;
        this.ingredientUnits = ingredientUnits;
    }

    @Transactional(readOnly = true)
    public PageResult<Ingredient> listIngredients(
            CanteenScope scope, String keyword, String category, int page, int size) {
        return store.listIngredients(scope, keyword, category, page, size);
    }

    @Transactional
    public Ingredient saveIngredient(CanteenScope scope, Ingredient ingredient, boolean create) {
        return saveIngredient(scope, ingredient, create, null);
    }

    @Transactional
    public Ingredient saveIngredient(
            CanteenScope scope,
            Ingredient ingredient,
            boolean create,
            List<IngredientUnit> configuredUnits) {
        validateIngredientUnit(ingredient.baseUnit());
        if (create) {
            store.createIngredient(scope, ingredient);
        } else {
            store.updateIngredient(scope, ingredient);
        }
        if (create || configuredUnits != null) {
            ingredientUnits.replaceIngredientUnits(
                    scope, ingredient.id(), normalizeUnits(ingredient, configuredUnits));
        }
        return store.findIngredient(scope, ingredient.id())
                .orElseThrow(() -> new IllegalStateException("Ingredient was not persisted"));
    }

    @Transactional(readOnly = true)
    public List<IngredientUnit> listIngredientUnits(
            CanteenScope scope, String ingredientId) {
        store.findIngredient(scope, ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("Ingredient not found: " + ingredientId));
        return ingredientUnits.listIngredientUnits(scope, ingredientId);
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
            quantityConverter.requireCompatible(scope, ingredient, recipe.unit());
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
        new UnitConverter().convert(BigDecimal.ONE, unit);
    }

    private List<IngredientUnit> normalizeUnits(
            Ingredient ingredient, List<IngredientUnit> configuredUnits) {
        List<IngredientUnit> requested = configuredUnits == null || configuredUnits.isEmpty()
                ? List.of(new IngredientUnit(
                        ingredient.baseUnit(), ingredient.baseUnit(), BigDecimal.ONE, true))
                : configuredUnits;
        Set<String> codes = new HashSet<>();
        List<IngredientUnit> normalized = requested.stream()
                .map(unit -> {
                    if (!codes.add(unit.unitCode())) {
                        throw new IllegalArgumentException(
                                "Duplicate ingredient business unit: " + unit.unitCode());
                    }
                    String expected = new UnitConverter()
                            .convert(BigDecimal.ONE, ingredient.baseUnit()).unit();
                    String actual = new UnitConverter()
                            .convert(BigDecimal.ONE, unit.baseUnit()).unit();
                    if (!expected.equals(actual)) {
                        throw new IllegalArgumentException(
                                "Business unit base is incompatible with ingredient " + ingredient.id());
                    }
                    return unit;
                })
                .toList();
        if (normalized.stream().noneMatch(unit -> unit.unitCode().equals(ingredient.baseUnit()))) {
            normalized = new java.util.ArrayList<>(normalized);
            normalized.add(new IngredientUnit(
                    ingredient.baseUnit(), ingredient.baseUnit(), BigDecimal.ONE, true));
        }
        return List.copyOf(normalized);
    }
}
