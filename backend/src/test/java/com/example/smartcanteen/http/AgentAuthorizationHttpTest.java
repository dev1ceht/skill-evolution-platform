package com.example.smartcanteen.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.smartcanteen.security.PasswordHasher;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "smart-canteen.security.enabled=true",
        "SMART_CANTEEN_BOOTSTRAP_ADMIN_PASSWORD="
})
class AgentAuthorizationHttpTest {

    private static final String SCHOOL_ID = "SCHOOL-AGENT-AUTH";
    private static final String CANTEEN_ID = "CANTEEN-AGENT-AUTH";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordHasher passwords;

    @BeforeEach
    void seedUserAndScope() {
        jdbc.update("DELETE FROM auth_refresh_sessions WHERE user_id = ?", "USER-AGENT-AUTH");
        jdbc.update("DELETE FROM app_users WHERE user_id = ?", "USER-AGENT-AUTH");
        jdbc.update("DELETE FROM canteens WHERE id = ?", CANTEEN_ID);
        jdbc.update("DELETE FROM schools WHERE id = ?", SCHOOL_ID);
        jdbc.update(
                "INSERT INTO schools (id, name, region_code, status) VALUES (?, ?, ?, 'ACTIVE')",
                SCHOOL_ID, "Agent Auth School", "AGENT-AUTH");
        jdbc.update(
                "INSERT INTO canteens (id, school_id, name, status) VALUES (?, ?, ?, 'ACTIVE')",
                CANTEEN_ID, SCHOOL_ID, "Agent Auth Canteen");
        jdbc.update(
                "INSERT INTO app_users (user_id, username, password_hash, display_name, role, "
                        + "school_id, canteen_id) VALUES (?, ?, ?, ?, 'CANTEEN_STAFF', ?, ?)",
                "USER-AGENT-AUTH", "agent-auth", passwords.hash("agent-password"),
                "Agent Auth", SCHOOL_ID, CANTEEN_ID);
    }

    @Test
    void agent_run_requires_authentication_and_explicit_scope() throws Exception {
        mvc.perform(start("SCHOOL-AGENT-AUTH", CANTEEN_ID, null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        String token = login();
        mvc.perform(start("SCHOOL-AGENT-FOREIGN", "CANTEEN-AGENT-FOREIGN", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void authenticated_staff_can_create_a_failed_run_for_an_unknown_trace_without_fake_success() throws Exception {
        String token = login();
        mvc.perform(start(SCHOOL_ID, CANTEEN_ID, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.errorCode").value("TOOL_FAILED"));
    }

    private String login() throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"agent-auth\",\"password\":\"agent-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder start(
            String schoolId, String canteenId, String token) {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request = post(
                "/api/v1/agent/runs")
                .queryParam("schoolId", schoolId)
                .queryParam("canteenId", canteenId)
                .header("Idempotency-Key", "agent-auth-" + schoolId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"intent\":\"traceability.query\",\"input\":{\"traceCode\":\"TRACE-MISSING\"}}");
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }
}
