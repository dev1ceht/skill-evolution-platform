package com.example.smartcanteen.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.smartcanteen.assistant.domain.AssistantResolution;
import com.example.smartcanteen.assistant.infrastructure.DeepSeekAssistantModelResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DeepSeekAssistantModelResolverTest {

    @Test
    void resolves_a_safe_traceability_result_from_openai_compatible_response() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DeepSeekAssistantModelResolver resolver = new DeepSeekAssistantModelResolver(
                builder.baseUrl("https://deepseek.test").build(),
                new ObjectMapper(),
                "deepseek-v4-flash",
                "test-key",
                true);
        server.expect(requestTo("https://deepseek.test/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andRespond(withSuccess(
                        """
                        {"choices":[{"message":{"content":"{\\"type\\":\\"TRACEABILITY_QUERY\\",\\"traceCode\\":\\"TRACE-001\\"}"}}]}
                        """,
                        MediaType.APPLICATION_JSON));

        Optional<AssistantResolution> result = resolver.resolve("查询 TRACE-001", Optional.empty());

        assertThat(result).get().extracting(AssistantResolution::type)
                .isEqualTo(AssistantResolution.Type.TRACEABILITY_QUERY);
        assertThat(result).get().extracting(AssistantResolution::traceCode)
                .isEqualTo("TRACE-001");
        server.verify();
    }

    @Test
    void fails_closed_when_model_is_disabled_or_key_is_missing() {
        DeepSeekAssistantModelResolver disabled = new DeepSeekAssistantModelResolver(
                RestClient.builder().build(),
                new ObjectMapper(),
                "deepseek-v4-flash",
                "test-key",
                false);
        DeepSeekAssistantModelResolver missingKey = new DeepSeekAssistantModelResolver(
                RestClient.builder().build(),
                new ObjectMapper(),
                "deepseek-v4-flash",
                "",
                true);

        assertThat(disabled.resolve("查询 TRACE-001", Optional.empty())).isEmpty();
        assertThat(missingKey.resolve("查询 TRACE-001", Optional.empty())).isEmpty();
    }

    @Test
    void resolves_a_read_only_procurement_gap_result_from_openai_compatible_response() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DeepSeekAssistantModelResolver resolver = new DeepSeekAssistantModelResolver(
                builder.baseUrl("https://deepseek.test").build(),
                new ObjectMapper(),
                "deepseek-v4-flash",
                "test-key",
                true);
        server.expect(requestTo("https://deepseek.test/chat/completions"))
                .andRespond(withSuccess(
                        """
                        {"choices":[{"message":{"content":"{\\\"type\\\":\\\"PROCUREMENT_GAP_QUERY\\\",\\\"intent\\\":\\\"procurement.gap.query\\\",\\\"menuDate\\\":\\\"2026-08-22\\\",\\\"mealTime\\\":\\\"LUNCH\\\"}"}}]}
                        """,
                        MediaType.APPLICATION_JSON));

        Optional<AssistantResolution> result = resolver.resolve("核对明日菜单食材缺口", Optional.empty());

        assertThat(result).get().extracting(AssistantResolution::type)
                .isEqualTo(AssistantResolution.Type.PROCUREMENT_GAP_QUERY);
        assertThat(result).get().extracting(item -> item.parameters().get("menuDate"))
                .isEqualTo(LocalDate.of(2026, 8, 22).toString());
        assertThat(result).get().extracting(item -> item.parameters().get("mealTime"))
                .isEqualTo("LUNCH");
        server.verify();
    }

    @Test
    void fails_closed_when_provider_response_exceeds_the_buffer_limit() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DeepSeekAssistantModelResolver resolver = new DeepSeekAssistantModelResolver(
                builder.baseUrl("https://deepseek.test").build(),
                new ObjectMapper(),
                "deepseek-v4-flash",
                "test-key",
                true);
        String response = "{\"choices\":[{\"message\":{\"content\":\""
                + "x".repeat(70_000)
                + "\"}}]}";
        server.expect(requestTo("https://deepseek.test/chat/completions"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        assertThat(resolver.resolve("无法确定的请求", Optional.empty())).isEmpty();
        server.verify();
    }

    @Test
    void rejects_oversized_input_before_calling_provider() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DeepSeekAssistantModelResolver resolver = new DeepSeekAssistantModelResolver(
                builder.baseUrl("https://deepseek.test").build(),
                new ObjectMapper(),
                "deepseek-v4-flash",
                "test-key",
                true);

        assertThat(resolver.resolve("x".repeat(2_001), Optional.empty())).isEmpty();
        server.verify();
    }

    @Test
    void rejects_insecure_or_unallowlisted_production_base_urls() {
        assertThatThrownBy(() -> new DeepSeekAssistantModelResolver(
                RestClient.builder(),
                new ObjectMapper(),
                "http://api.deepseek.com",
                "deepseek-v4-flash",
                "test-key",
                true,
                2_000,
                10_000,
                65_536,
                "api.deepseek.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DeepSeekAssistantModelResolver(
                RestClient.builder(),
                new ObjectMapper(),
                "https://proxy.example.test",
                "deepseek-v4-flash",
                "test-key",
                true,
                2_000,
                10_000,
                65_536,
                "api.deepseek.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
