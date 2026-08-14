package com.example.smartcanteen.http;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class GovernanceHttpTest {

    private static final String SCHOOL = "SCHOOL-P3-GOV";
    private static final String CANTEEN = "CANTEEN-P3-GOV";
    private static final String SCOPE = "schoolId=" + SCHOOL + "&canteenId=" + CANTEEN;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetScope() {
        jdbc.update("DELETE FROM governance_history WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM canteen_showcases WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM meal_suspensions WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM supplier_complaints WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM canteens WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM schools WHERE id = ?", SCHOOL);
        jdbc.update("INSERT INTO schools (id, name) VALUES (?, ?)", SCHOOL, "阶段3治理学校");
        jdbc.update(
                "INSERT INTO canteens (id, school_id, name) VALUES (?, ?, ?)",
                CANTEEN,
                SCHOOL,
                "阶段3治理食堂");
    }

    @Test
    void showcase_meal_suspension_and_supplier_complaint_have_auditable_workflows() throws Exception {
        mvc.perform(post("/api/v1/canteen-showcases?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"showcaseId":"SHOWCASE-P3-HTTP","title":"后厨公开日",
                                 "content":"明厨亮灶和留样流程","photos":["https://example.test/kitchen.jpg"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.version").value(0));
        mvc.perform(post("/api/v1/canteen-showcases/SHOWCASE-P3-HTTP/submit?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(post("/api/v1/canteen-showcases/SHOWCASE-P3-HTTP/review?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1,\"status\":\"APPROVED\",\"reviewRemark\":\"内容合规\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        mvc.perform(post("/api/v1/canteen-showcases/SHOWCASE-P3-HTTP/publish?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.version").value(3));
        mvc.perform(put("/api/v1/canteen-showcases/SHOWCASE-P3-HTTP?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"篡改","content":"不应覆盖","status":"PUBLISHED","version":3}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("immutable")));
        mvc.perform(get("/api/v1/canteen-showcases/SHOWCASE-P3-HTTP/history?" + SCOPE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)));

        mvc.perform(post("/api/v1/meal-suspensions?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"suspensionId":"SUSPENSION-P3-HTTP","mealDate":"2026-08-20",
                                 "mealPeriod":"LUNCH","reason":"设备检修"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        mvc.perform(post("/api/v1/meal-suspensions/SUSPENSION-P3-HTTP/review?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"status\":\"APPROVED\",\"reviewRemark\":\"已确认\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
        mvc.perform(get("/api/v1/meal-suspensions/stats?" + SCOPE
                        + "&from=2026-08-01&to=2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.APPROVED").value(1));

        mvc.perform(post("/api/v1/supplier-complaints?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"complaintId":"COMPLAINT-P3-HTTP","supplierId":"SUP-P3-001",
                                 "subject":"到货破损","description":"包装和标签均有破损",
                                 "deadline":"2026-08-30"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        mvc.perform(post("/api/v1/supplier-complaints/COMPLAINT-P3-HTTP/review?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));
        mvc.perform(post("/api/v1/supplier-complaints/COMPLAINT-P3-HTTP/process?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PROCESSING"));
        mvc.perform(post("/api/v1/supplier-complaints/COMPLAINT-P3-HTTP/reply?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":2,\"reply\":\"已补发并完成整改\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REPLIED"));
        mvc.perform(post("/api/v1/supplier-complaints/COMPLAINT-P3-HTTP/close?" + SCOPE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
        mvc.perform(get("/api/v1/supplier-complaints/COMPLAINT-P3-HTTP/history?" + SCOPE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(5)));

        mvc.perform(get("/api/v1/supplier-complaints?schoolId=" + SCHOOL
                        + "&canteenId=CANTEEN-OTHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records", hasSize(0)));
    }
}
