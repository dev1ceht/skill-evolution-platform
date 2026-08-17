package com.example.smartcanteen.agent.application;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Process-local observability counters for failures that are not Run state transitions. */
@Component
public class AgentMetricsRecorder {

    private final Counter authorizationDenials;

    public AgentMetricsRecorder(MeterRegistry registry) {
        authorizationDenials = Counter.builder("smart_canteen_agent_authorization_denials_total")
                .description("Agent HTTP authorization denials since process start")
                .register(registry);
    }

    public void recordAuthorizationDenied() {
        authorizationDenials.increment();
    }

    public long authorizationDenialsSinceStart() {
        return Math.round(authorizationDenials.count());
    }
}
