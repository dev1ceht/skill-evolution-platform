package com.example.smartcanteen.http;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ComplianceRecordHttpTest {

    private static final String SCHOOL = "SCHOOL-P3-COMPLIANCE";
    private static final String CANTEEN = "CANTEEN-P3-COMPLIANCE";
    private static final String SCOPE = "schoolId=" + SCHOOL + "&canteenId=" + CANTEEN;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetScope() {
        jdbc.update("DELETE FROM compliance_record_history WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM compliance_records WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM alert_records WHERE school_id = ? AND source = 'COMPLIANCE'", SCHOOL);
        jdbc.update("DELETE FROM canteens WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM schools WHERE id = ?", SCHOOL);
        jdbc.update("INSERT INTO schools (id, name) VALUES (?, ?)", SCHOOL, "阶段3合规学校");
        jdbc.update(
                "INSERT INTO canteens (id, school_id, name) VALUES (?, ?, ?)",
                CANTEEN,
                SCHOOL,
                "阶段3合规食堂");
    }

    @Test
    void compliance_record_requires_review_history_and_idempotent_expiry_alert() throws Exception {
        String createBody = """
                {
                  "recordId":"COMPLIANCE-P3-HTTP",
                  "category":"LICENSE",
                  "subjectType":"CANTEEN",
                  "subjectId":"CANTEEN-P3-COMPLIANCE",
                  "subjectName":"阶段3合规食堂",
                  "title":"食品经营许可证",
                  "credentialNo":"LIC-P3-HTTP",
                  "validFrom":"2026-01-01",
                  "validTo":"2026-08-20",
                  "attachmentRefs":["https://example.test/license.pdf"]
                }
                """;
        mvc.perform(post("/api/v1/compliance-records?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.version").value(0));

        mvc.perform(post("/api/v1/compliance-records/COMPLIANCE-P3-HTTP/submit?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(post("/api/v1/compliance-records/COMPLIANCE-P3-HTTP/review?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"status\":\"APPROVED\",\"reviewRemark\":\"资料已核验\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.version").value(2));

        mvc.perform(post("/api/v1/compliance-records/expiry-scan?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"asOf\":\"2026-08-14\",\"windowDays\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].source").value("COMPLIANCE"))
                .andExpect(jsonPath("$.data[0].alarmEventId").value("COMPLIANCE_EXPIRY"));
        mvc.perform(post("/api/v1/compliance-records/expiry-scan?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"asOf\":\"2026-08-14\",\"windowDays\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM alert_records WHERE school_id = ? AND source = 'COMPLIANCE'",
                Integer.class,
                SCHOOL)).isEqualTo(1);

        mvc.perform(get("/api/v1/compliance-records/COMPLIANCE-P3-HTTP/history?" + SCOPE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].action").value("CREATED"))
                .andExpect(jsonPath("$.data[1].action").value("SUBMITTED"))
                .andExpect(jsonPath("$.data[2].action").value("APPROVED"));

        mvc.perform(post("/api/v1/compliance-records/COMPLIANCE-P3-HTTP/review?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"status\":\"APPROVED\",\"reviewRemark\":\"旧版本\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("SUBMITTED")));
        mvc.perform(get("/api/v1/compliance-records/COMPLIANCE-P3-HTTP?schoolId=" + SCHOOL
                        + "&canteenId=CANTEEN-OTHER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not found")));
    }
}
