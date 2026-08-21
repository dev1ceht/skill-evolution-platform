package com.example.smartcanteen.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.assistant.application.AssistantIntentResolverRouter;
import com.example.smartcanteen.assistant.application.RuleBasedAssistantIntentResolver;
import com.example.smartcanteen.assistant.domain.AssistantResolution;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.Role;
import com.example.smartcanteen.assistant.port.AssistantModelResolver;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AssistantIntentResolverRouterTest {

    @Test
    void forwards_server_derived_role_context_to_the_model_fallback() {
        RuleBasedAssistantIntentResolver rules = new RuleBasedAssistantIntentResolver();
        AssistantModelResolver model = mock(AssistantModelResolver.class);
        ExecutionContext context = new ExecutionContext(
                "request-001",
                "USER-001",
                "study-user",
                new CanteenScope("SCHOOL-001", "CANTEEN-001"),
                Set.of(Role.DINER),
                Set.of("MENU_READ"));
        when(model.resolve("帮我看看今天有什么菜", Optional.empty(), context))
                .thenReturn(Optional.of(AssistantResolution.menuQuery("M001")));

        AssistantIntentResolverRouter router = new AssistantIntentResolverRouter(
                rules, model, true);

        AssistantResolution result = router.resolve(
                "帮我看看今天有什么菜", Optional.empty(), Optional.empty(), context);

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.MENU_QUERY);
        verify(model).resolve("帮我看看今天有什么菜", Optional.empty(), context);
    }

    @Test
    void uses_the_model_adapter_for_an_unsupported_message_when_explicitly_enabled() {
        RuleBasedAssistantIntentResolver rules = new RuleBasedAssistantIntentResolver();
        AssistantModelResolver model = mock(AssistantModelResolver.class);
        when(model.resolve("帮我看看今天有什么菜", Optional.empty()))
                .thenReturn(Optional.of(AssistantResolution.menuQuery("M001")));

        AssistantIntentResolverRouter router = new AssistantIntentResolverRouter(
                rules, model, true);

        AssistantResolution result = router.resolve("帮我看看今天有什么菜");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.MENU_QUERY);
        assertThat(result.menuId()).isEqualTo("M001");
    }

    @Test
    void keeps_the_auditable_rule_result_when_model_adapter_is_disabled() {
        RuleBasedAssistantIntentResolver rules = new RuleBasedAssistantIntentResolver();
        AssistantModelResolver model = mock(AssistantModelResolver.class);
        AssistantIntentResolverRouter router = new AssistantIntentResolverRouter(
                rules, model, false);

        AssistantResolution result = router.resolve("帮我看看今天有什么菜");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.UNSUPPORTED);
        verify(model, never()).resolve("帮我看看今天有什么菜", Optional.empty());
    }

    @Test
    void rejects_a_model_result_with_an_untrusted_resource_identifier() {
        RuleBasedAssistantIntentResolver rules = new RuleBasedAssistantIntentResolver();
        AssistantModelResolver model = mock(AssistantModelResolver.class);
        when(model.resolve("查询那批食材", Optional.empty()))
                .thenReturn(Optional.of(AssistantResolution.traceability("BATCH-001")));
        AssistantIntentResolverRouter router = new AssistantIntentResolverRouter(
                rules, model, true);

        AssistantResolution result = router.resolve("查询那批食材");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.UNSUPPORTED);
    }

    @Test
    void does_not_let_the_model_rewrite_an_explicitly_unsupported_business_request() {
        RuleBasedAssistantIntentResolver rules = new RuleBasedAssistantIntentResolver();
        AssistantModelResolver model = mock(AssistantModelResolver.class);
        when(model.resolve("帮我安排明天的采购", Optional.empty()))
                .thenReturn(Optional.of(AssistantResolution.menuQuery("M001")));
        AssistantIntentResolverRouter router = new AssistantIntentResolverRouter(
                rules, model, true);

        AssistantResolution result = router.resolve("帮我安排明天的采购");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.UNSUPPORTED);
        verify(model, never()).resolve("帮我安排明天的采购", Optional.empty());
    }

    @Test
    void rejects_model_output_that_requests_a_write_action() {
        RuleBasedAssistantIntentResolver rules = new RuleBasedAssistantIntentResolver();
        AssistantModelResolver model = mock(AssistantModelResolver.class);
        when(model.resolve("帮我处理这个事项", Optional.empty()))
                .thenReturn(Optional.of(AssistantResolution.menuPublish("M001")));
        AssistantIntentResolverRouter router = new AssistantIntentResolverRouter(
                rules, model, true);

        AssistantResolution result = router.resolve("帮我处理这个事项");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.UNSUPPORTED);
    }

    @Test
    void rejects_model_clarification_that_would_lead_to_a_write_action() {
        RuleBasedAssistantIntentResolver rules = new RuleBasedAssistantIntentResolver();
        AssistantModelResolver model = mock(AssistantModelResolver.class);
        when(model.resolve("帮我处理这个事项", Optional.empty()))
                .thenReturn(Optional.of(AssistantResolution.clarificationFor(
                        "menu.publish", "请提供菜单 ID", "menuId")));
        AssistantIntentResolverRouter router = new AssistantIntentResolverRouter(
                rules, model, true);

        AssistantResolution result = router.resolve("帮我处理这个事项");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.UNSUPPORTED);
    }
}
