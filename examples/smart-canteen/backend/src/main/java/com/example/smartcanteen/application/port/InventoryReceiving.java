package com.example.smartcanteen.application.port;

import java.math.BigDecimal;

/** Public use-case interface for idempotent inventory receiving. */
public interface InventoryReceiving {

    ReceiptResult receive(
            String idempotencyKey,
            String materialId,
            BigDecimal quantity,
            String unit);

    record ReceiptResult(String materialId, BigDecimal quantityBase, String baseUnit) {
    }
}
