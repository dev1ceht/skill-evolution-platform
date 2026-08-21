package com.example.smartcanteen.assistant.domain;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Structured result of the first deterministic assistant intent resolver. */
public record AssistantResolution(
        Type type,
        String intent,
        String traceCode,
        String menuId,
        List<String> missingFields,
        String message,
        Map<String, String> parameters) {

    private static final Set<String> WRITE_INTENTS = Set.of(
            "procurement.plan.generate",
            "procurement.order.create",
            "procurement.order.receive",
            "inventory.receive",
            "inventory.stock-out",
            "alert.dispose");

    public enum Type {
        TRACEABILITY_QUERY,
        MENU_QUERY,
        MENU_PUBLISH_REQUEST,
        WRITE_REQUEST,
        CONFIRM_PENDING_ACTION,
        CANCEL_PENDING_ACTION,
        CLARIFICATION,
        UNSUPPORTED
    }

    public AssistantResolution {
        Objects.requireNonNull(type, "type");
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
        Objects.requireNonNull(message, "message");
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
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
            boolean hasMenuId = menuId != null && !menuId.isBlank();
            boolean hasMenuDate = parameters.containsKey("menuDate")
                    && parameters.get("menuDate") != null
                    && !parameters.get("menuDate").isBlank();
            if (!hasMenuId && !hasMenuDate) {
                throw new IllegalArgumentException(
                        "Menu resolution requires menuId or menuDate");
            }
            if (hasMenuId && hasMenuDate) {
                throw new IllegalArgumentException(
                        "Menu resolution cannot contain both menuId and menuDate");
            }
            if (hasMenuId && parameters.containsKey("mealTime")
                    && parameters.get("mealTime") != null
                    && !parameters.get("mealTime").isBlank()) {
                throw new IllegalArgumentException(
                        "Menu resolution cannot contain menuId and mealTime");
            }
            if (hasMenuDate) {
                validateMenuDate(parameters.get("menuDate"));
                validateMealTime(parameters.get("mealTime"));
            }
            if (traceCode != null) {
                throw new IllegalArgumentException("Menu resolution cannot contain traceCode");
            }
        } else if (type == Type.MENU_PUBLISH_REQUEST) {
            if (!"menu.publish".equals(intent)) {
                throw new IllegalArgumentException(
                        "Menu publish resolution must select menu.publish");
            }
            if (menuId == null || menuId.isBlank()) {
                throw new IllegalArgumentException("Menu publish resolution requires menuId");
            }
            if (traceCode != null) {
                throw new IllegalArgumentException("Menu publish resolution cannot contain traceCode");
            }
        } else if (type == Type.CONFIRM_PENDING_ACTION || type == Type.CANCEL_PENDING_ACTION) {
            if (!"menu.publish".equals(intent) && !WRITE_INTENTS.contains(intent)) {
                throw new IllegalArgumentException(
                        "Pending action resolution must select a supported write intent");
            }
            if (traceCode != null || menuId != null) {
                throw new IllegalArgumentException(
                        "Pending action resolution cannot contain a resource identifier");
            }
        } else if (type == Type.WRITE_REQUEST) {
            if (!WRITE_INTENTS.contains(intent)) {
                throw new IllegalArgumentException("Unsupported write intent: " + intent);
            }
            if (traceCode != null || menuId != null) {
                throw new IllegalArgumentException("Write resolution cannot contain menu or trace identifiers");
            }
            if (parameters.isEmpty()) {
                throw new IllegalArgumentException("Write resolution requires parameters");
            }
        } else if (traceCode != null || menuId != null) {
            throw new IllegalArgumentException(
                    "Only business resolutions may contain a resource identifier");
        } else if (intent != null
                && !intent.equals("traceability.query")
                && !intent.equals("menu.query")
                && !intent.equals("menu.publish")
                && !WRITE_INTENTS.contains(intent)) {
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
                "已识别为食品溯源查询。",
                Map.of());
    }

    public static AssistantResolution menuQuery(String menuId) {
        return new AssistantResolution(
                Type.MENU_QUERY,
                "menu.query",
                null,
                menuId,
                List.of(),
                "已识别为日菜单查询。",
                Map.of());
    }

    public static AssistantResolution menuQueryByDate(LocalDate menuDate, String mealTime) {
        Objects.requireNonNull(menuDate, "menuDate");
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("menuDate", menuDate.toString());
        if (mealTime != null && !mealTime.isBlank()) {
            parameters.put("mealTime", mealTime.trim().toUpperCase(Locale.ROOT));
        }
        return new AssistantResolution(
                Type.MENU_QUERY,
                "menu.query",
                null,
                null,
                List.of(),
                "已识别为按日期的日菜单查询。",
                parameters);
    }

    public static AssistantResolution menuPublish(String menuId) {
        return new AssistantResolution(
                Type.MENU_PUBLISH_REQUEST,
                "menu.publish",
                null,
                menuId,
                List.of(),
                "已识别为菜单发布请求。",
                Map.of());
    }

    public static AssistantResolution writeRequest(
            String intent, Map<String, String> parameters, String message) {
        return new AssistantResolution(
                Type.WRITE_REQUEST,
                intent,
                null,
                null,
                List.of(),
                message,
                parameters);
    }

    public static AssistantResolution confirmPendingAction(String intent) {
        return new AssistantResolution(
                Type.CONFIRM_PENDING_ACTION,
                intent,
                null,
                null,
                List.of(),
                "已确认执行待处理操作。",
                Map.of());
    }

    public static AssistantResolution cancelPendingAction(String intent) {
        return new AssistantResolution(
                Type.CANCEL_PENDING_ACTION,
                intent,
                null,
                null,
                List.of(),
                "已取消待处理操作。",
                Map.of());
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
                message,
                Map.of());
    }

    public static AssistantResolution unsupported(String message) {
        return new AssistantResolution(
                Type.UNSUPPORTED, null, null, null, List.of(), message, Map.of());
    }

    public boolean isWriteRequest() {
        return type == Type.WRITE_REQUEST || WRITE_INTENTS.contains(intent);
    }

    public static boolean isWriteIntent(String intent) {
        return WRITE_INTENTS.contains(intent);
    }

    private static void validateMenuDate(String value) {
        try {
            LocalDate parsed = LocalDate.parse(value);
            if (!parsed.toString().equals(value)) {
                throw new IllegalArgumentException("menuDate must be YYYY-MM-DD");
            }
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("menuDate must be YYYY-MM-DD", exception);
        }
    }

    private static void validateMealTime(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported mealTime: " + value);
        }
    }
}
