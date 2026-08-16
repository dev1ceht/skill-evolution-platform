package com.example.smartcanteen.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.agent.application.AgentExecutionService;
import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.domain.RunStatus;
import com.example.smartcanteen.agent.domain.SkillDefinition;
import com.example.smartcanteen.agent.port.AgentRunStore;
import com.example.smartcanteen.agent.port.SkillRegistry;
import com.example.smartcanteen.agent.port.ToolExecutor;
import com.example.smartcanteen.application.BusinessAuthorizationPolicy;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentExecutionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-16T10:00:00Z");
    private final AgentRunStore runs = mock(AgentRunStore.class);
    private final SkillRegistry skills = mock(SkillRegistry.class);
    private final ToolExecutor tools = mock(ToolExecutor.class);
    private final BusinessAuthorizationPolicy policy = mock(BusinessAuthorizationPolicy.class);
    private final AgentExecutionService execution = new AgentExecutionService(
            runs, skills, tools, policy, Clock.fixed(NOW, ZoneOffset.UTC));
    private final CanteenScope scope = new CanteenScope("SCHOOL-001", "CANTEEN-001");
    private final AuthPrincipal principal = new AuthPrincipal(
            "USER-001", "operator", "Operator", Role.CANTEEN_STAFF,
            "SCHOOL-001", "CANTEEN-001");
    private final ExecutionContext context = ExecutionContext.fromTrustedPrincipal(
            "request-001", principal, scope, Set.of(Role.CANTEEN_STAFF), Set.of());
    private final SkillDefinition skill = new SkillDefinition(
            "smart-canteen.traceability", "1.0.0", "implemented", "medium", "not-required",
            "schoolId + canteenId", List.of("trace"), List.of("CANTEEN_STAFF"), List.of("trace"),
            new SkillDefinition.RuntimePolicy(
                    List.of("traceability.query"), "TraceabilityIntent", "TraceabilityResponse",
                    List.of("traceability.query"), "read", "not-required", "not-applicable",
                    "active", 3000, "read-only-bounded", "required"),
            "a".repeat(64));

    @Test
    void executes_a_registered_read_tool_and_closes_the_run() {
        AgentRun planned = plannedRun();
        when(runs.findById(planned.runId())).thenReturn(Optional.of(planned));
        when(skills.find(planned.skillId(), planned.skillVersion())).thenReturn(Optional.of(skill));
        when(tools.execute("traceability.query", context, planned.inputJson()))
                .thenReturn(new ToolExecutor.ToolResult("{\"traceCode\":\"TRACE-001\"}"));

        AgentRun result = execution.execute(planned, context);

        assertThat(result.status()).isEqualTo(RunStatus.SUCCEEDED);
        assertThat(result.resultJson()).contains("TRACE-001");
        verify(runs).update(eq(planned), any(AgentRun.class));
        verify(runs).appendEvent(
                planned.runId(), "RUN_SUCCEEDED", "EXECUTING", "SUCCEEDED", "USER-001",
                result.resultJson());
    }

    @Test
    void records_tool_failure_without_retrying_the_write_or_hiding_the_reason() {
        AgentRun planned = plannedRun();
        when(runs.findById(planned.runId())).thenReturn(Optional.of(planned));
        when(skills.find(planned.skillId(), planned.skillVersion())).thenReturn(Optional.of(skill));
        when(tools.execute("traceability.query", context, planned.inputJson()))
                .thenThrow(new IllegalArgumentException("Traceability code not found"));

        AgentRun result = execution.execute(planned, context);

        assertThat(result.status()).isEqualTo(RunStatus.FAILED);
        assertThat(result.errorCode()).isEqualTo("TOOL_FAILED");
        assertThat(result.errorMessage()).isEqualTo("Traceability code not found");
        verify(runs).appendEvent(
                planned.runId(), "RUN_FAILED", "EXECUTING", "FAILED", "USER-001",
                "Traceability code not found");
    }

    private AgentRun plannedRun() {
        return new AgentRun(
                "RUN-001", "agent-001", "f".repeat(64), "USER-001", "operator", scope,
                "traceability.query", skill.id(), skill.version(), skill.manifestDigest(),
                "p".repeat(64), "{}", "{\"traceCode\":\"TRACE-001\"}", RunStatus.PLANNED,
                null, null, null, null, 0, NOW, NOW);
    }
}
