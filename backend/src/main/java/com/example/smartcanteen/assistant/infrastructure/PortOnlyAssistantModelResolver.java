package com.example.smartcanteen.assistant.infrastructure;

import com.example.smartcanteen.assistant.domain.AssistantClarification;
import com.example.smartcanteen.assistant.domain.AssistantResolution;
import com.example.smartcanteen.assistant.port.AssistantModelResolver;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Safe default until a model vendor contract, credentials, and network policy are available.
 * It deliberately does not pretend that an external model is connected.
 */
@Component
public class PortOnlyAssistantModelResolver implements AssistantModelResolver {

    @Override
    public Optional<AssistantResolution> resolve(
            String message, Optional<AssistantClarification> pendingClarification) {
        return Optional.empty();
    }
}
