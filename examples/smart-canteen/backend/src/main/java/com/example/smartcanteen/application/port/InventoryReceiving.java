package com.example.smartcanteen.application.port;

import java.math.BigDecimal;
import com.example.smartcanteen.domain.CanteenScope;

/** Public use-case interface for idempotent inventory receiving. */
public interface InventoryReceiving {

    ReceiptResult receive(
            String idempotencyKey,
            String materialId,
            BigDecimal quantity,
            String unit);

    default ReceiptResult receive(
            CanteenScope scope,
            String idempotencyKey,
            String materialId,
            BigDecimal quantity,
            String unit) {
        return receive(idempotencyKey, materialId, quantity, unit);
    }

    record ReceiptResult(String materialId, BigDecimal quantityBase, String baseUnit) {
    }
}
