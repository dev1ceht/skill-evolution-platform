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
import com.example.smartcanteen.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OperationsToolExecutorTest {

    private final ProcurementPlanService plans = mock(ProcurementPlanService.class);
    private final ProcurementOperationsService procurement = mock(ProcurementOperationsService.class);
    private final AlertCenterService alerts = mock(AlertCenterService.class);
    private final BusinessAuthorizationPolicy authorization = mock(BusinessAuthorizationPolicy.class);
    private final OperationsToolExecutor executor = new OperationsToolExecutor(
            plans, procurement, alerts, authorization, new ObjectMapper());
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
}
