package com.example.smartcanteen.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.agent.application.AgentRunScheduler;
import com.example.smartcanteen.agent.application.AgentRunWorker;
import com.example.smartcanteen.agent.application.AgentRunClaimUnavailableException;
import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.domain.RunStatus;
import com.example.smartcanteen.agent.domain.SkillDefinition;
import com.example.smartcanteen.agent.port.AgentRunStore;
import com.example.smartcanteen.agent.port.SkillRegistry;
import com.example.smartcanteen.application.AuthService;
import com.example.smartcanteen.application.AgentSchedulerRolloutPolicy;
import com.example.smartcanteen.application.BusinessAuthorizationPolicy;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentRunSchedulerTest {

    private static final Instant NOW = Instant.parse("2026-08-18T10:00:00Z");
    private final AgentRunStore runs = mock(AgentRunStore.class);
    private final AgentRunWorker worker = mock(AgentRunWorker.class);
    private final SkillRegistry skills = mock(SkillRegistry.class);
    private final AuthService authentication = mock(AuthService.class);
    private final AgentSchedulerRolloutPolicy rollout = mock(AgentSchedulerRolloutPolicy.class);
    private final BusinessAuthorizationPolicy policy = mock(BusinessAuthorizationPolicy.class);
    private final CanteenScope scope = new CanteenScope("SCHOOL-001", "CANTEEN-001");
    private final AuthPrincipal principal = new AuthPrincipal(
            "USER-001", "operator", "Operator", Role.CANTEEN_STAFF,
            "SCHOOL-001", "CANTEEN-001");
    private final ExecutionContext context = ExecutionContext.fromTrustedPrincipal(
            "scheduler-request", principal, scope, Set.of(Role.CANTEEN_STAFF), Set.of());
    private final SkillDefinition skill = new SkillDefinition(
            "smart-canteen.traceability", "1.0.0", "implemented", "medium", "not-required",
            "schoolId + canteenId", List.of("trace"), List.of("CANTEEN_STAFF"), List.of("trace"),
            new SkillDefinition.RuntimePolicy(
                    List.of("traceability.query"), "TraceabilityIntent", "TraceabilityResponse",
                    List.of("traceability.query"), Map.of(), "read", "not-required", "not-applicable",
                    "active", 3000, "read-only-bounded", "required"),
            "a".repeat(64));

    @Test
    void polls_planned_runs_and_rebuilds_the_current_actor_context() {
        AgentRun planned = plannedRun();
        when(runs.supportsExecutionClaims()).thenReturn(true);
        when(rollout.allowedScopes()).thenReturn(Set.of(scope));
        when(runs.findPlanned(10, Set.of(scope))).thenReturn(List.of(planned));
        when(skills.find(planned.skillId(), planned.skillVersion())).thenReturn(Optional.of(skill));
        when(authentication.principalForUser(planned.actorUserId())).thenReturn(principal);
        when(policy.establishContext(
                eq(principal), startsWith("agent-scheduler-"), eq(scope), eq(false)))
                .thenReturn(context);

        AgentRunScheduler scheduler = scheduler(10);

        assertThat(scheduler.pollOnce()).isEqualTo(1);
        verify(worker).claimAndExecute(planned.runId(), "scheduler-1", context);
    }

    @Test
    void continues_polling_when_one_run_is_claimed_by_another_worker() {
        AgentRun first = plannedRun();
        AgentRun second = new AgentRun(
                "RUN-002", "agent-002", "g".repeat(64), "USER-001", "operator", scope,
                "traceability.query", skill.id(), skill.version(), skill.manifestDigest(),
                "q".repeat(64), "{}", "{}", RunStatus.PLANNED,
                null, null, null, null, 0, NOW, NOW);
        when(runs.supportsExecutionClaims()).thenReturn(true);
        when(rollout.allowedScopes()).thenReturn(Set.of(scope));
        when(runs.findPlanned(10, Set.of(scope))).thenReturn(List.of(first, second));
        when(skills.find(any(), any())).thenReturn(Optional.of(skill));
        when(authentication.principalForUser("USER-001")).thenReturn(principal);
        when(policy.establishContext(
                eq(principal), startsWith("agent-scheduler-"), eq(scope), eq(false)))
                .thenReturn(context);
        when(worker.claimAndExecute(eq(first.runId()), eq("scheduler-1"), eq(context)))
                .thenThrow(new AgentRunClaimUnavailableException(first.runId()));

        AgentRunScheduler scheduler = scheduler(10);

        assertThat(scheduler.pollOnce()).isEqualTo(1);
        verify(worker).claimAndExecute(second.runId(), "scheduler-1", context);
    }

    private AgentRunScheduler scheduler(int batchSize) {
        return new AgentRunScheduler(
                runs, worker, skills, authentication, rollout, policy, "scheduler-1", batchSize);
    }

    private AgentRun plannedRun() {
        return new AgentRun(
                "RUN-001", "agent-001", "f".repeat(64), "USER-001", "operator", scope,
                "traceability.query", skill.id(), skill.version(), skill.manifestDigest(),
                "p".repeat(64), "{}", "{}", RunStatus.PLANNED,
                null, null, null, null, 0, NOW, NOW);
    }
}
