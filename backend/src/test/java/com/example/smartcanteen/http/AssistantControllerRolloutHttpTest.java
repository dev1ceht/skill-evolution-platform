package com.example.smartcanteen.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "smart-canteen.assistant.enabled=true",
        "smart-canteen.assistant.allowed-scopes=SCHOOL-PILOT/CANTEEN-PILOT"
})
@AutoConfigureMockMvc
class AssistantControllerRolloutHttpTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void rejects_an_assistant_request_outside_the_configured_pilot_scope() throws Exception {
        mvc.perform(post("/api/v1/assistant/conversations/CONV-ROLLOUT/messages")
                        .queryParam("schoolId", "SCHOOL-OTHER")
                        .queryParam("canteenId", "CANTEEN-OTHER")
                        .header("Idempotency-Key", "rollout-message-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .requestAttr(
                                AuthPrincipal.class.getName(),
                                new AuthPrincipal(
                                        "USER-ROLLOUT",
                                        "rollout-user",
                                        "Rollout User",
                                        Role.CANTEEN_STAFF,
                                        "SCHOOL-OTHER",
                                        "CANTEEN-OTHER"))
                        .content("{\"message\":\"查询 TRACE-001 的食品溯源\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }
}
