package com.example.smartcanteen.agent.domain;

/** Raised when a worker attempts to persist after its execution lease was fenced. */
public class AgentRunClaimLostException extends IllegalStateException {

    public AgentRunClaimLostException(String runId) {
        super("Agent execution claim was lost: " + runId);
    }
}
