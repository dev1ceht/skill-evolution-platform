package com.example.smartcanteen.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProcurementServiceTest {

    private final UnitConverter converter = new UnitConverter();
    private final ProcurementService service = new ProcurementService(converter);

    @Test
    void calculates_only_positive_shortages_in_base_units() {
        List<IngredientRequirement> requirements = List.of(
                new IngredientRequirement("FLOUR", new BigDecimal("2"), "kg"),
                new IngredientRequirement("EGG", new BigDecimal("12"), "count"));

        List<ProcurementItem> result = service.calculateShortages(
                requirements,
                Map.of("FLOUR", new BigDecimal("500"), "EGG", new BigDecimal("20")));

        assertThat(result).containsExactly(
                new ProcurementItem("FLOUR", new BigDecimal("2000"), new BigDecimal("1500"), "g"));
    }

    @Test
    void converts_mass_volume_and_count_units() {
        assertThat(converter.toBase(new BigDecimal("1.5"), "kg"))
                .isEqualByComparingTo("1500");
        assertThat(converter.toBase(new BigDecimal("2"), "L"))
                .isEqualByComparingTo("2000");
        assertThat(converter.toBase(new BigDecimal("3"), "count"))
                .isEqualByComparingTo("3");
    }
}
