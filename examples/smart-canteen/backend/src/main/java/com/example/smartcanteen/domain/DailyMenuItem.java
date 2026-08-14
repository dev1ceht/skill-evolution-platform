package com.example.smartcanteen.domain;

import java.math.BigDecimal;

public record DailyMenuItem(String dishId, BigDecimal estimatedQuantity, int sortOrder) {

    public DailyMenuItem {
        if (dishId == null || dishId.isBlank()) {
            throw new IllegalArgumentException("dishId is required");
        }
        dishId = dishId.trim();
        if (estimatedQuantity == null || estimatedQuantity.signum() <= 0) {
            throw new IllegalArgumentException("estimatedQuantity must be positive");
        }
        if (sortOrder < 0) {
            throw new IllegalArgumentException("sortOrder cannot be negative");
        }
    }
}
