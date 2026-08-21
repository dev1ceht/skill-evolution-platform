package com.example.smartcanteen.application;

import com.example.smartcanteen.application.port.OperationalStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.DailyMenu;
import com.example.smartcanteen.domain.DailyMenuItem;
import com.example.smartcanteen.domain.Dish;
import com.example.smartcanteen.domain.MealPrepItem;
import com.example.smartcanteen.domain.MealPrepRecommendation;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.domain.TrafficForecast;
import com.example.smartcanteen.domain.TrafficForecastResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Builds a read-only meal-prep recommendation from a forecast fact and a published menu. */
@Service
public class MealPrepRecommendationService {

    private static final String ALLOCATION_METHOD =
            "PROPORTIONAL_MENU_ESTIMATE_LARGEST_REMAINDER";

    private final DailyMenuService menus;
    private final OperationalStore operational;
    private final TrafficForecastService forecasts;

    public MealPrepRecommendationService(
            DailyMenuService menus,
            OperationalStore operational,
            TrafficForecastService forecasts) {
        this.menus = menus;
        this.operational = operational;
        this.forecasts = forecasts;
    }

    @Transactional(readOnly = true)
    public MealPrepRecommendation recommend(
            CanteenScope scope, LocalDate menuDate, String mealTime) {
        if (scope == null || menuDate == null) {
            throw new IllegalArgumentException("scope and menuDate are required");
        }
        String normalizedMealTime = TrafficForecast.normalizeMealTime(mealTime);
        TrafficForecastResult forecast = forecasts.forecast(scope, menuDate, normalizedMealTime);
        if (!forecast.available()) {
            return MealPrepRecommendation.unavailable(
                    menuDate, normalizedMealTime, forecast, forecast.reason());
        }

        PageResult<DailyMenu> result = menus.listPublished(
                scope, menuDate, menuDate, normalizedMealTime, 1, 100);
        if (result.records().isEmpty()) {
            return MealPrepRecommendation.unavailable(
                    menuDate, normalizedMealTime, forecast, "NO_PUBLISHED_MENU");
        }
        if (result.records().size() > 1) {
            throw new IllegalStateException(
                    "Multiple published menus exist for the same meal slot");
        }
        DailyMenu menu = result.records().get(0);
        if (menu.items().isEmpty()) {
            return MealPrepRecommendation.unavailable(
                    menuDate, normalizedMealTime, forecast, "EMPTY_PUBLISHED_MENU");
        }
        return buildRecommendation(scope, menu, forecast);
    }

    private MealPrepRecommendation buildRecommendation(
            CanteenScope scope, DailyMenu menu, TrafficForecastResult forecast) {
        Set<String> dishIds = new HashSet<>();
        List<MenuDish> menuDishes = new ArrayList<>();
        BigDecimal totalPlanned = BigDecimal.ZERO;
        for (DailyMenuItem menuItem : menu.items()) {
            if (!dishIds.add(menuItem.dishId())) {
                throw new IllegalStateException("Published menu contains duplicate dish");
            }
            Dish dish = operational.findDish(scope, menuItem.dishId())
                    .filter(Dish::active)
                    .orElseThrow(() -> new IllegalStateException(
                            "Published menu references an unavailable dish: " + menuItem.dishId()));
            menuDishes.add(new MenuDish(menuItem, dish));
            totalPlanned = totalPlanned.add(menuItem.estimatedQuantity());
        }
        if (totalPlanned.signum() <= 0) {
            throw new IllegalStateException("Published menu has no positive planned quantity");
        }
        BigDecimal plannedTotal = totalPlanned;

        List<Allocation> allocations = menuDishes.stream()
                .map(item -> allocation(item, plannedTotal, forecast.expectedDinerCount()))
                .sorted(Comparator.comparing(Allocation::fraction).reversed()
                        .thenComparing(allocation -> allocation.menuItem().sortOrder())
                        .thenComparing(allocation -> allocation.menuItem().dishId()))
                .toList();
        long floorTotal = allocations.stream().mapToLong(Allocation::floor).sum();
        long remaining = forecast.expectedDinerCount() - floorTotal;
        if (remaining < 0 || remaining > allocations.size()) {
            throw new IllegalStateException("Unable to allocate forecast count deterministically");
        }
        Set<String> incremented = new HashSet<>();
        for (int index = 0; index < remaining; index++) {
            incremented.add(allocations.get(index).menuItem().dishId());
        }

        List<MealPrepItem> items = menuDishes.stream()
                .sorted(Comparator.comparing(item -> item.menuItem().sortOrder()))
                .map(item -> {
                    Allocation allocation = allocations.stream()
                            .filter(candidate -> candidate.menuItem().dishId()
                                    .equals(item.menuItem().dishId()))
                            .findFirst()
                            .orElseThrow();
                    long recommended = allocation.floor()
                            + (incremented.contains(item.menuItem().dishId()) ? 1 : 0);
                    return new MealPrepItem(
                            item.menuItem().dishId(),
                            item.dish().name(),
                            item.menuItem().estimatedQuantity(),
                            recommended,
                            item.menuItem().sortOrder());
                })
                .toList();
        return new MealPrepRecommendation(
                menu.menuDate(),
                menu.mealTime(),
                true,
                menu.id(),
                forecast,
                ALLOCATION_METHOD,
                items,
                forecast.expectedDinerCount(),
                null);
    }

    private static Allocation allocation(
            MenuDish item, BigDecimal totalPlanned, long expectedDiners) {
        BigDecimal raw = BigDecimal.valueOf(expectedDiners)
                .multiply(item.menuItem().estimatedQuantity())
                .divide(totalPlanned, 18, RoundingMode.HALF_UP);
        long floor = raw.setScale(0, RoundingMode.FLOOR).longValueExact();
        return new Allocation(item.menuItem(), floor, raw.subtract(BigDecimal.valueOf(floor)));
    }

    private record MenuDish(DailyMenuItem menuItem, Dish dish) {
    }

    private record Allocation(DailyMenuItem menuItem, long floor, BigDecimal fraction) {
    }
}
