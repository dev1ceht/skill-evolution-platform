package com.example.smartcanteen.domain;

import java.math.BigDecimal;
import java.time.Instant;

public record TraceabilityResult(
        String traceCode,
        String batchId,
        String orderId,
        String ingredientId,
        String ingredientName,
        String supplierId,
        String supplierName,
        BigDecimal quantity,
        String unit,
        Instant receivedAt) {
}
