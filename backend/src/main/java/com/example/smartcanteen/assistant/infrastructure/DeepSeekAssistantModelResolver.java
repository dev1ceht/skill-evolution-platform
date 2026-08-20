package com.example.smartcanteen.assistant.infrastructure;

import com.example.smartcanteen.assistant.domain.AssistantClarification;
import com.example.smartcanteen.assistant.domain.AssistantResolution;
import com.example.smartcanteen.assistant.port.AssistantModelResolver;
import com.example.smartcanteen.domain.MenuId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * DeepSeek adapter for the OpenAI-compatible chat-completions endpoint.
 *
 * <p>The model is used only as a fallback classifier. It may return read-only resolutions or a
 * clarification, while write requests and pending-action decisions remain deterministic and are
 * rejected by the router's safety policy.</p>
 */
@Primary
@Component
public class DeepSeekAssistantModelResolver implements AssistantModelResolver {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekAssistantModelResolver.class);
    private static final int DEFAULT_MAX_RESPONSE_BYTES = 65_536;
    private static final int DEFAULT_MAX_REQUEST_CHARS = 2_000;
    private static final int MAX_TIMEOUT_MS = 60_000;
    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是智慧食堂助手的意图分类器。只能输出一个 JSON 对象，不要 Markdown，不要解释。
            允许的 type 只有 TRACEABILITY_QUERY、MENU_QUERY、CLARIFICATION、UNSUPPORTED。
            TRACEABILITY_QUERY 必须包含 intent=traceability.query 和 TRACE- 或 TRACE_ 开头的 traceCode。
            MENU_QUERY 必须包含 intent=menu.query 和短格式 M001 或 MABC123 的 menuId。
            CLARIFICATION 可以包含 intent、missingFields 数组和 message。
            不得输出 WRITE_REQUEST、MENU_PUBLISH_REQUEST、CONFIRM_PENDING_ACTION 或 CANCEL_PENDING_ACTION。
            JSON 示例：{"type":"TRACEABILITY_QUERY","intent":"traceability.query","traceCode":"TRACE-001"}
            """;

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String modelName;
    private final String apiKey;
    private final boolean enabled;
    private final int maxRequestChars;
    private final int maxResponseBytes;

    public DeepSeekAssistantModelResolver(
            RestClient client,
            ObjectMapper objectMapper,
            String modelName,
            String apiKey,
            boolean enabled) {
        this(
                client,
                objectMapper,
                modelName,
                apiKey,
                enabled,
                DEFAULT_MAX_REQUEST_CHARS,
                DEFAULT_MAX_RESPONSE_BYTES);
    }

    private DeepSeekAssistantModelResolver(
            RestClient client,
            ObjectMapper objectMapper,
            String modelName,
            String apiKey,
            boolean enabled,
            int maxRequestChars,
            int maxResponseBytes) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.modelName = modelName == null ? "" : modelName.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.enabled = enabled;
        this.maxRequestChars = validateMaxRequestChars(maxRequestChars);
        this.maxResponseBytes = validateMaxResponseBytes(maxResponseBytes);
    }

    @Autowired
    public DeepSeekAssistantModelResolver(
            RestClient.Builder clientBuilder,
            ObjectMapper objectMapper,
            @Value("${smart-canteen.assistant.model.base-url:https://api.deepseek.com}") String baseUrl,
            @Value("${smart-canteen.assistant.model.name:deepseek-v4-flash}") String modelName,
            @Value("${smart-canteen.assistant.model.api-key:}") String apiKey,
            @Value("${smart-canteen.assistant.model.enabled:true}") boolean enabled,
            @Value("${smart-canteen.assistant.model.max-request-chars:2000}") int maxRequestChars,
            @Value("${smart-canteen.assistant.model.timeout-ms:10000}") int timeoutMs,
            @Value("${smart-canteen.assistant.model.max-response-bytes:65536}") int maxResponseBytes,
            @Value("${smart-canteen.assistant.model.allowed-hosts:api.deepseek.com}") String allowedHosts) {
        this(
                configureClient(clientBuilder, baseUrl, timeoutMs, allowedHosts).build(),
                objectMapper,
                modelName,
                apiKey,
                enabled,
                maxRequestChars,
                maxResponseBytes);
    }

    @Override
    public Optional<AssistantResolution> resolve(
            String message, Optional<AssistantClarification> pendingClarification) {
        if (!enabled
                || apiKey.isBlank()
                || modelName.isBlank()
                || message == null
                || message.isBlank()
                || message.length() > maxRequestChars) {
            return Optional.empty();
        }
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("model", modelName);
            request.put("messages", messages(message, pendingClarification));
            request.put("response_format", Map.of("type", "json_object"));
            request.put("thinking", Map.of("type", "disabled"));
            request.put("temperature", 0);
            request.put("max_tokens", 512);
            request.put("stream", false);

            return client.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(request)
                    .exchange((requestSpec, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            return Optional.empty();
                        }
                        try (InputStream body = response.getBody()) {
                            byte[] bytes = body.readNBytes(maxResponseBytes + 1);
                            if (bytes.length > maxResponseBytes) {
                                log.warn("DeepSeek assistant model response exceeded configured limit");
                                return Optional.empty();
                            }
                            return parseResponse(new String(bytes, StandardCharsets.UTF_8));
                        }
                    });
        } catch (RestClientException | IllegalArgumentException exception) {
            log.warn("DeepSeek assistant model unavailable; using deterministic fallback");
            return Optional.empty();
        }
    }

    private List<Map<String, String>> messages(
            String message, Optional<AssistantClarification> pendingClarification) {
        StringBuilder user = new StringBuilder(message.trim());
        if (pendingClarification != null && pendingClarification.isPresent()) {
            AssistantClarification pending = pendingClarification.get();
            user.append("\n待补充意图：").append(pending.intent());
            user.append("\n待补充字段：").append(String.join(",", pending.missingFields()));
        }
        return List.of(
                Map.of("role", "system", "content", DEFAULT_SYSTEM_PROMPT),
                Map.of("role", "user", "content", user.toString()));
    }

    private Optional<AssistantResolution> parseResponse(String response) throws JsonProcessingException {
        if (response == null || response.isBlank()) {
            return Optional.empty();
        }
        JsonNode envelope = objectMapper.readTree(response);
        String content = envelope.path("choices").path(0).path("message").path("content").asText(null);
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }
        JsonNode result = objectMapper.readTree(stripMarkdownFence(content));
        if (result.isTextual()) {
            result = objectMapper.readTree(result.asText());
        }
        return toResolution(result);
    }

    private Optional<AssistantResolution> toResolution(JsonNode result) {
        if (result == null || !result.isObject()) {
            return Optional.empty();
        }
        String type = result.path("type").asText("").trim().toUpperCase(Locale.ROOT);
        String message = result.path("message").asText("模型未能确定请求").trim();
        return switch (type) {
            case "TRACEABILITY_QUERY" -> traceability(result);
            case "MENU_QUERY" -> menu(result);
            case "CLARIFICATION" -> clarification(result, message);
            case "UNSUPPORTED" -> Optional.of(AssistantResolution.unsupported(message));
            default -> Optional.empty();
        };
    }

    private Optional<AssistantResolution> traceability(JsonNode result) {
        String traceCode = normalizedIdentifier(result.path("traceCode").asText(null), "TRACE-");
        return traceCode == null
                ? Optional.empty()
                : Optional.of(AssistantResolution.traceability(traceCode));
    }

    private Optional<AssistantResolution> menu(JsonNode result) {
        String menuId = normalizedShortMenuId(result.path("menuId").asText(null));
        return menuId == null ? Optional.empty() : Optional.of(AssistantResolution.menuQuery(menuId));
    }

    private Optional<AssistantResolution> clarification(JsonNode result, String message) {
        String intent = result.path("intent").isTextual()
                ? result.path("intent").asText().trim()
                : null;
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
                intent,
                message.isBlank() ? "请补充必要业务参数。" : message,
                missing.toArray(String[]::new)));
    }

    private static String normalizedIdentifier(String value, String prefix) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith(prefix) || normalized.startsWith(prefix.replace('-', '_'))
                ? normalized
                : null;
    }

    private static String normalizedShortMenuId(String value) {
        return MenuId.normalizeOrNull(value);
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

    private static RestClient.Builder configureClient(
            RestClient.Builder clientBuilder, String baseUrl, int timeoutMs, String allowedHosts) {
        String normalizedBaseUrl = normalizeBaseUrl(baseUrl, allowedHosts);
        int safeTimeoutMs = validateTimeoutMs(timeoutMs);
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(safeTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(safeTimeoutMs));
        return clientBuilder
                .baseUrl(normalizedBaseUrl)
                .requestFactory(requestFactory);
    }

    private static String normalizeBaseUrl(String baseUrl, String allowedHosts) {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.deepseek.com";
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        URI uri = URI.create(normalized);
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || host == null
                || uri.getUserInfo() != null
                || !isAllowedHost(host, allowedHosts)) {
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

    private static int validateTimeoutMs(int timeoutMs) {
        if (timeoutMs < 100 || timeoutMs > MAX_TIMEOUT_MS) {
            throw new IllegalArgumentException("assistant model timeout must be between 100 and 60000 ms");
        }
        return timeoutMs;
    }

    private static int validateMaxResponseBytes(int maxResponseBytes) {
        if (maxResponseBytes < 1024 || maxResponseBytes > MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException("assistant model response limit must be between 1024 and 1048576 bytes");
        }
        return maxResponseBytes;
    }

    private static int validateMaxRequestChars(int maxRequestChars) {
        if (maxRequestChars < 128 || maxRequestChars > 8_000) {
            throw new IllegalArgumentException("assistant model request limit must be between 128 and 8000 characters");
        }
        return maxRequestChars;
    }
}
