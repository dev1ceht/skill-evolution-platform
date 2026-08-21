package com.example.smartcanteen.assistant.port;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.assistant.domain.AssistantClarification;
import com.example.smartcanteen.assistant.domain.AssistantResolution;
import java.util.Optional;

/** Replaceable model boundary; model output remains untrusted until the router validates it. */
public interface AssistantModelResolver {

    Optional<AssistantResolution> resolve(
            String message, Optional<AssistantClarification> pendingClarification);

    /**
     * Optional context-aware extension. Existing adapters remain source-compatible and continue
     * to receive only the message until they opt into persona-aware resolution.
     */
    default Optional<AssistantResolution> resolve(
            String message,
            Optional<AssistantClarification> pendingClarification,
            ExecutionContext context) {
        return resolve(message, pendingClarification);
    }
}
