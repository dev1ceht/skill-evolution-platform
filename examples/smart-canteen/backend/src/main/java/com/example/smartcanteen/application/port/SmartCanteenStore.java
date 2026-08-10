package com.example.smartcanteen.application.port;

import com.example.smartcanteen.domain.IngredientRequirement;
import com.example.smartcanteen.domain.LedgerCode;
import com.example.smartcanteen.domain.Menu;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface SmartCanteenStore {

    Optional<Menu> findMenu(String menuId);

    void saveMenu(Menu menu);

    List<IngredientRequirement> findRecipe(String menuId);

    Map<String, BigDecimal> inventorySnapshot();

    StoredReceipt receiveOnce(ReceiptCommand command);

    Set<LedgerCode> missingLedgers();

    void completeLedger(LedgerCode ledgerCode);

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
