package com.example.smartcanteen.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.agent.application.AgentExecutionService;
import com.example.smartcanteen.agent.application.AgentRunClaimUnavailableException;
import com.example.smartcanteen.agent.application.AgentRunWorker;
import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.AgentRunClaim;
import com.example.smartcanteen.agent.domain.AgentRunClaimLostException;
import com.example.smartcanteen.agent.domain.AgentRunDecision;
import com.example.smartcanteen.agent.domain.AgentRunEvent;
import com.example.smartcanteen.agent.domain.AgentStep;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.domain.RunStatus;
import com.example.smartcanteen.agent.port.AgentRunStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentRunWorkerTest {

    private static final Instant NOW = Instant.parse("2026-08-16T10:00:00Z");
    private final AgentRunStoreFake runs = new AgentRunStoreFake();
    private final AgentExecutionService execution = mock(AgentExecutionService.class);
    private final AgentRunWorker worker = new AgentRunWorker(
            runs, execution, Duration.ofSeconds(30));
    private final CanteenScope scope = new CanteenScope("SCHOOL-001", "CANTEEN-001");
    private final AuthPrincipal principal = new AuthPrincipal(
            "USER-001", "operator", "Operator", Role.CANTEEN_STAFF,
            "SCHOOL-001", "CANTEEN-001");
    private final ExecutionContext context = ExecutionContext.fromTrustedPrincipal(
            "request-001", principal, scope, Set.of(Role.CANTEEN_STAFF), Set.of());

    @Test
    void claims_loads_executes_and_releases_in_one_worker_lifecycle() {
        AgentRun planned = plannedRun();
        AgentRunClaim claim = claim();
        runs.claim = Optional.of(claim);
        runs.run = Optional.of(planned);
        when(execution.validateExecutable(planned, context)).thenReturn(planned);
        when(execution.executeClaimed(planned, context, claim)).thenReturn(planned);

        AgentRun result = worker.claimAndExecute("RUN-001", "worker-001", context);

        assertThat(result).isSameAs(planned);
        assertThat(runs.claimRequestedWith).isEqualTo("worker-001");
        assertThat(runs.released).isSameAs(claim);
        verify(execution).executeClaimed(planned, context, claim);
    }

    @Test
    void does_not_execute_when_another_worker_holds_the_claim() {
        runs.run = Optional.of(plannedRun());
        runs.claim = Optional.empty();
        when(execution.validateExecutable(runs.run.orElseThrow(), context))
                .thenReturn(runs.run.orElseThrow());

        assertThatThrownBy(() -> worker.claimAndExecute("RUN-001", "worker-001", context))
                .isInstanceOf(AgentRunClaimUnavailableException.class)
                .hasMessageContaining("RUN-001");
    }

    @Test
    void always_releases_the_claim_when_execution_fails() {
        AgentRun planned = plannedRun();
        AgentRunClaim claim = claim();
        runs.claim = Optional.of(claim);
        runs.run = Optional.of(planned);
        when(execution.validateExecutable(planned, context)).thenReturn(planned);
        when(execution.executeClaimed(planned, context, claim))
                .thenThrow(new AgentRunClaimLostException(planned.runId()));

        assertThatThrownBy(() -> worker.claimAndExecute("RUN-001", "worker-001", context))
                .isInstanceOf(AgentRunClaimLostException.class)
                .hasMessageContaining("RUN-001");
        assertThat(runs.released).isSameAs(claim);
    }

    private AgentRun plannedRun() {
        return new AgentRun(
                "RUN-001", "agent-001", "f".repeat(64), "USER-001", "operator", scope,
                "traceability.query", "smart-canteen.traceability", "1.0.0", "a".repeat(64),
                "p".repeat(64), "{}", "{}", RunStatus.PLANNED,
                null, null, null, null, 0, NOW, NOW);
    }

    private AgentRunClaim claim() {
        return new AgentRunClaim(
                "RUN-001", "worker-001", "CLAIM-001", NOW, NOW.plusSeconds(30));
    }

    private static final class AgentRunStoreFake implements AgentRunStore {
        private Optional<AgentRunClaim> claim = Optional.empty();
        private Optional<AgentRun> run = Optional.empty();
        private String claimRequestedWith;
        private AgentRunClaim released;

        @Override
        public boolean supportsExecutionClaims() {
            return true;
        }

        @Override
        public Optional<AgentRunClaim> claimExecution(
                String runId, String ownerId, Duration leaseDuration) {
            claimRequestedWith = ownerId;
            return claim;
        }

        @Override
        public Optional<AgentRun> findById(String runId) {
            return run;
        }

        @Override
        public Optional<AgentRun> findByIdempotency(
                String actorUserId, CanteenScope scope, String idempotencyKey) {
            return Optional.empty();
        }

        @Override
        public boolean releaseExecutionClaim(AgentRunClaim claim) {
            released = claim;
            return true;
        }

        @Override
        public AgentRun insert(AgentRun run, List<AgentStep> steps) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(AgentRun expected, AgentRun updated) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateStep(AgentStep step) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void appendDecision(AgentRunDecision decision) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<AgentRunDecision> listDecisions(String runId) {
            return List.of();
        }

        @Override
        public List<AgentRunEvent> listEvents(String runId) {
            return List.of();
        }

        @Override
        public void appendEvent(
                String runId, String eventType, String fromStatus, String toStatus,
                String actorUserId, String payloadJson) {
            throw new UnsupportedOperationException();
        }
    }
}
