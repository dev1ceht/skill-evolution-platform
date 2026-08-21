package com.example.smartcanteen.agent.infrastructure;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.port.ToolExecutor;
import com.example.smartcanteen.application.MealOrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Executes the employee/student personal meal-order slice. */
@Component
public class MealOrderToolExecutor implements ToolExecutor {

    private static final Set<String> TOOLS = Set.of(
            "meal_order.query",
            "meal_order.create",
            "meal_order.cancel",
            "meal_order.pay");
    private static final Set<String> INPUT_FIELDS = Set.of(
            "menuId", "menuDate", "mealTime", "items", "orderId", "status",
            "businessIdempotencyKey");

    private final MealOrderService orders;
    private final ObjectMapper objectMapper;

    public MealOrderToolExecutor(MealOrderService orders, ObjectMapper objectMapper) {
        this.orders = Objects.requireNonNull(orders, "orders");
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
                case "meal_order.query" -> orders.listMine(
                        context.scope(),
                        context.actorUserId(),
                        optionalText(input, "status"),
                        1,
                        100);
                case "meal_order.create" -> orders.create(
                        context.scope(),
                        context.actorUserId(),
                        optionalText(input, "menuId"),
                        optionalDate(input, "menuDate"),
                        optionalText(input, "mealTime"),
                        parseItems(input.get("items")),
                        requiredText(input, "businessIdempotencyKey"));
                case "meal_order.cancel" -> orders.cancel(
                        context.scope(),
                        context.actorUserId(),
                        requiredText(input, "orderId"));
                case "meal_order.pay" -> orders.pay(
                        context.scope(),
                        context.actorUserId(),
                        requiredText(input, "orderId"),
                        requiredText(input, "businessIdempotencyKey"));
                default -> throw new IllegalArgumentException(
                        "Tool is not registered: " + toolName);
            };
            return new ToolResult(objectMapper.writeValueAsString(result));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid meal order tool input", exception);
        }
    }

    private static void requireObjectInput(JsonNode input) {
        if (input == null || !input.isObject()) {
            throw new IllegalArgumentException("Meal order tool input must be an object");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = input.fields();
        while (fields.hasNext()) {
            String field = fields.next().getKey();
            if (!INPUT_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unsupported meal order field: " + field);
            }
        }
    }

    private ArrayList<MealOrderService.RequestedItem> parseItems(JsonNode value)
            throws IOException {
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("items is required");
        }
        JsonNode array = value.isTextual()
                ? objectMapper.readTree(value.asText())
                : value;
        if (!array.isArray()) {
            throw new IllegalArgumentException("items must be an array");
        }
        ArrayList<MealOrderService.RequestedItem> result = new ArrayList<>();
        for (JsonNode item : array) {
            if (item == null || !item.isObject()) {
                throw new IllegalArgumentException("Each meal order item must be an object");
            }
            result.add(new MealOrderService.RequestedItem(
                    requiredText(item, "dishId"),
                    requiredQuantity(item, "quantity")));
        }
        return result;
    }

    private static int requiredQuantity(JsonNode input, String field) {
        JsonNode value = input.get(field);
        if (value == null || !value.canConvertToInt() || value.asInt() < 1 || value.asInt() > 20) {
            throw new IllegalArgumentException(field + " must be an integer between 1 and 20");
        }
        return value.asInt();
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

    private static LocalDate optionalDate(JsonNode input, String field) {
        String value = optionalText(input, field);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(value);
            if (!date.toString().equals(value)) {
                throw new IllegalArgumentException(field + " must be YYYY-MM-DD");
            }
            return date;
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(field + " must be YYYY-MM-DD", exception);
        }
    }
}
