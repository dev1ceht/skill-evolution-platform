package com.example.smartcanteen.domain;

import java.math.BigDecimal;

public record IngredientRequirement(String materialId, BigDecimal quantity, String unit) {
}
