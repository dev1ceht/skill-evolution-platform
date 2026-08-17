package com.example.smartcanteen.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.smartcanteen.security.PasswordHasher;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "smart-canteen.security.enabled=true",
        "SMART_CANTEEN_BOOTSTRAP_ADMIN_PASSWORD="
})
class PlatformFoundationHttpTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordHasher passwords;

    @BeforeEach
    void seedPlatformFixture() {
        jdbc.update(
                "DELETE FROM user_scope_assignments WHERE user_id IN "
                        + "(SELECT user_id FROM app_users WHERE username LIKE 'foundation-%')");
        jdbc.update(
                "DELETE FROM user_roles WHERE user_id IN "
                        + "(SELECT user_id FROM app_users WHERE username LIKE 'foundation-%')");
        jdbc.update(
                "DELETE FROM auth_refresh_sessions WHERE user_id IN "
                        + "(SELECT user_id FROM app_users WHERE username LIKE 'foundation-%')");
        jdbc.update(
                "DELETE FROM audit_logs WHERE actor_user_id IN "
                        + "(SELECT user_id FROM app_users WHERE username LIKE 'foundation-%')");
        jdbc.update("DELETE FROM app_users WHERE username LIKE 'foundation-%'");
        jdbc.update("DELETE FROM canteens WHERE id LIKE 'PF-%'");
        jdbc.update("DELETE FROM schools WHERE id LIKE 'PF-%'");

        jdbc.update(
                "INSERT INTO schools (id, name, region_code, status) VALUES (?, ?, ?, 'ACTIVE')",
                "PF-SCHOOL-ALLOWED", "授权学校", "REGION-ALLOWED");
        jdbc.update(
                "INSERT INTO schools (id, name, region_code, status) VALUES (?, ?, ?, 'ACTIVE')",
                "PF-SCHOOL-BLOCKED", "未授权学校", "REGION-BLOCKED");
        jdbc.update(
                "INSERT INTO canteens (id, school_id, name, address, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                "PF-CANTEEN-ALLOWED", "PF-SCHOOL-ALLOWED", "授权食堂", "一号楼");
        jdbc.update(
                "INSERT INTO canteens (id, school_id, name, address, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                "PF-CANTEEN-BLOCKED", "PF-SCHOOL-BLOCKED", "未授权食堂", "二号楼");

        jdbc.update(
                "INSERT INTO schools (id, name, region_code, status) VALUES (?, ?, ?, 'DISABLED')",
                "PF-SCHOOL-DISABLED", "DISABLED-SCHOOL", "REGION-DISABLED");
        jdbc.update(
                "INSERT INTO canteens (id, school_id, name, address, status) VALUES (?, ?, ?, ?, 'ACTIVE')",
                "PF-CANTEEN-DISABLED", "PF-SCHOOL-DISABLED", "DISABLED-CANTEEN", "DISABLED");

        insertUser("foundation-admin", "USER-PF-ADMIN", "SYSTEM_ADMIN", null, null);
        insertUser("foundation-regulator", "USER-PF-REGULATOR", "REGULATOR", null, null);
        insertUser("foundation-regulator-none", "USER-PF-REGULATOR-NONE", "REGULATOR", null, null);
        insertUser("foundation-regulator-canteen", "USER-PF-REGULATOR-CANTEEN", "REGULATOR", null, null);
        insertUser("foundation-school-admin", "USER-PF-SCHOOL-ADMIN", "SCHOOL_ADMIN",
                "PF-SCHOOL-ALLOWED", null);
        insertUser("foundation-blocked-user", "USER-PF-BLOCKED-USER", "CANTEEN_STAFF",
                "PF-SCHOOL-BLOCKED", "PF-CANTEEN-BLOCKED");
        jdbc.update(
                "INSERT INTO user_scope_assignments "
                        + "(assignment_id, user_id, scope_type, region_code) VALUES (?, ?, 'REGION', ?)",
                "PF-REGION-GRANT", "USER-PF-REGULATOR", "REGION-ALLOWED");
        jdbc.update(
                "INSERT INTO user_scope_assignments "
                        + "(assignment_id, user_id, scope_type, school_id, canteen_id) "
                        + "VALUES (?, ?, 'CANTEEN', ?, ?)",
                "PF-CANTEEN-GRANT", "USER-PF-REGULATOR-CANTEEN",
                "PF-SCHOOL-ALLOWED", "PF-CANTEEN-ALLOWED");
    }

    @Test
    void systemAdminCanManageOrganizationsRolesUsersAndAudit() throws Exception {
        String token = login("foundation-admin", "foundation-password");

        mvc.perform(post("/api/v1/ingredients")
                        .param("schoolId", "PF-SCHOOL-DISABLED")
                        .param("canteenId", "PF-CANTEEN-DISABLED")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"disabled-write","category":"test","baseUnit":"kg"}
                                """))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/schools")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"PF-SCHOOL-NEW","name":"新建学校","regionCode":"REGION-NEW"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("PF-SCHOOL-NEW"));

        mvc.perform(post("/api/v1/canteens")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"PF-CANTEEN-NEW","schoolId":"PF-SCHOOL-NEW","name":"新建食堂","address":"三号楼"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schoolId").value("PF-SCHOOL-NEW"));

        mvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code == 'SYSTEM_ADMIN')]").isNotEmpty());

        mvc.perform(get("/api/v1/canteens")
                        .param("schoolId", "PF-SCHOOL-NEW")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("PF-CANTEEN-NEW"));

        MvcResult created = mvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"foundation-created",
                                  "password":"foundation-password",
                                  "displayName":"新建员工",
                                  "primaryRole":"CANTEEN_STAFF",
                                  "roles":["CANTEEN_STAFF"],
                                  "schoolId":"PF-SCHOOL-ALLOWED",
                                  "canteenId":"PF-CANTEEN-ALLOWED",
                                  "scopeGrants":[{"type":"CANTEEN","schoolId":"PF-SCHOOL-ALLOWED","canteenId":"PF-CANTEEN-ALLOWED"}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("foundation-created"))
                .andExpect(jsonPath("$.data.roles[0]").value("CANTEEN_STAFF"))
                .andReturn();
        String userId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.userId");

        Integer scopeCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_scope_assignments WHERE user_id = ?",
                Integer.class, userId);
        org.junit.jupiter.api.Assertions.assertEquals(1, scopeCount);

        mvc.perform(put("/api/v1/users/" + userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"鏇存柊鍛樺伐\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("鏇存柊鍛樺伐"));

        mvc.perform(put("/api/v1/users/" + userId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scopeGrants":[{"type":"CANTEEN","schoolId":"PF-SCHOOL-ALLOWED","canteenId":"PF-CANTEEN-ALLOWED"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scopeGrants[0].userId").value(userId));

        mvc.perform(post("/api/v1/users/" + userId + "/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"foundation-created\",\"password\":\"foundation-password\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isNotEmpty());

        Integer auditCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE actor_user_id = 'USER-PF-ADMIN'",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertTrue(auditCount != null && auditCount >= 4);
    }

    @Test
    void regulatorMustHaveExplicitRegionScope() throws Exception {
        String token = login("foundation-regulator", "foundation-password");

        mvc.perform(get("/api/v1/ingredients")
                        .param("schoolId", "PF-SCHOOL-ALLOWED")
                        .param("canteenId", "PF-CANTEEN-ALLOWED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/ingredients")
                        .param("schoolId", "PF-SCHOOL-BLOCKED")
                        .param("canteenId", "PF-CANTEEN-BLOCKED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        mvc.perform(get("/api/v1/canteens")
                        .param("schoolId", "PF-SCHOOL-ALLOWED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("PF-CANTEEN-ALLOWED"));

        mvc.perform(get("/api/v1/canteens")
                        .param("schoolId", "PF-SCHOOL-BLOCKED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void canteenGrantDoesNotExpandToTheWholeSchool() throws Exception {
        String token = login("foundation-regulator-canteen", "foundation-password");

        mvc.perform(get("/api/v1/canteens")
                        .param("schoolId", "PF-SCHOOL-ALLOWED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("PF-CANTEEN-ALLOWED"));
    }

    @Test
    void unknownScopesAreRejectedForInternalAndOrdinaryWriters() throws Exception {
        String adminToken = login("foundation-admin", "foundation-password");

        mvc.perform(post("/api/v1/alerts")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source":"BRIGHT_KITCHEN",
                                  "thirdWarnId":"UNKNOWN-SCHOOL-ALERT",
                                  "schoolId":"PF-SCHOOL-UNKNOWN",
                                  "warnHappenTime":"2026-08-14T03:00:00Z",
                                  "alarmEventId":"UNKNOWN_SCOPE",
                                  "warnContent":"should be rejected"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        String schoolAdminToken = login("foundation-school-admin", "foundation-password");
        mvc.perform(get("/api/v1/ingredients")
                        .param("schoolId", "PF-SCHOOL-ALLOWED")
                        .param("canteenId", "PF-CANTEEN-UNKNOWN")
                        .header("Authorization", "Bearer " + schoolAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void explicitScopeRevocationTakesEffectForAnExistingAccessToken() throws Exception {
        String adminToken = login("foundation-admin", "foundation-password");
        MvcResult created = mvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"foundation-managed-scope",
                                  "password":"foundation-password",
                                  "displayName":"Managed Scope",
                                  "primaryRole":"CANTEEN_STAFF",
                                  "roles":["CANTEEN_STAFF"],
                                  "schoolId":"PF-SCHOOL-ALLOWED",
                                  "canteenId":"PF-CANTEEN-ALLOWED",
                                  "scopeGrants":[{"type":"CANTEEN","schoolId":"PF-SCHOOL-ALLOWED","canteenId":"PF-CANTEEN-ALLOWED"}]
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        String userId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.userId");
        String userToken = login("foundation-managed-scope", "foundation-password");

        mvc.perform(put("/api/v1/users/" + userId + "/scopes")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scopeGrants\":[]}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/ingredients")
                        .param("schoolId", "PF-SCHOOL-ALLOWED")
                        .param("canteenId", "PF-CANTEEN-ALLOWED")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void schoolAdminCannotManageAUserOutsideItsSchool() throws Exception {
        String token = login("foundation-school-admin", "foundation-password");

        mvc.perform(post("/api/v1/users/USER-PF-BLOCKED-USER/status")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void roleRevocationTakesEffectForAnExistingAccessToken() throws Exception {
        String token = login("foundation-admin", "foundation-password");
        jdbc.update("DELETE FROM user_roles WHERE user_id = ?", "USER-PF-ADMIN");
        jdbc.update("UPDATE app_users SET role = 'REGULATOR' WHERE user_id = ?", "USER-PF-ADMIN");

        mvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void regulatorWithoutGrantCannotUseScopedOperationalApi() throws Exception {
        String token = login("foundation-regulator-none", "foundation-password");

        mvc.perform(get("/api/v1/ingredients")
                        .param("schoolId", "PF-SCHOOL-ALLOWED")
                        .param("canteenId", "PF-CANTEEN-ALLOWED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        mvc.perform(post("/alarmApi/warn/report")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schoolId":"PF-SCHOOL-ALLOWED",
                                  "warnHappenTime":"2026-08-14 03:00:00",
                                  "alarmEventId":"UNAUTHORIZED_EXTERNAL",
                                  "warnContent":"should be rejected"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    void regulatorCannotQueryAlertsWithoutExplicitScope() throws Exception {
        String token = login("foundation-regulator", "foundation-password");

        mvc.perform(get("/api/v1/alerts")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    private void insertUser(
            String username, String userId, String role, String schoolId, String canteenId) {
        jdbc.update(
                "INSERT INTO app_users "
                        + "(user_id, username, password_hash, display_name, role, school_id, canteen_id, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                userId, username, passwords.hash("foundation-password"), username, role, schoolId, canteenId);
        jdbc.update(
                "INSERT INTO user_roles (user_id, role_code) VALUES (?, ?)", userId, role);
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username
                                + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.data.token");
    }
}
