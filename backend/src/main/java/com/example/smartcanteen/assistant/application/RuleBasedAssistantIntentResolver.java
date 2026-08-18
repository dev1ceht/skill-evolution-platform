package com.example.smartcanteen.assistant.application;

import com.example.smartcanteen.assistant.domain.AssistantResolution;
import com.example.smartcanteen.assistant.domain.AssistantClarification;
import com.example.smartcanteen.assistant.domain.AssistantPendingAction;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic first-step resolver. A model Adapter can be introduced later without changing
 * the assistant conversation or execution seams.
 */
@Component
public class RuleBasedAssistantIntentResolver implements AssistantIntentResolver {

    private static final Pattern RESOURCE_ID = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])([A-Za-z0-9]+(?:[-_][A-Za-z0-9]+)+)(?![A-Za-z0-9])");
    private static final Pattern UNSUPPORTED_REQUEST = Pattern.compile(
            "采购|进货|订货|库存|预警|告警|台账|审批|报损|付款|入库|出库|采购计划",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("\\b20\\d{2}[-/.]\\d{1,2}[-/.]\\d{1,2}\\b");
    private static final Pattern SUPPLIER_ID = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])SUP(?:PLIER)?[-_][A-Za-z0-9_-]+(?![A-Za-z0-9])");
    private static final Pattern PURCHASE_ORDER_ID = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])PO[-_][A-Za-z0-9_-]+(?![A-Za-z0-9])");
    private static final Pattern PROCUREMENT_PLAN_ID = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])PLAN[-_][A-Za-z0-9_-]+(?![A-Za-z0-9])");
    private static final Pattern INGREDIENT_ID = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])(?:INGREDIENT|ING)[-_][A-Za-z0-9_-]+(?![A-Za-z0-9])");
    private static final Pattern QUANTITY = Pattern.compile(
            "(?i)([0-9]+(?:\\.[0-9]+)?)\\s*(kg|公斤|g|克|l|升|ml|毫升|count|个)");
    private static final Pattern ALERT_ID = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])(?:[A-Z_]+:[A-Za-z0-9_-]+|WARN[-_][A-Za-z0-9_-]+)(?![A-Za-z0-9])");

    @Override
    public AssistantResolution resolve(String message) {
        return resolveDirect(message);
    }

    @Override
    public AssistantResolution resolve(
            String message, Optional<AssistantClarification> pendingClarification) {
        AssistantResolution direct = resolveDirect(message);
        if (pendingClarification != null
                && pendingClarification.isPresent()
                && !isExplicitNewUnsupportedRequest(message)
                && isResourceOnly(message)) {
            return resolvePendingAnswer(message, pendingClarification.get()).orElse(direct);
        }
        if (direct.type() != AssistantResolution.Type.UNSUPPORTED
                || pendingClarification == null
                || pendingClarification.isEmpty()) {
            return direct;
        }
        if (isExplicitNewUnsupportedRequest(message)) {
            return direct;
        }
        return resolvePendingAnswer(message, pendingClarification.get()).orElse(direct);
    }

    @Override
    public AssistantResolution resolve(
            String message,
            Optional<AssistantClarification> pendingClarification,
            Optional<AssistantPendingAction> pendingAction) {
        Optional<AssistantPendingAction> pending = pendingAction == null
                ? Optional.empty()
                : pendingAction;
        if (pending.isPresent() && isConfirmation(message)) {
            return AssistantResolution.confirmPendingAction(pending.get().intent());
        }
        if (pending.isPresent() && isCancellation(message)) {
            return AssistantResolution.cancelPendingAction(pending.get().intent());
        }
        return resolve(message, pendingClarification);
    }

    static boolean isExplicitNewUnsupportedRequest(String message) {
        return message != null
                && UNSUPPORTED_REQUEST.matcher(message).find()
                && resolveExplicitWrite(message) == null;
    }

    private AssistantResolution resolveDirect(String message) {
        if (message == null || message.isBlank()) {
            return AssistantResolution.clarification(
                    "请告诉我你要查询的内容；当前可以先查询食品溯源或日菜单。", "message");
        }
        AssistantResolution write = resolveExplicitWrite(message);
        if (write != null) {
            return write;
        }
        if (isExplicitNewUnsupportedRequest(message)) {
            return AssistantResolution.unsupported(
                    "当前助手可以识别采购、库存、预警写入请求，但写入必须先经过灰度开关、范围校验和明确确认。"
                            + "请改用已开放的食品溯源或日菜单查询，或补充完整的业务参数。");
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        boolean traceabilityRequest = normalized.contains("溯源")
                || normalized.contains("追溯")
                || normalized.contains("traceability")
                || normalized.contains("trace");
        if (traceabilityRequest) {
            Matcher matcher = RESOURCE_ID.matcher(message.trim());
            while (matcher.find()) {
                String candidate = matcher.group(1).toUpperCase(Locale.ROOT);
                if (isTraceabilityId(candidate)) {
                    return AssistantResolution.traceability(candidate);
                }
            }
            return AssistantResolution.clarificationFor(
                    "traceability.query",
                    "请提供批次溯源码，例如 TRACE-001。", "traceCode");
        }
        boolean publishRequest = normalized.contains("发布")
                || normalized.contains("上线")
                || normalized.contains("publish");
        if (publishRequest) {
            Matcher matcher = RESOURCE_ID.matcher(message.trim());
            while (matcher.find()) {
                String candidate = matcher.group(1).toUpperCase(Locale.ROOT);
                if (isMenuId(candidate)) {
                    return AssistantResolution.menuPublish(candidate);
                }
            }
            return AssistantResolution.clarificationFor(
                    "menu.publish",
                    "请提供要发布的日菜单 ID，例如 MENU-001。", "menuId");
        }
        boolean menuRequest = normalized.contains("菜单")
                || normalized.contains("食谱")
                || normalized.contains("daily menu")
                || normalized.contains("menu");
        if (menuRequest) {
            Matcher matcher = RESOURCE_ID.matcher(message.trim());
            while (matcher.find()) {
                String candidate = matcher.group(1).toUpperCase(Locale.ROOT);
                if (isMenuId(candidate)) {
                    return AssistantResolution.menuQuery(candidate);
                }
            }
            return AssistantResolution.clarificationFor(
                    "menu.query",
                    "请提供日菜单 ID，例如 MENU-001。", "menuId");
        }
        return AssistantResolution.unsupported(
                "当前助手已开放食品溯源和日菜单只读查询。请说明“查询 TRACE-001 的溯源信息”"
                        + "或“查询 MENU-001 的菜单”，采购、库存和预警写入需要明确的灰度与确认。");
    }

    private Optional<AssistantResolution> resolvePendingAnswer(
            String message, AssistantClarification pending) {
        Matcher matcher = RESOURCE_ID.matcher(message == null ? "" : message.trim());
        while (matcher.find()) {
            String candidate = matcher.group(1).toUpperCase(Locale.ROOT);
            if ("traceability.query".equals(pending.intent()) && isTraceabilityId(candidate)) {
                return Optional.of(AssistantResolution.traceability(candidate));
            }
            if ("menu.query".equals(pending.intent()) && isMenuId(candidate)) {
                return Optional.of(AssistantResolution.menuQuery(candidate));
            }
            if ("menu.publish".equals(pending.intent()) && isMenuId(candidate)) {
                return Optional.of(AssistantResolution.menuPublish(candidate));
            }
        }
        if ("traceability.query".equals(pending.intent())) {
            return Optional.of(AssistantResolution.clarificationFor(
                    "traceability.query",
                    "请提供批次溯源码，例如 TRACE-001。", "traceCode"));
        }
        if ("menu.query".equals(pending.intent())) {
            return Optional.of(AssistantResolution.clarificationFor(
                    "menu.query",
                    "请提供日菜单 ID，例如 MENU-001。", "menuId"));
        }
        if ("menu.publish".equals(pending.intent())) {
            return Optional.of(AssistantResolution.clarificationFor(
                    "menu.publish",
                    "请提供要发布的日菜单 ID，例如 MENU-001。", "menuId"));
        }
        if (AssistantResolution.isWriteIntent(pending.intent())) {
            AssistantResolution combined = resolveExplicitWrite(
                    pending.originalMessage() + " " + (message == null ? "" : message));
            if (combined != null) {
                return Optional.of(combined);
            }
        }
        return Optional.empty();
    }

    private static boolean isTraceabilityId(String value) {
        return value.startsWith("TRACE-") || value.startsWith("TRACE_");
    }

    private static boolean isMenuId(String value) {
        return value.startsWith("MENU-") || value.startsWith("MENU_");
    }

    private static boolean isResourceOnly(String message) {
        return message != null && RESOURCE_ID.matcher(message.trim()).matches();
    }

    private static boolean isConfirmation(String message) {
        return message != null && Set.of(
                        "确认", "确认发布", "确认采购", "确认入库", "确认出库", "确认处置",
                        "执行", "同意", "confirm", "yes")
                .contains(message.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean isCancellation(String message) {
        return message != null && Set.of("取消", "取消发布", "不发布", "cancel", "no")
                .contains(message.trim().toLowerCase(Locale.ROOT));
    }

    /** Resolves only explicit, parameter-bearing write commands; vague requests remain unsupported. */
    private static AssistantResolution resolveExplicitWrite(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if ((normalized.contains("生成采购计划") || normalized.contains("创建采购计划"))
                && normalized.contains("采购")) {
            Map<String, String> parameters = new LinkedHashMap<>();
            Matcher dates = DATE.matcher(message);
            while (dates.find()) {
                String date = dates.group().replace('/', '-').replace('.', '-');
                if (!parameters.containsKey("periodStart")) {
                    parameters.put("periodStart", date);
                } else if (!parameters.containsKey("periodEnd")) {
                    parameters.put("periodEnd", date);
                }
            }
            if (!parameters.containsKey("periodStart") || !parameters.containsKey("periodEnd")) {
                String[] missing = parameters.containsKey("periodStart")
                        ? new String[] {"periodEnd"}
                        : new String[] {"periodStart", "periodEnd"};
                return AssistantResolution.clarificationFor(
                        "procurement.plan.generate",
                        "请提供采购计划周期，例如“生成采购计划 2026-08-18 至 2026-08-24”。",
                        missing);
            }
            return AssistantResolution.writeRequest(
                    "procurement.plan.generate", parameters, "已识别为采购计划生成请求，将先生成待确认计划。");
        }
        if (normalized.contains("创建采购订单") || normalized.contains("新建采购订单")) {
            Map<String, String> parameters = new LinkedHashMap<>();
            putFirst(parameters, "planId", PROCUREMENT_PLAN_ID, message);
            putFirst(parameters, "supplierId", SUPPLIER_ID, message);
            putFirst(parameters, "ingredientId", INGREDIENT_ID, message);
            putQuantity(parameters, message);
            putPrice(parameters, message);
            String missing = firstMissing(
                    parameters, "planId", "supplierId", "ingredientId", "quantity", "unit", "unitPrice");
            if (missing != null) {
                return AssistantResolution.clarificationFor(
                        "procurement.order.create",
                        "请补充已确认采购计划 ID、供应商、食材、数量、单位和含税单价。",
                        missing);
            }
            return AssistantResolution.writeRequest(
                    "procurement.order.create", parameters, "已识别为采购订单创建请求，将先生成待确认订单。");
        }
        if ((normalized.contains("采购订单") || normalized.contains("采购单"))
                && normalized.contains("收货")) {
            Map<String, String> parameters = new LinkedHashMap<>();
            putFirst(parameters, "orderId", PURCHASE_ORDER_ID, message);
            putFirst(parameters, "ingredientId", INGREDIENT_ID, message);
            putQuantity(parameters, message);
            putFirst(parameters, "batchNo", Pattern.compile(
                    "(?i)(?<![A-Za-z0-9])BATCH[-_][A-Za-z0-9_-]+(?![A-Za-z0-9])"), message);
            putPrice(parameters, message, "purchasePrice");
            String missing = firstMissing(parameters, "orderId", "ingredientId", "quantity", "unit", "purchasePrice");
            if (missing != null) {
                return AssistantResolution.clarificationFor(
                        "procurement.order.receive",
                        "请补充采购订单号、食材、数量、单位、批次采购价。",
                        missing);
            }
            return AssistantResolution.writeRequest(
                    "procurement.order.receive", parameters, "已识别为采购收货请求，将先生成待确认收货单。");
        }
        if (normalized.contains("入库") && !normalized.contains("采购订单")) {
            Map<String, String> parameters = new LinkedHashMap<>();
            putFirst(parameters, "materialId", INGREDIENT_ID, message);
            putFirst(parameters, "supplierId", SUPPLIER_ID, message);
            putQuantity(parameters, message);
            putFirst(parameters, "batchNo", Pattern.compile(
                    "(?i)(?<![A-Za-z0-9])BATCH[-_][A-Za-z0-9_-]+(?![A-Za-z0-9])"), message);
            putPrice(parameters, message, "purchasePrice");
            String missing = firstMissing(
                    parameters, "materialId", "supplierId", "quantity", "unit", "batchNo", "purchasePrice");
            if (missing != null) {
                return AssistantResolution.clarificationFor(
                        "inventory.receive",
                        "请补充入库食材、供应商、数量、单位、批次和采购价，例如 ING-001 供应商 SUP-001 2 kg 批次 BATCH-001 采购价 8。",
                        missing);
            }
            return AssistantResolution.writeRequest(
                    "inventory.receive", parameters, "已识别为库存入库请求，将先生成待确认入库单。");
        }
        if (normalized.contains("出库")) {
            Map<String, String> parameters = new LinkedHashMap<>();
            putFirst(parameters, "ingredientId", INGREDIENT_ID, message);
            putQuantity(parameters, message);
            Matcher reason = Pattern.compile("原因[:：]?\\s*([^，,。]+)").matcher(message);
            if (reason.find()) {
                parameters.put("reason", reason.group(1).trim());
            }
            String missing = firstMissing(parameters, "ingredientId", "quantity", "unit");
            if (missing != null) {
                return AssistantResolution.clarificationFor(
                        "inventory.stock-out", "请补充出库食材 ID、数量和单位，例如 ING-001 2 kg。", missing);
            }
            return AssistantResolution.writeRequest(
                    "inventory.stock-out", parameters, "已识别为库存出库请求，将先生成待确认出库单。");
        }
        if (normalized.contains("处置预警")
                || normalized.contains("处理预警")
                || normalized.contains("关闭预警")) {
            Map<String, String> parameters = new LinkedHashMap<>();
            putFirst(parameters, "warnId", ALERT_ID, message);
            Matcher content = Pattern.compile("(?:说明|原因)[:：]?\\s*([^，,。]+)").matcher(message);
            if (content.find()) {
                parameters.put("processContent", content.group(1).trim());
            }
            String missing = firstMissing(parameters, "warnId");
            if (missing != null) {
                return AssistantResolution.clarificationFor(
                        "alert.dispose", "请提供要处置的预警 ID，例如 WARN-001。", missing);
            }
            return AssistantResolution.writeRequest(
                    "alert.dispose", parameters, "已识别为预警处置请求，将先生成待确认处置记录。");
        }
        return null;
    }

    private static void putFirst(
            Map<String, String> parameters, String key, Pattern pattern, String message) {
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            parameters.put(key, matcher.group().toUpperCase(Locale.ROOT));
        }
    }

    private static void putQuantity(Map<String, String> parameters, String message) {
        Matcher matcher = QUANTITY.matcher(message);
        if (!matcher.find()) {
            return;
        }
        parameters.put("quantity", matcher.group(1));
        parameters.put("unit", normalizeUnit(matcher.group(2)));
    }

    private static void putPrice(Map<String, String> parameters, String message) {
        putPrice(parameters, message, "unitPrice");
    }

    private static void putPrice(
            Map<String, String> parameters, String message, String key) {
        Matcher matcher = Pattern.compile(
                "(?i)(?:单价|采购价|价格|price)[:：]?\\s*([0-9]+(?:\\.[0-9]+)?)").matcher(message);
        if (matcher.find()) {
            parameters.put(key, matcher.group(1));
        }
    }

    private static String firstMissing(Map<String, String> parameters, String... fields) {
        for (String field : fields) {
            if (!parameters.containsKey(field)) {
                return field;
            }
        }
        return null;
    }

    private static String normalizeUnit(String unit) {
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "公斤" -> "kg";
            case "克" -> "g";
            case "升" -> "l";
            case "毫升" -> "ml";
            case "个" -> "count";
            default -> unit.toLowerCase(Locale.ROOT);
        };
    }
}
