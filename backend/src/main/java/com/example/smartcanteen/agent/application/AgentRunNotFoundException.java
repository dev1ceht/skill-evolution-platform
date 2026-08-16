package com.example.smartcanteen.agent.application;

/** Raised when a caller asks for a Run that is not present in durable runtime state. */
public class AgentRunNotFoundException extends RuntimeException {

    public AgentRunNotFoundException(String runId) {
        super("Agent Run not found: " + runId);
    }
}
