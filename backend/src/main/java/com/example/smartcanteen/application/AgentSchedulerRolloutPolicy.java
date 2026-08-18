package com.example.smartcanteen.application;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.ForbiddenException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Explicit canteen allowlist and fail-closed rollout gate for the asynchronous Agent scheduler. */
@Component
public class AgentSchedulerRolloutPolicy {

    private final Set<String> allowedScopes;

    public AgentSchedulerRolloutPolicy(
            @Value("${agent.runtime.scheduler.enabled:false}") boolean enabled,
            @Value("${agent.runtime.scheduler.allowed-scopes:}") String configuredScopes) {
        Set<String> parsed = parseScopes(configuredScopes);
        if (enabled && parsed.isEmpty()) {
            throw new IllegalArgumentException(
                    "agent.runtime.scheduler.allowed-scopes is required when the scheduler is enabled");
        }
        this.allowedScopes = parsed;
    }

    public void requireEnabled(CanteenScope scope) {
        if (!allowedScopes.contains(key(scope))) {
            throw new ForbiddenException("Agent scheduler is disabled for this scope");
        }
    }

    /** Returns the immutable scope set used to filter scheduler/recovery queries. */
    public Set<CanteenScope> allowedScopes() {
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
