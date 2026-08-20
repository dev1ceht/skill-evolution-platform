package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.ForbiddenException;
import org.junit.jupiter.api.Test;

class AgentSchedulerRolloutPolicyTest {

    private static final CanteenScope PILOT = new CanteenScope(
            "SCHOOL-PILOT", "CANTEEN-PILOT");

    @Test
    void allows_only_an_explicitly_configured_pilot_scope() {
        AgentSchedulerRolloutPolicy policy = new AgentSchedulerRolloutPolicy(
                true, "SCHOOL-PILOT/CANTEEN-PILOT");

        assertThatCode(() -> policy.requireEnabled(PILOT)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireEnabled(
                new CanteenScope("SCHOOL-OTHER", "CANTEEN-OTHER")))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void fails_closed_when_scheduler_is_enabled_without_an_allowlist() {
        assertThatThrownBy(() -> new AgentSchedulerRolloutPolicy(true, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowed-scopes");
    }

    @Test
    void allows_every_scope_when_the_wildcard_is_configured() {
        AgentSchedulerRolloutPolicy policy = new AgentSchedulerRolloutPolicy(true, "*");

        assertThatCode(() -> policy.requireEnabled(
                new CanteenScope("SCHOOL-OTHER", "CANTEEN-OTHER")))
                .doesNotThrowAnyException();
        assertThat(policy.isUnrestricted()).isTrue();
    }

    @Test
    void rejects_malformed_scope_configuration() {
        assertThatThrownBy(() -> new AgentSchedulerRolloutPolicy(true, "SCHOOL-PILOT"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SCHOOL/CANTEEN");
    }

    @Test
    void remains_empty_when_the_scheduler_is_disabled() {
        AgentSchedulerRolloutPolicy policy = new AgentSchedulerRolloutPolicy(false, "");

        assertThatThrownBy(() -> policy.requireEnabled(PILOT))
                .isInstanceOf(ForbiddenException.class);
    }
}
