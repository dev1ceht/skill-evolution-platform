package com.example.smartcanteen.application.port;

import java.math.BigDecimal;
import java.util.Map;
import com.example.smartcanteen.domain.CanteenScope;

/** Persistence seam for inventory reads and idempotent receipts. */
public interface InventoryStore {

    Map<String, BigDecimal> inventorySnapshot();

    StoredReceipt receiveOnce(ReceiptCommand command);

    default Map<String, BigDecimal> inventorySnapshot(CanteenScope scope) {
        return inventorySnapshot();
    }

    default StoredReceipt receiveOnce(CanteenScope scope, ReceiptCommand command) {
        return receiveOnce(command);
    }

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
