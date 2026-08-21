package com.example.smartcanteen.assistant.application;

import com.example.smartcanteen.assistant.domain.AssistantResolution;
import com.example.smartcanteen.assistant.domain.AssistantClarification;
import com.example.smartcanteen.assistant.domain.AssistantPendingAction;
import com.example.smartcanteen.domain.MenuId;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
    private static final Pattern DATE = Pattern.compile("\\b\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}\\b");
    private static final Pattern SUPPLIER_ID = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])SUP(?:PLIER)?[-_][A-Za-z0-9_-]+(?![A-Za-z0-9])");
    private static final Pattern PURCHASE_ORDER_ID = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])PO[-_][A-Za-z0-9_-]+(?![A-Za-z0-9])");
    private static final Pattern MEAL_ORDER_ID = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])MEAL[-_][A-Za-z0-9_-]+(?![A-Za-z0-9])");
    private static final Pattern MEAL_ORDER_ITEM = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])(DISH[-_][A-Za-z0-9_-]+)(?:\\s*(?:x|×|\\*)\\s*(\\d+))?(?:\\s*份)?(?![A-Za-z0-9])");
    private static final Pattern PROCUREMENT_PLAN_ID = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])PLAN[-_][A-Za-z0-9_-]+(?![A-Za-z0-9])");
    private static final Pattern INGREDIENT_ID = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])(?:INGREDIENT|ING)[-_][A-Za-z0-9_-]+(?![A-Za-z0-9])");
    private static final Pattern QUANTITY = Pattern.compile(
            "(?i)([0-9]+(?:\\.[0-9]+)?)\\s*(kg|公斤|g|克|l|升|ml|毫升|count|个)");
    private static final Pattern ALERT_ID = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])(?:[A-Z_]+:[A-Za-z0-9_-]+|WARN[-_][A-Za-z0-9_-]+)(?![A-Za-z0-9])");
    private static final Pattern INVENTORY_KEYWORD = Pattern.compile(
            "(?:查询|查看|查|请问)?\\s*([\\p{IsHan}A-Za-z0-9_-]{2,30}?)(?=(?:库存|还剩|剩余|剩下|够用))");

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
                && !isInventoryReadRequest(message)
                && !isMealOrderReadRequest(message)
                && !isProcurementGapReadRequest(message)
                && !isTrafficForecastReadRequest(message)
                && !isMealPrepReadRequest(message)
                && resolveExplicitWrite(message) == null;
    }

    private AssistantResolution resolveDirect(String message) {
        if (message == null || message.isBlank()) {
            return AssistantResolution.clarification(
                    "请告诉我你要查询的内容；当前可以查询菜单、库存、原料缺口、客流预测和备餐建议。",
                    "message");
        }
        AssistantResolution write = resolveExplicitWrite(message);
        if (write != null) {
            return write;
        }
        if (isMealOrderReadRequest(message)) {
            return AssistantResolution.mealOrderQuery();
        }
        if (isInventoryReadRequest(message)) {
            boolean warningOnly = isWarningOnlyInventoryRequest(message);
            String keyword = findInventoryKeyword(message).orElse(null);
            if (keyword == null) {
                keyword = findInventoryKeywordId(message).orElse(null);
            }
            return keyword == null && !warningOnly
                    ? AssistantResolution.inventoryQuery()
                    : AssistantResolution.inventoryQuery(keyword, warningOnly);
        }
        if (isProcurementGapReadRequest(message)) {
            Optional<LocalDate> menuDate = findMenuDate(message);
            if (menuDate.isEmpty()) {
                return AssistantResolution.clarificationFor(
                        "procurement.gap.query",
                        "请提供要检查的菜单日期，例如“明天的菜单有没有原材料不足”。",
                        "menuDate");
            }
            return AssistantResolution.procurementGapQuery(
                    menuDate.get(), findMealTime(message).orElse(null));
        }
        if (isMealPrepReadRequest(message)) {
            Optional<LocalDate> menuDate = findMenuDate(message);
            if (menuDate.isEmpty()) {
                return AssistantResolution.clarificationFor(
                        "meal_plan.query",
                        "请提供要分析的备餐日期，例如“明天午餐应该备多少份”。",
                        "menuDate");
            }
            Optional<String> mealTime = findMealTime(message);
            if (mealTime.isEmpty()) {
                return AssistantResolution.clarificationFor(
                        "meal_plan.query",
                        "请提供备餐餐次，例如早餐、午餐或晚餐。",
                        "mealTime");
            }
            return AssistantResolution.mealPlanQuery(
                    menuDate.get(), mealTime.get());
        }
        if (isTrafficForecastReadRequest(message)) {
            Optional<LocalDate> forecastDate = findMenuDate(message);
            if (forecastDate.isEmpty()) {
                return AssistantResolution.clarificationFor(
                        "traffic.forecast.query",
                        "请提供要查询的客流预测日期，例如“明天午餐预计有多少人”。",
                        "forecastDate");
            }
            Optional<String> mealTime = findMealTime(message);
            if (mealTime.isEmpty()) {
                return AssistantResolution.clarificationFor(
                        "traffic.forecast.query",
                        "请提供预测餐次，例如早餐、午餐或晚餐。",
                        "mealTime");
            }
            return AssistantResolution.trafficForecastQuery(forecastDate.get(), mealTime.get());
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
            Optional<String> menuId = findMenuId(message);
            if (menuId.isPresent()) {
                return AssistantResolution.menuPublish(menuId.get());
            }
            return AssistantResolution.clarificationFor(
                    "menu.publish",
                    "请提供要发布的日菜单 ID，例如 M001。", "menuId");
        }
        boolean menuRequest = normalized.contains("菜单")
                || normalized.contains("食谱")
                || normalized.contains("有什么菜")
                || normalized.contains("吃什么")
                || normalized.contains("daily menu")
                || normalized.contains("menu");
        if (menuRequest) {
            Optional<String> menuId = findMenuId(message);
            if (menuId.isPresent()) {
                return AssistantResolution.menuQuery(menuId.get());
            }
            Optional<LocalDate> menuDate = findMenuDate(message);
            if (menuDate.isPresent()) {
                return AssistantResolution.menuQueryByDate(
                        menuDate.get(), findMealTime(message).orElse(null));
            }
            return AssistantResolution.clarificationFor(
                    "menu.query",
                    "请提供菜单日期，例如“今天的菜单”或“2026-08-17 的菜单”。", "menuDate");
        }
        return AssistantResolution.unsupported(
                "当前助手已开放食品溯源、日菜单、库存、原料缺口、客流预测和备餐建议只读查询。"
                        + "请补充日期、餐次或资源编号；采购、库存和预警写入需要明确的灰度与确认。");
    }

    private Optional<AssistantResolution> resolvePendingAnswer(
            String message, AssistantClarification pending) {
        if ("menu.query".equals(pending.intent()) || "menu.publish".equals(pending.intent())) {
            Optional<String> menuId = findMenuId(message);
            if (menuId.isPresent()) {
                return Optional.of("menu.query".equals(pending.intent())
                        ? AssistantResolution.menuQuery(menuId.get())
                        : AssistantResolution.menuPublish(menuId.get()));
            }
            if ("menu.query".equals(pending.intent())) {
                Optional<LocalDate> menuDate = findMenuDate(message);
                if (menuDate.isPresent()) {
                    return Optional.of(AssistantResolution.menuQueryByDate(
                            menuDate.get(), findMealTime(message).orElse(null)));
                }
            }
        }
        Matcher matcher = RESOURCE_ID.matcher(message == null ? "" : message.trim());
        while (matcher.find()) {
            String candidate = matcher.group(1).toUpperCase(Locale.ROOT);
            if ("traceability.query".equals(pending.intent()) && isTraceabilityId(candidate)) {
                return Optional.of(AssistantResolution.traceability(candidate));
            }
            if ("menu.query".equals(pending.intent()) && MenuId.isValid(candidate)) {
                return Optional.of(AssistantResolution.menuQuery(candidate));
            }
            if ("menu.publish".equals(pending.intent()) && MenuId.isValid(candidate)) {
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
                    "请提供菜单日期，例如“今天的菜单”或“2026-08-17 的菜单”。", "menuDate"));
        }
        if ("procurement.gap.query".equals(pending.intent())) {
            Optional<LocalDate> menuDate = findMenuDate(message);
            if (menuDate.isPresent()) {
                return Optional.of(AssistantResolution.procurementGapQuery(
                        menuDate.get(), findMealTime(message).orElse(null)));
            }
            return Optional.of(AssistantResolution.clarificationFor(
                    "procurement.gap.query",
                    "请提供要检查的菜单日期，例如“明天的菜单有没有原材料不足”。",
                    "menuDate"));
        }
        if ("meal_plan.query".equals(pending.intent())) {
            String mergedMessage = mergeClarificationMessage(pending, message);
            Optional<LocalDate> menuDate = findMenuDate(mergedMessage);
            Optional<String> mealTime = findMealTime(mergedMessage);
            if (menuDate.isPresent() && mealTime.isPresent()) {
                return Optional.of(AssistantResolution.mealPlanQuery(
                        menuDate.get(), mealTime.get()));
            }
            return Optional.of(AssistantResolution.clarificationFor(
                    "meal_plan.query",
                    menuDate.isEmpty()
                            ? "请提供要分析的备餐日期，例如“明天午餐应该备多少份”。"
                            : "请提供备餐餐次，例如早餐、午餐或晚餐。",
                    menuDate.isEmpty() ? "menuDate" : "mealTime"));
        }
        if ("traffic.forecast.query".equals(pending.intent())) {
            String mergedMessage = mergeClarificationMessage(pending, message);
            Optional<LocalDate> forecastDate = findMenuDate(mergedMessage);
            Optional<String> mealTime = findMealTime(mergedMessage);
            if (forecastDate.isPresent() && mealTime.isPresent()) {
                return Optional.of(AssistantResolution.trafficForecastQuery(
                        forecastDate.get(), mealTime.get()));
            }
            if (forecastDate.isEmpty()) {
                return Optional.of(AssistantResolution.clarificationFor(
                        "traffic.forecast.query",
                        "请提供要查询的客流预测日期，例如“明天午餐预计有多少人”。",
                        "forecastDate"));
            }
            return Optional.of(AssistantResolution.clarificationFor(
                    "traffic.forecast.query",
                    "请提供预测餐次，例如早餐、午餐或晚餐。",
                    "mealTime"));
        }
        if ("menu.publish".equals(pending.intent())) {
            return Optional.of(AssistantResolution.clarificationFor(
                    "menu.publish",
                    "请提供要发布的日菜单 ID，例如 M001。", "menuId"));
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

    private static String mergeClarificationMessage(
            AssistantClarification pending, String answer) {
        return pending.originalMessage() + " " + (answer == null ? "" : answer);
    }

    private static boolean isTraceabilityId(String value) {
        return value.startsWith("TRACE-") || value.startsWith("TRACE_");
    }

    private static Optional<String> findMenuId(String message) {
        return MenuId.findIn(message);
    }

    private static boolean isInventoryReadRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("库存入库")
                || normalized.contains("库存出库")
                || normalized.contains("库存调整")
                || normalized.contains("库存盘点")) {
            return false;
        }
        return normalized.contains("库存")
                || normalized.contains("还剩")
                || normalized.contains("剩余")
                || normalized.contains("剩下")
                || normalized.contains("低库存")
                || normalized.contains("库存不足")
                || normalized.contains("库存预警")
                || normalized.contains("够用几天");
    }

    private static boolean isMealOrderReadRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("采购订单") || normalized.contains("采购单")) {
            return false;
        }
        return normalized.contains("我的订单")
                || normalized.contains("订单记录")
                || normalized.contains("订单状态")
                || normalized.contains("查订单")
                || normalized.contains("查看订单")
                || normalized.contains("meal_order.query");
    }

    private static boolean isProcurementGapReadRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        boolean material = normalized.contains("原材料")
                || normalized.contains("原料")
                || normalized.contains("食材");
        boolean shortage = normalized.contains("不足")
                || normalized.contains("缺口")
                || normalized.contains("缺料")
                || normalized.contains("不够")
                || normalized.contains("够不够");
        boolean planningContext = normalized.contains("菜单")
                || normalized.contains("采购")
                || normalized.contains("明天")
                || normalized.contains("明日")
                || normalized.contains("tomorrow");
        return material && shortage && planningContext;
    }

    private static boolean isMealPrepReadRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("备餐")
                || normalized.contains("备菜")
                || normalized.contains("备多少")
                || normalized.contains("准备多少份")
                || normalized.contains("meal prep")
                || normalized.contains("meal_plan");
    }

    private static boolean isTrafficForecastReadRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        boolean traffic = normalized.contains("客流")
                || normalized.contains("人流")
                || normalized.contains("用餐人数")
                || normalized.contains("预计有多少人")
                || normalized.contains("预计人数")
                || normalized.contains("traffic")
                || normalized.contains("forecast");
        boolean forecastContext = normalized.contains("预测")
                || normalized.contains("预计")
                || normalized.contains("明天")
                || normalized.contains("明日")
                || normalized.contains("tomorrow");
        return traffic && forecastContext && !isMealPrepReadRequest(message);
    }

    private static boolean isWarningOnlyInventoryRequest(String message) {
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        return normalized.contains("不足")
                || normalized.contains("低库存")
                || normalized.contains("库存预警")
                || normalized.contains("库存告警")
                || normalized.contains("低于阈值");
    }

    private static Optional<String> findInventoryKeyword(String message) {
        Matcher matcher = INVENTORY_KEYWORD.matcher(message == null ? "" : message.trim());
        if (!matcher.find()) {
            return Optional.empty();
        }
        String keyword = matcher.group(1).trim()
                .replaceFirst("^(查询|查看|查|请问)", "")
                .trim();
        return keyword.isBlank() || keyword.equals("哪些食材") || keyword.equals("食材")
                ? Optional.empty()
                : Optional.of(keyword);
    }

    private static Optional<String> findInventoryKeywordId(String message) {
        Matcher matcher = INGREDIENT_ID.matcher(message == null ? "" : message);
        return matcher.find()
                ? Optional.of(matcher.group().toUpperCase(Locale.ROOT))
                : Optional.empty();
    }

    private static Optional<LocalDate> findMenuDate(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = DATE.matcher(message);
        if (matcher.find()) {
            String candidate = matcher.group().replace('/', '-').replace('.', '-');
            return parseDate(candidate);
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("今天")
                || normalized.contains("今日")
                || normalized.contains("today")) {
            return Optional.of(LocalDate.now());
        }
        if (normalized.contains("明天")
                || normalized.contains("明日")
                || normalized.contains("tomorrow")) {
            return Optional.of(LocalDate.now().plusDays(1));
        }
        return Optional.empty();
    }

    private static Optional<String> findMealTime(String message) {
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("早餐")
                || normalized.contains("早饭")
                || normalized.contains("早上")
                || normalized.contains("breakfast")) {
            return Optional.of("BREAKFAST");
        }
        if (normalized.contains("午餐")
                || normalized.contains("午饭")
                || normalized.contains("中餐")
                || normalized.contains("中午")
                || normalized.contains("lunch")) {
            return Optional.of("LUNCH");
        }
        if (normalized.contains("晚餐")
                || normalized.contains("晚饭")
                || normalized.contains("晚上")
                || normalized.contains("dinner")) {
            return Optional.of("DINNER");
        }
        if (normalized.contains("加餐") || normalized.contains("snack")) {
            return Optional.of("SNACK");
        }
        return Optional.empty();
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
        if (isMealOrderCreateRequest(normalized)) {
            Map<String, String> parameters = new LinkedHashMap<>();
            findMenuId(message).ifPresent(menuId -> parameters.put("menuId", menuId));
            findMenuDate(message).ifPresent(date -> parameters.put("menuDate", date.toString()));
            findMealTime(message).ifPresent(mealTime -> parameters.put("mealTime", mealTime));
            String items = mealOrderItemsJson(message);
            if (!parameters.containsKey("menuId") && !parameters.containsKey("menuDate")) {
                return AssistantResolution.clarificationFor(
                        "meal_order.create",
                        "请提供菜单 ID 或日期，例如“帮我订 M822 的 DISH-001”。",
                        "menuId");
            }
            if (items == null) {
                return AssistantResolution.clarificationFor(
                        "meal_order.create",
                        "请提供要订购的菜品 ID，例如“DISH-001 x1”；当前先支持按菜品 ID 下单。",
                        "items");
            }
            parameters.put("items", items);
            return AssistantResolution.writeRequest(
                    "meal_order.create", parameters, "已识别为消费订单创建请求，将先生成待确认订单。");
        }
        if (isMealOrderCancelRequest(normalized)) {
            Map<String, String> parameters = new LinkedHashMap<>();
            Matcher matcher = MEAL_ORDER_ID.matcher(message);
            if (matcher.find()) {
                parameters.put("orderId", matcher.group());
            }
            if (!parameters.containsKey("orderId")) {
                return AssistantResolution.clarificationFor(
                        "meal_order.cancel",
                        "请提供要取消的消费订单号，例如“取消 MEAL-001”。",
                        "orderId");
            }
            return AssistantResolution.writeRequest(
                    "meal_order.cancel", parameters, "已识别为消费订单取消请求，将先生成待确认操作。");
        }
        boolean procurementDraft = isProcurementDraftRequest(normalized);
        boolean procurementPlan = normalized.contains("生成采购计划")
                || normalized.contains("创建采购计划");
        if (procurementPlan || procurementDraft) {
            Map<String, String> parameters = new LinkedHashMap<>();
            Matcher dates = DATE.matcher(message);
            while (dates.find()) {
                String date = dates.group().replace('/', '-').replace('.', '-');
                if (parseDate(date).isEmpty()) {
                    return AssistantResolution.clarificationFor(
                            "procurement.plan.generate",
                            "请提供有效的采购草稿日期，例如“2026-08-22”。",
                            "periodStart", "periodEnd");
                }
                if (!parameters.containsKey("periodStart")) {
                    parameters.put("periodStart", date);
                } else if (!parameters.containsKey("periodEnd")) {
                    parameters.put("periodEnd", date);
                }
            }
            if (procurementDraft) {
                findMenuDate(message).ifPresent(date -> {
                    parameters.putIfAbsent("periodStart", date.toString());
                    parameters.putIfAbsent("periodEnd", parameters.get("periodStart"));
                });
            }
            if (!parameters.containsKey("periodStart") || !parameters.containsKey("periodEnd")) {
                String[] missing = parameters.containsKey("periodStart")
                        ? new String[] {"periodEnd"}
                        : new String[] {"periodStart", "periodEnd"};
                return AssistantResolution.clarificationFor(
                        "procurement.plan.generate",
                        procurementDraft
                                ? "请提供采购草稿日期，例如“生成明天的采购申请草稿”或“采购 Draft 2026-08-22”。"
                                : "请提供采购计划周期，例如“生成采购计划 2026-08-18 至 2026-08-24”。",
                        missing);
            }
            return AssistantResolution.writeRequest(
                    "procurement.plan.generate",
                    parameters,
                    procurementDraft
                            ? "已识别为采购申请 Draft 请求，将先生成待确认草稿。"
                            : "已识别为采购计划生成请求，将先生成待确认计划。");
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

    private static boolean isMealOrderCreateRequest(String normalized) {
        return normalized.contains("点餐")
                || normalized.contains("订餐")
                || normalized.contains("下单")
                || normalized.contains("帮我订")
                || normalized.contains("创建消费订单")
                || normalized.contains("meal_order.create");
    }

    private static boolean isMealOrderCancelRequest(String normalized) {
        return (normalized.contains("取消订单")
                || normalized.contains("取消消费订单")
                || (normalized.contains("取消") && normalized.contains("我的订单"))
                || (normalized.contains("取消") && MEAL_ORDER_ID.matcher(normalized).find())
                || normalized.contains("meal_order.cancel"))
                && !normalized.contains("采购订单");
    }

    private static String mealOrderItemsJson(String message) {
        Matcher matcher = MEAL_ORDER_ITEM.matcher(message == null ? "" : message);
        StringBuilder items = new StringBuilder("[");
        int count = 0;
        while (matcher.find()) {
            if (count > 0) {
                items.append(',');
            }
            String dishId = matcher.group(1).toUpperCase(Locale.ROOT);
            String quantity = matcher.group(2) == null ? "1" : matcher.group(2);
            items.append("{\"dishId\":\"")
                    .append(dishId)
                    .append("\",\"quantity\":")
                    .append(quantity)
                    .append('}');
            count++;
        }
        return count == 0 ? null : items.append(']').toString();
    }

    private static boolean isProcurementDraftRequest(String normalized) {
        boolean draftMention = normalized.contains("采购申请")
                || normalized.contains("采购草稿")
                || normalized.contains("采购 draft")
                || normalized.contains("采购draft")
                || normalized.contains("采购申请 draft")
                || normalized.contains("采购申请draft")
                || normalized.contains("purchase draft");
        boolean explicitCreate = normalized.contains("生成")
                || normalized.contains("创建")
                || normalized.contains("generate")
                || normalized.contains("create");
        boolean bareDatedDraft = (normalized.contains("draft") || normalized.contains("草稿"))
                && DATE.matcher(normalized).find();
        boolean readOnlyMention = normalized.contains("查看")
                || normalized.contains("查询")
                || normalized.contains("查一下")
                || normalized.contains("状态")
                || normalized.contains("详情")
                || normalized.contains("有没有")
                || normalized.contains("是否");
        boolean negated = normalized.contains("不要")
                || normalized.contains("别")
                || normalized.contains("不生成")
                || normalized.contains("不创建")
                || normalized.contains("无需")
                || normalized.contains("不需要")
                || normalized.contains("取消");
        return draftMention && (explicitCreate || bareDatedDraft) && !readOnlyMention && !negated
                && !normalized.contains("订单");
    }

    private static Optional<LocalDate> parseDate(String candidate) {
        try {
            return Optional.of(LocalDate.parse(candidate));
        } catch (DateTimeParseException ignored) {
            return Optional.empty();
        }
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
