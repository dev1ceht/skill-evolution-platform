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
class RecipeImportHttpTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedDraftMenu() {
        jdbc.update(
                "DELETE FROM recipe_requirements WHERE school_id = ? AND canteen_id = ?",
                "SCHOOL-RECIPE-HTTP", "CANTEEN-RECIPE-HTTP");
        jdbc.update(
                "DELETE FROM menus WHERE school_id = ? AND canteen_id = ?",
                "SCHOOL-RECIPE-HTTP", "CANTEEN-RECIPE-HTTP");
        jdbc.update(
                "DELETE FROM canteens WHERE school_id = ?",
                "SCHOOL-RECIPE-HTTP");
        jdbc.update("DELETE FROM schools WHERE id = ?", "SCHOOL-RECIPE-HTTP");
        jdbc.update(
                "INSERT INTO schools (id, name) VALUES (?, ?)",
                "SCHOOL-RECIPE-HTTP", "recipe import school");
        jdbc.update(
                "INSERT INTO canteens (id, school_id, name) VALUES (?, ?, ?)",
                "CANTEEN-RECIPE-HTTP", "SCHOOL-RECIPE-HTTP", "recipe import canteen");
        jdbc.update(
                "INSERT INTO menus (school_id, canteen_id, id, status, version) "
                        + "VALUES (?, ?, ?, 'DRAFT', 0)",
                "SCHOOL-RECIPE-HTTP", "CANTEEN-RECIPE-HTTP", "MENU-RECIPE-HTTP");
    }

    @Test
    void imports_recipe_for_the_requested_menu_scope_and_replaces_it_on_retry() throws Exception {
        String endpoint = "/api/v1/menus/MENU-RECIPE-HTTP/recipe";
        String scope = "?schoolId=SCHOOL-RECIPE-HTTP&canteenId=CANTEEN-RECIPE-HTTP";

        mvc.perform(post(endpoint + scope)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requirements":[
                                  {"materialId":"FLOUR","quantity":2,"unit":"kg"},
                                  {"materialId":"EGG","quantity":12,"unit":"count"}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.menuId").value("MENU-RECIPE-HTTP"))
                .andExpect(jsonPath("$.data.requirements", org.hamcrest.Matchers.hasSize(2)));

        mvc.perform(post(endpoint + scope)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requirements":[
                                  {"materialId":"RICE","quantity":3,"unit":"kg"}
                                ]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requirements", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data.requirements[0].materialId").value("RICE"));
    }

    @Test
    void recipe_import_is_rejected_after_menu_approval() throws Exception {
        String scope = "?schoolId=SCHOOL-RECIPE-HTTP&canteenId=CANTEEN-RECIPE-HTTP";
        mvc.perform(post("/api/v1/menus/MENU-RECIPE-HTTP/submit" + scope))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/menu-approvals/MENU-RECIPE-HTTP/decision" + scope)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE\",\"comment\":\"ready\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/menus/MENU-RECIPE-HTTP/recipe" + scope)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requirements\":[{\"materialId\":\"EGG\",\"quantity\":1,\"unit\":\"count\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }
}
