package com.example.smartcanteen.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.smartcanteen.agent.domain.ExecutionContext;
import com.example.smartcanteen.agent.infrastructure.MealReviewToolExecutor;
import com.example.smartcanteen.application.MealReviewService;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.MealReview;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MealReviewToolExecutorTest {

    private final MealReviewService reviews = mock(MealReviewService.class);
    private final MealReviewToolExecutor executor = new MealReviewToolExecutor(
            reviews, new ObjectMapper().findAndRegisterModules());
    private final ExecutionContext context = new ExecutionContext(
            "REQ-REVIEW-001",
            "USER-DINER-001",
            "diner",
            new CanteenScope("SCHOOL-001", "CANTEEN-001"),
            Set.of(Role.DINER),
            Set.of("MEAL_REVIEW_READ", "MEAL_REVIEW_WRITE"));

    @Test
    void exposes_personal_review_query_and_create_tools() {
        assertThat(executor.supports("meal_review.query")).isTrue();
        assertThat(executor.supports("meal_review.create")).isTrue();
        assertThat(executor.supports("diner_complaint.create")).isFalse();
    }

    @Test
    void delegates_query_with_actor_from_execution_context() {
        PageResult<MealReview> expected = new PageResult<>(List.of(review()), 1, 100, 1);
        when(reviews.listMine(context.scope(), context.actorUserId(), 1, 100))
                .thenReturn(expected);

        var result = executor.execute("meal_review.query", context, "{}");

        assertThat(result.resultJson()).contains("REVIEW-001").contains("MEAL-001");
        verify(reviews).listMine(context.scope(), context.actorUserId(), 1, 100);
    }

    @Test
    void parses_create_input_and_keeps_business_idempotency_key_explicit() {
        when(reviews.create(
                        eq(context.scope()), eq(context.actorUserId()), eq("MEAL-001"), eq(5),
                        eq("很好"), eq("REVIEW-KEY")))
                .thenReturn(review());

        var result = executor.execute(
                "meal_review.create",
                context,
                "{\"orderId\":\"MEAL-001\",\"rating\":5,\"content\":\"很好\","
                        + "\"businessIdempotencyKey\":\"REVIEW-KEY\"}");

        assertThat(result.resultJson()).contains("REVIEW-001");
        verify(reviews).create(
                context.scope(), context.actorUserId(), "MEAL-001", 5, "很好", "REVIEW-KEY");
    }

    @Test
    void rejects_identity_override_and_missing_business_key() {
        assertThatThrownBy(() -> executor.execute(
                        "meal_review.query", context, "{\"actorUserId\":\"OTHER\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported meal review field");
        assertThatThrownBy(() -> executor.execute(
                        "meal_review.create", context,
                        "{\"orderId\":\"MEAL-001\",\"rating\":5}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("businessIdempotencyKey is required");
    }

    private static MealReview review() {
        Instant now = Instant.parse("2026-08-21T03:00:00Z");
        return new MealReview(
                "REVIEW-001", "USER-DINER-001", "MEAL-001", "MO-001", 5, "很好",
                "SUBMITTED", 0, now, now);
    }
}
