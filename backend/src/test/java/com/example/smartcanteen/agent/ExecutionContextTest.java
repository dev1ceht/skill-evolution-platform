package com.example.smartcanteen.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExecutionContextTest {

    @Test
    void derives_actor_and_scope_from_trusted_server_identity() {
        AuthPrincipal principal = new AuthPrincipal(
                "USER-001",
                "operator",
                "Operator",
                Role.CANTEEN_STAFF,
                "SCHOOL-001",
                "CANTEEN-001");
        CanteenScope scope = new CanteenScope("SCHOOL-001", "CANTEEN-001");

        ExecutionContext context = ExecutionContext.fromTrustedPrincipal(
                "request-001",
                principal,
                scope,
                Set.of(Role.CANTEEN_STAFF),
                Set.of("TRACEABILITY_READ"));

        assertThat(context.requestId()).isEqualTo("request-001");
        assertThat(context.actorUserId()).isEqualTo("USER-001");
        assertThat(context.actorUsername()).isEqualTo("operator");
        assertThat(context.scope()).isEqualTo(scope);
        assertThat(context.roles()).containsExactly(Role.CANTEEN_STAFF);
        assertThat(context.permissions()).containsExactly("TRACEABILITY_READ");
    }

    @Test
    void copies_claims_so_callers_cannot_mutate_the_execution_context() {
        AuthPrincipal principal = new AuthPrincipal(
                "USER-001",
                "operator",
                "Operator",
                Role.CANTEEN_STAFF,
                "SCHOOL-001",
                "CANTEEN-001");
        Set<Role> roles = new HashSet<>(Set.of(Role.CANTEEN_STAFF));
        Set<String> permissions = new HashSet<>(Set.of("TRACEABILITY_READ"));

        ExecutionContext context = ExecutionContext.fromTrustedPrincipal(
                "request-001",
                principal,
                new CanteenScope("SCHOOL-001", "CANTEEN-001"),
                roles,
                permissions);
        roles.add(Role.SCHOOL_ADMIN);
        permissions.add("MENU_PUBLISH");

        assertThat(context.roles()).containsExactly(Role.CANTEEN_STAFF);
        assertThat(context.permissions()).containsExactly("TRACEABILITY_READ");
        assertThatThrownBy(() -> context.roles().add(Role.SCHOOL_ADMIN))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void refuses_context_without_a_server_identity_or_explicit_scope() {
        assertThatThrownBy(() -> ExecutionContext.fromTrustedPrincipal(
                "request-001",
                null,
                new CanteenScope("SCHOOL-001", "CANTEEN-001"),
                Set.of(),
                Set.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("principal");

        AuthPrincipal principal = new AuthPrincipal(
                "USER-001",
                "operator",
                "Operator",
                Role.CANTEEN_STAFF,
                "SCHOOL-001",
                "CANTEEN-001");
        assertThatThrownBy(() -> ExecutionContext.fromTrustedPrincipal(
                "request-001", principal, null, Set.of(), Set.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("scope");
    }
}
