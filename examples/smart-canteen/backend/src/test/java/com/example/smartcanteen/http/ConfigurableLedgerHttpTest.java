package com.example.smartcanteen.http;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ConfigurableLedgerHttpTest {

    private static final String SCHOOL = "SCHOOL-P3-LEDGER";
    private static final String CANTEEN = "CANTEEN-P3-LEDGER";
    private static final String SCOPE = "schoolId=" + SCHOOL + "&canteenId=" + CANTEEN;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetScope() {
        jdbc.update("DELETE FROM operational_ledger_records WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM ledger_cycle_requirements WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM ledger_alerts WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM ledger_cycles WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM ledger_configurations WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM canteens WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM schools WHERE id = ?", SCHOOL);
        jdbc.update("INSERT INTO schools (id, name) VALUES (?, ?)", SCHOOL, "阶段3台账学校");
        jdbc.update(
                "INSERT INTO canteens (id, school_id, name) VALUES (?, ?, ?)",
                CANTEEN,
                SCHOOL,
                "阶段3台账食堂");
    }

    @Test
    void configuration_generates_one_cycle_and_completion_clears_only_after_required_fact() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/ledger-configurations?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "configurationId":"CONFIG-P3-LEDGER",
                                  "code":"TEMPERATURE_CHECK",
                                  "name":"冷藏温度记录",
                                  "frequency":"WEEKLY",
                                  "requiredFields":["temperature"],
                                  "template":{"unit":"℃"},
                                  "responsibleRole":"CANTEEN_STAFF",
                                  "reminderDays":1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("TEMPERATURE_CHECK"))
                .andExpect(jsonPath("$.data.version").value(0))
                .andReturn();
        String configurationId = JsonPath.read(
                created.getResponse().getContentAsString(), "$.data.id");

        MvcResult cycleResult = mvc.perform(post("/api/v1/ledger-cycles/configured/current?" + SCOPE
                        + "&asOf=" + LocalDate.of(2026, 8, 18)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].cycleId", notNullValue()))
                .andExpect(jsonPath("$.data[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data[0].missingLedgerCodes", hasSize(1)))
                .andReturn();
        String cycleId = JsonPath.read(
                cycleResult.getResponse().getContentAsString(), "$.data[0].cycleId");

        mvc.perform(post("/api/v1/ledger-cycles/configured/current?" + SCOPE
                        + "&asOf=" + LocalDate.of(2026, 8, 19)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].cycleId").value(cycleId));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_cycles WHERE school_id = ? AND canteen_id = ?",
                Integer.class,
                SCHOOL,
                CANTEEN)).isEqualTo(1);

        mvc.perform(post("/api/v1/ledger-cycles/configured/" + cycleId + "/records?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ledgerCode":"TEMPERATURE_CHECK","content":{"remark":"缺温度"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("temperature")));

        String body = """
                {"recordId":"LEDGER-P3-1","ledgerCode":"TEMPERATURE_CHECK",
                 "recorderId":"STAFF-P3","content":{"temperature":4.2},
                 "photos":["https://example.test/temperature.jpg"],"remark":"正常"}
                """;
        mvc.perform(post("/api/v1/ledger-cycles/configured/" + cycleId + "/records?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordId").value("LEDGER-P3-1"));
        mvc.perform(post("/api/v1/ledger-cycles/configured/" + cycleId + "/records?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordId").value("LEDGER-P3-1"));

        mvc.perform(post("/api/v1/ledger-cycles/configured/current?" + SCOPE
                        + "&asOf=" + LocalDate.of(2026, 8, 19)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("CLEARED"))
                .andExpect(jsonPath("$.data[0].missingLedgerCodes", hasSize(0)));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM operational_ledger_records WHERE school_id = ?",
                Integer.class,
                SCHOOL)).isEqualTo(1);

        mvc.perform(put("/api/v1/ledger-configurations/" + configurationId + "?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"TEMPERATURE_CHECK","name":"更新后的冷藏温度记录",
                                 "frequency":"WEEKLY","requiredFields":["temperature"],"version":0}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(put("/api/v1/ledger-configurations/" + configurationId + "?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"TEMPERATURE_CHECK","name":"过期更新",
                                 "frequency":"WEEKLY","requiredFields":["temperature"],"version":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("concurrently")));

        mvc.perform(get("/api/v1/ledger-configurations?schoolId=" + SCHOOL
                        + "&canteenId=CANTEEN-OTHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }
}
