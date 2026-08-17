package com.example.smartcanteen.domain;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

public final class UnitConverter {

    private static final Map<String, Conversion> CONVERSIONS = Map.of(
            "kg", new Conversion(new BigDecimal("1000"), "g"),
            "g", new Conversion(BigDecimal.ONE, "g"),
            "l", new Conversion(new BigDecimal("1000"), "ml"),
            "ml", new Conversion(BigDecimal.ONE, "ml"),
            "count", new Conversion(BigDecimal.ONE, "count"));

    public BigDecimal toBase(BigDecimal quantity, String unit) {
        return convert(quantity, unit).quantity();
    }

    public BaseQuantity convert(BigDecimal quantity, String unit) {
        if (quantity == null || quantity.signum() < 0) {
            throw new IllegalArgumentException("Quantity must be non-negative");
        }
        Conversion conversion = CONVERSIONS.get(unit.toLowerCase(Locale.ROOT));
        if (conversion == null) {
            throw new IllegalArgumentException("Unsupported unit: " + unit);
        }
        return new BaseQuantity(quantity.multiply(conversion.multiplier()), conversion.baseUnit());
    }

    private record Conversion(BigDecimal multiplier, String baseUnit) {
    }
}
