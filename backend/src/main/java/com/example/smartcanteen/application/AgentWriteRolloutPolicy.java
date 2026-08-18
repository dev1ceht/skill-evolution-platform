package com.example.smartcanteen.application;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.ForbiddenException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fail-closed rollout gate for Agent Skills that can change business state.
 *
 * <p>A write Skill is only executable when the deployment explicitly enables the pilot, lists
 * the exact school/canteen scopes, and lists the exact intents. Read-only Skills are unaffected.
 * Keeping this gate beside Runtime means HTTP, assistant and worker execution share the same
 * kill switch.</p>
 */
@Component
public class AgentWriteRolloutPolicy {

    private final boolean enabled;
    private final Set<String> allowedScopes;
    private final Set<String> allowedIntents;

    public AgentWriteRolloutPolicy(
            @Value("${agent.write.enabled:false}") boolean enabled,
            @Value("${agent.write.allowed-scopes:}") String configuredScopes,
            @Value("${agent.write.allowed-intents:}") String configuredIntents) {
        this.enabled = enabled;
        this.allowedScopes = parseScopes(configuredScopes);
        this.allowedIntents = parseValues(configuredIntents);
    }

    /** A fail-closed policy for non-Spring callers and focused unit tests. */
    public static AgentWriteRolloutPolicy disabled() {
        return new AgentWriteRolloutPolicy(false, "", "");
    }

    public void requireEnabled(CanteenScope scope, String intent) {
        if (!enabled) {
            throw new ForbiddenException("Agent write pilot is disabled");
        }
        if (scope == null) {
            throw new IllegalArgumentException("scope is required");
        }
        if (!allowedScopes.contains(key(scope))) {
            throw new ForbiddenException("Agent write pilot is disabled for this scope");
        }
        if (intent == null || intent.isBlank() || !allowedIntents.contains(intent)) {
            throw new ForbiddenException("Agent write pilot is disabled for this intent");
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

    private static Set<String> parseValues(String configuredValues) {
        if (configuredValues == null || configuredValues.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(configuredValues.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static void validateScopeEntry(String value) {
        String[] parts = value.split("/", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException(
                    "agent.write.allowed-scopes must contain SCHOOL/CANTEEN entries");
        }
    }

    private static String key(CanteenScope scope) {
        return scope.schoolId() + "/" + scope.canteenId();
    }
}
