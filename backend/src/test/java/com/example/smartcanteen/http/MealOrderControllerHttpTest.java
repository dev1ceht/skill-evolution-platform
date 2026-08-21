package com.example.smartcanteen.http;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.smartcanteen.security.AuthPrincipal;
import com.example.smartcanteen.security.Role;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest(properties = {
        "agent.write.enabled=true",
        "smart-canteen.assistant.allowed-scopes=SCHOOL-DINER-HTTP/CANTEEN-DINER-HTTP"
})
@AutoConfigureMockMvc
class MealOrderControllerHttpTest {

    private static final String SCHOOL = "SCHOOL-DINER-HTTP";
    private static final String CANTEEN = "CANTEEN-DINER-HTTP";
    private static final String SCOPE = "schoolId=" + SCHOOL + "&canteenId=" + CANTEEN;
    private static final String USER = "USER-DINER-HTTP";
    private static final String OTHER_USER = "USER-DINER-OTHER";
    private static final AuthPrincipal DINER = new AuthPrincipal(
            USER, "diner-http", "Diner", Role.DINER, SCHOOL, CANTEEN);
    private static final AuthPrincipal OTHER_DINER = new AuthPrincipal(
            OTHER_USER, "diner-other", "Other Diner", Role.DINER, SCHOOL, CANTEEN);
    private static final AuthPrincipal CANTEEN_STAFF = new AuthPrincipal(
            "USER-DINER-STAFF", "canteen-staff", "Canteen Staff", Role.CANTEEN_STAFF, SCHOOL, CANTEEN);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetScope() {
        jdbc.update("DELETE FROM meal_order_payments WHERE school_id = ?", SCHOOL);
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
                SCHOOL, "diner school", "DINER-REGION");
        jdbc.update(
                "INSERT INTO canteens (id, school_id, name, status) VALUES (?, ?, ?, 'ACTIVE')",
                CANTEEN, SCHOOL, "diner canteen");
        insertUser(USER, "diner-http");
        insertUser(OTHER_USER, "diner-other");
        jdbc.update(
                "INSERT INTO dishes (school_id, canteen_id, dish_id, name, category, description) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                SCHOOL, CANTEEN, "DISH-001", "番茄鸡蛋", "热菜", "家常口味");
        jdbc.update(
                "INSERT INTO daily_menus (school_id, canteen_id, menu_id, menu_date, meal_time, status) "
                        + "VALUES (?, ?, ?, ?, 'LUNCH', 'PUBLISHED')",
                SCHOOL, CANTEEN, "M901", LocalDate.now());
        jdbc.update(
                "INSERT INTO daily_menu_items (school_id, canteen_id, menu_id, dish_id, estimated_quantity, sort_order) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                SCHOOL, CANTEEN, "M901", "DISH-001", 100, 0);
    }

    @Test
    void lists_published_menu_creates_personal_unpaid_order_and_supports_idempotent_cancel() throws Exception {
        mvc.perform(get("/api/v1/diner/menus?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].items[0].dishId").value("DISH-001"))
                .andExpect(jsonPath("$.data.records[0].items[0].name").value("番茄鸡蛋"));

        String body = "{\"menuId\":\"M901\",\"items\":[{\"dishId\":\"DISH-001\",\"quantity\":2}]}";
        MvcResult created = mvc.perform(post("/api/v1/meal-orders?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER)
                        .header("Idempotency-Key", "DINER-ORDER-KEY-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.actorUserId").value(USER))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andReturn();
        String orderId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        mvc.perform(post("/api/v1/meal-orders?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER)
                        .header("Idempotency-Key", "DINER-ORDER-KEY-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId));
        mvc.perform(post("/api/v1/meal-orders?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER)
                        .header("Idempotency-Key", "DINER-ORDER-KEY-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuId\":\"M901\",\"items\":[{\"dishId\":\"DISH-001\",\"quantity\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Idempotency-Key")));

        mvc.perform(get("/api/v1/meal-orders?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].actorUserId").value(USER));
        mvc.perform(get("/api/v1/meal-orders?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), OTHER_DINER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(post("/api/v1/meal-orders/" + orderId + "/cancel?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), OTHER_DINER))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/meal-orders/" + orderId + "/cancel?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        mvc.perform(post("/api/v1/meal-orders/" + orderId + "/cancel?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void rejects_a_dish_that_is_not_in_the_published_menu() throws Exception {
        mvc.perform(post("/api/v1/meal-orders?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER)
                        .header("Idempotency-Key", "DINER-ORDER-INVALID-DISH")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuId\":\"M901\",\"items\":[{\"dishId\":\"DISH-404\",\"quantity\":1}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not included")));
    }

    @Test
    void scopes_idempotency_keys_to_the_actor() throws Exception {
        String body = "{\"menuId\":\"M901\",\"items\":[{\"dishId\":\"DISH-001\",\"quantity\":1}]}";
        MvcResult dinerOrder = mvc.perform(post("/api/v1/meal-orders?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER)
                        .header("Idempotency-Key", "SHARED-ACTOR-KEY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actorUserId").value(USER))
                .andReturn();
        MvcResult otherOrder = mvc.perform(post("/api/v1/meal-orders?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), OTHER_DINER)
                        .header("Idempotency-Key", "SHARED-ACTOR-KEY")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actorUserId").value(OTHER_USER))
                .andReturn();
        String dinerOrderId = JsonPath.read(
                dinerOrder.getResponse().getContentAsString(), "$.data.id");
        String otherOrderId = JsonPath.read(
                otherOrder.getResponse().getContentAsString(), "$.data.id");
        org.assertj.core.api.Assertions.assertThat(otherOrderId).isNotEqualTo(dinerOrderId);
    }

    @Test
    void diner_can_pay_own_unpaid_order_once_and_paid_order_cannot_be_cancelled() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/meal-orders?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER)
                        .header("Idempotency-Key", "PAY-ORDER-CREATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuId\":\"M901\",\"items\":[{\"dishId\":\"DISH-001\",\"quantity\":1}]}") )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
                .andReturn();
        String orderId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        MvcResult paid = mvc.perform(post("/api/v1/meal-orders/" + orderId + "/pay?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER)
                        .header("Idempotency-Key", "PAY-KEY-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CREATED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"))
                .andReturn();
        String paidOrderId = JsonPath.read(paid.getResponse().getContentAsString(), "$.data.id");
        org.assertj.core.api.Assertions.assertThat(paidOrderId).isEqualTo(orderId);

        mvc.perform(post("/api/v1/meal-orders/" + orderId + "/pay?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER)
                        .header("Idempotency-Key", "PAY-KEY-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("PAID"));
        mvc.perform(post("/api/v1/meal-orders/" + orderId + "/pay?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER)
                        .header("Idempotency-Key", "PAY-KEY-002"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already been paid")));
        mvc.perform(post("/api/v1/meal-orders/" + orderId + "/cancel?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Only unpaid")));
        mvc.perform(post("/api/v1/meal-orders/" + orderId + "/pay?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), OTHER_DINER)
                        .header("Idempotency-Key", "PAY-OTHER"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/meal-orders/" + orderId + "/pay?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), CANTEEN_STAFF)
                        .header("Idempotency-Key", "PAY-STAFF"))
                .andExpect(status().isForbidden());
    }

    @Test
    void assistant_queries_orders_and_requires_confirmation_before_creating_one() throws Exception {
        mvc.perform(assistantMessage(
                        "assistant-order-query", "查看我的订单", "CONV-DINER-QUERY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intent").value("meal_order.query"))
                .andExpect(jsonPath("$.data.runStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.total").value(0));

        mvc.perform(assistantMessage(
                        "assistant-order-preview", "帮我订 M901 的 DISH-001 x1", "CONV-DINER-WRITE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.data.intent").value("meal_order.create"))
                .andExpect(jsonPath("$.data.runStatus").value("WAITING_CONFIRMATION"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM meal_orders WHERE school_id = ? AND canteen_id = ?",
                Integer.class, SCHOOL, CANTEEN)).isZero();

        MvcResult confirmed = mvc.perform(assistantMessage(
                        "assistant-order-confirm", "确认", "CONV-DINER-WRITE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("RESULT"))
                .andExpect(jsonPath("$.data.intent").value("meal_order.create"))
                .andExpect(jsonPath("$.data.runStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.paymentStatus").value("UNPAID"))
                .andReturn();
        String orderId = JsonPath.read(
                confirmed.getResponse().getContentAsString(), "$.data.result.id");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM meal_orders WHERE school_id = ? AND canteen_id = ?",
                Integer.class, SCHOOL, CANTEEN)).isEqualTo(1);

        mvc.perform(assistantMessage(
                        "assistant-order-cancel-preview", "取消 " + orderId, "CONV-DINER-CANCEL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.data.intent").value("meal_order.cancel"))
                .andExpect(jsonPath("$.data.runStatus").value("WAITING_CONFIRMATION"));
        mvc.perform(assistantMessage(
                        "assistant-order-cancel-confirm", "确认", "CONV-DINER-CANCEL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("RESULT"))
                .andExpect(jsonPath("$.data.intent").value("meal_order.cancel"))
                .andExpect(jsonPath("$.data.runStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.status").value("CANCELLED"));
    }

    @Test
    void assistant_requires_confirmation_before_mock_payment() throws Exception {
        MvcResult created = mvc.perform(post("/api/v1/meal-orders?" + SCOPE)
                        .requestAttr(AuthPrincipal.class.getName(), DINER)
                        .header("Idempotency-Key", "ASSIST-PAY-CREATE")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuId\":\"M901\",\"items\":[{\"dishId\":\"DISH-001\",\"quantity\":1}]}") )
                .andExpect(status().isOk())
                .andReturn();
        String orderId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

        mvc.perform(assistantMessage(
                        "assistant-payment-preview", "支付订单 " + orderId, "CONV-DINER-PAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.data.intent").value("meal_order.pay"))
                .andExpect(jsonPath("$.data.runStatus").value("WAITING_CONFIRMATION"));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT payment_status FROM meal_orders WHERE school_id = ? AND canteen_id = ? AND order_id = ?",
                String.class, SCHOOL, CANTEEN, orderId)).isEqualTo("UNPAID");

        mvc.perform(assistantMessage(
                        "assistant-payment-confirm", "确认", "CONV-DINER-PAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kind").value("RESULT"))
                .andExpect(jsonPath("$.data.intent").value("meal_order.pay"))
                .andExpect(jsonPath("$.data.runStatus").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.paymentStatus").value("PAID"));
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
