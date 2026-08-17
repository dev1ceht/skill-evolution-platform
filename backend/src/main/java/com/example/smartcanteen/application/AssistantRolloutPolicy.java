package com.example.smartcanteen.application;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.ForbiddenException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Kill switch and explicit canteen allowlist for the natural-language assistant pilot.
 *
 * <p>An enabled process still denies every scope unless the deployment explicitly lists a
 * canteen. This makes enabling the pilot fail closed for production rollout configuration.</p>
 */
@Component
public class AssistantRolloutPolicy {

    private final boolean enabled;
    private final Set<String> allowedScopes;

    public AssistantRolloutPolicy(
            @Value("${smart-canteen.assistant.enabled:false}") boolean enabled,
            @Value("${smart-canteen.assistant.allowed-scopes:}") String configuredScopes) {
        this.enabled = enabled;
        this.allowedScopes = parseScopes(configuredScopes);
    }

    public void requireEnabled(CanteenScope scope) {
        if (!enabled) {
            throw new ForbiddenException("Assistant pilot is disabled");
        }
        if (!allowedScopes.contains(key(scope))) {
            throw new ForbiddenException("Assistant pilot is disabled for this scope");
        }
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
                    "smart-canteen.assistant.allowed-scopes must contain SCHOOL/CANTEEN entries");
        }
    }

    private static String key(CanteenScope scope) {
        if (scope == null) {
            throw new IllegalArgumentException("scope is required");
        }
        return scope.schoolId() + "/" + scope.canteenId();
    }
}
