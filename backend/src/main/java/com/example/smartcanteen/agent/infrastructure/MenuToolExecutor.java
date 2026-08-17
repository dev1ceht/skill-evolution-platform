package com.example.smartcanteen.agent.infrastructure;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.port.ToolExecutor;
import com.example.smartcanteen.application.DailyMenuService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Set;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Executes canonical daily-menu writes after the Agent run confirmation gate. */
@Component
public class MenuToolExecutor implements ToolExecutor {

    private static final Set<String> TOOLS = Set.of(
            "menu.query",
            "menu.validate-for-submit",
            "menu.submit",
            "menu.record-decision",
            "menu.publish");

    private final DailyMenuService menus;
    private final ObjectMapper objectMapper;

    public MenuToolExecutor(DailyMenuService menus, ObjectMapper objectMapper) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public boolean supports(String toolName) {
        return TOOLS.contains(toolName);
    }

    @Override
    public ToolResult execute(String toolName, ExecutionContext context, String inputJson) {
        try {
            JsonNode input = objectMapper.readTree(inputJson);
            String menuId = requiredText(input, "menuId");
            Object result;
            switch (toolName) {
                case "menu.query" -> result = menus.get(context.scope(), menuId);
                case "menu.validate-for-submit" -> result = menus.validateForSubmit(
                        context.scope(), menuId, requiredLong(input, "menuVersion"));
                case "menu.submit" -> result = menus.submitForApproval(
                        context.scope(), menuId, requiredLong(input, "menuVersion"), context.actorUserId());
                case "menu.record-decision" -> result = menus.recordDecision(
                        context.scope(),
                        menuId,
                        requiredLong(input, "menuVersion"),
                        requiredText(input, "decision"),
                        optionalText(input, "comment"),
                        context.actorUserId());
                case "menu.publish" -> result = menus.publish(
                        context.scope(), menuId, requiredLong(input, "menuVersion"), context.actorUserId());
                default -> throw new IllegalArgumentException("Tool is not registered: " + toolName);
            }
            return new ToolResult(objectMapper.writeValueAsString(result));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid menu tool input", exception);
        }
    }

    private static String requiredText(JsonNode input, String field) {
        JsonNode value = input == null ? null : input.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.asText();
    }

    private static String optionalText(JsonNode input, String field) {
        JsonNode value = input == null ? null : input.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static long requiredLong(JsonNode input, String field) {
        JsonNode value = input == null ? null : input.get(field);
        if (value == null || !value.canConvertToLong() || value.asLong() < 0) {
            throw new IllegalArgumentException(field + " must be a non-negative integer");
        }
        return value.asLong();
    }
}
