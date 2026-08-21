package com.example.smartcanteen.agent.infrastructure;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.port.ToolExecutor;
import com.example.smartcanteen.application.TrafficForecastService;
import com.example.smartcanteen.domain.TrafficForecast;
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

/** Executes a scoped, read-only query against versioned traffic forecast facts. */
@Component
public class TrafficForecastToolExecutor implements ToolExecutor {

    private static final String TOOL = "traffic.forecast.query";
    private static final Set<String> INPUT_FIELDS = Set.of("forecastDate", "mealTime");
    private static final Set<String> MEAL_TIMES = Set.of("BREAKFAST", "LUNCH", "DINNER", "SNACK");

    private final TrafficForecastService forecasts;
    private final ObjectMapper objectMapper;

    public TrafficForecastToolExecutor(
            TrafficForecastService forecasts, ObjectMapper objectMapper) {
        this.forecasts = Objects.requireNonNull(forecasts, "forecasts");
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
            LocalDate forecastDate = requiredDate(input, "forecastDate");
            String mealTime = requiredMealTime(input);
            return new ToolResult(objectMapper.writeValueAsString(
                    forecasts.forecast(context.scope(), forecastDate, mealTime)));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid traffic forecast query input", exception);
        }
    }

    private static void requireObjectInput(JsonNode input) {
        if (input == null || !input.isObject()) {
            throw new IllegalArgumentException("Traffic forecast query input must be an object");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = input.fields();
        while (fields.hasNext()) {
            String field = fields.next().getKey();
            if (!INPUT_FIELDS.contains(field)) {
                throw new IllegalArgumentException(
                        "Unsupported traffic forecast query field: " + field);
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

    private static String requiredMealTime(JsonNode input) {
        JsonNode value = input.get("mealTime");
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "mealTime is required and must be BREAKFAST, LUNCH, DINNER or SNACK");
        }
        String normalized = value.asText().trim().toUpperCase(Locale.ROOT);
        if (!MEAL_TIMES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "mealTime must be BREAKFAST, LUNCH, DINNER or SNACK");
        }
        return normalized;
    }
}
