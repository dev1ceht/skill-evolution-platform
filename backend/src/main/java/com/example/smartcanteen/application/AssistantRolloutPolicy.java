package com.example.smartcanteen.application;

import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.ForbiddenException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Kill switch and canteen rollout policy for the natural-language assistant.
 *
 * <p>The {@code *} scope entry enables every canteen. Deployments can still provide an explicit
 * comma-separated list when they need a narrower rollout.</p>
 */
@Component
public class AssistantRolloutPolicy {

    private static final String ALL_SCOPES = "*";

    private final boolean enabled;
    private final boolean businessWritesEnabled;
    private final Set<String> allowedScopes;
    private final boolean allScopes;

    @Autowired
    public AssistantRolloutPolicy(
            @Value("${smart-canteen.assistant.enabled:true}") boolean enabled,
            @Value("${smart-canteen.assistant.allowed-scopes:}") String configuredScopes,
            @Value("${smart-canteen.assistant.business-writes-enabled:false}")
                    boolean businessWritesEnabled) {
        this.enabled = enabled;
        this.businessWritesEnabled = businessWritesEnabled;
        this.allowedScopes = parseScopes(configuredScopes);
        this.allScopes = allowedScopes.contains(ALL_SCOPES);
    }

    /** Convenience constructor used by focused unit tests and local adapters. */
    public AssistantRolloutPolicy(boolean enabled, String configuredScopes) {
        this(enabled, configuredScopes, true);
    }

    public void requireEnabled(CanteenScope scope) {
        if (!enabled) {
            throw new ForbiddenException("Assistant pilot is disabled");
        }
        if (!allScopes && !allowedScopes.contains(key(scope))) {
            throw new ForbiddenException("Assistant pilot is disabled for this scope");
        }
    }

    public void requireBusinessWrites() {
        if (!businessWritesEnabled) {
            throw new ForbiddenException(
                    "Assistant business writes are disabled; use the operational pages");
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
