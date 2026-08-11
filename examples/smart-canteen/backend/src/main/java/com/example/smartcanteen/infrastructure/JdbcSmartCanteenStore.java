package com.example.smartcanteen.infrastructure;

import com.example.smartcanteen.application.port.SmartCanteenStore;
import com.example.smartcanteen.application.port.SmartCanteenStore.ReceiptCommand;
import com.example.smartcanteen.domain.IngredientRequirement;
import com.example.smartcanteen.domain.Menu;
import com.example.smartcanteen.domain.MenuStatus;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcSmartCanteenStore implements SmartCanteenStore {

    private final JdbcTemplate jdbc;

    public JdbcSmartCanteenStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Menu> findMenu(String menuId) {
        return jdbc.query(
                        """
                        SELECT id, status, decision_comment, version
                        FROM menus
                        WHERE id = ?
                        """,
                        (result, row) -> Menu.restore(
                                result.getString("id"),
                                MenuStatus.valueOf(result.getString("status")),
                                result.getString("decision_comment"),
                                result.getLong("version")),
                        menuId)
                .stream()
                .findFirst();
    }

    @Override
    public void saveMenu(Menu menu) {
        int changed = jdbc.update(
                """
                UPDATE menus
                SET status = ?, decision_comment = ?, version = version + 1
                WHERE id = ? AND version = ?
                """,
                menu.status().name(),
                menu.decisionComment(),
                menu.id(),
                menu.version());
        if (changed != 1) {
            throw new IllegalStateException("Menu changed concurrently: " + menu.id());
        }
    }

    @Override
    public List<IngredientRequirement> findRecipe(String menuId) {
        return jdbc.query(
                """
                SELECT material_id, quantity, unit
                FROM recipe_requirements
                WHERE menu_id = ?
                ORDER BY material_id
                """,
                (result, row) -> new IngredientRequirement(
                        result.getString("material_id"),
                        result.getBigDecimal("quantity"),
                        result.getString("unit")),
                menuId);
    }

    @Override
    public Map<String, BigDecimal> inventorySnapshot() {
        Map<String, BigDecimal> snapshot = new LinkedHashMap<>();
        jdbc.query(
                "SELECT material_id, quantity_base FROM inventory ORDER BY material_id",
                (result, row) -> Map.entry(
                        result.getString("material_id"),
                        result.getBigDecimal("quantity_base")))
                .forEach(entry -> snapshot.put(entry.getKey(), entry.getValue()));
        return Map.copyOf(snapshot);
    }

    @Override
    public StoredReceipt receiveOnce(ReceiptCommand command) {
        try {
            jdbc.update(
                    """
                    INSERT INTO inventory_receipts (
                        idempotency_key, material_id, request_quantity, request_unit,
                        quantity_base_after, base_unit
                    ) VALUES (?, ?, ?, ?, NULL, ?)
                    """,
                    command.idempotencyKey(),
                    command.materialId(),
                    command.requestQuantity(),
                    command.requestUnit(),
                    command.baseUnit());
        } catch (DuplicateKeyException duplicate) {
            ExistingReceipt existing = findReceipt(command.idempotencyKey())
                    .orElseThrow(() -> duplicate);
            if (!existing.matches(command)) {
                throw new IllegalArgumentException(
                        "Idempotency-Key was already used for a different inventory receipt");
            }
            return existing.result();
        }

        addInventory(command.materialId(), command.baseQuantity(), command.baseUnit());
        BigDecimal updated = jdbc.queryForObject(
                "SELECT quantity_base FROM inventory WHERE material_id = ?",
                BigDecimal.class,
                command.materialId());
        jdbc.update(
                """
                UPDATE inventory_receipts
                SET quantity_base_after = ?
                WHERE idempotency_key = ?
                """,
                updated,
                command.idempotencyKey());
        return new StoredReceipt(command.materialId(), updated, command.baseUnit());
    }

    private void addInventory(String materialId, BigDecimal quantity, String baseUnit) {
        if (incrementInventory(materialId, quantity, baseUnit) == 1) {
            return;
        }
        Optional<String> existingUnit = inventoryUnit(materialId);
        if (existingUnit.isPresent()) {
            throw incompatibleUnit(materialId, existingUnit.get(), baseUnit);
        }
        try {
            jdbc.update(
                    "INSERT INTO inventory (material_id, quantity_base, base_unit) VALUES (?, ?, ?)",
                    materialId,
                    quantity,
                    baseUnit);
        } catch (DuplicateKeyException concurrentInsert) {
            if (incrementInventory(materialId, quantity, baseUnit) != 1) {
                String actualUnit = inventoryUnit(materialId).orElse("unknown");
                throw incompatibleUnit(materialId, actualUnit, baseUnit);
            }
        }
    }

    private int incrementInventory(String materialId, BigDecimal quantity, String baseUnit) {
        return jdbc.update(
                """
                UPDATE inventory
                SET quantity_base = quantity_base + ?
                WHERE material_id = ? AND base_unit = ?
                """,
                quantity,
                materialId,
                baseUnit);
    }

    private Optional<String> inventoryUnit(String materialId) {
        return jdbc.queryForList(
                        "SELECT base_unit FROM inventory WHERE material_id = ?",
                        String.class,
                        materialId)
                .stream()
                .findFirst();
    }

    private IllegalArgumentException incompatibleUnit(
            String materialId, String actualUnit, String requestedUnit) {
        return new IllegalArgumentException(
                "Inventory unit mismatch for %s: stored %s, received %s"
                        .formatted(materialId, actualUnit, requestedUnit));
    }

    private Optional<ExistingReceipt> findReceipt(String idempotencyKey) {
        return jdbc.query(
                        """
                        SELECT material_id, request_quantity, request_unit,
                               quantity_base_after, base_unit
                        FROM inventory_receipts
                        WHERE idempotency_key = ?
                        """,
                        (result, row) -> new ExistingReceipt(
                                result.getString("material_id"),
                                result.getBigDecimal("request_quantity"),
                                result.getString("request_unit"),
                                result.getBigDecimal("quantity_base_after"),
                                result.getString("base_unit")),
                        idempotencyKey)
                .stream()
                .findFirst();
    }

    private record ExistingReceipt(
            String materialId,
            BigDecimal requestQuantity,
            String requestUnit,
            BigDecimal quantityBaseAfter,
            String baseUnit) {

        boolean matches(ReceiptCommand command) {
            return materialId.equals(command.materialId())
                    && requestQuantity.compareTo(command.requestQuantity()) == 0
                    && requestUnit.equalsIgnoreCase(command.requestUnit());
        }

        StoredReceipt result() {
            return new StoredReceipt(materialId, quantityBaseAfter, baseUnit);
        }
    }

}
