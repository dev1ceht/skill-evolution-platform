package com.example.smartcanteen.http;

import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class AlertCenterHttpTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanAlerts() {
        jdbc.update("DELETE FROM alert_records WHERE school_id = ?", "SCHOOL-ALERT-HTTP");
    }

    @Test
    void report_is_idempotent_and_disposal_is_available_through_pdf_compatibility_aliases()
            throws Exception {
        String payload = """
                {
                  "source":"MORNING_INSPECTION",
                  "thirdWarnId":"MI-HTTP-001",
                  "schoolId":"SCHOOL-ALERT-HTTP",
                  "schoolName":"Morning inspection school",
                  "areaCode":"440100",
                  "deviceId":"MI-001",
                  "deviceName":"Morning inspection device",
                  "canteenId":"CANTEEN-ALERT-HTTP",
                  "warnHappenTime":"2026-08-12 02:15:30",
                  "alarmEventId":"HAND_TEMPERATURE",
                  "warnFullPic":"https://files.example/morning.jpg",
                  "warnContent":"hand temperature is abnormal"
                }
                """;

        mvc.perform(post("/alarmApi/warn/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warnId").value("MORNING_INSPECTION:MI-HTTP-001"))
                .andExpect(jsonPath("$.data.status").value("UNPROCESSED"));

        mvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload.replace("2026-08-12 02:15:30", "2026-08-12T02:15:30Z")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warnId").value("MORNING_INSPECTION:MI-HTTP-001"));

        mvc.perform(post("/alarmApi/warnResult/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "source":"MORNING_INSPECTION",
                                  "thirdWarnId":"MI-HTTP-001",
                                  "processStatus":1,
                                  "processTime":"2026-08-12 02:20:00",
                                  "processUser":"operator-1",
                                  "processContent":"rechecked and cleared",
                                  "processFile":"https://files.example/disposal.jpg"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSED"))
                .andExpect(jsonPath("$.data.processUser").value("operator-1"));

        mvc.perform(get("/alarmWarn/school/queryPage")
                        .param("schoolId", "SCHOOL-ALERT-HTTP")
                        .param("source", "MORNING_INSPECTION")
                        .param("warnStatus", "\u5df2\u5904\u7406")
                        .param("alarmEventId", "HAND_TEMPERATURE")
                        .param("startDate", "2026-08-12")
                        .param("endDate", "2026-08-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records", hasSize(1)))
                .andExpect(jsonPath("$.data.records[0].thirdWarnId").value("MI-HTTP-001"));
    }

    @Test
    void non_ai_external_report_can_derive_a_stable_id_when_third_warn_id_is_absent()
            throws Exception {
        mvc.perform(post("/alarmApi/warn/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schoolId":"SCHOOL-ALERT-HTTP",
                                  "warnHappenTime":"2026-08-12 03:00:00",
                                  "alarmEventId":"LEDGER_MISSING",
                                  "warnContent":"ledger item is missing"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("DISTRICT_PLATFORM"))
                .andExpect(jsonPath("$.data.thirdWarnId").value(org.hamcrest.Matchers.startsWith("generated-")));
    }

    @Test
    void missing_external_id_uses_normalized_event_identity_and_conflicts_on_changed_payload()
            throws Exception {
        String first = """
                {
                  "source":"BRIGHT_KITCHEN",
                  "schoolId":"SCHOOL-ALERT-HTTP",
                  "canteenId":"CANTEEN-ALERT-HTTP",
                  "deviceId":"CAM-HTTP-001",
                  "warnHappenTime":"2026-08-12 03:00:00",
                  "alarmEventId":"AI_CAPTURE",
                  "warnContent":"temperature is abnormal"
                }
                """;

        String firstWarnId = warnId(mvc.perform(post("/alarmApi/warn/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(first))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        String equivalentTime = first.replace(
                "2026-08-12 03:00:00", "2026-08-12T03:00:00Z");
        String secondWarnId = warnId(mvc.perform(post("/alarmApi/warn/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(equivalentTime))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        assertThat(secondWarnId).isEqualTo(firstWarnId);

        mvc.perform(post("/alarmApi/warn/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(equivalentTime.replace(
                                "temperature is abnormal", "temperature is normal")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void changed_payload_for_the_same_external_id_uses_the_unified_error_envelope()
            throws Exception {
        String first = """
                {
                  "source":"BRIGHT_KITCHEN",
                  "thirdWarnId":"BK-CONFLICT",
                  "schoolId":"SCHOOL-ALERT-HTTP",
                  "warnHappenTime":"2026-08-12T02:15:30Z",
                  "alarmEventId":"AI_CAPTURE",
                  "warnContent":"first"
                }
                """;
        String changed = first.replace("first", "changed");
        mvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(first))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changed))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    private String warnId(String responseBody) throws Exception {
        return objectMapper.readTree(responseBody).path("data").path("warnId").asText();
    }
}
