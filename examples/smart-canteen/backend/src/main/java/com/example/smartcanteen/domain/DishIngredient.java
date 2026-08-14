package com.example.smartcanteen.domain;

import java.math.BigDecimal;

public record DishIngredient(String ingredientId, BigDecimal quantity, String unit) {

    public DishIngredient {
        if (ingredientId == null || ingredientId.isBlank()) {
            throw new IllegalArgumentException("ingredientId is required");
        }
        ingredientId = ingredientId.trim();
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("Dish ingredient quantity must be positive");
        }
        if (unit == null || unit.isBlank()) {
            throw new IllegalArgumentException("unit is required");
        }
        unit = unit.trim().toLowerCase();
    }
}
