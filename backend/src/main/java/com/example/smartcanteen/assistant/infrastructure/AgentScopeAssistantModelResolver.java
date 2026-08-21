package com.example.smartcanteen.assistant.infrastructure;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.assistant.domain.AssistantClarification;
import com.example.smartcanteen.assistant.domain.AssistantResolution;
import com.example.smartcanteen.assistant.domain.AssistantRoleContext;
import com.example.smartcanteen.assistant.port.AssistantModelResolver;
import com.example.smartcanteen.domain.MenuId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.formatter.DeepSeekFormatter;
import io.agentscope.harness.agent.HarnessAgent;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Optional AgentScope Java 2.0 classifier adapter.
 *
 * <p>It deliberately uses a HarnessAgent without filesystem, shell, memory, subagent or business
 * tools. The existing resolver router remains the safety boundary, and this adapter is only used
 * for read-only intent classification and clarification.</p>
 */
@Primary
@Component
@ConditionalOnProperty(
        name = "smart-canteen.assistant.model.provider",
        havingValue = "agentscope")
public class AgentScopeAssistantModelResolver implements AssistantModelResolver {

    private static final Logger log = LoggerFactory.getLogger(AgentScopeAssistantModelResolver.class);
    private static final int MAX_TIMEOUT_MS = 60_000;
    private static final int DEFAULT_MAX_RESPONSE_BYTES = 65_536;
    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是智慧食堂助手的只读意图分类器。只能输出一个 JSON 对象，不要 Markdown，不要解释。
            允许的 type 只有 TRACEABILITY_QUERY、MENU_QUERY、INVENTORY_QUERY、PROCUREMENT_GAP_QUERY、CLARIFICATION、UNSUPPORTED。
            TRACEABILITY_QUERY 必须包含 intent=traceability.query 和 TRACE- 或 TRACE_ 开头的 traceCode。
            MENU_QUERY 必须包含 intent=menu.query，并且二选一：只含短格式 M001 或 MABC123 的 menuId，或 ISO 日期格式的 menuDate；只有日期查询可选 mealTime，且只能是 BREAKFAST、LUNCH、DINNER、SNACK。
            INVENTORY_QUERY 必须包含 intent=inventory.query；可选 keyword 文本和 warningOnly 布尔值，只表示库存只读筛选。
            PROCUREMENT_GAP_QUERY 必须包含 intent=procurement.gap.query、ISO 日期格式的 menuDate；可选 mealTime，表示按已发布菜单和库存事实进行只读原料缺口分析。
            CLARIFICATION 可以包含 intent、missingFields 数组和 message。
            不得输出任何写入、发布、确认、采购、库存调整或支付动作。
            """;

    private final HarnessAgent agent;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final int maxRequestChars;
    private final int timeoutMs;
    private final int maxResponseBytes;

    @Autowired
    public AgentScopeAssistantModelResolver(
            ObjectMapper objectMapper,
            @Value("${smart-canteen.assistant.model.enabled:true}") boolean enabled,
            @Value("${smart-canteen.assistant.model.api-key:}") String apiKey,
            @Value("${smart-canteen.assistant.model.name:deepseek-v4-flash}") String modelName,
            @Value("${smart-canteen.assistant.model.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${smart-canteen.assistant.model.allowed-hosts:api.deepseek.com}") String allowedHosts,
            @Value("${smart-canteen.assistant.model.max-request-chars:2000}") int maxRequestChars,
            @Value("${smart-canteen.assistant.model.timeout-ms:10000}") int timeoutMs,
            @Value("${smart-canteen.assistant.model.max-response-bytes:65536}") int maxResponseBytes) {
        this(
                buildAgent(enabled, apiKey, modelName, baseUrl, allowedHosts),
                objectMapper,
                enabled && apiKey != null && !apiKey.isBlank(),
                validateMaxRequestChars(maxRequestChars),
                validateTimeoutMs(timeoutMs),
                validateMaxResponseBytes(maxResponseBytes));
    }

    public AgentScopeAssistantModelResolver(
            HarnessAgent agent,
            ObjectMapper objectMapper,
            boolean enabled,
            int maxRequestChars,
            int timeoutMs) {
        this(agent, objectMapper, enabled, maxRequestChars, timeoutMs, DEFAULT_MAX_RESPONSE_BYTES);
    }

    public AgentScopeAssistantModelResolver(
            HarnessAgent agent,
            ObjectMapper objectMapper,
            boolean enabled,
            int maxRequestChars,
            int timeoutMs,
            int maxResponseBytes) {
        this.agent = agent;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.maxRequestChars = validateMaxRequestChars(maxRequestChars);
        this.timeoutMs = validateTimeoutMs(timeoutMs);
        this.maxResponseBytes = validateMaxResponseBytes(maxResponseBytes);
    }

    @Override
    public Optional<AssistantResolution> resolve(
            String message, Optional<AssistantClarification> pendingClarification) {
        return resolve(message, pendingClarification, null);
    }

    @Override
    public Optional<AssistantResolution> resolve(
            String message,
            Optional<AssistantClarification> pendingClarification,
            ExecutionContext context) {
        if (!enabled
                || agent == null
                || message == null
                || message.isBlank()
                || message.length() > maxRequestChars) {
            return Optional.empty();
        }
        String requestId = context == null ? "anonymous" : context.requestId();
        try {
            AssistantRoleContext roleContext = context == null
                    ? null
                    : AssistantRoleContext.from(context);
            RuntimeContext.Builder runtimeBuilder = RuntimeContext.builder()
                    .sessionId(sessionId(context))
                    .userId(context == null ? "anonymous" : context.actorUserId());
            if (roleContext != null) {
                runtimeBuilder.put("assistantRoleContext", roleContext);
            }
            RuntimeContext runtimeContext = runtimeBuilder.build();
            Msg response = agent.call(prompt(message, pendingClarification, roleContext), runtimeContext)
                    .block(Duration.ofMillis(timeoutMs));
            if (response == null || response.getTextContent() == null) {
                return Optional.empty();
            }
            String text = response.getTextContent().trim();
            if (text.getBytes(StandardCharsets.UTF_8).length > maxResponseBytes) {
                return Optional.empty();
            }
            return parseResponse(text);
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn(
                    "AgentScope assistant model unavailable; provider=agentscope requestId={}",
                    requestId,
                    exception);
            return Optional.empty();
        }
    }

    private static HarnessAgent buildAgent(
            boolean enabled,
            String apiKey,
            String modelName,
            String baseUrl,
            String allowedHosts) {
        if (!enabled || apiKey == null || apiKey.isBlank()) {
            return null;
        }
        OpenAIChatModel model = OpenAIChatModel.builder()
                .apiKey(apiKey.trim())
                .modelName(requireText("modelName", modelName))
                .baseUrl(normalizeBaseUrl(baseUrl, allowedHosts))
                .endpointPath("/chat/completions")
                .formatter(new DeepSeekFormatter())
                .stream(false)
                .nativeStructuredOutput(false)
                .nativeStructuredOutputWithTools(false)
                .build();
        return HarnessAgent.builder()
                .name("smart-canteen-intent-classifier")
                .sysPrompt(DEFAULT_SYSTEM_PROMPT)
                .model(model)
                .maxIters(2)
                .disableFilesystemTools()
                .disableShellTool()
                .disableMemoryTools()
                .disableMemoryHooks()
                .disableSessionPersistence()
                .disableWorkspaceContext()
                .disableSubagents()
                .disableDynamicSkills()
                .disableDefaultWorkspaceSkills()
                .disableToolsConfig()
                .build();
    }

    private String prompt(
            String message,
            Optional<AssistantClarification> pendingClarification,
            AssistantRoleContext roleContext) {
        StringBuilder prompt = new StringBuilder(message.trim());
        if (roleContext != null) {
            prompt.append("\n服务端角色上下文：").append(roleContext.promptSummary());
        }
        if (pendingClarification != null && pendingClarification.isPresent()) {
            AssistantClarification pending = pendingClarification.get();
            prompt.append("\n待补充意图：").append(pending.intent());
            prompt.append("\n待补充字段：").append(String.join(",", pending.missingFields()));
        }
        return prompt.toString();
    }

    private static String sessionId(ExecutionContext context) {
        return context == null
                ? "assistant-classifier"
                : "assistant-classifier-" + context.requestId();
    }

    private Optional<AssistantResolution> parseResponse(String response)
            throws JsonProcessingException {
        JsonNode result = objectMapper.readTree(stripMarkdownFence(response));
        if (result.isTextual()) {
            result = objectMapper.readTree(result.asText());
        }
        if (result == null || !result.isObject()) {
            return Optional.empty();
        }
        String type = result.path("type").asText("").trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "TRACEABILITY_QUERY" -> traceability(result);
            case "MENU_QUERY" -> menu(result);
            case "INVENTORY_QUERY" -> inventory(result);
            case "PROCUREMENT_GAP_QUERY" -> procurementGap(result);
            case "CLARIFICATION" -> clarification(result);
            case "UNSUPPORTED" -> Optional.of(AssistantResolution.unsupported(
                    result.path("message").asText("模型未能确定请求").trim()));
            default -> Optional.empty();
        };
    }

    private static Optional<AssistantResolution> traceability(JsonNode result) {
        String traceCode = normalizeIdentifier(result.path("traceCode").asText(null), "TRACE-");
        return traceCode == null
                ? Optional.empty()
                : Optional.of(AssistantResolution.traceability(traceCode));
    }

    private static Optional<AssistantResolution> menu(JsonNode result) {
        String menuId = MenuId.normalizeOrNull(result.path("menuId").asText(null));
        String menuDate = result.path("menuDate").asText(null);
        String mealTime = result.path("mealTime").asText(null);
        if (menuId != null) {
            if ((menuDate != null && !menuDate.isBlank())
                    || (mealTime != null && !mealTime.isBlank())) {
                return Optional.empty();
            }
            return Optional.of(AssistantResolution.menuQuery(menuId));
        }
        if (menuDate == null || menuDate.isBlank()) {
            return Optional.empty();
        }
        try {
            LocalDate date = LocalDate.parse(menuDate.trim());
            return Optional.of(AssistantResolution.menuQueryByDate(date, mealTime));
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static Optional<AssistantResolution> inventory(JsonNode result) {
        JsonNode keywordNode = result.get("keyword");
        if (keywordNode != null && !keywordNode.isNull() && !keywordNode.isTextual()) {
            return Optional.empty();
        }
        JsonNode warningNode = result.get("warningOnly");
        if (warningNode != null && !warningNode.isNull() && !warningNode.isBoolean()) {
            return Optional.empty();
        }
        String keyword = keywordNode == null || keywordNode.isNull()
                ? null : keywordNode.asText().trim();
        boolean warningOnly = warningNode != null
                && !warningNode.isNull()
                && warningNode.asBoolean();
        return Optional.of(AssistantResolution.inventoryQuery(keyword, warningOnly));
    }

    private static Optional<AssistantResolution> procurementGap(JsonNode result) {
        String menuDate = result.path("menuDate").asText(null);
        if (menuDate == null || menuDate.isBlank()) {
            return Optional.empty();
        }
        String mealTime = result.path("mealTime").asText(null);
        try {
            return Optional.of(AssistantResolution.procurementGapQuery(
                    LocalDate.parse(menuDate.trim()), mealTime));
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static Optional<AssistantResolution> clarification(JsonNode result) {
        String intent = result.path("intent").isTextual()
                ? result.path("intent").asText().trim()
                : null;
        String message = result.path("message").asText("请补充必要业务参数。").trim();
        List<String> missing = new ArrayList<>();
        JsonNode fields = result.path("missingFields");
        if (fields.isArray()) {
            fields.forEach(field -> {
                if (field.isTextual() && !field.asText().isBlank()) {
                    missing.add(field.asText().trim());
                }
            });
        }
        return Optional.of(AssistantResolution.clarificationFor(
                intent, message, missing.toArray(String[]::new)));
    }

    private static String normalizeIdentifier(String value, String prefix) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith(prefix)
                        || normalized.startsWith(prefix.replace('-', '_'))
                ? normalized
                : null;
    }

    private static String stripMarkdownFence(String value) {
        String normalized = value.trim();
        if (normalized.startsWith("```") && normalized.endsWith("```")) {
            int firstLine = normalized.indexOf('\n');
            return firstLine < 0
                    ? normalized.substring(3, normalized.length() - 3).trim()
                    : normalized.substring(firstLine + 1, normalized.length() - 3).trim();
        }
        return normalized;
    }

    private static String normalizeBaseUrl(String baseUrl, String allowedHosts) {
        String normalized = requireText("baseUrl", baseUrl);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        URI uri = URI.create(normalized);
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || !isAllowedHost(uri.getHost(), allowedHosts)) {
            throw new IllegalArgumentException("assistant model base URL is not allowed");
        }
        return normalized;
    }

    private static boolean isAllowedHost(String host, String allowedHosts) {
        if (allowedHosts == null || allowedHosts.isBlank()) {
            return false;
        }
        for (String allowed : allowedHosts.split(",")) {
            if (host.equalsIgnoreCase(allowed.trim())) {
                return true;
            }
        }
        return false;
    }

    private static String requireText(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static int validateTimeoutMs(int timeoutMs) {
        if (timeoutMs < 100 || timeoutMs > MAX_TIMEOUT_MS) {
            throw new IllegalArgumentException("assistant model timeout must be between 100 and 60000 ms");
        }
        return timeoutMs;
    }

    private static int validateMaxRequestChars(int maxRequestChars) {
        if (maxRequestChars < 128 || maxRequestChars > 8_000) {
            throw new IllegalArgumentException("assistant model request limit must be between 128 and 8000 characters");
        }
        return maxRequestChars;
    }

    private static int validateMaxResponseBytes(int maxResponseBytes) {
        if (maxResponseBytes < 1_024 || maxResponseBytes > MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException(
                    "assistant model response limit must be between 1024 and 1048576 bytes");
        }
        return maxResponseBytes;
    }
}
