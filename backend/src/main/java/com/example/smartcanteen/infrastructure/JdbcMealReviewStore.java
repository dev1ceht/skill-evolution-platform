package com.example.smartcanteen.infrastructure;

import com.example.smartcanteen.application.port.MealReviewStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.MealReview;
import com.example.smartcanteen.domain.PageResult;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMealReviewStore implements MealReviewStore {

    private final JdbcTemplate jdbc;

    public JdbcMealReviewStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<MealReview> listMine(
            CanteenScope scope, String actorUserId, int page, int size) {
        requirePage(page, size);
        List<Object> params = new ArrayList<>(List.of(
                scope.schoolId(), scope.canteenId(), actorUserId));
        long total = count(
                "SELECT COUNT(*) FROM meal_reviews WHERE school_id = ? AND canteen_id = ? "
                        + "AND actor_user_id = ?",
                params);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add(offset(page, size));
        List<MealReview> records = jdbc.query(
                "SELECT * FROM meal_reviews WHERE school_id = ? AND canteen_id = ? "
                        + "AND actor_user_id = ? ORDER BY created_at DESC, review_id DESC LIMIT ? OFFSET ?",
                reviewMapper(), pageParams.toArray());
        return new PageResult<>(records, page, size, total);
    }

    @Override
    public Optional<MealReview> findByOrder(
            CanteenScope scope, String actorUserId, String orderId) {
        return jdbc.query(
                        "SELECT * FROM meal_reviews WHERE school_id = ? AND canteen_id = ? "
                                + "AND actor_user_id = ? AND order_id = ?",
                        reviewMapper(), scope.schoolId(), scope.canteenId(), actorUserId, orderId)
                .stream()
                .findFirst();
    }

    @Override
    public MealReview create(
            CanteenScope scope, MealReview review, String idempotencyKey, String requestHash) {
        Optional<MealReview> existing = findByIdempotency(
                scope, review.actorUserId(), idempotencyKey);
        if (existing.isPresent()) {
            ensureSameRequest(scope, existing.get(), requestHash);
            return existing.get();
        }
        try {
            jdbc.update(
                    "INSERT INTO meal_reviews (school_id, canteen_id, review_id, actor_user_id, "
                            + "order_id, order_no, rating, content, status, idempotency_key, "
                            + "request_hash, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    scope.schoolId(), scope.canteenId(), review.id(), review.actorUserId(),
                    review.orderId(), review.orderNo(), review.rating(), review.content(),
                    review.status(), idempotencyKey, requestHash, review.version());
        } catch (DuplicateKeyException exception) {
            Optional<MealReview> replay = findByIdempotency(
                    scope, review.actorUserId(), idempotencyKey);
            if (replay.isPresent()) {
                ensureSameRequest(scope, replay.get(), requestHash);
                return replay.get();
            }
            if (findByOrder(scope, review.actorUserId(), review.orderId()).isPresent()) {
                throw new IllegalArgumentException("Meal order has already been reviewed", exception);
            }
            throw exception;
        }
        return findByOrder(scope, review.actorUserId(), review.orderId())
                .orElseThrow(() -> new IllegalStateException("Meal review was not persisted"));
    }

    private Optional<MealReview> findByIdempotency(
            CanteenScope scope, String actorUserId, String idempotencyKey) {
        return jdbc.query(
                        "SELECT * FROM meal_reviews WHERE school_id = ? AND canteen_id = ? "
                                + "AND actor_user_id = ? AND idempotency_key = ?",
                        reviewMapper(), scope.schoolId(), scope.canteenId(), actorUserId, idempotencyKey)
                .stream()
                .findFirst();
    }

    private void ensureSameRequest(CanteenScope scope, MealReview existing, String requestHash) {
        String storedHash = jdbc.queryForObject(
                "SELECT request_hash FROM meal_reviews WHERE school_id = ? AND canteen_id = ? "
                        + "AND review_id = ?",
                String.class, scope.schoolId(), scope.canteenId(), existing.id());
        if (!requestHash.equals(storedHash)) {
            throw new IllegalArgumentException(
                    "Idempotency-Key was already used for a different meal review");
        }
    }

    private RowMapper<MealReview> reviewMapper() {
        return (result, row) -> new MealReview(
                result.getString("review_id"),
                result.getString("actor_user_id"),
                result.getString("order_id"),
                result.getString("order_no"),
                result.getInt("rating"),
                result.getString("content"),
                result.getString("status"),
                result.getLong("version"),
                instant(result.getTimestamp("created_at")),
                instant(result.getTimestamp("updated_at")));
    }

    private long count(String sql, List<?> parameters) {
        Number value = jdbc.queryForObject(sql, Number.class, parameters.toArray());
        return value == null ? 0 : value.longValue();
    }

    private static int offset(int page, int size) {
        return Math.multiplyExact(page - 1, size);
    }

    private static void requirePage(int page, int size) {
        if (page < 1 || page > 1_000_000 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be 1..1000000 and size must be 1..100");
        }
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
