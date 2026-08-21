package com.example.smartcanteen.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.assistant.domain.AssistantPersona;
import com.example.smartcanteen.assistant.domain.AssistantResolution;
import com.example.smartcanteen.assistant.domain.AssistantRoleContext;
import com.example.smartcanteen.assistant.infrastructure.AgentScopeAssistantModelResolver;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.AssistantMessage;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class AgentScopeAssistantModelResolverTest {

    @Test
    void sends_server_role_context_into_harness_runtime_context() {
        HarnessAgent agent = mock(HarnessAgent.class);
        when(agent.call(anyString(), any(RuntimeContext.class))).thenAnswer(invocation -> {
            RuntimeContext runtimeContext = invocation.getArgument(1);
            assertThat(runtimeContext.getUserId()).isEqualTo("USER-001");
            assertThat(runtimeContext.getSessionId()).isEqualTo("assistant-classifier-request-001");
            AssistantRoleContext roleContext = runtimeContext.get("assistantRoleContext");
            assertThat(roleContext).isNotNull();
            assertThat(roleContext.actorUserId()).isEqualTo("USER-001");
            assertThat(roleContext.persona()).isEqualTo(AssistantPersona.EMPLOYEE_STUDENT);
            assertThat(roleContext.scope())
                    .isEqualTo(new CanteenScope("SCHOOL-001", "CANTEEN-001"));
            assertThat(roleContext.permissions()).containsExactly("MENU_READ");
            return Mono.just(new AssistantMessage(
                    "assistant",
                    "{\"type\":\"MENU_QUERY\",\"intent\":\"menu.query\",\"menuId\":\"M001\"}"));
        });
        AgentScopeAssistantModelResolver resolver = new AgentScopeAssistantModelResolver(
                agent, new ObjectMapper(), true, 2_000, 1_000);

        Optional<AssistantResolution> result = resolver.resolve(
                "帮我看看今天有什么菜",
                Optional.empty(),
                new ExecutionContext(
                        "request-001",
                        "USER-001",
                        "study-user",
                        new CanteenScope("SCHOOL-001", "CANTEEN-001"),
                        Set.of(Role.DINER),
                        Set.of("MENU_READ")));

        assertThat(result).get().extracting(AssistantResolution::type)
                .isEqualTo(AssistantResolution.Type.MENU_QUERY);
        assertThat(result).get().extracting(AssistantResolution::menuId)
                .isEqualTo("M001");
    }

    @Test
    void fails_closed_when_the_optional_agent_is_disabled() {
        AgentScopeAssistantModelResolver resolver = new AgentScopeAssistantModelResolver(
                null, new ObjectMapper(), false, 2_000, 1_000);

        assertThat(resolver.resolve("查询 M001", Optional.empty())).isEmpty();
    }
}
