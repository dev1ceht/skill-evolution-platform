package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.InventoryReceiving;
import com.example.smartcanteen.application.port.InventoryStore;
import com.example.smartcanteen.domain.BaseQuantity;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.UnitConverter;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryReceivingService implements InventoryReceiving {

    private final InventoryStore inventory;
    private final UnitConverter unitConverter;

    public InventoryReceivingService(InventoryStore inventory, UnitConverter unitConverter) {
        this.inventory = inventory;
        this.unitConverter = unitConverter;
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceiptResult receive(
            String idempotencyKey,
            String materialId,
            BigDecimal quantity,
            String unit) {
        return receive(CanteenScope.DEFAULT, idempotencyKey, materialId, quantity, unit);
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReceiptResult receive(
            CanteenScope scope,
            String idempotencyKey,
            String materialId,
            BigDecimal quantity,
            String unit) {
        requireIdentifier("Idempotency-Key", idempotencyKey, 128);
        requireIdentifier("materialId", materialId, 64);
        requireIdentifier("unit", unit, 16);
        BaseQuantity received = unitConverter.convert(quantity, unit);
        InventoryStore.StoredReceipt stored = inventory.receiveOnce(scope, new InventoryStore.ReceiptCommand(
                idempotencyKey,
                materialId,
                quantity,
                unit,
                received.quantity(),
                received.unit()));
        return new ReceiptResult(
                stored.materialId(), stored.quantityBase(), stored.baseUnit());
    }

    private static void requireIdentifier(String label, String value, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(label + " exceeds " + maxLength + " characters");
        }
    }
}
