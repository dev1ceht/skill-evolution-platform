package com.example.smartcanteen.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.RunStatus;
import com.example.smartcanteen.domain.CanteenScope;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RunStatusTest {

    private static final Instant NOW = Instant.parse("2026-08-16T10:00:00Z");

    @Test
    void allows_only_declared_forward_lifecycle_transitions() {
        assertThat(RunStatus.PLANNED.canTransitionTo(RunStatus.EXECUTING)).isTrue();
        assertThat(RunStatus.EXECUTING.canTransitionTo(RunStatus.FAILED)).isTrue();
        assertThat(RunStatus.SUCCEEDED.canTransitionTo(RunStatus.EXECUTING)).isFalse();
        assertThat(RunStatus.PLANNED.canTransitionTo(RunStatus.SUCCEEDED)).isFalse();
    }

    @Test
    void rejects_invalid_domain_transition_instead_of_persisting_it() {
        AgentRun planned = new AgentRun(
                "RUN-STATUS-001", "status-001", "f".repeat(64), "USER-001", "operator",
                new CanteenScope("SCHOOL-001", "CANTEEN-001"), "traceability.query",
                "smart-canteen.traceability", "1.0.0", "m".repeat(64), "p".repeat(64),
                "{}", "{}", RunStatus.PLANNED, null, null, null, null, 0, NOW, NOW);

        assertThatThrownBy(() -> planned.withStatus(RunStatus.SUCCEEDED, null, NOW))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PLANNED -> SUCCEEDED");
    }
}
