package com.example.smartcanteen.agent.port;

import com.example.smartcanteen.agent.domain.AgentRun;
import com.example.smartcanteen.agent.domain.AgentStep;
import com.example.smartcanteen.domain.CanteenScope;
import java.util.List;
import java.util.Optional;

public interface AgentRunStore {

    Optional<AgentRun> findByIdempotency(
            String actorUserId, CanteenScope scope, String idempotencyKey);

    Optional<AgentRun> findById(String runId);

    void insert(AgentRun run, List<AgentStep> steps);

    void update(AgentRun expected, AgentRun updated);

    void updateStep(AgentStep step);

    void appendEvent(
            String runId,
            String eventType,
            String fromStatus,
            String toStatus,
            String actorUserId,
            String payloadJson);
}
