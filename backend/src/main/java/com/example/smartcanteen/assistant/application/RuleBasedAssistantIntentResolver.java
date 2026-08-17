package com.example.smartcanteen.assistant.application;

import com.example.smartcanteen.assistant.domain.AssistantResolution;
import com.example.smartcanteen.assistant.domain.AssistantClarification;
import java.util.Locale;
import java.util.Optional;
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

    @Override
    public AssistantResolution resolve(String message) {
        return resolveDirect(message);
    }

    @Override
    public AssistantResolution resolve(
            String message, Optional<AssistantClarification> pendingClarification) {
        AssistantResolution direct = resolveDirect(message);
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

    static boolean isExplicitNewUnsupportedRequest(String message) {
        return message != null && UNSUPPORTED_REQUEST.matcher(message).find();
    }

    private AssistantResolution resolveDirect(String message) {
        if (message == null || message.isBlank()) {
            return AssistantResolution.clarification(
                    "请告诉我你要查询的内容；当前可以先查询食品溯源或日菜单。", "message");
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
                        + "或“查询 MENU-001 的菜单”，采购和预警助手将在后续阶段开放。");
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
        return Optional.empty();
    }

    private static boolean isTraceabilityId(String value) {
        return value.startsWith("TRACE-") || value.startsWith("TRACE_");
    }

    private static boolean isMenuId(String value) {
        return value.startsWith("MENU-") || value.startsWith("MENU_");
    }
}
