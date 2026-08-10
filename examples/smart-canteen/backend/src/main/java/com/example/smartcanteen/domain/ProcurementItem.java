package com.example.smartcanteen.domain;

import java.math.BigDecimal;

public record ProcurementItem(
        String materialId,
        BigDecimal requiredBaseQuantity,
        BigDecimal shortageBaseQuantity,
        String baseUnit) {
}
