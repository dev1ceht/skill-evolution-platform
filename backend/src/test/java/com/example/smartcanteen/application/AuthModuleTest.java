package com.example.smartcanteen.application;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        "BOOTSTRAP_ADMIN_PASSWORD="
})
class AuthModuleTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordHasher passwords;

    @BeforeEach
    void seedUser() {
        jdbc.update(
                "DELETE FROM alert_records WHERE third_warn_id IN (?, ?)",
                "AUTH-ALERT-OWN",
                "AUTH-ALERT-FOREIGN");
        jdbc.update("DELETE FROM canteens WHERE id = ?", "CANTEEN-AUTH");
        jdbc.update("DELETE FROM schools WHERE id = ?", "SCHOOL-AUTH");
        jdbc.update(
                "INSERT INTO schools (id, name, region_code, status) VALUES (?, ?, ?, 'ACTIVE')",
                "SCHOOL-AUTH", "Auth School", "AUTH-REGION");
        jdbc.update(
                "INSERT INTO canteens (id, school_id, name, status) VALUES (?, ?, ?, 'ACTIVE')",
                "CANTEEN-AUTH", "SCHOOL-AUTH", "Auth Canteen");
        jdbc.update("DELETE FROM auth_refresh_sessions WHERE user_id = ?", "USER-AUTH-TEST");
        jdbc.update("DELETE FROM audit_logs WHERE actor_user_id IN (?, ?)",
                "USER-AUTH-TEST", "USER-AUTH-SUPPLIER");
        jdbc.update("DELETE FROM app_users WHERE user_id = ?", "USER-AUTH-TEST");
        jdbc.update("DELETE FROM auth_refresh_sessions WHERE user_id = ?", "USER-AUTH-SUPPLIER");
        jdbc.update("DELETE FROM app_users WHERE user_id = ?", "USER-AUTH-SUPPLIER");
        jdbc.update(
                "INSERT INTO app_users (user_id, username, password_hash, display_name, role, school_id, canteen_id) "
                        + "VALUES (?, ?, ?, ?, 'SCHOOL_ADMIN', ?, ?)",
                "USER-AUTH-TEST",
                "auth-test",
                passwords.hash("correct-password"),
                "Auth Test",
                "SCHOOL-AUTH",
                "CANTEEN-AUTH");
        jdbc.update(
                "INSERT INTO app_users (user_id, username, password_hash, display_name, role, school_id, canteen_id) "
                        + "VALUES (?, ?, ?, ?, 'SUPPLIER', ?, ?)",
                "USER-AUTH-SUPPLIER",
                "supplier-test",
                passwords.hash("supplier-password"),
                "Supplier Test",
                "SCHOOL-AUTH",
                "CANTEEN-AUTH");
    }

    @Test
    void login_me_refresh_and_logout_rotate_refresh_tokens() throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"auth-test\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.userInfo.role").value("SCHOOL_ADMIN"))
                .andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.data.token");
        String refresh = JsonPath.read(login.getResponse().getContentAsString(), "$.data.refreshToken");

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("auth-test"));

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();
        String rotated = JsonPath.read(
                refreshed.getResponse().getContentAsString(), "$.data.refreshToken");
        mvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refresh + "\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rotated + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + rotated + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void protected_scope_api_rejects_missing_or_foreign_access() throws Exception {
        mvc.perform(get("/api/v1/ingredients")
                        .param("schoolId", "SCHOOL-AUTH")
                        .param("canteenId", "CANTEEN-AUTH"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"auth-test\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.data.token");
        mvc.perform(get("/api/v1/ingredients")
                        .param("schoolId", "SCHOOL-OTHER")
                        .param("canteenId", "CANTEEN-OTHER")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        MvcResult supplierLogin = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"supplier-test\",\"password\":\"supplier-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String supplierToken = JsonPath.read(
                supplierLogin.getResponse().getContentAsString(), "$.data.token");
        mvc.perform(post("/api/v1/ingredients")
                        .param("schoolId", "SCHOOL-AUTH")
                        .param("canteenId", "CANTEEN-AUTH")
                        .header("Authorization", "Bearer " + supplierToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"受限食材\",\"category\":\"测试\",\"baseUnit\":\"g\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
        mvc.perform(get("/api/v1/ingredients")
                        .param("schoolId", "SCHOOL-AUTH")
                        .param("canteenId", "CANTEEN-AUTH")
                        .header("Authorization", "Bearer " + supplierToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void disabling_an_account_invalidates_an_existing_access_token() throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"auth-test\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.data.token");

        jdbc.update("UPDATE app_users SET status = 'DISABLED' WHERE user_id = ?", "USER-AUTH-TEST");

        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    void alert_api_applies_role_and_payload_scope_boundaries() throws Exception {
        MvcResult login = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"auth-test\",\"password\":\"correct-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.data.token");

        String ownPayload = """
                {
                  "source":"BRIGHT_KITCHEN",
                  "thirdWarnId":"AUTH-ALERT-OWN",
                  "schoolId":"SCHOOL-AUTH",
                  "canteenId":"CANTEEN-AUTH",
                  "warnHappenTime":"2026-08-14T04:00:00Z",
                  "alarmEventId":"AUTH_SCOPE_CHECK",
                  "warnContent":"owned alert"
                }
                """;
        mvc.perform(post("/api/v1/alerts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ownPayload))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/alerts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ownPayload
                                .replace("AUTH-ALERT-OWN", "AUTH-ALERT-FOREIGN")
                                .replace("SCHOOL-AUTH", "SCHOOL-OTHER")
                                .replace("CANTEEN-AUTH", "CANTEEN-OTHER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }
}
