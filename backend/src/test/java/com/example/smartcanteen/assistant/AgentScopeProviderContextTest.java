package com.example.smartcanteen.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.smartcanteen.assistant.infrastructure.AgentScopeAssistantModelResolver;
import com.example.smartcanteen.assistant.port.AssistantModelResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "smart-canteen.assistant.model.provider=agentscope",
        "smart-canteen.assistant.model.enabled=false"
})
class AgentScopeProviderContextTest {

    @Autowired
    private AssistantModelResolver resolver;

    @Test
    void selects_the_agentscope_adapter_without_needing_a_model_key() {
        assertThat(resolver).isInstanceOf(AgentScopeAssistantModelResolver.class);
        assertThat(resolver.resolve("查询 M001", java.util.Optional.empty())).isEmpty();
    }
}
