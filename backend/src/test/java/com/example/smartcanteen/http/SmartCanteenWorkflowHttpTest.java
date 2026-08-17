package com.example.smartcanteen.http;

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
class SmartCanteenWorkflowHttpTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void menu_approval_drives_procurement_receipt_and_ledger_clearance() throws Exception {
        mvc.perform(post("/api/v1/menus/{menuId}/submit", "MENU-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));

        mvc.perform(post("/api/v1/menu-approvals/{menuId}/decision", "MENU-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"APPROVE","comment":"审批通过"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mvc.perform(post("/api/v1/procurement-plans/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"menuId":"MENU-001"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].materialId").value("FLOUR"))
                .andExpect(jsonPath("$.data.items[0].shortageBaseQuantity").value(1500))
                .andExpect(jsonPath("$.data.items[0].baseUnit").value("g"));

        mvc.perform(post("/api/v1/inventory/receipts")
                        .header("Idempotency-Key", "receipt-MENU-001-FLOUR")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"materialId":"FLOUR","quantity":1.5,"unit":"kg"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantityBase").value(2000));

        mvc.perform(post("/api/v1/ledger-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ledgerCode":"PURCHASE_ACCEPTANCE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cleared").value(true));

        mvc.perform(get("/api/v1/ledger-alerts/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cleared").value(true))
                .andExpect(jsonPath("$.data.missingLedgerCodes").isEmpty());
    }

    @Test
    void unknown_ledger_code_is_rejected_instead_of_succeeding_as_a_no_op() throws Exception {
        mvc.perform(post("/api/v1/ledger-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ledgerCode":"UNKNOWN_LEDGER"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    void overlong_idempotency_key_is_rejected_at_the_public_boundary() throws Exception {
        mvc.perform(post("/api/v1/inventory/receipts")
                        .header("Idempotency-Key", "x".repeat(129))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"materialId":"FLOUR","quantity":1.0,"unit":"kg"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }
}
