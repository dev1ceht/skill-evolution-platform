package com.example.smartcanteen.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.smartcanteen.agent.application.AgentRuntime;
import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.domain.StartRunCommand;
import com.example.smartcanteen.agent.port.AgentRunStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
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
        "SMART_CANTEEN_BOOTSTRAP_ADMIN_PASSWORD="
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
        jdbc.update("DELETE FROM agent_run_events");
        jdbc.update("DELETE FROM agent_steps");
        jdbc.update("DELETE FROM agent_run_decisions");
        jdbc.update("DELETE FROM agent_runs");
    }

    @Test
    void persists_run_step_and_initial_event_in_one_runtime_transaction() {
        ExecutionContext context = ExecutionContext.fromTrustedPrincipal(
                "request-persistence-001",
                new AuthPrincipal(
                        "USER-RUNTIME-001",
                        "runtime-user",
                        "Runtime User",
                        Role.CANTEEN_STAFF,
                        "SCHOOL-001",
                        "CANTEEN-001"),
                new CanteenScope("SCHOOL-001", "CANTEEN-001"),
                Set.of(Role.CANTEEN_STAFF),
                Set.of("TRACEABILITY_READ"));

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
                created.runId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM agent_steps WHERE run_id = ? AND step_id = 'step-1'",
                String.class,
                created.runId())).isEqualTo("PENDING");
    }
}
