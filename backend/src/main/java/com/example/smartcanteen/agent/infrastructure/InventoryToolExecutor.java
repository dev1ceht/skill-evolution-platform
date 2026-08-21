package com.example.smartcanteen.agent.infrastructure;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.port.ToolExecutor;
import com.example.smartcanteen.application.ProcurementOperationsService;
import com.example.smartcanteen.domain.InventoryLine;
import com.example.smartcanteen.domain.PageResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Executes the inventory read Skill through the canonical operational service. */
@Component
public class InventoryToolExecutor implements ToolExecutor {

    private static final Set<String> TOOLS = Set.of("inventory.query");
    private static final Set<String> INPUT_FIELDS = Set.of("keyword", "warningOnly");
    private static final int ASSISTANT_PAGE = 1;
    private static final int ASSISTANT_PAGE_SIZE = 100;
    private static final int MAX_KEYWORD_LENGTH = 100;

    private final ProcurementOperationsService procurement;
    private final ObjectMapper objectMapper;

    public InventoryToolExecutor(
            ProcurementOperationsService procurement, ObjectMapper objectMapper) {
        this.procurement = Objects.requireNonNull(procurement, "procurement");
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
            String keyword = optionalText(input, "keyword");
            boolean warningOnly = optionalBoolean(input, "warningOnly", false);
            Object result = listAllInventory(context, keyword, warningOnly);
            return new ToolResult(objectMapper.writeValueAsString(result));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid inventory query input", exception);
        }
    }

    private PageResult<InventoryLine> listAllInventory(
            ExecutionContext context, String keyword, boolean warningOnly) {
        List<InventoryLine> records = new ArrayList<>();
        int pageNumber = ASSISTANT_PAGE;
        PageResult<InventoryLine> page;
        do {
            page = procurement.listInventory(
                    context.scope(), keyword, warningOnly, pageNumber, ASSISTANT_PAGE_SIZE);
            records.addAll(page.records());
            pageNumber++;
        } while (records.size() < page.total() && !page.records().isEmpty());

        if (records.size() < page.total()) {
            throw new IllegalStateException(
                    "Inventory query did not return all matching records");
        }
        return new PageResult<>(
                records,
                ASSISTANT_PAGE,
                Math.max(ASSISTANT_PAGE_SIZE, records.size()),
                page.total());
    }

    private static void requireObjectInput(JsonNode input) {
        if (input == null || !input.isObject()) {
            throw new IllegalArgumentException("Inventory query input must be an object");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = input.fields();
        while (fields.hasNext()) {
            String field = fields.next().getKey();
            if (!INPUT_FIELDS.contains(field)) {
                throw new IllegalArgumentException("Unsupported inventory query field: " + field);
            }
        }
    }

    private static String optionalText(JsonNode input, String field) {
        JsonNode value = input == null ? null : input.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException(field + " must be text");
        }
        if (value.asText().isBlank()) {
            return null;
        }
        String keyword = value.asText().trim();
        if (keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new IllegalArgumentException(
                    field + " must be at most " + MAX_KEYWORD_LENGTH + " characters");
        }
        return keyword;
    }

    private static boolean optionalBoolean(JsonNode input, String field, boolean defaultValue) {
        JsonNode value = input == null ? null : input.get(field);
        if (value == null) {
            return defaultValue;
        }
        if (value.isNull()) {
            throw new IllegalArgumentException(field + " must be boolean");
        }
        if (!value.isBoolean()) {
            throw new IllegalArgumentException(field + " must be boolean");
        }
        return value.asBoolean();
    }
}
