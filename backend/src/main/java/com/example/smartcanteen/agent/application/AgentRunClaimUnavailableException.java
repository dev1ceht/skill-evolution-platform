package com.example.smartcanteen.agent.application;

/** Raised when a worker cannot acquire the execution claim for a Run. */
public class AgentRunClaimUnavailableException extends IllegalStateException {

    public AgentRunClaimUnavailableException(String runId) {
        super("Agent Run is already claimed or is not executable: " + runId);
    }
}
