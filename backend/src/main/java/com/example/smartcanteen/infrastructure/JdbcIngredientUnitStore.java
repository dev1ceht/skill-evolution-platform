package com.example.smartcanteen.infrastructure;

import com.example.smartcanteen.application.port.IngredientUnitStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.IngredientUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcIngredientUnitStore implements IngredientUnitStore {

    private final JdbcTemplate jdbc;

    public JdbcIngredientUnitStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<IngredientUnit> listIngredientUnits(
            CanteenScope scope, String ingredientId) {
        return jdbc.query(
                "SELECT unit_code, base_unit, to_base_factor, status FROM ingredient_units "
                        + "WHERE school_id = ? AND canteen_id = ? AND ingredient_id = ? "
                        + "ORDER BY unit_code",
                (result, row) -> new IngredientUnit(
                        result.getString("unit_code"),
                        result.getString("base_unit"),
                        result.getBigDecimal("to_base_factor"),
                        "ACTIVE".equals(result.getString("status"))),
                scope.schoolId(), scope.canteenId(), ingredientId);
    }

    @Override
    public Optional<IngredientUnit> findIngredientUnit(
            CanteenScope scope, String ingredientId, String unitCode) {
        return jdbc.query(
                        "SELECT unit_code, base_unit, to_base_factor, status FROM ingredient_units "
                                + "WHERE school_id = ? AND canteen_id = ? AND ingredient_id = ? "
                                + "AND unit_code = ?",
                        (result, row) -> new IngredientUnit(
                                result.getString("unit_code"),
                                result.getString("base_unit"),
                                result.getBigDecimal("to_base_factor"),
                                "ACTIVE".equals(result.getString("status"))),
                        scope.schoolId(), scope.canteenId(), ingredientId, unitCode)
                .stream()
                .findFirst();
    }

    @Override
    public void replaceIngredientUnits(
            CanteenScope scope, String ingredientId, List<IngredientUnit> units) {
        jdbc.update(
                "DELETE FROM ingredient_units WHERE school_id = ? AND canteen_id = ? "
                        + "AND ingredient_id = ?",
                scope.schoolId(), scope.canteenId(), ingredientId);
        try {
            for (IngredientUnit unit : units) {
                jdbc.update(
                        "INSERT INTO ingredient_units (school_id, canteen_id, ingredient_id, "
                                + "unit_code, base_unit, to_base_factor, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        scope.schoolId(),
                        scope.canteenId(),
                        ingredientId,
                        unit.unitCode(),
                        unit.baseUnit(),
                        unit.toBaseFactor(),
                        unit.active() ? "ACTIVE" : "DISABLED");
            }
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("Duplicate ingredient business unit");
        }
    }
}
