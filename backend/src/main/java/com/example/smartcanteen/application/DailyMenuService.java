package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.OperationalStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.DailyMenu;
import com.example.smartcanteen.domain.DailyMenuItem;
import com.example.smartcanteen.domain.Dish;
import com.example.smartcanteen.domain.PageResult;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyMenuService {

    private final OperationalStore store;

    public DailyMenuService(OperationalStore store) {
        this.store = store;
    }

    @Transactional(readOnly = true)
    public PageResult<DailyMenu> list(
            CanteenScope scope, LocalDate from, LocalDate to, int page, int size) {
        LocalDate start = from == null ? LocalDate.now() : from;
        LocalDate end = to == null ? start : to;
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }
        return store.listDailyMenus(scope, start, end, page, size);
    }

    @Transactional(readOnly = true)
    public DailyMenu get(CanteenScope scope, String menuId) {
        return requireMenu(scope, menuId);
    }

    /** Read-only preflight used by the Agent plan before a menu is submitted. */
    @Transactional(readOnly = true)
    public DailyMenu validateForSubmit(
            CanteenScope scope, String menuId, long expectedVersion) {
        DailyMenu menu = requireMenu(scope, menuId);
        requireVersion(menu, expectedVersion);
        if (!"DRAFT".equals(menu.status()) && !"REJECTED".equals(menu.status())) {
            throw new IllegalStateException(
                    "Daily menu cannot be submitted from status " + menu.status());
        }
        validateMenuContents(scope, menu);
        ensureUniqueMealSlot(scope, menu);
        return menu;
    }

    @Transactional
    public DailyMenu save(CanteenScope scope, DailyMenu requested, boolean create) {
        validateMenuContents(scope, requested);
        ensureUniqueMealSlot(scope, requested);
        DailyMenu existing = store.findDailyMenu(scope, requested.id()).orElse(null);
        if (!create && existing == null) {
            throw new IllegalArgumentException("Daily menu not found: " + requested.id());
        }
        if (existing != null && "PUBLISHED".equals(existing.status())) {
            throw new IllegalStateException("Published daily menus cannot be edited");
        }
        DailyMenu normalized = new DailyMenu(
                requested.id(),
                requested.menuDate(),
                requested.mealTime(),
                "DRAFT",
                requested.version(),
                requested.items());
        store.saveDailyMenu(scope, normalized, create);
        return store.findDailyMenu(scope, requested.id())
                .orElseThrow(() -> new IllegalStateException("Daily menu was not persisted"));
    }

    @Transactional
    public DailyMenu submitForApproval(
            CanteenScope scope, String menuId, long expectedVersion, String actorUserId) {
        requireActor(actorUserId);
        validateForSubmit(scope, menuId, expectedVersion);
        store.submitDailyMenu(scope, menuId, expectedVersion, actorUserId);
        return requireMenu(scope, menuId);
    }

    @Transactional
    public DailyMenu recordDecision(
            CanteenScope scope,
            String menuId,
            long expectedVersion,
            String decision,
            String comment,
            String actorUserId) {
        requireActor(actorUserId);
        DailyMenu menu = requireMenu(scope, menuId);
        requireVersion(menu, expectedVersion);
        if (!"PENDING_APPROVAL".equals(menu.status())) {
            throw new IllegalStateException(
                    "Daily menu cannot be decided from status " + menu.status());
        }
        if (actorUserId.equals(menu.submittedBy())) {
            throw new IllegalStateException(
                    "The menu submitter cannot approve or reject the same menu");
        }
        String normalizedDecision = decision == null ? "" : decision.trim().toUpperCase();
        if (!Set.of("APPROVE", "REJECT").contains(normalizedDecision)) {
            throw new IllegalArgumentException("Unsupported menu decision: " + decision);
        }
        if (comment != null && comment.length() > 500) {
            throw new IllegalArgumentException("comment exceeds 500 characters");
        }
        store.decideDailyMenu(
                scope, menuId, expectedVersion, normalizedDecision, comment, actorUserId);
        return requireMenu(scope, menuId);
    }

    @Transactional
    public DailyMenu publish(
            CanteenScope scope, String menuId, long expectedVersion, String actorUserId) {
        requireActor(actorUserId);
        DailyMenu menu = requireMenu(scope, menuId);
        requireVersion(menu, expectedVersion);
        if ("PUBLISHED".equals(menu.status())) {
            throw new IllegalStateException("Published daily menus cannot be published again");
        }
        if (!"APPROVED".equals(menu.status())) {
            throw new IllegalStateException(
                    "Daily menu cannot be published before domain approval; status is "
                            + menu.status());
        }
        if (actorUserId.equals(menu.submittedBy()) || actorUserId.equals(menu.decisionBy())) {
            throw new IllegalStateException(
                    "The menu submitter or approver cannot publish the same menu");
        }
        if (menu.items().isEmpty()) {
            throw new IllegalStateException("A daily menu must contain at least one dish");
        }
        store.publishDailyMenu(scope, menuId, expectedVersion, actorUserId);
        return requireMenu(scope, menuId);
    }

    private DailyMenu requireMenu(CanteenScope scope, String menuId) {
        if (menuId == null || menuId.isBlank()) {
            throw new IllegalArgumentException("menuId is required");
        }
        return store.findDailyMenu(scope, menuId)
                .orElseThrow(() -> new IllegalArgumentException("Daily menu not found: " + menuId));
    }

    private static void requireVersion(DailyMenu menu, long expectedVersion) {
        if (menu.version() != expectedVersion) {
            throw new IllegalStateException(
                    "Daily menu changed concurrently: " + menu.id());
        }
    }

    private static void requireActor(String actorUserId) {
        if (actorUserId == null || actorUserId.isBlank()) {
            throw new IllegalArgumentException("actorUserId is required");
        }
    }

    /** Validate the menu aggregate and its referenced recipe graph before a lifecycle write. */
    private void validateMenuContents(CanteenScope scope, DailyMenu menu) {
        if (menu.items().isEmpty()) {
            throw new IllegalStateException("A daily menu must contain at least one dish");
        }
        Set<String> dishIds = new HashSet<>();
        for (DailyMenuItem item : menu.items()) {
            if (!dishIds.add(item.dishId())) {
                throw new IllegalArgumentException("Daily menu contains duplicate dish");
            }
            Dish dish = store.findDish(scope, item.dishId())
                    .filter(Dish::active)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown or disabled dish: " + item.dishId()));
            if (dish.ingredients().isEmpty()) {
                throw new IllegalArgumentException(
                        "Dish has no recipe ingredients: " + item.dishId());
            }
            Set<String> ingredientIds = new HashSet<>();
            dish.ingredients().forEach(recipe -> {
                if (!ingredientIds.add(recipe.ingredientId())) {
                    throw new IllegalArgumentException(
                            "Dish contains duplicate ingredient: " + recipe.ingredientId());
                }
                store.findIngredient(scope, recipe.ingredientId())
                        .filter(com.example.smartcanteen.domain.Ingredient::active)
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Unknown or disabled recipe ingredient: "
                                        + recipe.ingredientId()));
            });
        }
    }

    private void ensureUniqueMealSlot(CanteenScope scope, DailyMenu menu) {
        PageResult<DailyMenu> sameDay = store.listDailyMenus(
                scope, menu.menuDate(), menu.menuDate(), 1, 100);
        boolean occupied = sameDay.records().stream().anyMatch(existing ->
                !existing.id().equals(menu.id())
                        && existing.mealTime().equals(menu.mealTime()));
        if (occupied) {
            throw new IllegalStateException(
                    "A daily menu already exists for " + menu.menuDate() + " / " + menu.mealTime());
        }
    }
}
