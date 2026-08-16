package com.example.smartcanteen.agent.port;

import com.example.smartcanteen.agent.domain.ExecutionContext;

/** Executes one named, registered business tool. */
public interface ToolExecutor {

    ToolResult execute(String toolName, ExecutionContext context, String inputJson);

    record ToolResult(String resultJson) {
    }
}
