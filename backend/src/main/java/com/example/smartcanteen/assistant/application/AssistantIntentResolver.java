package com.example.smartcanteen.assistant.application;

import com.example.smartcanteen.assistant.domain.AssistantResolution;

/** Resolves user language into a small, auditable set of structured intents. */
public interface AssistantIntentResolver {

    AssistantResolution resolve(String message);
}
