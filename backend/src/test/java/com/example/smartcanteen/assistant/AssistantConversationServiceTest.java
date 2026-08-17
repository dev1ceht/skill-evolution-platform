package com.example.smartcanteen.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.agent.application.AgentExecutionService;
import com.example.smartcanteen.agent.application.AgentRuntime;
import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.port.SkillRegistry;
import com.example.smartcanteen.application.BusinessAuthorizationPolicy;
import com.example.smartcanteen.assistant.application.AssistantConversationService;
import com.example.smartcanteen.assistant.application.RuleBasedAssistantIntentResolver;
import com.example.smartcanteen.assistant.domain.AssistantConversation;
import com.example.smartcanteen.assistant.domain.AssistantTurn;
import com.example.smartcanteen.assistant.port.AssistantConversationStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class AssistantConversationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-17T05:00:00Z");
    private static final CanteenScope SCOPE = new CanteenScope("SCHOOL-ASSIST", "CANTEEN-ASSIST");
    private static final AuthPrincipal PRINCIPAL = new AuthPrincipal(
            "USER-ASSIST", "assistant-user", "Assistant User", Role.CANTEEN_STAFF,
            SCOPE.schoolId(), SCOPE.canteenId());

    @Test
    void persists_a_clarification_without_creating_a_business_run() {
        AssistantConversationStore store = mock(AssistantConversationStore.class);
        AgentRuntime runtime = mock(AgentRuntime.class);
        AgentExecutionService execution = mock(AgentExecutionService.class);
        SkillRegistry skills = mock(SkillRegistry.class);
        BusinessAuthorizationPolicy policy = mock(BusinessAuthorizationPolicy.class);
        ExecutionContext context = ExecutionContext.fromTrustedPrincipal(
                "request-assist-1", PRINCIPAL, SCOPE,
                Set.of(Role.CANTEEN_STAFF), Set.of());
        AssistantConversation conversation = AssistantConversation.active(
                "CONVERSATION-001", context, NOW);
        when(store.ensureConversation(anyString(), any(), any())).thenReturn(conversation);
        when(store.findByIdempotency(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(store.nextSequence("CONVERSATION-001")).thenReturn(1L);

        AssistantConversationService service = new AssistantConversationService(
                new RuleBasedAssistantIntentResolver(),
                store,
                runtime,
                execution,
                skills,
                policy,
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        AssistantTurn turn = service.handle(
                "CONVERSATION-001",
                "帮我查一下这批食材的溯源",
                "message-001",
                context,
                PRINCIPAL);

        assertThat(turn.kind()).isEqualTo("CLARIFICATION");
        assertThat(turn.missingFields()).containsExactly("traceCode");
        assertThat(turn.message()).contains("溯源码");
        verify(runtime, never()).start(any(), any());
        verify(execution, never()).execute(any(), any());
        verify(store).append(any(AssistantConversationStore.StoredTurn.class));
    }

    @Test
    void locks_the_conversation_before_loading_pending_clarification_state() {
        AssistantConversationStore store = mock(AssistantConversationStore.class);
        AgentRuntime runtime = mock(AgentRuntime.class);
        AgentExecutionService execution = mock(AgentExecutionService.class);
        SkillRegistry skills = mock(SkillRegistry.class);
        BusinessAuthorizationPolicy policy = mock(BusinessAuthorizationPolicy.class);
        ExecutionContext context = ExecutionContext.fromTrustedPrincipal(
                "request-assist-lock", PRINCIPAL, SCOPE,
                Set.of(Role.CANTEEN_STAFF), Set.of());
        AssistantConversation conversation = AssistantConversation.active(
                "CONVERSATION-LOCK", context, NOW);
        when(store.ensureConversation(anyString(), any(), any())).thenReturn(conversation);
        when(store.findByIdempotency(anyString(), anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(store.findClarification("CONVERSATION-LOCK")).thenReturn(Optional.empty());
        when(store.nextSequence("CONVERSATION-LOCK")).thenReturn(1L);

        AssistantConversationService service = new AssistantConversationService(
                new RuleBasedAssistantIntentResolver(),
                store,
                runtime,
                execution,
                skills,
                policy,
                new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(NOW, ZoneOffset.UTC));

        service.handle(
                "CONVERSATION-LOCK",
                "帮我安排明天的采购",
                "lock-message-001",
                context,
                PRINCIPAL);

        InOrder order = inOrder(store);
        order.verify(store).lockConversation("CONVERSATION-LOCK");
        order.verify(store).findClarification("CONVERSATION-LOCK");
    }
}
