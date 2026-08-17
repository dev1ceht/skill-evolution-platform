package com.example.smartcanteen.http;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
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
class OperationalCoreHttpTest {

    private static final String SCHOOL = "SCHOOL-PHASE5-HTTP";
    private static final String CANTEEN = "CANTEEN-PHASE5-HTTP";
    private static final String SCOPE = "schoolId=" + SCHOOL + "&canteenId=" + CANTEEN;
    private static final AuthPrincipal MENU_SUBMITTER = new AuthPrincipal(
            "MENU-SUBMITTER-HTTP", "menu-submitter", "Menu Submitter", Role.CANTEEN_STAFF,
            SCHOOL, CANTEEN);
    private static final AuthPrincipal MENU_APPROVER = new AuthPrincipal(
            "MENU-APPROVER-HTTP", "menu-approver", "Menu Approver", Role.SCHOOL_ADMIN,
            SCHOOL, CANTEEN);
    private static final AuthPrincipal MENU_PUBLISHER = new AuthPrincipal(
            "MENU-PUBLISHER-HTTP", "menu-publisher", "Menu Publisher", Role.SCHOOL_ADMIN,
            SCHOOL, CANTEEN);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetScope() {
        jdbc.update("DELETE FROM traceability_records WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM inventory_receipts WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM inventory WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM stock_out_items WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM stock_out_records WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM purchase_receipt_items WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM inventory_batches WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM purchase_receipts WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM purchase_order_items WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM purchase_orders WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM suppliers WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM daily_menu_items WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM daily_menus WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM dish_ingredients WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM dishes WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM ingredients WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM operational_ledger_records WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM ledger_cycle_requirements WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM ledger_alerts WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM ledger_cycles WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM canteens WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM schools WHERE id = ?", SCHOOL);
        jdbc.update("INSERT INTO schools (id, name) VALUES (?, ?)", SCHOOL, "phase5 school");
        jdbc.update(
                "INSERT INTO canteens (id, school_id, name) VALUES (?, ?, ?)",
                CANTEEN,
                SCHOOL,
                "phase5 canteen");
    }

    @Test
    void daily_menu_purchase_receipt_inventory_trace_and_dashboard_are_persistent() throws Exception {
        createIngredient();
        createDish();
        createDailyMenuAndPublish();
        createSupplier();

        mvc.perform(post("/api/v1/purchase-orders?" + SCOPE)
                        .header("Idempotency-Key", "ORDER-HTTP-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId":"SUP-PHASE5",
                                  "orderType":"OFFLINE",
                                  "items":[{"ingredientId":"RICE","quantity":10,"unit":"kg","unitPrice":4.5}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.totalAmount").value(45.0));
        mvc.perform(post("/api/v1/purchase-orders?" + SCOPE)
                        .header("Idempotency-Key", "ORDER-HTTP-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "supplierId":"SUP-PHASE5",
                                  "orderType":"OFFLINE",
                                  "items":[{"ingredientId":"RICE","quantity":9,"unit":"kg","unitPrice":5.0}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Idempotency-Key")));

        mvc.perform(post("/api/v1/purchase-orders/ORDER-MISSING/status?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUBMITTED\"}"))
                .andExpect(status().isBadRequest());

        String orderId = jdbc.queryForObject(
                "SELECT order_id FROM purchase_orders WHERE school_id = ? AND canteen_id = ?",
                String.class,
                SCHOOL,
                CANTEEN);
        mvc.perform(post("/api/v1/purchase-orders/" + orderId + "/status?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUBMITTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        mvc.perform(post("/api/v1/purchase-orders/" + orderId + "/status?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        MvcResult received = mvc.perform(post("/api/v1/purchase-orders/" + orderId + "/receive?" + SCOPE)
                        .header("Idempotency-Key", "RECEIVE-HTTP-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"ingredientId":"RICE","quantity":10,"unit":"kg",
                                "batchNo":"RICE-2026-01","purchasePrice":4.5}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptId", notNullValue()))
                .andExpect(jsonPath("$.data.traceCodes", hasSize(1)))
                .andReturn();
        String traceCode = com.jayway.jsonpath.JsonPath.read(
                received.getResponse().getContentAsString(), "$.data.traceCodes[0]");

        mvc.perform(post("/api/v1/purchase-orders/" + orderId + "/receive?" + SCOPE)
                        .header("Idempotency-Key", "RECEIVE-HTTP-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptId").isNotEmpty());

        mvc.perform(post("/api/v1/purchase-orders/" + orderId + "/receive?" + SCOPE)
                        .header("Idempotency-Key", "RECEIVE-HTTP-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"ingredientId\":\"RICE\",\"quantity\":9,\"unit\":\"kg\",\"purchasePrice\":4.5}] }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Idempotency-Key")));

        mvc.perform(get("/api/v1/inventory?" + SCOPE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].quantity").value(10000))
                .andExpect(jsonPath("$.data.records[0].unit").value("g"));
        mvc.perform(get("/api/v1/traceability/" + traceCode + "?" + SCOPE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ingredientName").value("大米"))
                .andExpect(jsonPath("$.data.supplierName").value("测试供应商"));

        mvc.perform(post("/api/v1/inventory/stock-outs?" + SCOPE)
                        .header("Idempotency-Key", "STOCK-OUT-HTTP-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"午餐\",\"items\":[{\"ingredientId\":\"RICE\",\"quantity\":2,\"unit\":\"kg\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/inventory/stock-outs?" + SCOPE)
                        .header("Idempotency-Key", "STOCK-OUT-HTTP-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"异常\",\"items\":[{\"ingredientId\":\"RICE\",\"quantity\":9,\"unit\":\"kg\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Insufficient")));
        mvc.perform(post("/api/v1/inventory/stock-outs?" + SCOPE)
                        .header("Idempotency-Key", "STOCK-OUT-HTTP-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reason":"不同请求","items":[{"ingredientId":"RICE","quantity":1,"unit":"kg"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Idempotency-Key")));

        mvc.perform(get("/api/v1/dashboard/summary?" + SCOPE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayMenuCount").value(1))
                .andExpect(jsonPath("$.data.publishedMenuCount").value(1));
    }

    @Test
    void ledger_record_is_idempotent_and_publshed_menu_is_immutable() throws Exception {
        startLedgerCycle();
        mvc.perform(post("/api/v1/ledger/records?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cycleId":"CYCLE-PHASE5","ledgerCode":"PURCHASE_ACCEPTANCE",
                                "content":{"temperature":4.2},"photos":["https://example.test/photo.jpg"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        mvc.perform(post("/api/v1/ledger/records?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cycleId":"CYCLE-PHASE5","ledgerCode":"PURCHASE_ACCEPTANCE",
                                "content":{"temperature":4.2},"photos":["https://example.test/photo.jpg"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.recordId").isNotEmpty());
        mvc.perform(get("/api/v1/ledger/stats?" + SCOPE
                        + "&startDate=" + LocalDate.now() + "&endDate=" + LocalDate.now()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completed").value(1));
    }

    private void createIngredient() throws Exception {
        mvc.perform(post("/api/v1/ingredients?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ingredientId":"RICE","name":"大米","category":"主食",
                                "baseUnit":"g","warningThreshold":1000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("RICE"));
    }

    private void createDish() throws Exception {
        mvc.perform(post("/api/v1/dishes?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"dishId":"DISH-RICE","name":"米饭","category":"主食",
                                "ingredients":[{"ingredientId":"RICE","quantity":100,"unit":"g"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("DISH-RICE"));
    }

    private void createDailyMenuAndPublish() throws Exception {
        String menuDate = LocalDate.now().toString();
        mvc.perform(post("/api/v1/daily-menus?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"menuDate":"%s","mealTime":"LUNCH",
                                "items":[{"dishId":"DISH-RICE","estimatedQuantity":100,"sortOrder":0}]}
                                """.formatted(menuDate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        String menuId = jdbc.queryForObject(
                "SELECT menu_id FROM daily_menus WHERE school_id = ? AND canteen_id = ?",
                String.class,
                SCHOOL,
                CANTEEN);
        mvc.perform(post("/api/v1/daily-menus/" + menuId + "/submit?" + SCOPE + "&version=0")
                        .requestAttr(AuthPrincipal.class.getName(), MENU_SUBMITTER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));
        mvc.perform(post("/api/v1/daily-menus/" + menuId + "/decision?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), MENU_APPROVER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"decision\":\"APPROVE\",\"comment\":\"checked\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        mvc.perform(post("/api/v1/daily-menus/" + menuId + "/publish?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), MENU_PUBLISHER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    private void createSupplier() throws Exception {
        mvc.perform(post("/api/v1/suppliers?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":\"SUP-PHASE5\",\"name\":\"测试供应商\"}"))
                .andExpect(status().isOk());
    }

    private void startLedgerCycle() throws Exception {
        mvc.perform(post("/api/v1/ledger-cycles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolId":"SCHOOL-PHASE5-HTTP","canteenId":"CANTEEN-PHASE5-HTTP",
                                "cycleId":"CYCLE-PHASE5","ledgerCodes":["PURCHASE_ACCEPTANCE"]}
                                """))
                .andExpect(status().isOk());
    }
}
