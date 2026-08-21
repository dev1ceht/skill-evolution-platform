package com.example.smartcanteen.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.ForbiddenException;
import com.example.smartcanteen.security.Role;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BusinessAuthorizationPolicyTest {

    private final AuthorizationService authorization = mock(AuthorizationService.class);
    private final OrganizationService organization = mock(OrganizationService.class);
    private final BusinessAuthorizationPolicy policy =
            new BusinessAuthorizationPolicy(true, authorization, organization);
    private final AuthPrincipal operator = new AuthPrincipal(
            "USER-001",
            "operator",
            "Operator",
            Role.CANTEEN_STAFF,
            "SCHOOL-001",
            "CANTEEN-001");
    private final CanteenScope scope = new CanteenScope("SCHOOL-001", "CANTEEN-001");

    @Test
    void establishes_context_only_after_scope_is_known_authorized_and_active() {
        when(organization.isKnownScope(scope)).thenReturn(true);
        when(authorization.canAccess(operator, scope)).thenReturn(true);
        when(organization.isActiveScope(scope)).thenReturn(true);
        when(authorization.rolesFor(operator)).thenReturn(Set.of(Role.CANTEEN_STAFF));
        when(authorization.permissionsFor(operator)).thenReturn(Set.of("TRACEABILITY_READ"));

        ExecutionContext context = policy.establishContext(operator, "request-001", scope, true);

        assertThat(context.actorUserId()).isEqualTo("USER-001");
        assertThat(context.scope()).isEqualTo(scope);
        assertThat(context.permissions()).containsExactly("TRACEABILITY_READ");
    }

    @Test
    void rejects_unknown_or_foreign_scope_before_a_business_service_can_run() {
        when(organization.isKnownScope(scope)).thenReturn(false);

        assertThatThrownBy(() -> policy.establishContext(operator, "request-001", scope, false))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("The requested school/canteen scope is not registered");

        when(organization.isKnownScope(scope)).thenReturn(true);
        when(authorization.canAccess(operator, scope)).thenReturn(false);
        assertThatThrownBy(() -> policy.establishContext(operator, "request-002", scope, false))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("User is outside the requested school/canteen scope");
    }

    @Test
    void rejects_writes_to_an_inactive_scope_but_allows_reads() {
        when(organization.isKnownScope(scope)).thenReturn(true);
        when(authorization.canAccess(operator, scope)).thenReturn(true);
        when(organization.isActiveScope(scope)).thenReturn(false);

        assertThatThrownBy(() -> policy.establishContext(operator, "request-001", scope, true))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("The requested school/canteen scope is disabled");

        when(authorization.rolesFor(operator)).thenReturn(Set.of(Role.CANTEEN_STAFF));
        when(authorization.permissionsFor(operator)).thenReturn(Set.of("TRACEABILITY_READ"));
        assertThat(policy.establishContext(operator, "request-002", scope, false).scope())
                .isEqualTo(scope);
    }

    @Test
    void role_and_permission_checks_use_persisted_authorization() {
        when(authorization.rolesFor(operator)).thenReturn(Set.of(Role.CANTEEN_STAFF));
        when(authorization.permissionsFor(operator)).thenReturn(Set.of("TRACEABILITY_READ"));
        when(authorization.hasPermission(operator, "TRACEABILITY_READ")).thenReturn(true);
        when(authorization.hasPermission(operator, "MENU_PUBLISH")).thenReturn(false);

        policy.requireAnyRole(operator, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        policy.requirePermission(operator, "TRACEABILITY_READ");

        assertThatThrownBy(() -> policy.requireAnyRole(operator, Role.REGULATOR))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("User role is not allowed for this operation");
        assertThatThrownBy(() -> policy.requirePermission(operator, "MENU_PUBLISH"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("User permission is not allowed for this operation");
    }

    @Test
    void menu_intents_require_their_fine_grained_duty_permission() {
        ExecutionContext submitter = ExecutionContext.fromTrustedPrincipal(
                "request-menu-submit", operator, scope,
                Set.of(Role.CANTEEN_STAFF), Set.of("MENU_SUBMIT", "MENU_READ"));
        policy.requireIntentAccess(submitter, "menu.query");
        policy.requireIntentAccess(submitter, "menu.submit");
        assertThatThrownBy(() -> policy.requireIntentAccess(submitter, "menu.record-decision"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("MENU_APPROVE");

        ExecutionContext approver = ExecutionContext.fromTrustedPrincipal(
                "request-menu-approve", operator, scope,
                Set.of(Role.SCHOOL_ADMIN), Set.of("MENU_APPROVE", "MENU_PUBLISH"));
        policy.requireIntentAccess(approver, "menu.record-decision");
        policy.requireIntentAccess(approver, "menu.publish");
    }

    @Test
    void diner_persona_can_read_menu_but_cannot_publish_it() {
        ExecutionContext diner = new ExecutionContext(
                "request-diner",
                "USER-DINER",
                "diner",
                scope,
                Set.of(Role.DINER),
                Set.of("MENU_READ"));

        policy.requireIntentAccess(diner, "menu.query");
        assertThatThrownBy(() -> policy.requireIntentAccess(diner, "menu.publish"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("MENU_PUBLISH");
    }

    @Test
    void inventory_queries_require_the_operations_read_permission() {
        ExecutionContext operatorContext = new ExecutionContext(
                "request-inventory-read",
                "USER-OPERATOR",
                "operator",
                scope,
                Set.of(Role.CANTEEN_STAFF),
                Set.of("INVENTORY_READ"));
        ExecutionContext dinerContext = new ExecutionContext(
                "request-diner-inventory",
                "USER-DINER",
                "diner",
                scope,
                Set.of(Role.DINER),
                Set.of("MENU_READ"));

        policy.requireIntentAccess(operatorContext, "inventory.query");
        assertThatThrownBy(() -> policy.requireIntentAccess(dinerContext, "inventory.query"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("INVENTORY_READ");
    }

    @Test
    void domain_approval_reloads_current_roles_before_high_risk_execution() {
        AuthService authentication = mock(AuthService.class);
        BusinessAuthorizationPolicy currentPolicy = new BusinessAuthorizationPolicy(
                true, authorization, organization, authentication);
        ExecutionContext staleApprover = ExecutionContext.fromTrustedPrincipal(
                "request-stock-out", operator, scope,
                Set.of(Role.SCHOOL_ADMIN), Set.of("INVENTORY_STOCK_OUT"));
        when(authentication.principalForUser(operator.userId())).thenReturn(operator);
        when(authorization.rolesFor(operator)).thenReturn(Set.of(Role.CANTEEN_STAFF));

        assertThatThrownBy(() -> currentPolicy.requireDomainApproval(
                staleApprover, "inventory.stock-out"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("domain approver");
    }
}
