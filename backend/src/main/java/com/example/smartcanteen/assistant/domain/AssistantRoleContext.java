package com.example.smartcanteen.assistant.domain;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.Role;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Server-derived persona context used by the conversational layer.
 *
 * <p>This is not a second authorization system. It gives the model a bounded description of the
 * current actor while the existing BusinessAuthorizationPolicy remains the authority for tools
 * and Agent Runs.</p>
 */
public record AssistantRoleContext(
        String actorUserId,
        String actorUsername,
        AssistantPersona persona,
        CanteenScope scope,
        Set<Role> roles,
        Set<String> permissions) {

    public AssistantRoleContext {
        requireText("actorUserId", actorUserId);
        requireText("actorUsername", actorUsername);
        Objects.requireNonNull(persona, "persona");
        Objects.requireNonNull(scope, "scope");
        roles = immutableRoles(roles);
        permissions = permissions == null
                ? Set.of()
                : Collections.unmodifiableSet(Set.copyOf(permissions));
    }

    public static AssistantRoleContext from(ExecutionContext context) {
        Objects.requireNonNull(context, "context");
        return new AssistantRoleContext(
                context.actorUserId(),
                context.actorUsername(),
                AssistantPersona.fromRoles(context.roles()),
                context.scope(),
                context.roles(),
                context.permissions());
    }

    /** Compact, non-secret context intended for a model prompt or a trace event. */
    public String promptSummary() {
        return "actorUserId=" + actorUserId
                + "; persona=" + persona
                + "; scope=" + scope.schoolId() + "/" + scope.canteenId()
                + "; roles=" + roles
                + "; permissions=" + permissions;
    }

    private static Set<Role> immutableRoles(Set<Role> roles) {
        EnumSet<Role> normalized = EnumSet.noneOf(Role.class);
        if (roles != null) {
            normalized.addAll(roles);
        }
        return Collections.unmodifiableSet(normalized);
    }

    private static void requireText(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
