package com.example.smartcanteen.http;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ProcurementPlanClosedLoopHttpTest {

    private static final String SCHOOL = "SCHOOL-PHASE2-HTTP";
    private static final String CANTEEN = "CANTEEN-PHASE2-HTTP";
    private static final String SCOPE = "schoolId=" + SCHOOL + "&canteenId=" + CANTEEN;
    private static final LocalDate MENU_DATE = LocalDate.of(2026, 8, 14);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetScope() {
        jdbc.update("DELETE FROM procurement_plan_orders WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM procurement_plan_items WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM procurement_plan_menus WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM procurement_plans WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM traceability_records WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM purchase_receipt_items WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM purchase_receipts WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM inventory_batches WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM stock_out_items WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM stock_out_records WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM inventory WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM purchase_order_items WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM purchase_orders WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM suppliers WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM daily_menu_items WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM daily_menus WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM dish_ingredients WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM dishes WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM ingredient_units WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM ingredients WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM canteens WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM schools WHERE id = ?", SCHOOL);
        jdbc.update("INSERT INTO schools (id, name) VALUES (?, ?)", SCHOOL, "phase2 school");
        jdbc.update(
                "INSERT INTO canteens (id, school_id, name) VALUES (?, ?, ?)",
                CANTEEN, SCHOOL, "phase2 canteen");
    }

    @Test
    void published_recipe_becomes_plan_order_partial_receipt_and_inventory_batches() throws Exception {
        createIngredientAndCustomUnit();
        createDishAndPublishedMenu();
        jdbc.update(
                "INSERT INTO inventory (school_id, canteen_id, material_id, quantity_base, base_unit, "
                        + "warning_threshold, last_update_time) VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
                SCHOOL, CANTEEN, "RICE", "1000", "g", "500");
        createSupplier();

        MvcResult generated = mvc.perform(post("/api/v1/procurement-plans/generate-range?" + SCOPE)
                        .header("Idempotency-Key", "PLAN-PHASE2-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodStart\":\"2026-08-14\",\"periodEnd\":\"2026-08-14\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.sourceMenuIds", hasSize(1)))
                .andExpect(jsonPath("$.data.items", hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].requiredBaseQuantity").value(10000))
                .andExpect(jsonPath("$.data.items[0].inventoryBaseQuantity").value(1000))
                .andExpect(jsonPath("$.data.items[0].shortageBaseQuantity").value(9000))
                .andExpect(jsonPath("$.data.items[0].baseUnit").value("g"))
                .andReturn();
        String planId = com.jayway.jsonpath.JsonPath.read(
                generated.getResponse().getContentAsString(), "$.data.id");

        mvc.perform(post("/api/v1/procurement-plans/generate-range?" + SCOPE)
                        .header("Idempotency-Key", "PLAN-PHASE2-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodStart\":\"2026-08-14\",\"periodEnd\":\"2026-08-14\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(planId));

        mvc.perform(put("/api/v1/procurement-plans/" + planId + "/items?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"items\":[{\"ingredientId\":\"RICE\","
                                + "\"quantity\":2,\"unit\":\"bag\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.items[0].plannedBaseQuantity").value(10000));

        mvc.perform(put("/api/v1/procurement-plans/" + planId + "/items?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"items\":[{\"ingredientId\":\"RICE\","
                                + "\"quantity\":1,\"unit\":\"bag\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("changed")));

        mvc.perform(post("/api/v1/procurement-plans/" + planId + "/confirm?" + SCOPE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        MvcResult orderResult = mvc.perform(post(
                        "/api/v1/procurement-plans/" + planId + "/purchase-orders?" + SCOPE)
                        .header("Idempotency-Key", "ORDER-FROM-PLAN-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":\"SUP-PHASE2\",\"orderType\":\"OFFLINE\","
                                + "\"items\":[{\"ingredientId\":\"RICE\",\"quantity\":2,"
                                + "\"unit\":\"bag\",\"unitPrice\":20}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.totalAmount").value(40.0))
                .andReturn();
        String orderId = com.jayway.jsonpath.JsonPath.read(
                orderResult.getResponse().getContentAsString(), "$.data.id");

        mvc.perform(post("/api/v1/procurement-plans/" + planId + "/purchase-orders?" + SCOPE)
                        .header("Idempotency-Key", "ORDER-FROM-PLAN-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":\"SUP-PHASE2\",\"orderType\":\"OFFLINE\","
                                + "\"items\":[{\"ingredientId\":\"RICE\",\"quantity\":2,"
                                + "\"unit\":\"bag\",\"unitPrice\":20}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId));

        transitionOrder(orderId, "SUBMITTED");
        transitionOrder(orderId, "CONFIRMED");

        mvc.perform(post("/api/v1/purchase-orders/" + orderId + "/receive?" + SCOPE)
                        .header("Idempotency-Key", "RECEIVE-PHASE2-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"ingredientId\":\"RICE\",\"quantity\":1,"
                                + "\"unit\":\"bag\",\"purchasePrice\":20,\"batchNo\":\"RICE-B1\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.traceCodes", hasSize(1)));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT quantity_base FROM inventory WHERE school_id = ? AND canteen_id = ? AND material_id = ?",
                java.math.BigDecimal.class, SCHOOL, CANTEEN, "RICE"))
                .isEqualByComparingTo("6000");

        MvcResult finalReceipt = mvc.perform(post(
                        "/api/v1/purchase-orders/" + orderId + "/receive?" + SCOPE)
                        .header("Idempotency-Key", "RECEIVE-PHASE2-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"ingredientId\":\"RICE\",\"quantity\":1,"
                                + "\"unit\":\"bag\",\"purchasePrice\":20,\"batchNo\":\"RICE-B2\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptId", notNullValue()))
                .andExpect(jsonPath("$.data.traceCodes", hasSize(1)))
                .andReturn();

        mvc.perform(post("/api/v1/purchase-orders/" + orderId + "/receive?" + SCOPE)
                        .header("Idempotency-Key", "RECEIVE-PHASE2-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.receiptId").isNotEmpty());
        mvc.perform(get("/api/v1/inventory?" + SCOPE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].quantity").value(11000));
        String traceCode = com.jayway.jsonpath.JsonPath.read(
                finalReceipt.getResponse().getContentAsString(), "$.data.traceCodes[0]");
        mvc.perform(get("/api/v1/traceability/" + traceCode + "?" + SCOPE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ingredientName").value("大米"));
        mvc.perform(post("/api/v1/purchase-orders/" + orderId + "/receive?" + SCOPE)
                        .header("Idempotency-Key", "RECEIVE-PHASE2-3")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("status")));
    }

    private void createIngredientAndCustomUnit() throws Exception {
        mvc.perform(post("/api/v1/ingredients?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ingredientId\":\"RICE\",\"name\":\"大米\","
                                + "\"category\":\"主食\",\"baseUnit\":\"g\","
                                + "\"units\":[{\"unitCode\":\"bag\",\"baseUnit\":\"g\","
                                + "\"toBaseFactor\":5000}]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/ingredients/RICE/units?" + SCOPE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    private void createDishAndPublishedMenu() throws Exception {
        mvc.perform(post("/api/v1/dishes?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dishId\":\"DISH-RICE-P2\",\"name\":\"米饭\","
                                + "\"category\":\"主食\",\"ingredients\":[{\"ingredientId\":\"RICE\","
                                + "\"quantity\":100,\"unit\":\"g\"}]}"))
                .andExpect(status().isOk());
        MvcResult menu = mvc.perform(post("/api/v1/daily-menus?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuDate\":\"2026-08-14\",\"mealTime\":\"LUNCH\","
                                + "\"items\":[{\"dishId\":\"DISH-RICE-P2\","
                                + "\"estimatedQuantity\":100,\"sortOrder\":0}]}"))
                .andExpect(status().isOk())
                .andReturn();
        String menuId = com.jayway.jsonpath.JsonPath.read(
                menu.getResponse().getContentAsString(), "$.data.id");
        mvc.perform(post("/api/v1/daily-menus/" + menuId + "/publish?" + SCOPE))
                .andExpect(status().isOk());
    }

    private void createSupplier() throws Exception {
        mvc.perform(post("/api/v1/suppliers?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"supplierId\":\"SUP-PHASE2\",\"name\":\"阶段2供应商\"}"))
                .andExpect(status().isOk());
    }

    private void transitionOrder(String orderId, String statusValue) throws Exception {
        mvc.perform(post("/api/v1/purchase-orders/" + orderId + "/status?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + statusValue + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(statusValue));
    }
}
