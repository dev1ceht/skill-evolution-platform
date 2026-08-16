package com.example.smartcanteen.agent.domain;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Server-derived identity and scope used while an Agent operation is evaluated.
 *
 * <p>The request body must never be used to construct this object. Callers should use
 * {@link #fromTrustedPrincipal(String, AuthPrincipal, CanteenScope, Set, Set)} only after the
 * request-boundary authentication and authorization policy has resolved the current roles and
 * permissions.
 */
public record ExecutionContext(
        String requestId,
        String actorUserId,
        String actorUsername,
        CanteenScope scope,
        Set<Role> roles,
        Set<String> permissions) {

    public ExecutionContext {
        requireIdentifier("requestId", requestId, 128);
        requireIdentifier("actorUserId", actorUserId, 128);
        requireIdentifier("actorUsername", actorUsername, 128);
        Objects.requireNonNull(scope, "scope");
        roles = immutableRoles(roles);
        permissions = permissions == null
                ? Set.of()
                : Collections.unmodifiableSet(Set.copyOf(permissions));
    }

    public static ExecutionContext fromTrustedPrincipal(
            String requestId,
            AuthPrincipal principal,
            CanteenScope scope,
            Set<Role> resolvedRoles,
            Set<String> resolvedPermissions) {
        Objects.requireNonNull(principal, "principal");
        return new ExecutionContext(
                requestId,
                principal.userId(),
                principal.username(),
                scope,
                resolvedRoles,
                resolvedPermissions);
    }

    public boolean hasRole(Role role) {
        return role != null && roles.contains(role);
    }

    public boolean hasPermission(String permission) {
        return permission != null && permissions.contains(permission);
    }

    private static Set<Role> immutableRoles(Set<Role> roles) {
        EnumSet<Role> normalized = EnumSet.noneOf(Role.class);
        if (roles != null) {
            normalized.addAll(roles);
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static void requireIdentifier(String name, String value, int maxLength) {
        Objects.requireNonNull(value, name);
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(
                    name + " must be non-blank and at most " + maxLength + " characters");
        }
    }
}
