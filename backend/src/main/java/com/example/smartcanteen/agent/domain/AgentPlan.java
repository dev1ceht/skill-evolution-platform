package com.example.smartcanteen.agent.domain;

import com.example.smartcanteen.domain.CanteenScope;
import java.util.List;

public record AgentPlan(
        String skillId,
        String skillVersion,
        String manifestDigest,
        String intent,
        CanteenScope scope,
        String inputDigest,
        List<String> tools,
        String planHash,
        String planJson) {

    public AgentPlan {
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
