package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.smartcanteen.application.port.InventoryReceiving;
import com.example.smartcanteen.application.port.InventoryStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.UnitConverter;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScopedInventoryReceivingModuleTest {

    @Test
    void the_same_idempotency_key_can_be_reused_in_another_canteen() {
        CanteenScope north = new CanteenScope("SCHOOL-SCOPE", "CANTEEN-NORTH");
        CanteenScope south = new CanteenScope("SCHOOL-SCOPE", "CANTEEN-SOUTH");
        ScopedInventoryStore store = new ScopedInventoryStore();
        InventoryReceiving module = new InventoryReceivingService(store, new UnitConverter());

        InventoryReceiving.ReceiptResult northResult = module.receive(
                north, "receipt-shared", "FLOUR", new BigDecimal("1.5"), "kg");
        InventoryReceiving.ReceiptResult southResult = module.receive(
                south, "receipt-shared", "FLOUR", new BigDecimal("2"), "kg");

        assertThat(northResult.quantityBase()).isEqualByComparingTo("1500");
        assertThat(southResult.quantityBase()).isEqualByComparingTo("2000");
        assertThat(store.receipts).hasSize(2);
    }

    private static final class ScopedInventoryStore implements InventoryStore {

        private final Map<String, BigDecimal> receipts = new HashMap<>();

        @Override
        public Map<String, BigDecimal> inventorySnapshot() {
            return Map.of();
        }

        @Override
        public StoredReceipt receiveOnce(ReceiptCommand command) {
            throw new UnsupportedOperationException("scope is required");
        }

        @Override
        public StoredReceipt receiveOnce(CanteenScope scope, ReceiptCommand command) {
            String key = scope.schoolId() + ":" + scope.canteenId() + ":" + command.idempotencyKey();
            BigDecimal previous = receipts.putIfAbsent(key, command.baseQuantity());
            if (previous != null) {
                return new StoredReceipt(command.materialId(), previous, command.baseUnit());
            }
            return new StoredReceipt(command.materialId(), command.baseQuantity(), command.baseUnit());
        }
    }
}
