package com.example.smartcanteen.assistant.application;

import com.example.smartcanteen.assistant.domain.AssistantResolution;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Deterministic first-step resolver. A model Adapter can be introduced later without changing
 * the assistant conversation or execution seams.
 */
@Component
public class RuleBasedAssistantIntentResolver implements AssistantIntentResolver {

    private static final Pattern TRACE_CODE = Pattern.compile(
            "(?i)(?<![A-Za-z0-9])([A-Za-z0-9]+(?:[-_][A-Za-z0-9]+)+)(?![A-Za-z0-9])");

    @Override
    public AssistantResolution resolve(String message) {
        if (message == null || message.isBlank()) {
            return AssistantResolution.clarification(
                    "请告诉我你要查询的内容；当前可以先查询食品溯源。", "message");
        }
        String normalized = message.trim().toLowerCase(Locale.ROOT);
        boolean traceabilityRequest = normalized.contains("溯源")
                || normalized.contains("追溯")
                || normalized.contains("traceability")
                || normalized.contains("trace");
        if (traceabilityRequest) {
            Matcher matcher = TRACE_CODE.matcher(message.trim());
            if (matcher.find()) {
                return AssistantResolution.traceability(matcher.group(1).toUpperCase(Locale.ROOT));
            }
            return AssistantResolution.clarification(
                    "请提供批次溯源码，例如 TRACE-001。", "traceCode");
        }
        return AssistantResolution.unsupported(
                "当前助手已开放食品溯源查询。请说明“查询 TRACE-001 的溯源信息”，"
                        + "采购、菜单和预警助手将在后续阶段开放。");
    }
}
