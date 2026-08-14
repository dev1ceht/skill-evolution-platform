package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.IngredientUnitStore;
import com.example.smartcanteen.domain.BaseQuantity;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.Ingredient;
import com.example.smartcanteen.domain.IngredientUnit;
import com.example.smartcanteen.domain.UnitConverter;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/** Converts an ingredient quantity using food-specific units first, then standard units. */
@Service
public class IngredientQuantityConverter {

    private final IngredientUnitStore units;
    private final UnitConverter standardUnits;

    public IngredientQuantityConverter(
            IngredientUnitStore units, UnitConverter standardUnits) {
        this.units = units;
        this.standardUnits = standardUnits;
    }

    public BaseQuantity toBase(
            CanteenScope scope, Ingredient ingredient, BigDecimal quantity, String unitCode) {
        if (quantity == null || quantity.signum() < 0) {
            throw new IllegalArgumentException("Quantity must be non-negative");
        }
        if (unitCode == null || unitCode.isBlank()) {
            throw new IllegalArgumentException("unit is required");
        }
        String normalizedUnit = unitCode.trim().toLowerCase();
        IngredientUnit configured = units.findIngredientUnit(
                        scope, ingredient.id(), normalizedUnit)
                .filter(IngredientUnit::active)
                .orElse(null);
        if (configured != null) {
            String expected = standardUnits.convert(BigDecimal.ONE, ingredient.baseUnit()).unit();
            String configuredBase = standardUnits.convert(
                    BigDecimal.ONE, configured.baseUnit()).unit();
            if (!expected.equals(configuredBase)) {
                throw new IllegalArgumentException(
                        "Configured unit base does not match ingredient " + ingredient.id());
            }
            return new BaseQuantity(
                    quantity.multiply(configured.toBaseFactor()), expected);
        }
        BaseQuantity converted = standardUnits.convert(quantity, normalizedUnit);
        String expected = standardUnits.convert(BigDecimal.ONE, ingredient.baseUnit()).unit();
        if (!expected.equals(converted.unit())) {
            throw new IllegalArgumentException(
                    "Unit " + unitCode + " is incompatible with ingredient " + ingredient.id());
        }
        return converted;
    }

    public void requireCompatible(
            CanteenScope scope, Ingredient ingredient, String unitCode) {
        toBase(scope, ingredient, BigDecimal.ONE, unitCode);
    }
}
