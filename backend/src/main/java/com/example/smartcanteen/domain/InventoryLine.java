package com.example.smartcanteen.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record InventoryLine(
        String ingredientId,
        String ingredientName,
        String category,
        BigDecimal quantity,
        String unit,
        BigDecimal warningThreshold,
        boolean warning,
        Instant lastUpdateTime) {
}
