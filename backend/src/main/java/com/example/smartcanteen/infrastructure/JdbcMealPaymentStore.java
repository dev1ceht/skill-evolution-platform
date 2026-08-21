package com.example.smartcanteen.infrastructure;

import com.example.smartcanteen.application.port.MealPaymentStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.MealPayment;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcMealPaymentStore implements MealPaymentStore {

    private final JdbcTemplate jdbc;

    public JdbcMealPaymentStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<MealPayment> findByIdempotency(
            CanteenScope scope, String actorUserId, String idempotencyKey) {
        return jdbc.query(
                        "SELECT * FROM meal_order_payments WHERE school_id = ? AND canteen_id = ? "
                                + "AND actor_user_id = ? AND idempotency_key = ?",
                        paymentMapper(),
                        scope.schoolId(),
                        scope.canteenId(),
                        actorUserId,
                        idempotencyKey)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<MealPayment> findByOrder(CanteenScope scope, String orderId) {
        return jdbc.query(
                        "SELECT * FROM meal_order_payments WHERE school_id = ? AND canteen_id = ? "
                                + "AND order_id = ?",
                        paymentMapper(),
                        scope.schoolId(),
                        scope.canteenId(),
                        orderId)
                .stream()
                .findFirst();
    }

    @Override
    public MealPayment create(CanteenScope scope, MealPayment payment) {
        Optional<MealPayment> existing = findByIdempotency(
                scope, payment.actorUserId(), payment.idempotencyKey());
        if (existing.isPresent()) {
            ensureSameRequest(existing.get(), payment.requestHash());
            return existing.get();
        }
        try {
            jdbc.update(
                    "INSERT INTO meal_order_payments (school_id, canteen_id, payment_id, "
                            + "order_id, actor_user_id, amount, method, status, idempotency_key, "
                            + "request_hash, version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    scope.schoolId(),
                    scope.canteenId(),
                    payment.id(),
                    payment.orderId(),
                    payment.actorUserId(),
                    payment.amount(),
                    payment.method(),
                    payment.status(),
                    payment.idempotencyKey(),
                    payment.requestHash(),
                    payment.version());
        } catch (DuplicateKeyException exception) {
            Optional<MealPayment> replay = findByIdempotency(
                    scope, payment.actorUserId(), payment.idempotencyKey());
            if (replay.isPresent()) {
                ensureSameRequest(replay.get(), payment.requestHash());
                return replay.get();
            }
            if (findByOrder(scope, payment.orderId()).isPresent()) {
                throw new IllegalArgumentException("Meal order has already been paid", exception);
            }
            throw exception;
        }
        return findByIdempotency(scope, payment.actorUserId(), payment.idempotencyKey())
                .orElseThrow(() -> new IllegalStateException("Meal payment was not persisted"));
    }

    private static void ensureSameRequest(MealPayment existing, String requestHash) {
        if (!requestHash.equals(existing.requestHash())) {
            throw new IllegalArgumentException(
                    "Idempotency-Key was already used for a different meal payment");
        }
    }

    private RowMapper<MealPayment> paymentMapper() {
        return (result, row) -> new MealPayment(
                result.getString("payment_id"),
                result.getString("actor_user_id"),
                result.getString("order_id"),
                result.getBigDecimal("amount"),
                result.getString("method"),
                result.getString("status"),
                result.getString("idempotency_key"),
                result.getString("request_hash"),
                result.getLong("version"),
                instant(result.getTimestamp("created_at")),
                instant(result.getTimestamp("updated_at")));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
