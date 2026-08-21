package com.example.smartcanteen.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.infrastructure.ProcurementGapToolExecutor;
import com.example.smartcanteen.application.ProcurementPlanService;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.ProcurementGapAnalysis;
import com.example.smartcanteen.domain.ProcurementGapItem;
import com.example.smartcanteen.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProcurementGapToolExecutorTest {

    private final ProcurementPlanService plans = mock(ProcurementPlanService.class);
    private final ProcurementGapToolExecutor executor = new ProcurementGapToolExecutor(
            plans, new ObjectMapper().findAndRegisterModules());
    private final ExecutionContext context = new ExecutionContext(
            "REQ-GAP-001",
            "USER-GAP-001",
            "operator",
            new CanteenScope("SCHOOL-001", "CANTEEN-001"),
            Set.of(Role.CANTEEN_STAFF),
            Set.of("PROCUREMENT_ANALYSIS_READ"));

    @Test
    void supports_only_the_read_only_procurement_gap_tool() {
        assertThat(executor.supports("procurement.gap.query")).isTrue();
        assertThat(executor.supports("procurement.plan.generate")).isFalse();
    }

    @Test
    void delegates_date_and_meal_time_with_the_server_scope() {
        LocalDate date = LocalDate.of(2026, 8, 22);
        ProcurementGapAnalysis expected = ProcurementGapAnalysis.of(
                date,
                "LUNCH",
                List.of("M-001"),
                List.of(new ProcurementGapItem(
                        "ING-001",
                        "西兰花",
                        "VEGETABLE",
                        new BigDecimal("38"),
                        new BigDecimal("19"),
                        BigDecimal.ZERO,
                        new BigDecimal("19"),
                        "kg")));
        when(plans.analyzeGap(eq(context.scope()), eq(date), eq("LUNCH")))
                .thenReturn(expected);

        var result = executor.execute(
                "procurement.gap.query",
                context,
                "{\"menuDate\":\"2026-08-22\",\"mealTime\":\"LUNCH\"}");

        assertThat(result.resultJson())
                .contains("M-001")
                .contains("ING-001")
                .contains("\"shortageCount\":1");
        verify(plans).analyzeGap(context.scope(), date, "LUNCH");
    }

    @Test
    void rejects_unknown_fields_and_invalid_dates() {
        assertThatThrownBy(() -> executor.execute(
                        "procurement.gap.query",
                        context,
                        "{\"menuDate\":\"2026-08-22\",\"page\":1}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported procurement gap query field: page");
        assertThatThrownBy(() -> executor.execute(
                        "procurement.gap.query",
                        context,
                        "{\"menuDate\":\"2026-99-22\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("menuDate must be YYYY-MM-DD");
    }
}
