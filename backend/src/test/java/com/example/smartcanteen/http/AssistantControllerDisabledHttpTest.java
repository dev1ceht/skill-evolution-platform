package com.example.smartcanteen.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "smart-canteen.assistant.enabled=false")
@AutoConfigureMockMvc
class AssistantControllerDisabledHttpTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void rejects_requests_when_the_assistant_pilot_is_disabled() throws Exception {
        mvc.perform(post("/api/v1/assistant/conversations/CONV-DISABLED/messages")
                        .queryParam("schoolId", "SCHOOL-DISABLED")
                        .queryParam("canteenId", "CANTEEN-DISABLED")
                        .header("Idempotency-Key", "disabled-message-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"查询 TRACE-001 的食品溯源\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }
}
