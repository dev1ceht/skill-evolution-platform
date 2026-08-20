package com.example.smartcanteen.application;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.ForbiddenException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Rollout gate for the asynchronous Agent scheduler. {@code *} means every canteen scope. */
@Component
public class AgentSchedulerRolloutPolicy {

    private static final String ALL_SCOPES = "*";

    private final boolean enabled;
    private final Set<String> allowedScopes;
    private final boolean allScopes;

    public AgentSchedulerRolloutPolicy(
            @Value("${agent.runtime.scheduler.enabled:true}") boolean enabled,
            @Value("${agent.runtime.scheduler.allowed-scopes:}") String configuredScopes) {
        this.enabled = enabled;
        Set<String> parsed = parseScopes(configuredScopes);
        if (enabled && parsed.isEmpty()) {
            throw new IllegalArgumentException(
                    "agent.runtime.scheduler.allowed-scopes is required when the scheduler is enabled");
        }
        this.allowedScopes = parsed;
        this.allScopes = allowedScopes.contains(ALL_SCOPES);
    }

    public void requireEnabled(CanteenScope scope) {
        if (!enabled) {
            throw new ForbiddenException("Agent scheduler is disabled");
        }
        if (!allScopes && !allowedScopes.contains(key(scope))) {
            throw new ForbiddenException("Agent scheduler is disabled for this scope");
        }
    }

    /** Returns whether scheduler/recovery queries should include every scope. */
    public boolean isUnrestricted() {
        return enabled && allScopes;
    }

    /** Returns the immutable explicit scope set used to filter scheduler/recovery queries. */
    public Set<CanteenScope> allowedScopes() {
        if (allScopes) {
            return Set.of();
        }
        return allowedScopes.stream()
                .map(value -> value.split("/", -1))
                .map(parts -> new CanteenScope(parts[0], parts[1]))
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Set<String> parseScopes(String configuredScopes) {
        if (configuredScopes == null || configuredScopes.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(configuredScopes.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    if (ALL_SCOPES.equals(value)) {
                        return ALL_SCOPES;
                    }
                    validateScopeEntry(value);
                    return value;
                })
                .collect(Collectors.toUnmodifiableSet());
    }

    private static void validateScopeEntry(String value) {
        String[] parts = value.split("/", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException(
                    "agent.runtime.scheduler.allowed-scopes must contain SCHOOL/CANTEEN entries");
        }
    }

    private static String key(CanteenScope scope) {
        if (scope == null) {
            throw new IllegalArgumentException("scope is required");
        }
        return scope.schoolId() + "/" + scope.canteenId();
    }
}
