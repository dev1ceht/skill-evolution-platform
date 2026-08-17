package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.smartcanteen.application.port.InventoryReceiving;
import com.example.smartcanteen.application.port.InventoryStore;
import com.example.smartcanteen.domain.UnitConverter;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InventoryReceivingModuleTest {

    @Test
    void inventory_module_converts_units_before_crossing_the_store_port() {
        CapturingInventoryStore store = new CapturingInventoryStore();
        InventoryReceiving module = new InventoryReceivingService(store, new UnitConverter());

        InventoryReceiving.ReceiptResult result = module.receive(
                "receipt-module-001", "FLOUR", new BigDecimal("1.5"), "kg");

        assertThat(store.command.baseQuantity()).isEqualByComparingTo("1500");
        assertThat(store.command.baseUnit()).isEqualTo("g");
        assertThat(result.quantityBase()).isEqualByComparingTo("2000");
        assertThat(result.baseUnit()).isEqualTo("g");
    }

    private static final class CapturingInventoryStore implements InventoryStore {

        private ReceiptCommand command;

        @Override
        public Map<String, BigDecimal> inventorySnapshot() {
            return Map.of();
        }

        @Override
        public StoredReceipt receiveOnce(ReceiptCommand command) {
            this.command = command;
            return new StoredReceipt(command.materialId(), new BigDecimal("2000"), "g");
        }
    }
}
