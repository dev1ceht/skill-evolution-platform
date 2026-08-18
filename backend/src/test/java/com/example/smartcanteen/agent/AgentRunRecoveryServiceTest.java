package com.example.smartcanteen.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.agent.application.AgentRunRecoveryService;
import com.example.smartcanteen.agent.application.AgentRuntime;
import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.domain.RunStatus;
import com.example.smartcanteen.agent.port.AgentRunStore;
import com.example.smartcanteen.application.AgentSchedulerRolloutPolicy;
import com.example.smartcanteen.domain.CanteenScope;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentRunRecoveryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");
    private final AgentRunStore runs = mock(AgentRunStore.class);
    private final AgentRuntime runtime = mock(AgentRuntime.class);
    private final AgentSchedulerRolloutPolicy rollout = mock(AgentSchedulerRolloutPolicy.class);
    private final AgentRun stale = staleRun();

    @Test
    void recovers_only_claim_expired_executing_runs_with_an_internal_owner_context() {
        Instant cutoff = NOW.minusSeconds(120);
        when(runs.supportsExecutionClaims()).thenReturn(true);
        when(rollout.allowedScopes()).thenReturn(Set.of(stale.scope()));
        when(runs.findStaleExecuting(cutoff, 10, Set.of(stale.scope())))
                .thenReturn(List.of(stale));
        AgentRun recovered = stale.withFailure(
                "RECOVERY_REQUIRED", "Execution was interrupted; business outcome requires reconciliation",
                RunStatus.RECONCILIATION_REQUIRED, NOW);
        when(runtime.markReconciliationRequiredFromRecovery(
                eq(stale.runId()),
                eq(stale.version()),
                any(ExecutionContext.class),
                eq("agent-recovery-RUN-001-v2")))
                .thenReturn(recovered);

        AgentRunRecoveryService recovery = new AgentRunRecoveryService(
                runs,
                runtime,
                rollout,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(120),
                10);

        assertThat(recovery.recoverOnce()).isEqualTo(1);
        var contextCaptor = org.mockito.ArgumentCaptor.forClass(ExecutionContext.class);
        verify(runtime).markReconciliationRequiredFromRecovery(
                eq(stale.runId()),
                eq(stale.version()),
                contextCaptor.capture(),
                eq("agent-recovery-RUN-001-v2"));
        assertThat(contextCaptor.getValue())
                .extracting(ExecutionContext::actorUserId)
                .isEqualTo(stale.actorUserId());
        assertThat(contextCaptor.getValue().permissions())
                .containsExactly(AgentRuntime.AGENT_RUN_RECOVERY_PERMISSION);
        assertThat(contextCaptor.getValue().requestId()).startsWith("agent-recovery-");
    }

    @Test
    void treats_a_run_already_recovered_by_another_worker_as_a_noop() {
        Instant cutoff = NOW.minusSeconds(120);
        when(runs.supportsExecutionClaims()).thenReturn(true);
        when(rollout.allowedScopes()).thenReturn(Set.of(stale.scope()));
        when(runs.findStaleExecuting(cutoff, 10, Set.of(stale.scope())))
                .thenReturn(List.of(stale));
        when(runtime.markReconciliationRequiredFromRecovery(
                eq(stale.runId()),
                eq(stale.version()),
                any(ExecutionContext.class),
                eq("agent-recovery-RUN-001-v2")))
                .thenReturn(stale);

        AgentRunRecoveryService recovery = new AgentRunRecoveryService(
                runs,
                runtime,
                rollout,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(120),
                10);

        assertThat(recovery.recoverOnce()).isZero();
    }

    @Test
    void fails_closed_when_the_store_cannot_prove_claim_fencing() {
        AgentRunRecoveryService recovery = new AgentRunRecoveryService(
                runs,
                runtime,
                rollout,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Duration.ofSeconds(120),
                10);

        assertThatThrownBy(recovery::recoverOnce)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("durable execution claims");
    }

    private static AgentRun staleRun() {
        return new AgentRun(
                "RUN-001", "agent-001", "f".repeat(64), "USER-001", "operator",
                new CanteenScope("SCHOOL-001", "CANTEEN-001"), "traceability.query",
                "smart-canteen.traceability", "1.0.0", "a".repeat(64), "p".repeat(64),
                "{}", "{}", RunStatus.EXECUTING, "step-1", null, null, null, 2,
                NOW.minusSeconds(600), NOW.minusSeconds(180));
    }
}
