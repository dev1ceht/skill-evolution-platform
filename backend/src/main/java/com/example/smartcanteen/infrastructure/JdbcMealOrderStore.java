package com.example.smartcanteen.infrastructure;

import com.example.smartcanteen.application.port.MealOrderStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.MealOrder;
import com.example.smartcanteen.domain.MealOrderItem;
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
public class JdbcMealOrderStore implements MealOrderStore {

    private final JdbcTemplate jdbc;

    public JdbcMealOrderStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PageResult<MealOrder> listMine(
            CanteenScope scope, String actorUserId, String status, int page, int size) {
        requirePage(page, size);
        StringBuilder where = new StringBuilder(
                " WHERE school_id = ? AND canteen_id = ? AND actor_user_id = ? ");
        List<Object> params = new ArrayList<>(List.of(
                scope.schoolId(), scope.canteenId(), actorUserId));
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ? ");
            params.add(status);
        }
        long total = count("SELECT COUNT(*) FROM meal_orders" + where, params);
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(size);
        pageParams.add(offset(page, size));
        List<MealOrder> records = jdbc.query(
                "SELECT * FROM meal_orders" + where
                        + " ORDER BY created_at DESC, order_id DESC LIMIT ? OFFSET ?",
                orderMapper(),
                pageParams.toArray());
        return new PageResult<>(records, page, size, total);
    }

    @Override
    public Optional<MealOrder> find(CanteenScope scope, String orderId) {
        return jdbc.query(
                        "SELECT * FROM meal_orders WHERE school_id = ? AND canteen_id = ? "
                                + "AND order_id = ?",
                        orderMapper(),
                        scope.schoolId(),
                        scope.canteenId(),
                        orderId)
                .stream()
                .findFirst();
    }

    @Override
    public MealOrder create(
            CanteenScope scope,
            MealOrder order,
            String idempotencyKey,
            String requestHash) {
        Optional<MealOrder> existing = findByIdempotency(
                scope, order.actorUserId(), idempotencyKey);
        if (existing.isPresent()) {
            ensureSameRequest(scope, existing.get(), order.actorUserId(), requestHash);
            return existing.get();
        }
        try {
            jdbc.update(
                    "INSERT INTO meal_orders (school_id, canteen_id, order_id, order_no, "
                            + "actor_user_id, menu_id, meal_date, meal_time, status, payment_status, "
                            + "total_amount, idempotency_key, request_hash, version) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    scope.schoolId(),
                    scope.canteenId(),
                    order.id(),
                    order.orderNo(),
                    order.actorUserId(),
                    order.menuId(),
                    java.sql.Date.valueOf(order.mealDate()),
                    order.mealTime(),
                    order.status(),
                    order.paymentStatus(),
                    order.totalAmount(),
                    idempotencyKey,
                    requestHash,
                    order.version());
            for (MealOrderItem item : order.items()) {
                jdbc.update(
                        "INSERT INTO meal_order_items (school_id, canteen_id, order_id, dish_id, "
                                + "dish_name, quantity, unit_price, amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        scope.schoolId(),
                        scope.canteenId(),
                        order.id(),
                        item.dishId(),
                        item.dishName(),
                        item.quantity(),
                        item.unitPrice(),
                        item.amount());
            }
        } catch (DuplicateKeyException exception) {
            MealOrder concurrent = findByIdempotency(
                            scope, order.actorUserId(), idempotencyKey)
                    .orElseThrow(() -> exception);
            ensureSameRequest(scope, concurrent, order.actorUserId(), requestHash);
            return concurrent;
        }
        return find(scope, order.id())
                .orElseThrow(() -> new IllegalStateException("Meal order was not persisted"));
    }

    @Override
    public MealOrder cancel(
            CanteenScope scope,
            String orderId,
            String actorUserId,
            long expectedVersion) {
        int changed = jdbc.update(
                "UPDATE meal_orders SET status = 'CANCELLED', version = version + 1, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE school_id = ? AND canteen_id = ? "
                        + "AND order_id = ? AND actor_user_id = ? AND status = 'CREATED' AND version = ?",
                scope.schoolId(),
                scope.canteenId(),
                orderId,
                actorUserId,
                expectedVersion);
        if (changed != 1) {
            throw new IllegalStateException("Meal order was changed concurrently: " + orderId);
        }
        return find(scope, orderId)
                .orElseThrow(() -> new IllegalStateException("Meal order disappeared: " + orderId));
    }

    @Override
    public MealOrder markPaid(
            CanteenScope scope,
            String orderId,
            String actorUserId,
            long expectedVersion) {
        int changed = jdbc.update(
                "UPDATE meal_orders SET payment_status = 'PAID', version = version + 1, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE school_id = ? AND canteen_id = ? "
                        + "AND order_id = ? AND actor_user_id = ? AND status = 'CREATED' "
                        + "AND payment_status = 'UNPAID' AND version = ?",
                scope.schoolId(),
                scope.canteenId(),
                orderId,
                actorUserId,
                expectedVersion);
        if (changed != 1) {
            throw new IllegalStateException(
                    "Meal order was changed concurrently or is no longer payable: " + orderId);
        }
        return find(scope, orderId)
                .orElseThrow(() -> new IllegalStateException("Meal order disappeared: " + orderId));
    }

    private Optional<MealOrder> findByIdempotency(
            CanteenScope scope, String actorUserId, String idempotencyKey) {
        return jdbc.query(
                        "SELECT * FROM meal_orders WHERE school_id = ? AND canteen_id = ? "
                                + "AND actor_user_id = ? AND idempotency_key = ?",
                        orderMapper(),
                        scope.schoolId(),
                        scope.canteenId(),
                        actorUserId,
                        idempotencyKey)
                .stream()
                .findFirst();
    }

    private void ensureSameRequest(
            CanteenScope scope,
            MealOrder existing,
            String actorUserId,
            String requestHash) {
        String storedHash = jdbc.queryForObject(
                "SELECT request_hash FROM meal_orders WHERE school_id = ? AND canteen_id = ? "
                        + "AND order_id = ?",
                String.class,
                scope.schoolId(),
                scope.canteenId(),
                existing.id());
        if (!actorUserId.equals(existing.actorUserId()) || !requestHash.equals(storedHash)) {
            throw new IllegalArgumentException(
                    "Idempotency-Key was already used for a different meal order");
        }
    }

    private RowMapper<MealOrder> orderMapper() {
        return (result, row) -> new MealOrder(
                result.getString("order_id"),
                result.getString("order_no"),
                result.getString("actor_user_id"),
                result.getString("menu_id"),
                result.getDate("meal_date").toLocalDate(),
                result.getString("meal_time"),
                result.getString("status"),
                result.getString("payment_status"),
                result.getBigDecimal("total_amount"),
                jdbc.query(
                        "SELECT dish_id, dish_name, quantity, unit_price, amount "
                                + "FROM meal_order_items WHERE school_id = ? AND canteen_id = ? "
                                + "AND order_id = ? ORDER BY dish_id",
                        (item, itemRow) -> new MealOrderItem(
                                item.getString("dish_id"),
                                item.getString("dish_name"),
                                item.getInt("quantity"),
                                item.getBigDecimal("unit_price"),
                                item.getBigDecimal("amount")),
                        result.getString("school_id"),
                        result.getString("canteen_id"),
                        result.getString("order_id")),
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
