package com.example.smartcanteen.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.infrastructure.OperationsToolExecutor;
import com.example.smartcanteen.application.AlertCenterService;
import com.example.smartcanteen.application.BusinessAuthorizationPolicy;
import com.example.smartcanteen.application.ProcurementOperationsService;
import com.example.smartcanteen.application.ProcurementPlanService;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.ProcurementPlan;
import com.example.smartcanteen.domain.ProcurementPlanItem;
import com.example.smartcanteen.domain.ProcurementPlanStatus;
import com.example.smartcanteen.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OperationsToolExecutorTest {

    private final ProcurementPlanService plans = mock(ProcurementPlanService.class);
    private final ProcurementOperationsService procurement = mock(ProcurementOperationsService.class);
    private final AlertCenterService alerts = mock(AlertCenterService.class);
    private final BusinessAuthorizationPolicy authorization = mock(BusinessAuthorizationPolicy.class);
    private final OperationsToolExecutor executor = new OperationsToolExecutor(
            plans, procurement, alerts, authorization, new ObjectMapper().findAndRegisterModules());
    private final ExecutionContext context = new ExecutionContext(
            "REQ-001",
            "USER-001",
            "operator",
            new CanteenScope("SCHOOL-001", "CANTEEN-001"),
            Set.of(Role.CANTEEN_STAFF),
            Set.of("INVENTORY_RECEIVE"));

    @Test
    void accepts_only_declared_business_write_tools() {
        assertThat(executor.supports("inventory.receive")).isTrue();
        assertThat(executor.supports("alert.dispose")).isTrue();
        assertThat(executor.supports("traceability.query")).isFalse();
    }

    @Test
    void delegates_inventory_receive_with_scoped_idempotency() {
        when(procurement.receiveInventory(
                        eq(context.scope()),
                        eq("step-REQ"),
                        eq("SUP-001"),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.example.smartcanteen.application.port.OperationalStore.ReceiveResult(
                        "DIRECT-001", "RECEIPT-001", java.util.List.of("TRACE-001")));

        var result = executor.execute(
                "inventory.receive",
                context,
                "{\"materialId\":\"ING-001\",\"quantity\":\"2.5\",\"unit\":\"kg\","
                        + "\"supplierId\":\"SUP-001\",\"batchNo\":\"BATCH-001\","
                        + "\"purchasePrice\":\"8\",\"businessIdempotencyKey\":\"step-REQ\"}");

        assertThat(result.resultJson()).contains("DIRECT-001").contains("TRACE-001");
        verify(procurement).receiveInventory(
                eq(context.scope()),
                eq("step-REQ"),
                eq("SUP-001"),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void delegates_procurement_draft_generation_without_creating_an_order() {
        ProcurementPlan draft = new ProcurementPlan(
                "PLAN-001",
                "PLAN001",
                LocalDate.of(2026, 8, 22),
                LocalDate.of(2026, 8, 22),
                ProcurementPlanStatus.DRAFT,
                0,
                Instant.parse("2026-08-21T00:00:00Z"),
                List.of("M001"),
                List.of(new ProcurementPlanItem(
                        "ING-001",
                        new BigDecimal("20000"),
                        new BigDecimal("1000"),
                        BigDecimal.ZERO,
                        new BigDecimal("19000"),
                        new BigDecimal("19000"),
                        "g")),
                List.of());
        when(plans.generate(
                        eq(context.scope()),
                        eq(LocalDate.of(2026, 8, 22)),
                        eq(LocalDate.of(2026, 8, 22)),
                        eq("step-PLAN-DRAFT")))
                .thenReturn(draft);

        var result = executor.execute(
                "procurement.plan.generate",
                context,
                "{\"periodStart\":\"2026-08-22\",\"periodEnd\":\"2026-08-22\","
                        + "\"businessIdempotencyKey\":\"step-PLAN-DRAFT\"}");

        assertThat(result.resultJson()).contains("\"status\":\"DRAFT\"");
        verify(plans).generate(
                eq(context.scope()),
                eq(LocalDate.of(2026, 8, 22)),
                eq(LocalDate.of(2026, 8, 22)),
                eq("step-PLAN-DRAFT"));
    }
}
