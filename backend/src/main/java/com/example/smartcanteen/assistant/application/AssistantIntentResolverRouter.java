package com.example.smartcanteen.assistant.application;

import com.example.smartcanteen.assistant.domain.AssistantClarification;
import com.example.smartcanteen.assistant.domain.AssistantPendingAction;
import com.example.smartcanteen.assistant.domain.AssistantResolution;
import com.example.smartcanteen.assistant.port.AssistantModelResolver;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
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
            @Value("${smart-canteen.assistant.model.enabled:false}") boolean modelEnabled) {
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
            return model.resolve(message, pending)
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
                    && (hasPrefix(resolution.menuId(), "MENU-")
                    || hasPrefix(resolution.menuId(), "MENU_"));
            case MENU_PUBLISH_REQUEST, CONFIRM_PENDING_ACTION, CANCEL_PENDING_ACTION -> false;
            case CLARIFICATION -> resolution.intent() == null
                    || resolution.intent().equals("traceability.query")
                    || resolution.intent().equals("menu.query");
            case UNSUPPORTED -> true;
        };
    }

    private static boolean hasPrefix(String value, String prefix) {
        return value != null && value.toUpperCase(Locale.ROOT).startsWith(prefix);
    }
}
