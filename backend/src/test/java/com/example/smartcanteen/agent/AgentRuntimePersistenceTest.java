package com.example.smartcanteen.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.smartcanteen.agent.application.AgentRuntime;
import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.AgentRunClaim;
import com.example.smartcanteen.agent.domain.AgentRunClaimLostException;
import com.example.smartcanteen.agent.domain.AgentStep;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.domain.StartRunCommand;
import com.example.smartcanteen.agent.port.AgentRunStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "smart-canteen.security.enabled=false",
        "BOOTSTRAP_ADMIN_PASSWORD="
})
class AgentRuntimePersistenceTest {

    @Autowired
    private AgentRuntime runtime;

    @Autowired
    private AgentRunStore runs;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void clearRuntimeState() {
        jdbc.update("DELETE FROM agent_run_claims");
        jdbc.update("DELETE FROM agent_run_events");
        jdbc.update("DELETE FROM agent_steps");
        jdbc.update("DELETE FROM agent_run_decisions");
        jdbc.update("DELETE FROM agent_runs");
    }

    @Test
    void persists_run_step_and_initial_event_in_one_runtime_transaction() {
        ExecutionContext context = persistenceContext("request-persistence-001");

        AgentRun created = runtime.start(new StartRunCommand(
                "request-persistence-001",
                "traceability.query",
                "{\"traceCode\":\"TRACE-PERSISTENCE-001\"}",
                "runtime-persistence-001"), context);

        AgentRun reloaded = runs.findByIdempotency(
                        "USER-RUNTIME-001", context.scope(), "runtime-persistence-001")
                .orElseThrow();
        assertThat(reloaded.runId()).isEqualTo(created.runId());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_steps WHERE run_id = ?",
                Integer.class,
                created.runId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM agent_run_events WHERE run_id = ?",
                Integer.class,
                created.runId())).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM agent_steps WHERE run_id = ? AND step_id = 'step-1'",
                String.class,
                created.runId())).isEqualTo("PENDING");
    }

    @Test
    void claims_a_planned_run_once_renews_it_and_allows_a_new_owner_after_release() {
        AgentRun created = runtime.start(
                new StartRunCommand(
                        "request-claim-001",
                        "traceability.query",
                        "{\"traceCode\":\"TRACE-CLAIM-001\"}",
                        "runtime-claim-001"),
                persistenceContext("request-claim-001"));
        AgentRunClaim first = runs.claimExecution(
                created.runId(), "worker-a", Duration.ofSeconds(30))
                .orElseThrow();

        assertThat(runs.claimExecution(
                        created.runId(), "worker-b", Duration.ofSeconds(30)))
                .isEmpty();
        assertThat(runs.renewExecutionClaim(
                first, Duration.ofSeconds(30))).isTrue();
        assertThat(runs.releaseExecutionClaim(first)).isTrue();
        assertThat(runs.claimExecution(
                        created.runId(), "worker-b", Duration.ofSeconds(30)))
                .isPresent();
    }

    @Test
    void an_expired_replacement_fences_the_previous_worker() {
        AgentRun created = runtime.start(
                new StartRunCommand(
                        "request-claim-002",
                        "traceability.query",
                        "{\"traceCode\":\"TRACE-CLAIM-002\"}",
                        "runtime-claim-002"),
                persistenceContext("request-claim-002"));
        Instant now = Instant.now();
        AgentRunClaim first = runs.claimExecution(
                created.runId(), "worker-a", Duration.ofSeconds(30))
                .orElseThrow();
        jdbc.update(
                "UPDATE agent_run_claims SET claimed_at = ?, expires_at = ? WHERE run_id = ?",
                Timestamp.from(now.minusSeconds(60)),
                Timestamp.from(now.minusSeconds(1)),
                created.runId());
        AgentRunClaim replacement = runs.claimExecution(
                created.runId(), "worker-b", Duration.ofSeconds(30))
                .orElseThrow();

        AgentRun executing = created.withStatus(
                com.example.smartcanteen.agent.domain.RunStatus.EXECUTING,
                "step-1",
                now.plusSeconds(2));
        assertThatThrownBy(() -> runs.updateClaimed(created, executing, first))
                .isInstanceOf(AgentRunClaimLostException.class);
        assertThat(runs.renewExecutionClaim(
                replacement, Duration.ofSeconds(30))).isTrue();
    }

    @Test
    void a_claim_cannot_be_reused_for_another_run_or_step() {
        AgentRun first = runtime.start(
                new StartRunCommand(
                        "request-claim-003",
                        "traceability.query",
                        "{\"traceCode\":\"TRACE-CLAIM-003\"}",
                        "runtime-claim-003"),
                persistenceContext("request-claim-003"));
        AgentRun second = runtime.start(
                new StartRunCommand(
                        "request-claim-004",
                        "traceability.query",
                        "{\"traceCode\":\"TRACE-CLAIM-004\"}",
                        "runtime-claim-004"),
                persistenceContext("request-claim-004"));
        AgentRunClaim secondClaim = runs.claimExecution(
                        second.runId(), "worker-b", Duration.ofSeconds(30))
                .orElseThrow();
        AgentRun updated = first.withStatus(
                com.example.smartcanteen.agent.domain.RunStatus.EXECUTING,
                "step-1",
                Instant.now());
        AgentStep step = new AgentStep(
                first.runId(),
                "step-1",
                0,
                "traceability.query",
                "step-key",
                "input-digest",
                "PENDING",
                0,
                null,
                null,
                null,
                null,
                null);

        assertThatThrownBy(() -> runs.updateClaimed(first, updated, secondClaim))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runs.updateStepClaimed(step, secondClaim))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> runs.appendEventClaimed(
                        first.runId(),
                        "TEST",
                        "PLANNED",
                        "EXECUTING",
                        "runtime-user",
                        "{}",
                        secondClaim))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void a_valid_claim_fences_and_persists_run_step_and_event() {
        AgentRun created = runtime.start(
                new StartRunCommand(
                        "request-claim-005",
                        "traceability.query",
                        "{\"traceCode\":\"TRACE-CLAIM-005\"}",
                        "runtime-claim-005"),
                persistenceContext("request-claim-005"));
        AgentRunClaim claim = runs.claimExecution(
                        created.runId(), "worker-a", Duration.ofSeconds(30))
                .orElseThrow();
        Instant now = Instant.now();
        AgentRun executing = created.withStatus(
                com.example.smartcanteen.agent.domain.RunStatus.EXECUTING,
                "step-1",
                now);
        String stepId = jdbc.queryForObject(
                "SELECT step_id FROM agent_steps WHERE run_id = ?",
                String.class,
                created.runId());
        AgentStep checkpoint = new AgentStep(
                created.runId(),
                stepId,
                0,
                "traceability.query",
                "runtime-claim-005",
                "input-digest",
                "SUCCEEDED",
                1,
                "{}",
                null,
                null,
                now,
                now);

        runs.updateClaimed(created, executing, claim);
        runs.updateStepClaimed(checkpoint, claim);
        runs.appendEventClaimed(
                created.runId(),
                "CLAIMED_WRITE",
                "PLANNED",
                "EXECUTING",
                "runtime-user",
                "{}",
                claim);

        assertThat(runs.findById(created.runId()).orElseThrow().status())
                .isEqualTo(com.example.smartcanteen.agent.domain.RunStatus.EXECUTING);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM agent_steps WHERE run_id = ? AND step_id = ?",
                String.class,
                created.runId(),
                stepId)).isEqualTo("SUCCEEDED");
        assertThat(runs.listEvents(created.runId()))
                .extracting(event -> event.eventType())
                .contains("CLAIMED_WRITE");
    }

    @Test
    void stale_scan_waits_for_the_execution_claim_to_expire() {
        AgentRun created = runtime.start(
                new StartRunCommand(
                        "request-claim-stale-001",
                        "traceability.query",
                        "{\"traceCode\":\"TRACE-CLAIM-STALE-001\"}",
                        "runtime-claim-stale-001"),
                persistenceContext("request-claim-stale-001"));
        AgentRunClaim claim = runs.claimExecution(
                        created.runId(), "worker-stale", Duration.ofSeconds(30))
                .orElseThrow();
        Instant staleAt = Instant.now().minusSeconds(300);
        AgentRun executing = created.withStatus(
                com.example.smartcanteen.agent.domain.RunStatus.EXECUTING,
                "step-1",
                staleAt);
        runs.updateClaimed(created, executing, claim);

        assertThat(runs.findStaleExecuting(Instant.now().minusSeconds(60), 10))
                .extracting(AgentRun::runId)
                .doesNotContain(created.runId());
        assertThat(runs.confirmStaleExecution(created.runId(), executing.version())).isFalse();

        Instant expiredClaimedAt = Instant.now().minusSeconds(60);
        jdbc.update(
                "UPDATE agent_run_claims SET claimed_at = ?, expires_at = ? WHERE run_id = ?",
                Timestamp.from(expiredClaimedAt),
                Timestamp.from(expiredClaimedAt.plusSeconds(1)),
                created.runId());

        assertThat(runs.findStaleExecuting(Instant.now().minusSeconds(60), 10))
                .extracting(AgentRun::runId)
                .contains(created.runId());
        assertThat(runs.confirmStaleExecution(created.runId(), executing.version())).isTrue();
    }

    @Test
    void rollout_scope_filter_is_applied_before_the_poll_limit() {
        AgentRun nonPilot = runtime.start(
                new StartRunCommand(
                        "request-scope-non-pilot",
                        "traceability.query",
                        "{\"traceCode\":\"TRACE-SCOPE-NON-PILOT\"}",
                        "runtime-scope-non-pilot"),
                persistenceContext(
                        "request-scope-non-pilot", "SCHOOL-OTHER", "CANTEEN-OTHER"));
        AgentRun pilot = runtime.start(
                new StartRunCommand(
                        "request-scope-pilot",
                        "traceability.query",
                        "{\"traceCode\":\"TRACE-SCOPE-PILOT\"}",
                        "runtime-scope-pilot"),
                persistenceContext("request-scope-pilot"));

        assertThat(runs.findPlanned(1, Set.of(pilot.scope())))
                .extracting(AgentRun::runId)
                .containsExactly(pilot.runId());
    }

    private ExecutionContext persistenceContext(String requestId) {
        return persistenceContext(requestId, "SCHOOL-001", "CANTEEN-001");
    }

    private ExecutionContext persistenceContext(
            String requestId, String schoolId, String canteenId) {
        return ExecutionContext.fromTrustedPrincipal(
                requestId,
                new AuthPrincipal(
                        "USER-RUNTIME-001",
                        "runtime-user",
                        "Runtime User",
                        Role.CANTEEN_STAFF,
                        schoolId,
                        canteenId),
                new CanteenScope(schoolId, canteenId),
                Set.of(Role.CANTEEN_STAFF),
                Set.of("TRACEABILITY_READ"));
    }
}
