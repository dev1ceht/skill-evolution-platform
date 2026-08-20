package com.example.smartcanteen.assistant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.smartcanteen.application.AssistantRolloutPolicy;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.ForbiddenException;
import org.junit.jupiter.api.Test;

class AssistantRolloutPolicyTest {

    private static final CanteenScope PILOT = new CanteenScope(
            "SCHOOL-PILOT", "CANTEEN-PILOT");

    @Test
    void allows_an_explicitly_configured_pilot_scope() {
        AssistantRolloutPolicy policy = new AssistantRolloutPolicy(
                true, "SCHOOL-PILOT/CANTEEN-PILOT");

        assertDoesNotThrow(() -> policy.requireEnabled(PILOT));
    }

    @Test
    void denies_a_scope_that_is_not_in_the_allowlist() {
        AssistantRolloutPolicy policy = new AssistantRolloutPolicy(
                true, "SCHOOL-PILOT/CANTEEN-OTHER");

        assertThrows(ForbiddenException.class, () -> policy.requireEnabled(PILOT));
    }

    @Test
    void global_disable_wins_over_an_allowlisted_scope() {
        AssistantRolloutPolicy policy = new AssistantRolloutPolicy(
                false, "SCHOOL-PILOT/CANTEEN-PILOT");

        assertThrows(ForbiddenException.class, () -> policy.requireEnabled(PILOT));
    }

    @Test
    void rejects_malformed_scope_configuration() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new AssistantRolloutPolicy(true, "SCHOOL-PILOT"));
    }

    @Test
    void allows_every_scope_when_the_wildcard_is_configured() {
        AssistantRolloutPolicy policy = new AssistantRolloutPolicy(true, "*");

        assertDoesNotThrow(() -> policy.requireEnabled(
                new CanteenScope("SCHOOL-OTHER", "CANTEEN-OTHER")));
    }
}
