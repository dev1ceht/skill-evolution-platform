package com.example.smartcanteen.assistant.domain;

import java.util.List;
import java.util.Objects;

/** Structured result of the first deterministic assistant intent resolver. */
public record AssistantResolution(
        Type type,
        String intent,
        String traceCode,
        String menuId,
        List<String> missingFields,
        String message) {

    public enum Type {
        TRACEABILITY_QUERY,
        MENU_QUERY,
        CLARIFICATION,
        UNSUPPORTED
    }

    public AssistantResolution {
        Objects.requireNonNull(type, "type");
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        Objects.requireNonNull(message, "message");
        if (type == Type.TRACEABILITY_QUERY) {
            if (!"traceability.query".equals(intent)) {
                throw new IllegalArgumentException(
                        "Traceability resolution must select traceability.query");
            }
            if (traceCode == null || traceCode.isBlank()) {
                throw new IllegalArgumentException("Traceability resolution requires traceCode");
            }
            if (menuId != null) {
                throw new IllegalArgumentException("Traceability resolution cannot contain menuId");
            }
        } else if (type == Type.MENU_QUERY) {
            if (!"menu.query".equals(intent)) {
                throw new IllegalArgumentException("Menu resolution must select menu.query");
            }
            if (menuId == null || menuId.isBlank()) {
                throw new IllegalArgumentException("Menu resolution requires menuId");
            }
            if (traceCode != null) {
                throw new IllegalArgumentException("Menu resolution cannot contain traceCode");
            }
        } else if (traceCode != null || menuId != null) {
            throw new IllegalArgumentException(
                    "Only business resolutions may contain a resource identifier");
        } else if (intent != null
                && !intent.equals("traceability.query")
                && !intent.equals("menu.query")) {
            throw new IllegalArgumentException("Unsupported clarification intent: " + intent);
        }
    }

    public static AssistantResolution traceability(String traceCode) {
        return new AssistantResolution(
                Type.TRACEABILITY_QUERY,
                "traceability.query",
                traceCode,
                null,
                List.of(),
                "已识别为食品溯源查询。");
    }

    public static AssistantResolution menuQuery(String menuId) {
        return new AssistantResolution(
                Type.MENU_QUERY,
                "menu.query",
                null,
                menuId,
                List.of(),
                "已识别为日菜单查询。");
    }

    public static AssistantResolution clarification(String message, String... missingFields) {
        return clarification(null, message, missingFields);
    }

    public static AssistantResolution clarificationFor(
            String intent, String message, String... missingFields) {
        return clarification(intent, message, missingFields);
    }

    private static AssistantResolution clarification(
            String intent, String message, String... missingFields) {
        return new AssistantResolution(
                Type.CLARIFICATION,
                intent,
                null,
                null,
                List.of(missingFields),
                message);
    }

    public static AssistantResolution unsupported(String message) {
        return new AssistantResolution(Type.UNSUPPORTED, null, null, null, List.of(), message);
    }
}
