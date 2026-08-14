package com.example.smartcanteen.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PurchaseOrder(
        String id,
        String orderNo,
        String supplierId,
        String orderType,
        String status,
        Instant expectedDeliveryAt,
        BigDecimal totalAmount,
        String remark,
        Instant createdAt,
        List<PurchaseOrderItem> items) {

    public PurchaseOrder {
        id = required(id, "orderId", 64);
        orderNo = required(orderNo, "orderNo", 64);
        supplierId = required(supplierId, "supplierId", 64);
        orderType = required(orderType, "orderType", 16).toUpperCase();
        if (!List.of("ONLINE", "OFFLINE").contains(orderType)) {
            throw new IllegalArgumentException("Unsupported orderType: " + orderType);
        }
        status = required(status, "status", 16).toUpperCase();
        if (totalAmount == null || totalAmount.signum() < 0) {
            throw new IllegalArgumentException("totalAmount must be non-negative");
        }
        remark = optional(remark, "remark", 1000);
        items = items == null ? List.of() : List.copyOf(items);
    }

    private static String required(String value, String name, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new IllegalArgumentException(name + " exceeds " + max + " characters");
        }
        return normalized;
    }

    private static String optional(String value, String name, int max) {
        return value == null || value.isBlank() ? null : required(value, name, max);
    }
}
