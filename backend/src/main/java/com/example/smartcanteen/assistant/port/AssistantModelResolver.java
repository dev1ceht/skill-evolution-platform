package com.example.smartcanteen.assistant.port;

import com.example.smartcanteen.assistant.domain.AssistantClarification;
import com.example.smartcanteen.assistant.domain.AssistantResolution;
import java.util.Optional;

/** Replaceable model boundary; model output remains untrusted until the router validates it. */
public interface AssistantModelResolver {

    Optional<AssistantResolution> resolve(
            String message, Optional<AssistantClarification> pendingClarification);
}
