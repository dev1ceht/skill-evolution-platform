package com.example.smartcanteen.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;

import com.example.smartcanteen.agent.application.AgentRuntime;
import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.AgentStep;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.domain.RunStatus;
import com.example.smartcanteen.agent.domain.SkillDefinition;
import com.example.smartcanteen.agent.domain.StartRunCommand;
import com.example.smartcanteen.agent.port.AgentRunStore;
import com.example.smartcanteen.agent.port.SkillRegistry;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentRuntimeTest {

    private static final Instant NOW = Instant.parse("2026-08-16T10:00:00Z");
    private final SkillRegistry skills = mock(SkillRegistry.class);
    private final AgentRunStore runs = mock(AgentRunStore.class);
    private final AgentRuntime runtime = new AgentRuntime(
            skills,
            runs,
            new ObjectMapper(),
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final AuthPrincipal principal = new AuthPrincipal(
            "USER-001",
            "operator",
            "Operator",
            Role.CANTEEN_STAFF,
            "SCHOOL-001",
            "CANTEEN-001");
    private final ExecutionContext context = ExecutionContext.fromTrustedPrincipal(
            "request-001",
            principal,
            new CanteenScope("SCHOOL-001", "CANTEEN-001"),
            Set.of(Role.CANTEEN_STAFF),
            Set.of("TRACEABILITY_READ"));

    @BeforeEach
    void default_to_winning_the_idempotency_insert() {
        when(runs.insert(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private final SkillDefinition traceability = new SkillDefinition(
            "smart-canteen.traceability",
            "1.0.0",
            "implemented",
            "medium",
            "not-required",
            "schoolId + canteenId",
            ListOf.values("查询食品溯源链路"),
            ListOf.values("CANTEEN_STAFF"),
            ListOf.values("resolve-trace-code"),
            new SkillDefinition.RuntimePolicy(
                    ListOf.values("traceability.query"),
                    "TraceabilityIntent",
                    "TraceabilityResponse",
                    ListOf.values("traceability.query"),
                    "read",
                    "not-required",
                    "not-applicable",
                    "active",
                    3000,
                    "read-only-bounded",
                    "required"),
            "a".repeat(64));

    @Test
    void creates_an_immutable_plan_with_a_manifest_snapshot_and_event_ready_status() {
        when(skills.findByIntent("traceability.query")).thenReturn(Optional.of(traceability));
        when(runs.findByIdempotency("USER-001", context.scope(), "agent-001"))
                .thenReturn(Optional.empty());

        AgentRun run = runtime.start(new StartRunCommand(
                "request-001",
                "traceability.query",
                "{\"traceCode\":\"TRACE-001\"}",
                "agent-001"), context);

        assertThat(run.status()).isEqualTo(RunStatus.PLANNED);
        assertThat(run.skillId()).isEqualTo("smart-canteen.traceability");
        assertThat(run.manifestDigest()).isEqualTo("a".repeat(64));
        assertThat(run.planHash()).hasSize(64);
        assertThat(run.planJson()).contains("traceability.query");
        verify(runs).insert(org.mockito.ArgumentMatchers.eq(run), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void returns_the_existing_run_for_the_same_idempotency_key_and_payload() {
        when(skills.findByIntent("traceability.query")).thenReturn(Optional.of(traceability));
        when(runs.findByIdempotency("USER-001", context.scope(), "agent-001"))
                .thenReturn(Optional.empty());
        AgentRun first = runtime.start(new StartRunCommand(
                "request-001", "traceability.query", "{\"traceCode\":\"TRACE-001\"}", "agent-001"), context);
        when(runs.findByIdempotency("USER-001", context.scope(), "agent-001"))
                .thenReturn(Optional.of(first));

        assertThat(runtime.start(new StartRunCommand(
                "request-001", "traceability.query", "{ \"traceCode\" : \"TRACE-001\" }", "agent-001"), context))
                .isSameAs(first);
        verify(runs).insert(org.mockito.ArgumentMatchers.eq(first), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void refuses_unknown_intents_before_persisting_a_run() {
        when(skills.findByIntent("unknown.intent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> runtime.start(new StartRunCommand(
                "request-001", "unknown.intent", "{}", "agent-002"), context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No active Skill");
        verify(runs, never()).insert(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void treats_a_same_payload_under_a_different_intent_as_a_idempotency_conflict() {
        when(skills.findByIntent("traceability.query")).thenReturn(Optional.of(traceability));
        when(skills.findByIntent("other.intent")).thenReturn(Optional.of(traceability));
        when(runs.findByIdempotency("USER-001", context.scope(), "agent-003"))
                .thenReturn(Optional.empty());
        AgentRun first = runtime.start(new StartRunCommand(
                "request-001", "traceability.query", "{\"traceCode\":\"TRACE-001\"}", "agent-003"), context);
        when(runs.findByIdempotency("USER-001", context.scope(), "agent-003"))
                .thenReturn(Optional.of(first));

        assertThatThrownBy(() -> runtime.start(new StartRunCommand(
                "request-001", "other.intent", "{\"traceCode\":\"TRACE-001\"}", "agent-003"), context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Idempotency key");
    }

    @Test
    void converts_a_concurrent_idempotency_insert_race_into_a_replay() {
        when(skills.findByIntent("traceability.query")).thenReturn(Optional.of(traceability));
        when(runs.findByIdempotency("USER-001", context.scope(), "agent-race"))
                .thenReturn(Optional.empty(), Optional.empty());
        AgentRun first = runtime.start(new StartRunCommand(
                "request-001", "traceability.query", "{\"traceCode\":\"TRACE-001\"}", "agent-race"), context);

        when(runs.findByIdempotency("USER-001", context.scope(), "agent-race"))
                .thenReturn(Optional.empty(), Optional.of(first));
        when(runs.insert(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList())).thenReturn(first);

        assertThat(runtime.start(new StartRunCommand(
                "request-001", "traceability.query", "{ \"traceCode\" : \"TRACE-001\" }", "agent-race"), context))
                .isSameAs(first);
    }

    @Test
    void internal_recovery_requires_authority_and_leaves_an_idempotency_marker() {
        AgentRun executing = new AgentRun(
                "RUN-RECOVERY-001", "agent-recovery-001", "f".repeat(64), "USER-001", "operator",
                context.scope(), "traceability.query", traceability.id(), traceability.version(),
                traceability.manifestDigest(), "p".repeat(64), "{}", "{}", RunStatus.EXECUTING,
                "step-1", null, null, null, 2, NOW.minusSeconds(300), NOW.minusSeconds(180));
        when(runs.findById(executing.runId())).thenReturn(Optional.of(executing));
        when(runs.supportsExecutionClaims()).thenReturn(true);
        when(runs.confirmStaleExecution(executing.runId(), executing.version())).thenReturn(true);
        ExecutionContext recoveryContext = ExecutionContext.fromTrustedPrincipal(
                "agent-recovery-request", principal, context.scope(), Set.of(Role.SYSTEM_ADMIN),
                Set.of(AgentRuntime.AGENT_RUN_RECOVERY_PERMISSION));

        AgentRun recovered = runtime.markReconciliationRequiredFromRecovery(
                executing.runId(), executing.version(), recoveryContext,
                "agent-recovery-RUN-RECOVERY-001-v2");

        assertThat(recovered.status()).isEqualTo(RunStatus.RECONCILIATION_REQUIRED);
        verify(runs).appendEvent(
                eq(executing.runId()),
                eq("RUN_RECONCILIATION_REQUIRED"),
                eq("EXECUTING"),
                eq("RECONCILIATION_REQUIRED"),
                eq("USER-001"),
                contains("agent-recovery-RUN-RECOVERY-001-v2"));
        assertThatThrownBy(() -> runtime.markReconciliationRequiredFromRecovery(
                executing.runId(), executing.version(), context,
                "agent-recovery-RUN-RECOVERY-001-v2"))
                .isInstanceOf(com.example.smartcanteen.security.ForbiddenException.class);
    }

    private static final class ListOf {
        private ListOf() {
        }

        static java.util.List<String> values(String value) {
            return java.util.List.of(value);
        }
    }
}
