package com.example.smartcanteen.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ProcurementService {

    private final UnitConverter unitConverter;

    public ProcurementService(UnitConverter unitConverter) {
        this.unitConverter = unitConverter;
    }

    public List<ProcurementItem> calculateShortages(
            List<IngredientRequirement> requirements,
            Map<String, BigDecimal> inventoryByMaterial) {
        List<ProcurementItem> shortages = new ArrayList<>();
        for (IngredientRequirement requirement : requirements) {
            BaseQuantity required = unitConverter.convert(requirement.quantity(), requirement.unit());
            BigDecimal available = inventoryByMaterial.getOrDefault(
                    requirement.materialId(), BigDecimal.ZERO);
            BigDecimal shortage = required.quantity().subtract(available);
            if (shortage.signum() > 0) {
                shortages.add(new ProcurementItem(
                        requirement.materialId(), required.quantity(), shortage, required.unit()));
            }
        }
        return List.copyOf(shortages);
    }
}
