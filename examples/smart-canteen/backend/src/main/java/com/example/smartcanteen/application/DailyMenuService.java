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

    @Transactional
    public DailyMenu save(CanteenScope scope, DailyMenu requested, boolean create) {
        if (requested.items().isEmpty()) {
            throw new IllegalArgumentException("A daily menu must contain at least one dish");
        }
        Set<String> dishIds = new HashSet<>();
        for (DailyMenuItem item : requested.items()) {
            if (!dishIds.add(item.dishId())) {
                throw new IllegalArgumentException("Daily menu contains duplicate dish");
            }
            store.findDish(scope, item.dishId())
                    .filter(Dish::active)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown or disabled dish: " + item.dishId()));
        }
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
    public DailyMenu publish(CanteenScope scope, String menuId) {
        DailyMenu menu = store.findDailyMenu(scope, menuId)
                .orElseThrow(() -> new IllegalArgumentException("Daily menu not found: " + menuId));
        if ("PUBLISHED".equals(menu.status())) {
            return menu;
        }
        if (menu.items().isEmpty()) {
            throw new IllegalStateException("A daily menu must contain at least one dish");
        }
        store.publishDailyMenu(scope, menuId);
        return store.findDailyMenu(scope, menuId)
                .orElseThrow(() -> new IllegalStateException("Published menu was not persisted"));
    }
}
