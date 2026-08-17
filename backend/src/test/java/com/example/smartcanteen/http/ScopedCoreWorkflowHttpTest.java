package com.example.smartcanteen.http;

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
class ScopedCoreWorkflowHttpTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedScopedMenus() {
        jdbc.update("DELETE FROM inventory_receipts WHERE school_id = ?", "SCHOOL-HTTP-SCOPE");
        jdbc.update("DELETE FROM inventory WHERE school_id = ?", "SCHOOL-HTTP-SCOPE");
        jdbc.update("DELETE FROM recipe_requirements WHERE school_id = ?", "SCHOOL-HTTP-SCOPE");
        jdbc.update("DELETE FROM menus WHERE school_id = ?", "SCHOOL-HTTP-SCOPE");
        jdbc.update("DELETE FROM canteens WHERE school_id = ?", "SCHOOL-HTTP-SCOPE");
        jdbc.update("DELETE FROM schools WHERE id = ?", "SCHOOL-HTTP-SCOPE");
        jdbc.update(
                "INSERT INTO schools (id, name) VALUES (?, ?)",
                "SCHOOL-HTTP-SCOPE", "HTTP scope school");
        jdbc.update(
                "INSERT INTO canteens (id, school_id, name) VALUES (?, ?, ?)",
                "CANTEEN-HTTP-NORTH", "SCHOOL-HTTP-SCOPE", "north");
        jdbc.update(
                "INSERT INTO canteens (id, school_id, name) VALUES (?, ?, ?)",
                "CANTEEN-HTTP-SOUTH", "SCHOOL-HTTP-SCOPE", "south");
        jdbc.update(
                "INSERT INTO menus (school_id, canteen_id, id, status, version) "
                        + "VALUES (?, ?, ?, 'DRAFT', 0)",
                "SCHOOL-HTTP-SCOPE", "CANTEEN-HTTP-NORTH", "MENU-HTTP-SHARED");
        jdbc.update(
                "INSERT INTO menus (school_id, canteen_id, id, status, version) "
                        + "VALUES (?, ?, ?, 'DRAFT', 0)",
                "SCHOOL-HTTP-SCOPE", "CANTEEN-HTTP-SOUTH", "MENU-HTTP-SHARED");
        jdbc.update(
                "INSERT INTO recipe_requirements "
                        + "(school_id, canteen_id, menu_id, material_id, quantity, unit) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "SCHOOL-HTTP-SCOPE", "CANTEEN-HTTP-NORTH", "MENU-HTTP-SHARED",
                "FLOUR", "2", "kg");
        jdbc.update(
                "INSERT INTO recipe_requirements "
                        + "(school_id, canteen_id, menu_id, material_id, quantity, unit) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "SCHOOL-HTTP-SCOPE", "CANTEEN-HTTP-SOUTH", "MENU-HTTP-SHARED",
                "FLOUR", "1", "kg");
        jdbc.update(
                "INSERT INTO inventory "
                        + "(school_id, canteen_id, material_id, quantity_base, base_unit) "
                        + "VALUES (?, ?, ?, ?, ?)",
                "SCHOOL-HTTP-SCOPE", "CANTEEN-HTTP-NORTH", "FLOUR", "500", "g");
        jdbc.update(
                "INSERT INTO inventory "
                        + "(school_id, canteen_id, material_id, quantity_base, base_unit) "
                        + "VALUES (?, ?, ?, ?, ?)",
                "SCHOOL-HTTP-SCOPE", "CANTEEN-HTTP-SOUTH", "FLOUR", "1500", "g");
    }

    @Test
    void menu_and_receipt_operations_keep_the_requested_canteen_scope() throws Exception {
        mvc.perform(post("/api/v1/menus/{menuId}/submit", "MENU-HTTP-SHARED")
                        .queryParam("schoolId", "SCHOOL-HTTP-SCOPE")
                        .queryParam("canteenId", "CANTEEN-HTTP-NORTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));

        mvc.perform(post("/api/v1/menus/{menuId}/submit", "MENU-HTTP-SHARED")
                        .queryParam("schoolId", "SCHOOL-HTTP-SCOPE")
                        .queryParam("canteenId", "CANTEEN-HTTP-SOUTH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));

        mvc.perform(post("/api/v1/menu-approvals/{menuId}/decision", "MENU-HTTP-SHARED")
                        .queryParam("schoolId", "SCHOOL-HTTP-SCOPE")
                        .queryParam("canteenId", "CANTEEN-HTTP-NORTH")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"comment\":\"north\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mvc.perform(post("/api/v1/menu-approvals/{menuId}/decision", "MENU-HTTP-SHARED")
                        .queryParam("schoolId", "SCHOOL-HTTP-SCOPE")
                        .queryParam("canteenId", "CANTEEN-HTTP-SOUTH")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECT\",\"comment\":\"south\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));

        mvc.perform(post("/api/v1/inventory/receipts")
                        .queryParam("schoolId", "SCHOOL-HTTP-SCOPE")
                        .queryParam("canteenId", "CANTEEN-HTTP-NORTH")
                        .header("Idempotency-Key", "HTTP-SHARED-RECEIPT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialId\":\"WATER\",\"quantity\":1,\"unit\":\"L\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantityBase").value(1000));

        mvc.perform(post("/api/v1/inventory/receipts")
                        .queryParam("schoolId", "SCHOOL-HTTP-SCOPE")
                        .queryParam("canteenId", "CANTEEN-HTTP-SOUTH")
                        .header("Idempotency-Key", "HTTP-SHARED-RECEIPT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialId\":\"WATER\",\"quantity\":2,\"unit\":\"L\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantityBase").value(2000));
    }

    @Test
    void partial_scope_is_rejected_as_a_unified_business_error() throws Exception {
        mvc.perform(post("/api/v1/menus/{menuId}/submit", "MENU-HTTP-SHARED")
                        .queryParam("schoolId", "SCHOOL-HTTP-SCOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000))
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void procurement_reads_recipe_and_inventory_from_the_requested_canteen() throws Exception {
        approve("CANTEEN-HTTP-NORTH", "north");
        approve("CANTEEN-HTTP-SOUTH", "south");

        mvc.perform(post("/api/v1/procurement-plans/generate")
                        .queryParam("schoolId", "SCHOOL-HTTP-SCOPE")
                        .queryParam("canteenId", "CANTEEN-HTTP-NORTH")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuId\":\"MENU-HTTP-SHARED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data.items[0].materialId").value("FLOUR"))
                .andExpect(jsonPath("$.data.items[0].shortageBaseQuantity").value(1500))
                .andExpect(jsonPath("$.data.items[0].baseUnit").value("g"));

        mvc.perform(post("/api/v1/procurement-plans/generate")
                        .queryParam("schoolId", "SCHOOL-HTTP-SCOPE")
                        .queryParam("canteenId", "CANTEEN-HTTP-SOUTH")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuId\":\"MENU-HTTP-SHARED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    private void approve(String canteenId, String comment) throws Exception {
        mvc.perform(post("/api/v1/menus/{menuId}/submit", "MENU-HTTP-SHARED")
                        .queryParam("schoolId", "SCHOOL-HTTP-SCOPE")
                        .queryParam("canteenId", canteenId))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/menu-approvals/{menuId}/decision", "MENU-HTTP-SHARED")
                        .queryParam("schoolId", "SCHOOL-HTTP-SCOPE")
                        .queryParam("canteenId", canteenId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"comment\":\""
                                + comment + "\"}"))
                .andExpect(status().isOk());
    }
}
