package com.example.smartcanteen.http;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class LedgerCycleHttpTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void cycle_completion_is_idempotent_and_clears_alert_after_last_requirement() throws Exception {
        mvc.perform(post("/api/v1/ledger-cycles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schoolId":"SCHOOL-PHASE1",
                                  "canteenId":"CANTEEN-PHASE1",
                                  "cycleId":"CYCLE-PHASE1-001",
                                  "ledgerCodes":["PURCHASE_ACCEPTANCE","SAMPLE_RETENTION"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schoolId").value("SCHOOL-PHASE1"))
                .andExpect(jsonPath("$.data.canteenId").value("CANTEEN-PHASE1"))
                .andExpect(jsonPath("$.data.cycleId").value("CYCLE-PHASE1-001"))
                .andExpect(jsonPath("$.data.cleared").value(false))
                .andExpect(jsonPath("$.data.missingLedgerCodes", hasSize(2)));

        String completion = """
                {
                  "schoolId":"SCHOOL-PHASE1",
                  "canteenId":"CANTEEN-PHASE1",
                  "ledgerCode":"PURCHASE_ACCEPTANCE"
                }
                """;

        mvc.perform(post("/api/v1/ledger-cycles/{cycleId}/records", "CYCLE-PHASE1-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cleared").value(false))
                .andExpect(jsonPath("$.data.missingLedgerCodes", hasSize(1)))
                .andExpect(jsonPath("$.data.missingLedgerCodes[0]").value("SAMPLE_RETENTION"));

        mvc.perform(post("/api/v1/ledger-cycles/{cycleId}/records", "CYCLE-PHASE1-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(completion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cleared").value(false))
                .andExpect(jsonPath("$.data.missingLedgerCodes", hasSize(1)))
                .andExpect(jsonPath("$.data.missingLedgerCodes[0]").value("SAMPLE_RETENTION"));

        mvc.perform(post("/api/v1/ledger-cycles/{cycleId}/records", "CYCLE-PHASE1-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schoolId":"SCHOOL-PHASE1",
                                  "canteenId":"CANTEEN-PHASE1",
                                  "ledgerCode":"SAMPLE_RETENTION"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cleared").value(true))
                .andExpect(jsonPath("$.data.missingLedgerCodes").isEmpty());

        mvc.perform(get("/api/v1/ledger-cycles/{cycleId}/alerts/current", "CYCLE-PHASE1-001")
                        .param("schoolId", "SCHOOL-PHASE1")
                        .param("canteenId", "CANTEEN-PHASE1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cleared").value(true))
                .andExpect(jsonPath("$.data.missingLedgerCodes").isEmpty());
    }

    @Test
    void cycle_definition_cannot_be_reused_for_a_different_requirement_set() throws Exception {
        String cycle = "CYCLE-PHASE1-CONFLICT";

        mvc.perform(post("/api/v1/ledger-cycles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schoolId":"SCHOOL-PHASE1",
                                  "canteenId":"CANTEEN-PHASE1",
                                  "cycleId":"CYCLE-PHASE1-CONFLICT",
                                  "ledgerCodes":["PURCHASE_ACCEPTANCE"]
                                }
                                """))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/ledger-cycles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schoolId":"SCHOOL-PHASE1",
                                  "canteenId":"CANTEEN-PHASE1",
                                  "cycleId":"CYCLE-PHASE1-CONFLICT",
                                  "ledgerCodes":["SAMPLE_RETENTION"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void alerts_are_isolated_by_school_and_canteen_scope() throws Exception {
        String sharedCycleId = "CYCLE-SCOPE-SHARED";
        startCycle("SCHOOL-SCOPE-A", "CANTEEN-SCOPE-A", sharedCycleId);
        startCycle("SCHOOL-SCOPE-B", "CANTEEN-SCOPE-B", sharedCycleId);

        mvc.perform(post("/api/v1/ledger-cycles/{cycleId}/records", sharedCycleId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schoolId":"SCHOOL-SCOPE-B",
                                  "canteenId":"CANTEEN-SCOPE-B",
                                  "ledgerCode":"PURCHASE_ACCEPTANCE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cleared").value(true));

        mvc.perform(get("/api/v1/ledger-cycles/{cycleId}/alerts/current", sharedCycleId)
                        .param("schoolId", "SCHOOL-SCOPE-A")
                        .param("canteenId", "CANTEEN-SCOPE-A"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cleared").value(false))
                .andExpect(jsonPath("$.data.missingLedgerCodes", hasSize(1)))
                .andExpect(jsonPath("$.data.missingLedgerCodes[0]").value("PURCHASE_ACCEPTANCE"));
    }

    @Test
    void unknown_ledger_code_is_rejected_when_starting_a_cycle() throws Exception {
        mvc.perform(post("/api/v1/ledger-cycles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schoolId":"SCHOOL-PHASE1",
                                  "canteenId":"CANTEEN-PHASE1",
                                  "cycleId":"CYCLE-PHASE1-UNKNOWN",
                                  "ledgerCodes":["NOT_A_REAL_LEDGER"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void missing_scope_query_parameter_uses_the_unified_error_envelope() throws Exception {
        mvc.perform(get("/api/v1/ledger-cycles/{cycleId}/alerts/current", "CYCLE-MISSING-SCOPE")
                        .param("schoolId", "SCHOOL-PHASE1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    private void startCycle(String schoolId, String canteenId, String cycleId) throws Exception {
        mvc.perform(post("/api/v1/ledger-cycles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schoolId":"%s",
                                  "canteenId":"%s",
                                  "cycleId":"%s",
                                  "ledgerCodes":["PURCHASE_ACCEPTANCE"]
                                }
                                """.formatted(schoolId, canteenId, cycleId)))
                .andExpect(status().isOk());
    }
}
