package com.example.smartcanteen.agent.infrastructure;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.port.ToolExecutor;
import com.example.smartcanteen.application.AlertCenterService;
import com.example.smartcanteen.application.BusinessAuthorizationPolicy;
import com.example.smartcanteen.application.ProcurementOperationsService;
import com.example.smartcanteen.application.ProcurementPlanService;
import com.example.smartcanteen.application.port.OperationalStore;
import com.example.smartcanteen.domain.AlertDisposal;
import com.example.smartcanteen.domain.AlertRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Adapter for the first business-write Skill slice.
 *
 * <p>The executor deliberately accepts only the canonical domain service inputs. It does not
 * call supplier or notification gateways; those remain explicit ports and therefore cannot be
 * accidentally activated by a natural-language request.</p>
 */
@Component
public class OperationsToolExecutor implements ToolExecutor {

    private static final Set<String> TOOLS = Set.of(
            "procurement.plan.generate",
            "procurement.order.create",
            "procurement.order.receive",
            "inventory.receive",
            "inventory.stock-out",
            "alert.dispose");

    private final ProcurementPlanService plans;
    private final ProcurementOperationsService procurement;
    private final AlertCenterService alerts;
    private final BusinessAuthorizationPolicy authorization;
    private final ObjectMapper objectMapper;

    public OperationsToolExecutor(
            ProcurementPlanService plans,
            ProcurementOperationsService procurement,
            AlertCenterService alerts,
            BusinessAuthorizationPolicy authorization,
            ObjectMapper objectMapper) {
        this.plans = Objects.requireNonNull(plans, "plans");
        this.procurement = Objects.requireNonNull(procurement, "procurement");
        this.alerts = Objects.requireNonNull(alerts, "alerts");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
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
            JsonNode input = objectMapper.readTree(inputJson);
            authorization.requireDomainApproval(context, toolName);
            Object result = switch (toolName) {
                case "procurement.plan.generate" -> plans.generate(
                        context.scope(),
                        LocalDate.parse(requiredText(input, "periodStart")),
                        LocalDate.parse(requiredText(input, "periodEnd")),
                        businessIdempotency(input));
                case "procurement.order.create" -> plans.convertToOrder(
                        context.scope(),
                        requiredText(input, "planId"),
                        businessIdempotency(input),
                        requiredText(input, "supplierId"),
                        optionalText(input, "orderType") == null
                                ? "OFFLINE" : optionalText(input, "orderType"),
                        optionalInstant(input, "expectedDeliveryAt"),
                        optionalText(input, "remark"),
                        orderLines(input));
                case "procurement.order.receive" -> procurement.receive(
                        context.scope(),
                        requiredText(input, "orderId"),
                        businessIdempotency(input),
                        receiveItems(input));
                case "inventory.receive" -> procurement.receiveInventory(
                        context.scope(),
                        businessIdempotency(input),
                        requiredText(input, "supplierId"),
                        inventoryReceiveItem(input));
                case "inventory.stock-out" -> procurement.stockOut(
                        context.scope(),
                        businessIdempotency(input),
                        optionalText(input, "reason"),
                        stockOutItems(input));
                case "alert.dispose" -> disposeAlert(input, context);
                default -> throw new IllegalArgumentException("Tool is not registered: " + toolName);
            };
            return new ToolResult(objectMapper.writeValueAsString(result));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Invalid business write tool input", exception);
        }
    }

    private AlertRecord disposeAlert(JsonNode input, ExecutionContext context) {
        String warnId = requiredText(input, "warnId");
        AlertRecord existing = alerts.find(warnId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + warnId));
        if (!existing.schoolId().equals(context.scope().schoolId())
                || existing.canteenId() == null
                || !existing.canteenId().equals(context.scope().canteenId())) {
            throw new IllegalArgumentException("Alert is outside the requested canteen scope");
        }
        String content = optionalText(input, "processContent");
        Instant processTime = optionalInstant(input, "processTime");
        return alerts.dispose(
                warnId,
                new AlertDisposal(
                        1,
                        processTime == null ? Instant.now() : processTime,
                        context.actorUserId(),
                        content == null ? "自然语言助手处置" : content,
                        optionalText(input, "processFile")),
                businessIdempotency(input));
    }

    private List<ProcurementPlanService.OrderLine> orderLines(JsonNode input) {
        JsonNode items = input == null ? null : input.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            return List.of(new ProcurementPlanService.OrderLine(
                    requiredText(input, "ingredientId"),
                    decimal(input, "quantity", true),
                    requiredText(input, "unit"),
                    decimal(input, "unitPrice", false)));
        }
        return stream(items).map(item -> new ProcurementPlanService.OrderLine(
                requiredText(item, "ingredientId"),
                decimal(item, "quantity", true),
                requiredText(item, "unit"),
                decimal(item, "unitPrice", false))).toList();
    }

    private OperationalStore.ReceiveItem inventoryReceiveItem(JsonNode input) {
        return new OperationalStore.ReceiveItem(
                requiredText(input, "materialId"),
                decimal(input, "quantity", true),
                requiredText(input, "unit"),
                requiredText(input, "batchNo"),
                decimal(input, "purchasePrice", false),
                optionalDate(input, "productionDate"),
                optionalDate(input, "expiryDate"));
    }

    private List<OperationalStore.ReceiveItem> receiveItems(JsonNode input) {
        JsonNode items = input == null ? null : input.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            return List.of(new OperationalStore.ReceiveItem(
                    requiredText(input, "ingredientId"),
                    decimal(input, "quantity", true),
                    requiredText(input, "unit"),
                    optionalText(input, "batchNo"),
                    decimal(input, "purchasePrice", false),
                    optionalDate(input, "productionDate"),
                    optionalDate(input, "expiryDate")));
        }
        return stream(items).map(item -> new OperationalStore.ReceiveItem(
                requiredText(item, "ingredientId"),
                decimal(item, "quantity", true),
                requiredText(item, "unit"),
                optionalText(item, "batchNo"),
                decimal(item, "purchasePrice", false),
                optionalDate(item, "productionDate"),
                optionalDate(item, "expiryDate"))).toList();
    }

    private List<OperationalStore.StockOutItem> stockOutItems(JsonNode input) {
        JsonNode items = input == null ? null : input.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            return List.of(new OperationalStore.StockOutItem(
                    requiredText(input, "ingredientId"),
                    decimal(input, "quantity", true),
                    requiredText(input, "unit")));
        }
        return stream(items).map(item -> new OperationalStore.StockOutItem(
                requiredText(item, "ingredientId"),
                decimal(item, "quantity", true),
                requiredText(item, "unit"))).toList();
    }

    private static java.util.stream.Stream<JsonNode> stream(JsonNode items) {
        java.util.List<JsonNode> values = new java.util.ArrayList<>();
        items.elements().forEachRemaining(values::add);
        return values.stream();
    }

    private static String businessIdempotency(JsonNode input) {
        return requiredText(input, "businessIdempotencyKey");
    }

    private static BigDecimal decimal(JsonNode input, String field, boolean positive) {
        String value = requiredText(input, field);
        try {
            BigDecimal result = new BigDecimal(value);
            if (positive ? result.signum() <= 0 : result.signum() < 0) {
                throw new IllegalArgumentException(field + " must be "
                        + (positive ? "positive" : "non-negative"));
            }
            return result;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be a decimal number", exception);
        }
    }

    private static String requiredText(JsonNode input, String field) {
        JsonNode value = input == null ? null : input.get(field);
        if (value == null || !value.isValueNode() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.asText().trim();
    }

    private static String optionalText(JsonNode input, String field) {
        JsonNode value = input == null ? null : input.get(field);
        return value == null || value.isNull() || value.asText().isBlank()
                ? null : value.asText().trim();
    }

    private static Instant optionalInstant(JsonNode input, String field) {
        String value = optionalText(input, field);
        return value == null ? null : Instant.parse(value);
    }

    private static LocalDate optionalDate(JsonNode input, String field) {
        String value = optionalText(input, field);
        return value == null ? null : LocalDate.parse(value);
    }
}
