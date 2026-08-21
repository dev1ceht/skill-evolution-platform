package com.example.smartcanteen.agent.infrastructure;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.port.ToolExecutor;
import com.example.smartcanteen.application.MealReviewService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Executes the employee/student personal meal-review slice. */
@Component
public class MealReviewToolExecutor implements ToolExecutor {

    private static final Set<String> TOOLS = Set.of("meal_review.query", "meal_review.create");
    private static final Set<String> INPUT_FIELDS = Set.of(
            "orderId", "rating", "content", "businessIdempotencyKey");

    private final MealReviewService reviews;
    private final ObjectMapper objectMapper;

    public MealReviewToolExecutor(MealReviewService reviews, ObjectMapper objectMapper) {
        this.reviews = Objects.requireNonNull(reviews, "reviews");
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
                case "meal_review.query" -> reviews.listMine(
                        context.scope(), context.actorUserId(), 1, 100);
                case "meal_review.create" -> reviews.create(
                        context.scope(),
                        context.actorUserId(),
                        requiredText(input, "orderId"),
                        requiredRating(input, "rating"),
                        optionalText(input, "content"),
                        requiredText(input, "businessIdempotencyKey"));
                default -> throw new IllegalArgumentException(
                        "Tool is not registered: " + toolName);
            };
            return new ToolResult(objectMapper.writeValueAsString(result));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid meal review tool input", exception);
        }
    }

    private static void requireObjectInput(JsonNode input) {
        if (input == null || !input.isObject()) {
            throw new IllegalArgumentException("Meal review tool input must be an object");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = input.fields();
        while (fields.hasNext()) {
            String field = fields.next().getKey();
            if (!INPUT_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unsupported meal review field: " + field);
            }
        }
    }

    private static int requiredRating(JsonNode input, String field) {
        JsonNode value = input == null ? null : input.get(field);
        if (value == null) {
            throw new IllegalArgumentException(field + " must be an integer between 1 and 5");
        }
        try {
            int rating = value.isNumber() ? value.asInt() : Integer.parseInt(value.asText().trim());
            if (rating < 1 || rating > 5) {
                throw new NumberFormatException("out of range");
            }
            return rating;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be an integer between 1 and 5");
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
