package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.ForbiddenException;
import org.junit.jupiter.api.Test;

class AgentWriteRolloutPolicyTest {

    private final CanteenScope scope = new CanteenScope("SCHOOL-001", "CANTEEN-001");

    @Test
    void fails_closed_when_write_pilot_is_disabled() {
        AgentWriteRolloutPolicy policy = new AgentWriteRolloutPolicy(
                false, "SCHOOL-001/CANTEEN-001", "inventory.stock-out");

        assertThatThrownBy(() -> policy.requireEnabled(scope, "inventory.stock-out"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    void requires_exact_scope_and_intent_allowlists() {
        AgentWriteRolloutPolicy policy = new AgentWriteRolloutPolicy(
                true, "SCHOOL-001/CANTEEN-001", "inventory.stock-out");

        assertThatThrownBy(() -> policy.requireEnabled(
                new CanteenScope("SCHOOL-002", "CANTEEN-001"), "inventory.stock-out"))
                .isInstanceOf(ForbiddenException.class);
        assertThatThrownBy(() -> policy.requireEnabled(scope, "alert.dispose"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void allows_every_scope_and_intent_when_wildcards_are_configured() {
        AgentWriteRolloutPolicy policy = new AgentWriteRolloutPolicy(true, "*", "*");

        assertThatCode(() -> policy.requireEnabled(
                new CanteenScope("SCHOOL-OTHER", "CANTEEN-OTHER"), "alert.dispose"))
                .doesNotThrowAnyException();
    }
}
