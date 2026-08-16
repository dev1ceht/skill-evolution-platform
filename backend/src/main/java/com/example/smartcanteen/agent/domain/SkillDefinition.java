package com.example.smartcanteen.agent.domain;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable runtime projection of one SOP manifest entry. */
public record SkillDefinition(
        String id,
        String version,
        String status,
        String riskLevel,
        String approval,
        String scope,
        List<String> triggers,
        List<String> permissions,
        List<String> steps,
        RuntimePolicy runtime,
        String manifestDigest) {

    public SkillDefinition {
        requireText("id", id);
        requireText("version", version);
        requireText("status", status);
        requireText("riskLevel", riskLevel);
        requireText("approval", approval);
        requireText("scope", scope);
        triggers = immutableList(triggers);
        permissions = immutableList(permissions);
        steps = immutableList(steps);
        if (triggers.isEmpty()) {
            throw new IllegalArgumentException("triggers must not be empty");
        }
        if (permissions.isEmpty()) {
            throw new IllegalArgumentException("permissions must not be empty");
        }
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("steps must not be empty");
        }
        requireText("manifestDigest", manifestDigest);
    }

    public boolean isAvailable() {
        return "implemented".equals(status)
                && runtime != null
                && "active".equals(runtime.activation());
    }

    public boolean supportsIntent(String intent) {
        return runtime != null && intent != null && runtime.intents().contains(intent);
    }

    private static List<String> immutableList(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static void requireText(String name, String value) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    public record RuntimePolicy(
            List<String> intents,
            String inputSchema,
            String outputSchema,
            List<String> tools,
            String sideEffect,
            String runConfirmation,
            String domainApproval,
            String activation,
            long deadlineMs,
            String retryPolicy,
            String evidence) {

        public RuntimePolicy {
            intents = immutableRequiredList("intents", intents);
            tools = immutableRequiredList("tools", tools);
            requireText("inputSchema", inputSchema);
            requireText("outputSchema", outputSchema);
            requireText("sideEffect", sideEffect);
            requireText("runConfirmation", runConfirmation);
            requireText("domainApproval", domainApproval);
            requireText("activation", activation);
            requireText("retryPolicy", retryPolicy);
            requireText("evidence", evidence);
            if (!Set.of("read", "write").contains(sideEffect)) {
                throw new IllegalArgumentException("sideEffect must be read or write");
            }
            if (deadlineMs <= 0) {
                throw new IllegalArgumentException("deadlineMs must be positive");
            }
        }

        private static List<String> immutableRequiredList(String name, List<String> values) {
            List<String> copy = values == null ? List.of() : List.copyOf(values);
            if (copy.isEmpty()) {
                throw new IllegalArgumentException(name + " must not be empty");
            }
            return copy;
        }

        private static void requireText(String name, String value) {
            Objects.requireNonNull(value, name);
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
        }
    }
}
