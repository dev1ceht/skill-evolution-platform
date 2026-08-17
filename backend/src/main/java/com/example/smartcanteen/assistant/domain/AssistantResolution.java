package com.example.smartcanteen.assistant.domain;

import java.util.List;
import java.util.Objects;

/** Structured result of the first deterministic assistant intent resolver. */
public record AssistantResolution(
        Type type,
        String intent,
        String traceCode,
        List<String> missingFields,
        String message) {

    public enum Type {
        TRACEABILITY_QUERY,
        CLARIFICATION,
        UNSUPPORTED
    }

    public AssistantResolution {
        Objects.requireNonNull(type, "type");
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        Objects.requireNonNull(message, "message");
        if (type == Type.TRACEABILITY_QUERY) {
            if (!"traceability.query".equals(intent)) {
                throw new IllegalArgumentException("Traceability resolution must select traceability.query");
            }
            if (traceCode == null || traceCode.isBlank()) {
                throw new IllegalArgumentException("Traceability resolution requires traceCode");
            }
        }
        if (type != Type.TRACEABILITY_QUERY && traceCode != null) {
            throw new IllegalArgumentException("Only traceability resolution may contain traceCode");
        }
    }

    public static AssistantResolution traceability(String traceCode) {
        return new AssistantResolution(
                Type.TRACEABILITY_QUERY,
                "traceability.query",
                traceCode,
                List.of(),
                "已识别为食品溯源查询。");
    }

    public static AssistantResolution clarification(String message, String... missingFields) {
        return new AssistantResolution(
                Type.CLARIFICATION,
                null,
                null,
                List.of(missingFields),
                message);
    }

    public static AssistantResolution unsupported(String message) {
        return new AssistantResolution(Type.UNSUPPORTED, null, null, List.of(), message);
    }
}
