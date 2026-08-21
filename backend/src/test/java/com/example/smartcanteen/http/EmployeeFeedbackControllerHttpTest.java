package com.example.smartcanteen.http;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
        "agent.write.enabled=true",
        "smart-canteen.assistant.allowed-scopes=SCHOOL-FEEDBACK-HTTP/CANTEEN-FEEDBACK-HTTP"
})
@AutoConfigureMockMvc
class EmployeeFeedbackControllerHttpTest {

    private static final String SCHOOL = "SCHOOL-FEEDBACK-HTTP";
    private static final String CANTEEN = "CANTEEN-FEEDBACK-HTTP";
    private static final String SCOPE = "schoolId=" + SCHOOL + "&canteenId=" + CANTEEN;
    private static final String USER = "USER-FEEDBACK-HTTP";
    private static final String OTHER_USER = "USER-FEEDBACK-OTHER";
    private static final AuthPrincipal DINER = new AuthPrincipal(
            USER, "feedback-diner", "Feedback Diner", Role.DINER, SCHOOL, CANTEEN);
    private static final AuthPrincipal OTHER_DINER = new AuthPrincipal(
            OTHER_USER, "feedback-other", "Other Diner", Role.DINER, SCHOOL, CANTEEN);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetScope() {
        jdbc.update("DELETE FROM meal_reviews WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM diner_complaints WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM meal_order_items WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM meal_orders WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM daily_menu_items WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM daily_menus WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM dishes WHERE school_id = ?", SCHOOL);
        jdbc.update("DELETE FROM audit_logs WHERE actor_user_id IN (?, ?)", USER, OTHER_USER);
        jdbc.update("DELETE FROM app_users WHERE user_id IN (?, ?)", USER, OTHER_USER);
        jdbc.update("DELETE FROM canteens WHERE id = ?", CANTEEN);
        jdbc.update("DELETE FROM schools WHERE id = ?", SCHOOL);
        jdbc.update(
                "INSERT INTO schools (id, name, region_code, status) VALUES (?, ?, ?, 'ACTIVE')",
                SCHOOL, "feedback school", "FEEDBACK-REGION");
        jdbc.update(
                "INSERT INTO canteens (id, school_id, name, status) VALUES (?, ?, ?, 'ACTIVE')",
                CANTEEN, SCHOOL, "feedback canteen");
        insertUser(USER, "feedback-diner");
        insertUser(OTHER_USER, "feedback-other");
        jdbc.update(
                "INSERT INTO dishes (school_id, canteen_id, dish_id, name, category, description) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                SCHOOL, CANTEEN, "DISH-FEEDBACK-001", "番茄鸡蛋", "热菜", "家常口味");
        jdbc.update(
                "INSERT INTO daily_menus (school_id, canteen_id, menu_id, menu_date, meal_time, status) "
                        + "VALUES (?, ?, ?, ?, 'LUNCH', 'PUBLISHED')",
                SCHOOL, CANTEEN, "MABC123", LocalDate.now());
        jdbc.update(
                "INSERT INTO daily_menu_items (school_id, canteen_id, menu_id, dish_id, estimated_quantity, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                SCHOOL, CANTEEN, "MABC123", "DISH-FEEDBACK-001", 100, 0);
        jdbc.update(
                "INSERT INTO meal_orders (school_id, canteen_id, order_id, order_no, actor_user_id, "
                        + "menu_id, meal_date, meal_time, status, payment_status, total_amount, "
                        + "idempotency_key, request_hash, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, "
                        + "'CREATED', 'UNPAID', ?, ?, ?, 0)",
                SCHOOL, CANTEEN, "MEAL-FEEDBACK-001", "MO-FEEDBACK-001", USER,
                "MABC123", LocalDate.now(), "LUNCH", BigDecimal.ZERO,
                "FEEDBACK-ORDER-KEY", "FEEDBACK-ORDER-HASH");
        jdbc.update(
                "INSERT INTO meal_order_items (school_id, canteen_id, order_id, dish_id, dish_name, "
                        + "quantity, unit_price, amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                SCHOOL, CANTEEN, "MEAL-FEEDBACK-001", "DISH-FEEDBACK-001", "番茄鸡蛋",
                1, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    @Test
    void diner_can_submit_one_review_for_own_order_and_query_personal_reviews() throws Exception {
        String body = "{\"orderId\":\"MEAL-FEEDBACK-001\",\"rating\":5,\"content\":\"很好吃\"}";
        MvcResult created = mvc.perform(post("/api/v1/meal-reviews?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER)
                        .header("Idempotency-Key", "REVIEW-KEY-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actorUserId").value(USER))
                .andExpect(jsonPath("$.data.orderId").value("MEAL-FEEDBACK-001"))
                .andExpect(jsonPath("$.data.rating").value(5))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andReturn();
        String reviewId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        mvc.perform(post("/api/v1/meal-reviews?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER)
                        .header("Idempotency-Key", "REVIEW-KEY-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(reviewId));

        mvc.perform(post("/api/v1/meal-reviews?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER)
                        .header("Idempotency-Key", "REVIEW-KEY-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already been reviewed")));

        mvc.perform(get("/api/v1/meal-reviews?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(reviewId));

        mvc.perform(get("/api/v1/meal-reviews?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), OTHER_DINER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mvc.perform(post("/api/v1/meal-reviews?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), OTHER_DINER)
                        .header("Idempotency-Key", "REVIEW-OTHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/meal-reviews?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER)
                        .header("Idempotency-Key", "REVIEW-INVALID-RATING")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"MEAL-FEEDBACK-001\",\"rating\":6}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void diner_can_submit_and_query_personal_complaint() throws Exception {
        String body = "{\"category\":\"SERVICE\",\"subject\":\"窗口排队\","
                + "\"description\":\"午餐窗口排队时间较长\","
                + "\"relatedOrderId\":\"MEAL-FEEDBACK-001\"}";
        MvcResult created = mvc.perform(post("/api/v1/diner-complaints?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER)
                        .header("Idempotency-Key", "COMPLAINT-KEY-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actorUserId").value(USER))
                .andExpect(jsonPath("$.data.category").value("SERVICE"))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andReturn();
        String complaintId = JsonPath.read(
                created.getResponse().getContentAsString(), "$.data.id");

        mvc.perform(post("/api/v1/diner-complaints?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER)
                        .header("Idempotency-Key", "COMPLAINT-KEY-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(complaintId));

        mvc.perform(post("/api/v1/diner-complaints?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER)
                        .header("Idempotency-Key", "COMPLAINT-KEY-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.replace("窗口排队", "卫生问题")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Idempotency-Key")));

        mvc.perform(get("/api/v1/diner-complaints?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(complaintId));
        mvc.perform(get("/api/v1/diner-complaints?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), OTHER_DINER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mvc.perform(post("/api/v1/diner-complaints?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER)
                        .header("Idempotency-Key", "COMPLAINT-INVALID")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"UNKNOWN\",\"subject\":\"x\","
                                + "\"description\":\"y\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Unsupported complaint category")));
    }

    @Test
    void assistant_requires_confirmation_before_review_and_complaint_writes() throws Exception {
        mvc.perform(assistantMessage(
                        "assistant-review-preview", "评价 MEAL-FEEDBACK-001 5分 内容：很好吃", "CONV-FEEDBACK-REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.data.intent").value("meal_review.create"))
                .andExpect(jsonPath("$.data.runStatus").value("WAITING_CONFIRMATION"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM meal_reviews WHERE school_id = ?", Integer.class, SCHOOL))
                .isZero();

        mvc.perform(assistantMessage(
                        "assistant-review-confirm", "确认", "CONV-FEEDBACK-REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("RESULT"))
                .andExpect(jsonPath("$.data.intent").value("meal_review.create"))
                .andExpect(jsonPath("$.data.runStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.rating").value(5));

        mvc.perform(assistantMessage(
                        "assistant-complaint-preview",
                        "我要投诉 主题：服务 描述：窗口排队时间较长",
                        "CONV-FEEDBACK-COMPLAINT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.data.intent").value("diner_complaint.create"))
                .andExpect(jsonPath("$.data.runStatus").value("WAITING_CONFIRMATION"));

        mvc.perform(assistantMessage(
                        "assistant-complaint-confirm", "确认", "CONV-FEEDBACK-COMPLAINT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("RESULT"))
                .andExpect(jsonPath("$.data.intent").value("diner_complaint.create"))
                .andExpect(jsonPath("$.data.runStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.status").value("SUBMITTED"));
    }

    private MockHttpServletRequestBuilder assistantMessage(
            String idempotencyKey, String text, String conversationId) {
        return post("/api/v1/assistant/conversations/{conversationId}/messages", conversationId)
                .queryParam("schoolId", SCHOOL)
                .queryParam("canteenId", CANTEEN)
                .header("Idempotency-Key", idempotencyKey)
                .header("X-Request-Id", "request-" + idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"" + text + "\"}")
                .requestAttr(AuthPrincipal.class.getName(), DINER);
    }

    private void insertUser(String userId, String username) {
        jdbc.update(
                "INSERT INTO app_users (user_id, username, password_hash, display_name, role, school_id, canteen_id) "
                        + "VALUES (?, ?, ?, ?, 'DINER', ?, ?)",
                userId, username, "test-password-hash", username, SCHOOL, CANTEEN);
    }
}
