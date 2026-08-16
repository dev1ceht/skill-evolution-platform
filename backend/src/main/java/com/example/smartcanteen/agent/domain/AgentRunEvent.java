package com.example.smartcanteen.agent.domain;

import java.time.Instant;

/** Append-only timeline entry used for recovery and operator evidence. */
public record AgentRunEvent(
        String eventId,
        String runId,
        long sequence,
        String eventType,
        String fromStatus,
        String toStatus,
        String actorUserId,
        String payloadJson,
        Instant occurredAt) {
}
