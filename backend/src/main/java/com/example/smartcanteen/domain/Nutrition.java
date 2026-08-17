package com.example.smartcanteen.domain;

import java.math.BigDecimal;

public record Nutrition(
        BigDecimal energyKcal,
        BigDecimal proteinG,
        BigDecimal fatG,
        BigDecimal carbohydrateG) {

    public Nutrition {
        energyKcal = nonNegative(energyKcal, "energyKcal");
        proteinG = nonNegative(proteinG, "proteinG");
        fatG = nonNegative(fatG, "fatG");
        carbohydrateG = nonNegative(carbohydrateG, "carbohydrateG");
    }

    public static Nutrition zero() {
        return new Nutrition(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    public Nutrition plus(Nutrition other) {
        return new Nutrition(
                energyKcal.add(other.energyKcal),
                proteinG.add(other.proteinG),
                fatG.add(other.fatG),
                carbohydrateG.add(other.carbohydrateG));
    }

    public Nutrition multiply(BigDecimal factor) {
        return new Nutrition(
                energyKcal.multiply(factor),
                proteinG.multiply(factor),
                fatG.multiply(factor),
                carbohydrateG.multiply(factor));
    }

    private static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
        return value;
    }
}
