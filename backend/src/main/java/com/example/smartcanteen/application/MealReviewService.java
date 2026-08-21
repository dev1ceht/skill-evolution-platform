package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.MealOrderStore;
import com.example.smartcanteen.application.port.MealReviewStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.MealOrder;
import com.example.smartcanteen.domain.MealReview;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.security.ForbiddenException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MealReviewService {

    private final MealOrderStore orders;
    private final MealReviewStore reviews;
    private final Clock clock;

    @Autowired
    public MealReviewService(MealOrderStore orders, MealReviewStore reviews) {
        this(orders, reviews, Clock.systemUTC());
    }

    public MealReviewService(MealOrderStore orders, MealReviewStore reviews, Clock clock) {
        this.orders = Objects.requireNonNull(orders, "orders");
        this.reviews = Objects.requireNonNull(reviews, "reviews");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(readOnly = true)
    public PageResult<MealReview> listMine(
            CanteenScope scope, String actorUserId, int page, int size) {
        requireText(actorUserId, "actorUserId", 64);
        return reviews.listMine(scope, actorUserId, page, size);
    }

    @Transactional
    public MealReview create(
            CanteenScope scope,
            String actorUserId,
            String orderId,
            int rating,
            String content,
            String idempotencyKey) {
        requireText(actorUserId, "actorUserId", 64);
        String normalizedOrderId = requireText(orderId, "orderId", 64);
        String normalizedKey = requireText(idempotencyKey, "idempotencyKey", 128);
        MealOrder order = orders.find(scope, normalizedOrderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Meal order not found: " + normalizedOrderId));
        if (!actorUserId.equals(order.actorUserId())) {
            throw new ForbiddenException("Only the order owner can submit a meal review");
        }
        if ("CANCELLED".equals(order.status())) {
            throw new IllegalArgumentException("Cancelled meal order cannot be reviewed");
        }
        Instant now = clock.instant();
        MealReview review = new MealReview(
                "REVIEW-" + UUID.randomUUID(),
                actorUserId,
                order.id(),
                order.orderNo(),
                rating,
                content,
                "SUBMITTED",
                0,
                now,
                now);
        return reviews.create(scope, review, normalizedKey, requestHash(review));
    }

    private static String requestHash(MealReview review) {
        String canonical = review.orderId() + "|" + review.rating() + "|"
                + (review.content() == null ? "" : review.content());
        return sha256(canonical);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must be non-blank and at most " + maxLength + " characters");
        }
        return value.trim();
    }
}
