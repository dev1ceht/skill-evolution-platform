package com.example.smartcanteen.agent.infrastructure;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.port.ToolExecutor;
import com.example.smartcanteen.application.ProcurementPlanService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Executes the deterministic, read-only published-menu ingredient gap analysis. */
@Component
public class ProcurementGapToolExecutor implements ToolExecutor {

    private static final String TOOL = "procurement.gap.query";
    private static final Set<String> INPUT_FIELDS = Set.of("menuDate", "mealTime");
    private static final Set<String> MEAL_TIMES = Set.of("BREAKFAST", "LUNCH", "DINNER", "SNACK");

    private final ProcurementPlanService plans;
    private final ObjectMapper objectMapper;

    public ProcurementGapToolExecutor(
            ProcurementPlanService plans, ObjectMapper objectMapper) {
        this.plans = Objects.requireNonNull(plans, "plans");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public boolean supports(String toolName) {
        return TOOL.equals(toolName);
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
            LocalDate menuDate = requiredDate(input, "menuDate");
            String mealTime = optionalMealTime(input, "mealTime");
            return new ToolResult(objectMapper.writeValueAsString(
                    plans.analyzeGap(context.scope(), menuDate, mealTime)));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid procurement gap query input", exception);
        }
    }

    private static void requireObjectInput(JsonNode input) {
        if (input == null || !input.isObject()) {
            throw new IllegalArgumentException("Procurement gap query input must be an object");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = input.fields();
        while (fields.hasNext()) {
            String field = fields.next().getKey();
            if (!INPUT_FIELDS.contains(field)) {
                throw new IllegalArgumentException(
                        "Unsupported procurement gap query field: " + field);
            }
        }
    }

    private static LocalDate requiredDate(JsonNode input, String field) {
        JsonNode value = input.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required and must be YYYY-MM-DD");
        }
        String normalized = value.asText().trim();
        try {
            LocalDate date = LocalDate.parse(normalized);
            if (!date.toString().equals(normalized)) {
                throw new IllegalArgumentException(field + " must be YYYY-MM-DD");
            }
            return date;
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(field + " must be YYYY-MM-DD", exception);
        }
    }

    private static String optionalMealTime(JsonNode input, String field) {
        JsonNode value = input.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " must be a supported meal time");
        }
        String normalized = value.asText().trim().toUpperCase(Locale.ROOT);
        if (!MEAL_TIMES.contains(normalized)) {
            throw new IllegalArgumentException(field + " must be BREAKFAST, LUNCH, DINNER or SNACK");
        }
        return normalized;
    }
}
