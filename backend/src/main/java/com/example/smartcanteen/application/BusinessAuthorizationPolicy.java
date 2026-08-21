package com.example.smartcanteen.application;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.SkillDefinition;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.ForbiddenException;
import com.example.smartcanteen.security.Role;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Request-independent authorization policy shared by HTTP controllers and Agent execution.
 *
 * <p>HTTP adapters are responsible for extracting {@link AuthPrincipal}; this policy is
 * responsible for applying the same scope, role, permission and active-canteen rules regardless
 * of whether the caller arrived through HTTP or an Agent command.
 */
@Component
public class BusinessAuthorizationPolicy {

    private final boolean securityEnabled;
    private final AuthorizationService authorization;
    private final OrganizationService organization;
    private final AuthService authentication;

    public BusinessAuthorizationPolicy(
            boolean securityEnabled,
            AuthorizationService authorization,
            OrganizationService organization) {
        this(securityEnabled, authorization, organization, null);
    }

    @Autowired
    public BusinessAuthorizationPolicy(
            @Value("${smart-canteen.security.enabled:true}") boolean securityEnabled,
            AuthorizationService authorization,
            OrganizationService organization,
            AuthService authentication) {
        this.securityEnabled = securityEnabled;
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.organization = Objects.requireNonNull(organization, "organization");
        this.authentication = authentication;
    }

    public ExecutionContext establishContext(
            AuthPrincipal principal,
            String requestId,
            CanteenScope scope,
            boolean write) {
        requireScope(principal, scope, write);
        if (!securityEnabled) {
            return ExecutionContext.fromTrustedPrincipal(
                    requestId, requirePrincipalForContext(principal), scope, Set.of(), Set.of());
        }
        return ExecutionContext.fromTrustedPrincipal(
                requestId,
                principal,
                scope,
                authorization.rolesFor(principal),
                authorization.permissionsFor(principal));
    }

    public CanteenScope requireScope(
            AuthPrincipal principal, CanteenScope scope, boolean write) {
        Objects.requireNonNull(scope, "scope");
        if (!securityEnabled) {
            return scope;
        }
        requireAuthenticated(principal);
        if (!organization.isKnownScope(scope)) {
            throw new ForbiddenException("The requested school/canteen scope is not registered");
        }
        if (!authorization.canAccess(principal, scope)) {
            throw new ForbiddenException("User is outside the requested school/canteen scope");
        }
        if (write && !organization.isActiveScope(scope)) {
            throw new ForbiddenException("The requested school/canteen scope is disabled");
        }
        return scope;
    }

    public void requireAnyRole(AuthPrincipal principal, Role... allowedRoles) {
        if (!securityEnabled) {
            return;
        }
        requireAuthenticated(principal);
        Set<Role> actualRoles = authorization.rolesFor(principal);
        boolean allowed = allowedRoles != null
                && Arrays.stream(allowedRoles).anyMatch(actualRoles::contains);
        if (!allowed) {
            throw new ForbiddenException("User role is not allowed for this operation");
        }
    }

    public void requirePermission(AuthPrincipal principal, String permissionCode) {
        if (!securityEnabled) {
            return;
        }
        requireAuthenticated(principal);
        if (!authorization.hasPermission(principal, permissionCode)) {
            throw new ForbiddenException("User permission is not allowed for this operation");
        }
    }

    /** Accepts role names and fine-grained permission codes declared by the Skill. */
    public void requireSkillAccess(AuthPrincipal principal, SkillDefinition skill) {
        Objects.requireNonNull(skill, "skill");
        if (!securityEnabled) {
            return;
        }
        requireAuthenticated(principal);
        Set<Role> roles = authorization.rolesFor(principal);
        boolean allowed = skill.permissions().stream().anyMatch(required -> {
            try {
                return roles.contains(Role.valueOf(required));
            } catch (IllegalArgumentException ignored) {
                return authorization.hasPermission(principal, required);
            }
        });
        if (!allowed) {
            throw new ForbiddenException("User is not allowed to execute Skill: " + skill.id());
        }
    }

    /** Rechecks current persisted identity, scope and Skill permission immediately before a tool runs. */
    public void requireCurrentExecution(
            AgentRun run, ExecutionContext context, SkillDefinition skill) {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(skill, "skill");
        if (!run.actorUserId().equals(context.actorUserId())
                || !run.scope().equals(context.scope())) {
            throw new ForbiddenException("User is outside the Agent Run scope");
        }
        if (!securityEnabled) {
            return;
        }
        if (authentication == null) {
            throw new IllegalStateException("Current principal resolver is not configured");
        }
        AuthPrincipal current;
        try {
            current = authentication.principalForUser(run.actorUserId());
        } catch (IllegalArgumentException exception) {
            throw new ForbiddenException("Agent actor is no longer active");
        }
        requireScope(current, run.scope(), "write".equals(skill.runtime().sideEffect()));
        requireSkillAccess(current, skill);
        // Duties must be evaluated against the reloaded principal, not the roles that
        // were captured when the Run was created. This closes the privilege-downgrade
        // window between planning and execution.
        requireIntentAccess(current, run.intent());
        requireDomainApproval(current, run.intent());
    }

    /** Applies finer-grained duties for menu approval actions after a Run is resumed. */
    public void requireIntentAccess(ExecutionContext context, String intent) {
        if (context == null || !securityEnabled) {
            return;
        }
        String permissionCode = permissionForIntent(intent);
        if (permissionCode != null && !context.hasPermission(permissionCode)) {
            throw new ForbiddenException("User lacks permission " + permissionCode
                    + " for Agent intent " + intent);
        }
    }

    /**
     * Applies domain-approval duties that are stricter than the permission required to request a
     * write. Runtime confirmation is not a substitute for these duties: a high-risk order,
     * stock-out, or alert disposal must still be executed by an authorized approver role.
     */
    public void requireDomainApproval(ExecutionContext context, String intent) {
        if (!securityEnabled || context == null || intent == null) {
            return;
        }
        if (authentication != null) {
            AuthPrincipal current;
            try {
                current = authentication.principalForUser(context.actorUserId());
            } catch (IllegalArgumentException exception) {
                throw new ForbiddenException("Agent actor is no longer active");
            }
            requireDomainApproval(current, intent);
            return;
        }
        requireDomainApproval(context.roles(), intent);
    }

    /** Applies high-risk domain duties to a freshly reloaded authenticated principal. */
    private void requireDomainApproval(AuthPrincipal principal, String intent) {
        if (!securityEnabled || principal == null || intent == null) {
            return;
        }
        requireDomainApproval(authorization.rolesFor(principal), intent);
    }

    private static void requireDomainApproval(Set<Role> roles, String intent) {
        switch (intent) {
            case "procurement.order.create", "procurement.order.receive", "inventory.stock-out" ->
                    requireDomainRole(roles, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN);
            case "alert.dispose" ->
                    requireDomainRole(roles, Role.SYSTEM_ADMIN, Role.REGULATOR);
            default -> {
                // Plan generation and inventory receiving are validation-gated writes but do not
                // change an already-approved order or dispose a risk signal.
            }
        }
    }

    private static void requireDomainRole(Set<Role> roles, Role... approverRoles) {
        boolean allowed = roles != null
                && Arrays.stream(approverRoles).anyMatch(roles::contains);
        if (!allowed) {
            throw new ForbiddenException("A domain approver role is required for this Agent intent");
        }
    }

    private void requireIntentAccess(AuthPrincipal principal, String intent) {
        if (!securityEnabled || principal == null) {
            return;
        }
        String permissionCode = permissionForIntent(intent);
        if (permissionCode != null && !authorization.hasPermission(principal, permissionCode)) {
            throw new ForbiddenException("User lacks permission " + permissionCode
                    + " for Agent intent " + intent);
        }
    }

    private static String permissionForIntent(String intent) {
        if (intent == null) {
            return null;
        }
        return switch (intent) {
            case "menu.query" -> "MENU_READ";
            case "menu.validate-for-submit" -> "MENU_VALIDATE";
            case "menu.submit" -> "MENU_SUBMIT";
            case "menu.record-decision" -> "MENU_APPROVE";
            case "menu.publish" -> "MENU_PUBLISH";
            case "inventory.query" -> "INVENTORY_READ";
            case "procurement.gap.query" -> "PROCUREMENT_ANALYSIS_READ";
            case "traffic.forecast.query" -> "TRAFFIC_FORECAST_READ";
            case "meal_plan.query" -> "MEAL_PLAN_ANALYSIS_READ";
            case "procurement.plan.generate" -> "PROCUREMENT_PLAN_WRITE";
            case "procurement.order.create" -> "PROCUREMENT_ORDER_WRITE";
            case "procurement.order.receive" -> "PROCUREMENT_RECEIVE";
            case "inventory.receive" -> "INVENTORY_RECEIVE";
            case "inventory.stock-out" -> "INVENTORY_STOCK_OUT";
            case "alert.dispose" -> "ALERT_DISPOSE";
            default -> null;
        };
    }

    public Set<Role> rolesFor(AuthPrincipal principal) {
        if (!securityEnabled) {
            return Set.of();
        }
        requireAuthenticated(principal);
        return authorization.rolesFor(principal);
    }

    public Set<String> permissionsFor(AuthPrincipal principal) {
        if (!securityEnabled) {
            return Set.of();
        }
        requireAuthenticated(principal);
        return authorization.permissionsFor(principal);
    }

    private static void requireAuthenticated(AuthPrincipal principal) {
        if (principal == null) {
            throw new ForbiddenException("Authentication is required");
        }
    }

    private static AuthPrincipal requirePrincipalForContext(AuthPrincipal principal) {
        // Security-disabled mode is retained for local development, but a Runtime context still
        // needs a server identity. Callers must use a test principal instead of an anonymous body.
        return Objects.requireNonNull(principal, "principal");
    }
}
