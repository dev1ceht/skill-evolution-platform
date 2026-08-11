package com.example.smartcanteen.application.port;

import java.math.BigDecimal;
import java.util.Map;

/** Persistence seam for inventory reads and idempotent receipts. */
public interface InventoryStore {

    Map<String, BigDecimal> inventorySnapshot();

    StoredReceipt receiveOnce(ReceiptCommand command);

    record ReceiptCommand(
            String idempotencyKey,
            String materialId,
            BigDecimal requestQuantity,
            String requestUnit,
            BigDecimal baseQuantity,
            String baseUnit) {
    }

    record StoredReceipt(String materialId, BigDecimal quantityBase, String baseUnit) {
    }
}
