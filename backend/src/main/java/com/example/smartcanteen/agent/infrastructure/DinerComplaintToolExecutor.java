package com.example.smartcanteen.agent.infrastructure;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.port.ToolExecutor;
import com.example.smartcanteen.application.DinerComplaintService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Executes the employee/student personal complaint slice. */
@Component
public class DinerComplaintToolExecutor implements ToolExecutor {

    private static final Set<String> TOOLS = Set.of(
            "diner_complaint.query", "diner_complaint.create");
    private static final Set<String> INPUT_FIELDS = Set.of(
            "status", "category", "subject", "description", "relatedOrderId",
            "businessIdempotencyKey");

    private final DinerComplaintService complaints;
    private final ObjectMapper objectMapper;

    public DinerComplaintToolExecutor(
            DinerComplaintService complaints, ObjectMapper objectMapper) {
        this.complaints = Objects.requireNonNull(complaints, "complaints");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public boolean supports(String toolName) {
        return TOOLS.contains(toolName);
    }

    @Override
    public ToolResult execute(String toolName, ExecutionContext context, String inputJson) {
        Objects.requireNonNull(context, "context");
        if (!supports(toolName)) {
            throw new IllegalArgumentException("Tool is not registered: " + toolName);
        }
        try {
            JsonNode input = objectMapper.readTree(inputJson == null ? "{}" : inputJson);
            requireObjectInput(input);
            Object result = switch (toolName) {
                case "diner_complaint.query" -> complaints.listMine(
                        context.scope(), context.actorUserId(), optionalText(input, "status"), 1, 100);
                case "diner_complaint.create" -> complaints.create(
                        context.scope(),
                        context.actorUserId(),
                        requiredText(input, "category"),
                        requiredText(input, "subject"),
                        requiredText(input, "description"),
                        optionalText(input, "relatedOrderId"),
                        requiredText(input, "businessIdempotencyKey"));
                default -> throw new IllegalArgumentException(
                        "Tool is not registered: " + toolName);
            };
            return new ToolResult(objectMapper.writeValueAsString(result));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid diner complaint tool input", exception);
        }
    }

    private static void requireObjectInput(JsonNode input) {
        if (input == null || !input.isObject()) {
            throw new IllegalArgumentException("Diner complaint tool input must be an object");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = input.fields();
        while (fields.hasNext()) {
            String field = fields.next().getKey();
            if (!INPUT_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unsupported diner complaint field: " + field);
            }
        }
    }

    private static String requiredText(JsonNode input, String field) {
        JsonNode value = input == null ? null : input.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.asText().trim();
    }

    private static String optionalText(JsonNode input, String field) {
        JsonNode value = input == null ? null : input.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException(field + " must be text");
        }
        return value.asText().trim();
    }
}
