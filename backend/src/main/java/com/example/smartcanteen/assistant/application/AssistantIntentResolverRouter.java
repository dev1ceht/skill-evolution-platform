package com.example.smartcanteen.assistant.application;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.assistant.domain.AssistantClarification;
import com.example.smartcanteen.assistant.domain.AssistantPendingAction;
import com.example.smartcanteen.assistant.domain.AssistantResolution;
import com.example.smartcanteen.domain.MenuId;
import com.example.smartcanteen.assistant.port.AssistantModelResolver;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Routes only unresolved messages to the optional model boundary. Deterministic rules always win
 * when they can classify a message, and model output is checked before it can reach Runtime.
 */
@Primary
@Component
public class AssistantIntentResolverRouter implements AssistantIntentResolver {

    private static final Logger log = LoggerFactory.getLogger(AssistantIntentResolverRouter.class);

    private final RuleBasedAssistantIntentResolver rules;
    private final AssistantModelResolver model;
    private final boolean modelEnabled;

    public AssistantIntentResolverRouter(
            RuleBasedAssistantIntentResolver rules,
            AssistantModelResolver model,
            @Value("${smart-canteen.assistant.model.enabled:true}") boolean modelEnabled) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.model = Objects.requireNonNull(model, "model");
        this.modelEnabled = modelEnabled;
    }

    @Override
    public AssistantResolution resolve(String message) {
        return resolve(message, Optional.empty());
    }

    @Override
    public AssistantResolution resolve(
            String message, Optional<AssistantClarification> pendingClarification) {
        return resolve(message, pendingClarification, Optional.empty());
    }

    @Override
    public AssistantResolution resolve(
            String message,
            Optional<AssistantClarification> pendingClarification,
            Optional<AssistantPendingAction> pendingAction) {
        return resolve(message, pendingClarification, pendingAction, null);
    }

    @Override
    public AssistantResolution resolve(
            String message,
            Optional<AssistantClarification> pendingClarification,
            Optional<AssistantPendingAction> pendingAction,
            ExecutionContext context) {
        Optional<AssistantClarification> pending = pendingClarification == null
                ? Optional.empty()
                : pendingClarification;
        Optional<AssistantPendingAction> action = pendingAction == null
                ? Optional.empty()
                : pendingAction;
        AssistantResolution deterministic = rules.resolve(message, pending, action);
        if (!modelEnabled
                || deterministic.type() != AssistantResolution.Type.UNSUPPORTED
                || RuleBasedAssistantIntentResolver.isExplicitNewUnsupportedRequest(message)) {
            return deterministic;
        }
        try {
            Optional<AssistantResolution> modelResult = context == null
                    ? model.resolve(message, pending)
                    : model.resolve(message, pending, context);
            return modelResult
                    .filter(AssistantIntentResolverRouter::isSafeModelResolution)
                    .orElse(deterministic);
        } catch (RuntimeException exception) {
            log.warn("Assistant model resolver unavailable; using deterministic fallback");
            return deterministic;
        }
    }

    private static boolean isSafeModelResolution(AssistantResolution resolution) {
        if (resolution == null) {
            return false;
        }
        return switch (resolution.type()) {
            case TRACEABILITY_QUERY -> resolution.intent().equals("traceability.query")
                    && (hasPrefix(resolution.traceCode(), "TRACE-")
                    || hasPrefix(resolution.traceCode(), "TRACE_"));
            case MENU_QUERY -> resolution.intent().equals("menu.query")
                    && (isShortMenuId(resolution.menuId()) || isDateMenuQuery(resolution));
            case INVENTORY_QUERY -> resolution.intent().equals("inventory.query")
                    && isInventoryQuery(resolution);
            case PROCUREMENT_GAP_QUERY -> resolution.intent().equals("procurement.gap.query")
                    && isProcurementGapQuery(resolution);
            case TRAFFIC_FORECAST_QUERY -> resolution.intent().equals("traffic.forecast.query")
                    && isDateMealQuery(resolution, "forecastDate");
            case MEAL_PLAN_QUERY -> resolution.intent().equals("meal_plan.query")
                    && isDateMealQuery(resolution, "menuDate");
            case MEAL_ORDER_QUERY -> resolution.intent().equals("meal_order.query")
                    && resolution.parameters().keySet().stream()
                    .allMatch(key -> key.equals("status"));
            case MEAL_REVIEW_QUERY -> resolution.intent().equals("meal_review.query")
                    && resolution.parameters().isEmpty();
            case DINER_COMPLAINT_QUERY -> resolution.intent().equals("diner_complaint.query")
                    && resolution.parameters().keySet().stream()
                    .allMatch(key -> key.equals("status"));
            case MENU_PUBLISH_REQUEST, WRITE_REQUEST, CONFIRM_PENDING_ACTION, CANCEL_PENDING_ACTION -> false;
            case CLARIFICATION -> resolution.intent() == null
                    || resolution.intent().equals("traceability.query")
                    || resolution.intent().equals("menu.query")
                    || resolution.intent().equals("procurement.gap.query")
                    || resolution.intent().equals("traffic.forecast.query")
                    || resolution.intent().equals("meal_plan.query")
                    || resolution.intent().equals("meal_order.query")
                    || resolution.intent().equals("meal_review.query")
                    || resolution.intent().equals("diner_complaint.query")
                    || AssistantResolution.isWriteIntent(resolution.intent());
            case UNSUPPORTED -> true;
        };
    }

    private static boolean hasPrefix(String value, String prefix) {
        return value != null && value.toUpperCase(Locale.ROOT).startsWith(prefix);
    }

    private static boolean isShortMenuId(String value) {
        return MenuId.isValid(value);
    }

    private static boolean isDateMenuQuery(AssistantResolution resolution) {
        return resolution.menuId() == null
                && resolution.parameters().containsKey("menuDate")
                && resolution.parameters().get("menuDate") != null
                && !resolution.parameters().get("menuDate").isBlank();
    }

    private static boolean isInventoryQuery(AssistantResolution resolution) {
        String keyword = resolution.parameters().get("keyword");
        String warningOnly = resolution.parameters().get("warningOnly");
        return (keyword == null || !keyword.isBlank())
                && (warningOnly == null
                || warningOnly.equalsIgnoreCase("true")
                || warningOnly.equalsIgnoreCase("false"));
    }

    private static boolean isProcurementGapQuery(AssistantResolution resolution) {
        String menuDate = resolution.parameters().get("menuDate");
        String mealTime = resolution.parameters().get("mealTime");
        return menuDate != null
                && !menuDate.isBlank()
                && (mealTime == null
                || Set.of("BREAKFAST", "LUNCH", "DINNER", "SNACK")
                .contains(mealTime.toUpperCase(Locale.ROOT)));
    }

    private static boolean isDateMealQuery(AssistantResolution resolution, String dateField) {
        String date = resolution.parameters().get(dateField);
        String mealTime = resolution.parameters().get("mealTime");
        return date != null
                && !date.isBlank()
                && mealTime != null
                && Set.of("BREAKFAST", "LUNCH", "DINNER", "SNACK")
                .contains(mealTime.toUpperCase(Locale.ROOT));
    }
}
