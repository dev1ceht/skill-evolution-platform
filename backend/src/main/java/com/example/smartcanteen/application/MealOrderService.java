package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.MealOrderStore;
import com.example.smartcanteen.application.port.MealPaymentStore;
import com.example.smartcanteen.application.port.OperationalStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.DailyMenu;
import com.example.smartcanteen.domain.DailyMenuItem;
import com.example.smartcanteen.domain.DinerMenu;
import com.example.smartcanteen.domain.DinerMenuItem;
import com.example.smartcanteen.domain.Dish;
import com.example.smartcanteen.domain.MealOrder;
import com.example.smartcanteen.domain.MealOrderItem;
import com.example.smartcanteen.domain.MealPayment;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.security.ForbiddenException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MealOrderService {

    private final DailyMenuService menus;
    private final OperationalStore catalog;
    private final MealOrderStore store;
    private final MealPaymentStore payments;
    private final Clock clock;

    @Autowired
    public MealOrderService(
            DailyMenuService menus,
            OperationalStore catalog,
            MealOrderStore store,
            MealPaymentStore payments) {
        this(menus, catalog, store, payments, Clock.systemUTC());
    }

    public MealOrderService(
            DailyMenuService menus,
            OperationalStore catalog,
            MealOrderStore store,
            MealPaymentStore payments,
            Clock clock) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.store = Objects.requireNonNull(store, "store");
        this.payments = Objects.requireNonNull(payments, "payments");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(readOnly = true)
    public PageResult<DinerMenu> listPublishedMenus(
            CanteenScope scope,
            LocalDate menuDate,
            String mealTime,
            int page,
            int size) {
        String normalizedMealTime = normalizeMealTime(mealTime);
        LocalDate date = menuDate == null
                ? LocalDate.now(clock.withZone(ZoneId.systemDefault()))
                : menuDate;
        PageResult<DailyMenu> source = menus.listPublished(
                scope, date, date, normalizedMealTime, page, size);
        List<DinerMenu> result = source.records().stream()
                .map(menu -> toDinerMenu(scope, menu))
                .toList();
        return new PageResult<>(result, source.current(), source.size(), source.total());
    }

    @Transactional(readOnly = true)
    public PageResult<MealOrder> listMine(
            CanteenScope scope,
            String actorUserId,
            String status,
            int page,
            int size) {
        requireActor(actorUserId);
        return store.listMine(scope, actorUserId, normalizeStatus(status), page, size);
    }

    @Transactional
    public MealOrder create(
            CanteenScope scope,
            String actorUserId,
            String menuId,
            LocalDate menuDate,
            String mealTime,
            List<RequestedItem> requestedItems,
            String idempotencyKey) {
        requireActor(actorUserId);
        String normalizedIdempotencyKey = requireText(
                idempotencyKey, "idempotencyKey", 128);
        List<RequestedItem> items = normalizeItems(requestedItems);
        DailyMenu menu = resolveMenu(scope, menuId, menuDate, mealTime);
        Map<String, DailyMenuItem> menuItems = new HashMap<>();
        for (DailyMenuItem item : menu.items()) {
            menuItems.put(item.dishId(), item);
        }

        List<MealOrderItem> orderItems = items.stream()
                .map(item -> {
                    if (!menuItems.containsKey(item.dishId())) {
                        throw new IllegalArgumentException(
                                "Dish is not included in published menu: " + item.dishId());
                    }
                    Dish dish = catalog.findDish(scope, item.dishId())
                            .filter(Dish::active)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "Dish is unavailable: " + item.dishId()));
                    return new MealOrderItem(
                            dish.id(),
                            dish.name(),
                            item.quantity(),
                            BigDecimal.ZERO,
                            BigDecimal.ZERO);
                })
                .toList();
        Instant now = clock.instant();
        MealOrder order = new MealOrder(
                "MEAL-" + UUID.randomUUID(),
                "MO-" + UUID.randomUUID(),
                actorUserId,
                menu.id(),
                menu.menuDate(),
                menu.mealTime(),
                "CREATED",
                "UNPAID",
                BigDecimal.ZERO,
                orderItems,
                0,
                now,
                now);
        return store.create(
                scope,
                order,
                normalizedIdempotencyKey,
                requestHash(menu, orderItems));
    }

    @Transactional
    public MealOrder cancel(CanteenScope scope, String actorUserId, String orderId) {
        requireActor(actorUserId);
        String normalizedOrderId = requireText(orderId, "orderId", 64);
        MealOrder current = store.find(scope, normalizedOrderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Meal order not found: " + normalizedOrderId));
        if (!actorUserId.equals(current.actorUserId())) {
            throw new ForbiddenException("Only the order owner can cancel a meal order");
        }
        if ("CANCELLED".equals(current.status())) {
            return current;
        }
        if (!"CREATED".equals(current.status())) {
            throw new IllegalStateException(
                    "Meal order cannot be cancelled from status " + current.status());
        }
        if (!"UNPAID".equals(current.paymentStatus())) {
            throw new IllegalArgumentException("Only unpaid meal orders can be cancelled");
        }
        return store.cancel(scope, normalizedOrderId, actorUserId, current.version());
    }

    @Transactional
    public MealOrder pay(
            CanteenScope scope,
            String actorUserId,
            String orderId,
            String idempotencyKey) {
        requireActor(actorUserId);
        String normalizedOrderId = requireText(orderId, "orderId", 64);
        String normalizedKey = requireText(idempotencyKey, "idempotencyKey", 128);
        MealOrder current = store.find(scope, normalizedOrderId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Meal order not found: " + normalizedOrderId));
        if (!actorUserId.equals(current.actorUserId())) {
            throw new ForbiddenException("Only the order owner can pay a meal order");
        }
        String requestHash = paymentRequestHash(current);
        Optional<MealPayment> replay = payments.findByIdempotency(
                scope, actorUserId, normalizedKey);
        if (replay.isPresent()) {
            if (!requestHash.equals(replay.get().requestHash())) {
                throw new IllegalArgumentException(
                        "Idempotency-Key was already used for a different meal payment");
            }
            return current;
        }
        if ("CANCELLED".equals(current.status())) {
            throw new IllegalArgumentException("Cancelled meal order cannot be paid");
        }
        if (!"CREATED".equals(current.status())) {
            throw new IllegalStateException(
                    "Meal order cannot be paid from status " + current.status());
        }
        if ("PAID".equals(current.paymentStatus())
                || payments.findByOrder(scope, current.id()).isPresent()) {
            throw new IllegalArgumentException("Meal order has already been paid");
        }
        Instant now = clock.instant();
        MealPayment payment = new MealPayment(
                "PAY-" + UUID.randomUUID(),
                actorUserId,
                current.id(),
                current.totalAmount(),
                "STUDY_MOCK",
                "SUCCEEDED",
                normalizedKey,
                requestHash,
                0,
                now,
                now);
        MealPayment persisted = payments.create(scope, payment);
        if (!payment.id().equals(persisted.id())) {
            return store.find(scope, current.id())
                    .orElseThrow(() -> new IllegalStateException(
                            "Meal order disappeared during payment replay"));
        }
        return store.markPaid(scope, current.id(), actorUserId, current.version());
    }

    private DailyMenu resolveMenu(
            CanteenScope scope,
            String menuId,
            LocalDate menuDate,
            String mealTime) {
        if (menuId != null && !menuId.isBlank()) {
            return menus.getPublished(scope, menuId.trim());
        }
        if (menuDate == null) {
            throw new IllegalArgumentException("menuId or menuDate is required");
        }
        String normalizedMealTime = normalizeMealTime(mealTime);
        PageResult<DailyMenu> candidates = menus.listPublished(
                scope, menuDate, menuDate, normalizedMealTime, 1, 100);
        if (candidates.records().isEmpty()) {
            throw new IllegalArgumentException(
                    "Published menu not found for " + menuDate
                            + (normalizedMealTime == null ? "" : " / " + normalizedMealTime));
        }
        if (candidates.records().size() > 1) {
            throw new IllegalArgumentException(
                    "mealTime is required when more than one menu is published for the date");
        }
        return candidates.records().get(0);
    }

    private DinerMenu toDinerMenu(CanteenScope scope, DailyMenu menu) {
        List<DinerMenuItem> items = menu.items().stream()
                .map(DailyMenuItem::dishId)
                .map(dishId -> catalog.findDish(scope, dishId)
                        .filter(Dish::active)
                        .map(dish -> new DinerMenuItem(
                                dish.id(), dish.name(), dish.category(),
                                dish.description(), dish.imageUrl())))
                .flatMap(java.util.Optional::stream)
                .toList();
        return new DinerMenu(menu.id(), menu.menuDate(), menu.mealTime(), items);
    }

    private static List<RequestedItem> normalizeItems(List<RequestedItem> requestedItems) {
        if (requestedItems == null || requestedItems.isEmpty()) {
            throw new IllegalArgumentException("At least one meal order item is required");
        }
        if (requestedItems.size() > 20) {
            throw new IllegalArgumentException("At most 20 meal order items are allowed");
        }
        Set<String> dishIds = new HashSet<>();
        return requestedItems.stream()
                .map(item -> Objects.requireNonNull(item, "meal order item"))
                .peek(item -> {
                    if (!dishIds.add(item.dishId())) {
                        throw new IllegalArgumentException(
                                "Duplicate dish in meal order: " + item.dishId());
                    }
                })
                .toList();
    }

    private static String requestHash(DailyMenu menu, List<MealOrderItem> items) {
        String canonical = menu.id() + "|" + menu.menuDate() + "|" + menu.mealTime() + "|"
                + items.stream()
                .map(item -> item.dishId() + ":" + item.quantity())
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return sha256(canonical);
    }

    private static String paymentRequestHash(MealOrder order) {
        return sha256(order.id() + "|" + order.totalAmount() + "|STUDY_MOCK");
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

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("CREATED", "CANCELLED").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported meal order status: " + status);
        }
        return normalized;
    }

    private static String normalizeMealTime(String mealTime) {
        if (mealTime == null || mealTime.isBlank()) {
            return null;
        }
        String normalized = mealTime.trim().toUpperCase(Locale.ROOT);
        if (!List.of("BREAKFAST", "LUNCH", "DINNER", "SNACK").contains(normalized)) {
            throw new IllegalArgumentException("Unsupported mealTime: " + mealTime);
        }
        return normalized;
    }

    private static void requireActor(String actorUserId) {
        requireText(actorUserId, "actorUserId", 64);
    }

    private static String requireText(String value, String field, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw new IllegalArgumentException(
                    field + " must be non-blank and at most " + max + " characters");
        }
        return value.trim();
    }

    public record RequestedItem(String dishId, int quantity) {

        public RequestedItem {
            if (dishId == null || dishId.isBlank()) {
                throw new IllegalArgumentException("dishId is required");
            }
            dishId = dishId.trim();
            if (quantity < 1 || quantity > 20) {
                throw new IllegalArgumentException("quantity must be between 1 and 20");
            }
        }
    }
}
