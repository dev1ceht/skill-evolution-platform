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
        INVENTORY_QUERY,
        PROCUREMENT_GAP_QUERY,
        TRAFFIC_FORECAST_QUERY,
        MEAL_PLAN_QUERY,
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
        } else if (type == Type.INVENTORY_QUERY) {
            if (!"inventory.query".equals(intent)) {
                throw new IllegalArgumentException(
                        "Inventory resolution must select inventory.query");
            }
            if (traceCode != null || menuId != null) {
                throw new IllegalArgumentException(
                        "Inventory resolution cannot contain menu or trace identifiers");
            }
            validateInventoryParameters(parameters);
        } else if (type == Type.PROCUREMENT_GAP_QUERY) {
            if (!"procurement.gap.query".equals(intent)) {
                throw new IllegalArgumentException(
                        "Procurement gap resolution must select procurement.gap.query");
            }
            if (traceCode != null || menuId != null) {
                throw new IllegalArgumentException(
                        "Procurement gap resolution cannot contain menu or trace identifiers");
            }
            validateProcurementGapParameters(parameters);
        } else if (type == Type.TRAFFIC_FORECAST_QUERY) {
            if (!"traffic.forecast.query".equals(intent)) {
                throw new IllegalArgumentException(
                        "Traffic forecast resolution must select traffic.forecast.query");
            }
            if (traceCode != null || menuId != null) {
                throw new IllegalArgumentException(
                        "Traffic forecast resolution cannot contain resource identifiers");
            }
            validateTrafficForecastParameters(parameters);
        } else if (type == Type.MEAL_PLAN_QUERY) {
            if (!"meal_plan.query".equals(intent)) {
                throw new IllegalArgumentException(
                        "Meal plan resolution must select meal_plan.query");
            }
            if (traceCode != null || menuId != null) {
                throw new IllegalArgumentException(
                        "Meal plan resolution cannot contain resource identifiers");
            }
            validateMealPlanParameters(parameters);
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
                && !intent.equals("inventory.query")
                && !intent.equals("procurement.gap.query")
                && !intent.equals("traffic.forecast.query")
                && !intent.equals("meal_plan.query")
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

    public static AssistantResolution inventoryQuery() {
        return inventoryQuery(null, null);
    }

    public static AssistantResolution inventoryQuery(String keyword, boolean warningOnly) {
        return inventoryQuery(keyword, Boolean.valueOf(warningOnly));
    }

    private static AssistantResolution inventoryQuery(String keyword, Boolean warningOnly) {
        Map<String, String> parameters = new LinkedHashMap<>();
        if (keyword != null && !keyword.isBlank()) {
            parameters.put("keyword", keyword.trim());
            parameters.put("warningOnly", String.valueOf(Boolean.TRUE.equals(warningOnly)));
        } else if (Boolean.TRUE.equals(warningOnly)) {
            parameters.put("warningOnly", "true");
        }
        return new AssistantResolution(
                Type.INVENTORY_QUERY,
                "inventory.query",
                null,
                null,
                List.of(),
                "已识别为库存只读查询。",
                parameters);
    }

    public static AssistantResolution procurementGapQuery(LocalDate menuDate, String mealTime) {
        Objects.requireNonNull(menuDate, "menuDate");
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("menuDate", menuDate.toString());
        if (mealTime != null && !mealTime.isBlank()) {
            parameters.put("mealTime", mealTime.trim().toUpperCase(Locale.ROOT));
        }
        return new AssistantResolution(
                Type.PROCUREMENT_GAP_QUERY,
                "procurement.gap.query",
                null,
                null,
                List.of(),
                "已识别为菜单原料缺口只读分析。",
                parameters);
    }

    public static AssistantResolution trafficForecastQuery(
            LocalDate forecastDate, String mealTime) {
        Objects.requireNonNull(forecastDate, "forecastDate");
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("forecastDate", forecastDate.toString());
        parameters.put("mealTime", requireMealTime(mealTime));
        return new AssistantResolution(
                Type.TRAFFIC_FORECAST_QUERY,
                "traffic.forecast.query",
                null,
                null,
                List.of(),
                "已识别为客流预测只读查询。",
                parameters);
    }

    public static AssistantResolution mealPlanQuery(LocalDate menuDate, String mealTime) {
        Objects.requireNonNull(menuDate, "menuDate");
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("menuDate", menuDate.toString());
        parameters.put("mealTime", requireMealTime(mealTime));
        return new AssistantResolution(
                Type.MEAL_PLAN_QUERY,
                "meal_plan.query",
                null,
                null,
                List.of(),
                "已识别为备餐建议只读分析。",
                parameters);
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

    private static void validateInventoryParameters(Map<String, String> parameters) {
        String keyword = parameters.get("keyword");
        if (keyword != null && keyword.isBlank()) {
            throw new IllegalArgumentException("Inventory query keyword must not be blank");
        }
        if (keyword != null && keyword.length() > 100) {
            throw new IllegalArgumentException("Inventory query keyword must be at most 100 characters");
        }
        String warningOnly = parameters.get("warningOnly");
        if (warningOnly != null
                && !warningOnly.equalsIgnoreCase("true")
                && !warningOnly.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException("Inventory query warningOnly must be boolean");
        }
        for (String key : parameters.keySet()) {
            if (!Set.of("keyword", "warningOnly").contains(key)) {
                throw new IllegalArgumentException("Unsupported inventory query parameter: " + key);
            }
        }
    }

    private static void validateProcurementGapParameters(Map<String, String> parameters) {
        String menuDate = parameters.get("menuDate");
        if (menuDate == null || menuDate.isBlank()) {
            throw new IllegalArgumentException("Procurement gap resolution requires menuDate");
        }
        validateMenuDate(menuDate);
        validateMealTime(parameters.get("mealTime"));
        for (String key : parameters.keySet()) {
            if (!Set.of("menuDate", "mealTime").contains(key)) {
                throw new IllegalArgumentException(
                        "Unsupported procurement gap query parameter: " + key);
            }
        }
    }

    private static void validateTrafficForecastParameters(Map<String, String> parameters) {
        String forecastDate = parameters.get("forecastDate");
        if (forecastDate == null || forecastDate.isBlank()) {
            throw new IllegalArgumentException("Traffic forecast resolution requires forecastDate");
        }
        validateMenuDate(forecastDate);
        requireMealTime(parameters.get("mealTime"));
        for (String key : parameters.keySet()) {
            if (!Set.of("forecastDate", "mealTime").contains(key)) {
                throw new IllegalArgumentException(
                        "Unsupported traffic forecast query parameter: " + key);
            }
        }
    }

    private static void validateMealPlanParameters(Map<String, String> parameters) {
        String menuDate = parameters.get("menuDate");
        if (menuDate == null || menuDate.isBlank()) {
            throw new IllegalArgumentException("Meal plan resolution requires menuDate");
        }
        validateMenuDate(menuDate);
        requireMealTime(parameters.get("mealTime"));
        for (String key : parameters.keySet()) {
            if (!Set.of("menuDate", "mealTime").contains(key)) {
                throw new IllegalArgumentException(
                        "Unsupported meal plan query parameter: " + key);
            }
        }
    }

    private static String requireMealTime(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("mealTime is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported mealTime: " + value);
        }
        return normalized;
    }
}
