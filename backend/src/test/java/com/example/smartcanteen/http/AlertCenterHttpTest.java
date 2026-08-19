package com.example.smartcanteen.http;

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
class AlertCenterHttpTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanAlerts() {
        jdbc.update("DELETE FROM alert_records WHERE school_id = ?", "SCHOOL-ALERT-HTTP");
    }

    @Test
    void report_is_idempotent_and_disposal_is_available_through_canonical_api()
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
                  "warnHappenTime":"2026-08-12T02:15:30Z",
                  "alarmEventId":"HAND_TEMPERATURE",
                  "warnFullPic":"https://files.example/morning.jpg",
                  "warnContent":"hand temperature is abnormal"
                }
                """;

        mvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warnId").value("MORNING_INSPECTION:MI-HTTP-001"))
                .andExpect(jsonPath("$.data.status").value("UNPROCESSED"));

        mvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.warnId").value("MORNING_INSPECTION:MI-HTTP-001"));

        mvc.perform(post("/api/v1/alerts/MORNING_INSPECTION:MI-HTTP-001/disposal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "processStatus":1,
                                  "processTime":"2026-08-12T02:20:00Z",
                                  "processUser":"operator-1",
                                  "processContent":"rechecked and cleared",
                                  "processFile":"https://files.example/disposal.jpg"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSED"))
                .andExpect(jsonPath("$.data.processUser").value("operator-1"));

        mvc.perform(get("/api/v1/alerts")
                        .param("schoolId", "SCHOOL-ALERT-HTTP")
                        .param("source", "MORNING_INSPECTION")
                        .param("status", "PROCESSED")
                        .param("alarmEventId", "HAND_TEMPERATURE")
                        .param("startDate", "2026-08-12")
                        .param("endDate", "2026-08-12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records", hasSize(1)))
                .andExpect(jsonPath("$.data.records[0].thirdWarnId").value("MI-HTTP-001"));
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
}
