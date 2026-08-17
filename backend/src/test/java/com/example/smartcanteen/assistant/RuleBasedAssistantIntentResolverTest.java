package com.example.smartcanteen.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.smartcanteen.assistant.application.RuleBasedAssistantIntentResolver;
import com.example.smartcanteen.assistant.domain.AssistantClarification;
import com.example.smartcanteen.assistant.domain.AssistantResolution;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuleBasedAssistantIntentResolverTest {

    private final RuleBasedAssistantIntentResolver resolver =
            new RuleBasedAssistantIntentResolver();

    @Test
    void resolves_a_traceability_message_with_a_trace_code() {
        AssistantResolution result = resolver.resolve("请查询 TRACE-001 的食品溯源信息");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.TRACEABILITY_QUERY);
        assertThat(result.intent()).isEqualTo("traceability.query");
        assertThat(result.traceCode()).isEqualTo("TRACE-001");
        assertThat(result.missingFields()).isEmpty();
    }

    @Test
    void asks_for_the_trace_code_when_the_user_requests_traceability_without_one() {
        AssistantResolution result = resolver.resolve("帮我查一下这批食材的溯源");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.CLARIFICATION);
        assertThat(result.missingFields()).containsExactly("traceCode");
        assertThat(result.message()).contains("溯源码");
    }

    @Test
    void does_not_treat_a_menu_id_as_a_traceability_code() {
        AssistantResolution result = resolver.resolve("查询 MENU-001 的食品溯源");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.CLARIFICATION);
        assertThat(result.missingFields()).containsExactly("traceCode");
    }

    @Test
    void explains_the_supported_capability_for_an_unrelated_message() {
        AssistantResolution result = resolver.resolve("帮我安排明天的采购");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.UNSUPPORTED);
        assertThat(result.intent()).isNull();
        assertThat(result.message()).contains("食品溯源");
    }

    @Test
    void resolves_a_menu_query_with_a_menu_id() {
        AssistantResolution result = resolver.resolve("请查询 MENU-001 的午餐菜单");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.MENU_QUERY);
        assertThat(result.intent()).isEqualTo("menu.query");
        assertThat(result.menuId()).isEqualTo("MENU-001");
    }

    @Test
    void asks_for_a_menu_id_when_menu_query_is_missing_one() {
        AssistantResolution result = resolver.resolve("帮我看看今天的菜单");

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.CLARIFICATION);
        assertThat(result.missingFields()).containsExactly("menuId");
        assertThat(result.message()).contains("MENU-001");
    }

    @Test
    void resolves_a_trace_code_as_the_answer_to_a_pending_clarification() {
        AssistantClarification pending = new AssistantClarification(
                "CONV-001",
                "traceability.query",
                "帮我查一下这批食材的溯源",
                java.util.List.of("traceCode"),
                Instant.parse("2026-08-17T05:00:00Z"),
                Instant.parse("2026-08-17T05:00:00Z"));

        AssistantResolution result = resolver.resolve("TRACE-001", Optional.of(pending));

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.TRACEABILITY_QUERY);
        assertThat(result.traceCode()).isEqualTo("TRACE-001");
    }

    @Test
    void does_not_consume_a_new_unsupported_request_as_a_pending_answer() {
        AssistantClarification pending = new AssistantClarification(
                "CONV-001",
                "traceability.query",
                "帮我查一下这批食材的溯源",
                java.util.List.of("traceCode"),
                Instant.parse("2026-08-17T05:00:00Z"),
                Instant.parse("2026-08-17T05:00:00Z"));

        AssistantResolution result = resolver.resolve("帮我安排明天的采购", Optional.of(pending));

        assertThat(result.type()).isEqualTo(AssistantResolution.Type.UNSUPPORTED);
        assertThat(result.message()).contains("食品溯源");
    }
}
