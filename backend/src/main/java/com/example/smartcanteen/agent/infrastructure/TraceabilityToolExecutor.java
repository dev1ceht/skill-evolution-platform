package com.example.smartcanteen.agent.infrastructure;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.port.ToolExecutor;
import com.example.smartcanteen.application.DashboardService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Tool catalog entry for the first read-only Agent vertical slice. */
@Component
public class TraceabilityToolExecutor implements ToolExecutor {

    private final DashboardService dashboard;
    private final ObjectMapper objectMapper;

    public TraceabilityToolExecutor(DashboardService dashboard, ObjectMapper objectMapper) {
        this.dashboard = Objects.requireNonNull(dashboard, "dashboard");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public boolean supports(String toolName) {
        return "traceability.query".equals(toolName);
    }

    @Override
    public ToolResult execute(String toolName, ExecutionContext context, String inputJson) {
        if (!"traceability.query".equals(toolName)) {
            throw new IllegalArgumentException("Tool is not registered: " + toolName);
        }
        try {
            JsonNode input = objectMapper.readTree(inputJson);
            JsonNode traceCode = input == null ? null : input.get("traceCode");
            if (traceCode == null || !traceCode.isTextual() || traceCode.asText().isBlank()) {
                throw new IllegalArgumentException("traceCode is required");
            }
            return new ToolResult(objectMapper.writeValueAsString(
                    dashboard.trace(context.scope(), traceCode.asText())));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid traceability input", exception);
        }
    }
}
