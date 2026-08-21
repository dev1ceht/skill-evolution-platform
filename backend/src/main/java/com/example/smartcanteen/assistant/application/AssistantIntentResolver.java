package com.example.smartcanteen.assistant.application;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.assistant.domain.AssistantResolution;
import com.example.smartcanteen.assistant.domain.AssistantClarification;
import com.example.smartcanteen.assistant.domain.AssistantPendingAction;
import java.util.Optional;

/** Resolves user language into a small, auditable set of structured intents. */
public interface AssistantIntentResolver {

    AssistantResolution resolve(String message);

    /** Resolves a message with the conversation's pending clarification, if any. */
    default AssistantResolution resolve(
            String message, Optional<AssistantClarification> pendingClarification) {
        return resolve(message);
    }

    /** Resolves a message with both clarification and confirmation state, if present. */
    default AssistantResolution resolve(
            String message,
            Optional<AssistantClarification> pendingClarification,
            Optional<AssistantPendingAction> pendingAction) {
        return resolve(message, pendingClarification);
    }

    /** Resolves with the server-derived actor and canteen scope. */
    default AssistantResolution resolve(
            String message,
            Optional<AssistantClarification> pendingClarification,
            Optional<AssistantPendingAction> pendingAction,
            ExecutionContext context) {
        return resolve(message, pendingClarification, pendingAction);
    }
}
