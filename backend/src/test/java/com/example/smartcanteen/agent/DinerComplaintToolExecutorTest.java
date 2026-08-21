package com.example.smartcanteen.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.infrastructure.DinerComplaintToolExecutor;
import com.example.smartcanteen.application.DinerComplaintService;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.DinerComplaint;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DinerComplaintToolExecutorTest {

    private final DinerComplaintService complaints = mock(DinerComplaintService.class);
    private final DinerComplaintToolExecutor executor = new DinerComplaintToolExecutor(
            complaints, new ObjectMapper().findAndRegisterModules());
    private final ExecutionContext context = new ExecutionContext(
            "REQ-COMPLAINT-001",
            "USER-DINER-001",
            "diner",
            new CanteenScope("SCHOOL-001", "CANTEEN-001"),
            Set.of(Role.DINER),
            Set.of("DINER_COMPLAINT_READ", "DINER_COMPLAINT_WRITE"));

    @Test
    void exposes_personal_complaint_query_and_create_tools() {
        assertThat(executor.supports("diner_complaint.query")).isTrue();
        assertThat(executor.supports("diner_complaint.create")).isTrue();
        assertThat(executor.supports("supplier_complaint.create")).isFalse();
    }

    @Test
    void delegates_query_with_actor_from_execution_context() {
        PageResult<DinerComplaint> expected = new PageResult<>(List.of(complaint()), 1, 100, 1);
        when(complaints.listMine(context.scope(), context.actorUserId(), null, 1, 100))
                .thenReturn(expected);

        var result = executor.execute("diner_complaint.query", context, "{}");

        assertThat(result.resultJson()).contains("COMPLAINT-001").contains("排队");
        verify(complaints).listMine(context.scope(), context.actorUserId(), null, 1, 100);
    }

    @Test
    void parses_create_input_and_rejects_identity_override() {
        when(complaints.create(
                        eq(context.scope()), eq(context.actorUserId()), eq("SERVICE"),
                        eq("窗口排队"), eq("午餐排队时间较长"), eq("MEAL-001"), eq("COMPLAINT-KEY")))
                .thenReturn(complaint());

        var result = executor.execute(
                "diner_complaint.create",
                context,
                "{\"category\":\"SERVICE\",\"subject\":\"窗口排队\","
                        + "\"description\":\"午餐排队时间较长\",\"relatedOrderId\":\"MEAL-001\","
                        + "\"businessIdempotencyKey\":\"COMPLAINT-KEY\"}");

        assertThat(result.resultJson()).contains("COMPLAINT-001");
        verify(complaints).create(
                context.scope(), context.actorUserId(), "SERVICE", "窗口排队",
                "午餐排队时间较长", "MEAL-001", "COMPLAINT-KEY");

        assertThatThrownBy(() -> executor.execute(
                        "diner_complaint.create", context,
                        "{\"actorUserId\":\"OTHER\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported diner complaint field");
    }

    private static DinerComplaint complaint() {
        Instant now = Instant.parse("2026-08-21T03:00:00Z");
        return new DinerComplaint(
                "COMPLAINT-001", "USER-DINER-001", "SERVICE", "窗口排队",
                "午餐排队时间较长", "MEAL-001", "SUBMITTED", null, 0, now, now);
    }
}
