package com.example.smartcanteen.assistant.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

/** User-facing result of one assistant message, linked to an optional Agent Run. */
public record AssistantTurn(
        String conversationId,
        String turnId,
        long sequence,
        String kind,
        String message,
        String intent,
        String runId,
        String runStatus,
        JsonNode result,
        List<String> missingFields,
        Instant createdAt) {

    public AssistantTurn {
        missingFields = missingFields == null ? List.of() : List.copyOf(missingFields);
    }
}
