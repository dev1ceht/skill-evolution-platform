package com.example.smartcanteen.http;

import com.example.smartcanteen.application.CatalogService;
import com.example.smartcanteen.application.DailyMenuService;
import com.example.smartcanteen.application.DashboardService;
import com.example.smartcanteen.application.OperationalLedgerService;
import com.example.smartcanteen.application.ProcurementOperationsService;
import com.example.smartcanteen.application.port.OperationalStore;
import com.example.smartcanteen.domain.CanteenScope;
import com.example.smartcanteen.domain.DailyMenu;
import com.example.smartcanteen.domain.DailyMenuItem;
import com.example.smartcanteen.domain.Dish;
import com.example.smartcanteen.domain.DishIngredient;
import com.example.smartcanteen.domain.DashboardSummary;
import com.example.smartcanteen.domain.Ingredient;
import com.example.smartcanteen.domain.IngredientUnit;
import com.example.smartcanteen.domain.InventoryLine;
import com.example.smartcanteen.domain.Nutrition;
import com.example.smartcanteen.domain.OperationalLedgerRecord;
import com.example.smartcanteen.domain.PageResult;
import com.example.smartcanteen.domain.PurchaseOrder;
import com.example.smartcanteen.domain.PurchaseOrderItem;
import com.example.smartcanteen.domain.RiskAssessment;
import com.example.smartcanteen.domain.Supplier;
import com.example.smartcanteen.domain.TraceabilityResult;
import com.example.smartcanteen.security.ScopeAccess;
import com.example.smartcanteen.security.Role;
import com.example.smartcanteen.security.RoleAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OperationalController {

    private final CatalogService catalog;
    private final DailyMenuService dailyMenus;
    private final OperationalLedgerService ledgers;
    private final ProcurementOperationsService procurement;
    private final DashboardService dashboard;
    private final ScopeAccess scopes;
    private final RoleAccess roles;

    public OperationalController(
            CatalogService catalog,
            DailyMenuService dailyMenus,
            OperationalLedgerService ledgers,
            ProcurementOperationsService procurement,
            DashboardService dashboard,
            ScopeAccess scopes,
            RoleAccess roles) {
        this.catalog = catalog;
        this.dailyMenus = dailyMenus;
        this.ledgers = ledgers;
        this.procurement = procurement;
        this.dashboard = dashboard;
        this.scopes = scopes;
        this.roles = roles;
    }

    @GetMapping("/ingredients")
    public ApiResponse<PageView<Ingredient>> listIngredients(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        roles.requireReader(request);
        return ApiResponse.ok(PageView.from(catalog.listIngredients(
                scopes.require(request, schoolId, canteenId), keyword, category, page, size)));
    }

    @PostMapping("/ingredients")
    public ApiResponse<Ingredient> createIngredient(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody IngredientRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        CanteenScope scope = scopes.require(request, schoolId, canteenId);
        Ingredient ingredient = body.toDomain(body.ingredientId() == null
                ? "INGREDIENT-" + UUID.randomUUID() : body.ingredientId());
        return ApiResponse.ok(catalog.saveIngredient(
                scope, ingredient, true, body.unitsDomain()));
    }

    @PutMapping("/ingredients/{ingredientId}")
    public ApiResponse<Ingredient> updateIngredient(
            HttpServletRequest request,
            @PathVariable String ingredientId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody IngredientRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        CanteenScope scope = scopes.require(request, schoolId, canteenId);
        return ApiResponse.ok(catalog.saveIngredient(
                scope, body.toDomain(ingredientId), false, body.unitsDomain()));
    }

    @GetMapping("/ingredients/{ingredientId}/units")
    public ApiResponse<List<IngredientUnit>> listIngredientUnits(
            HttpServletRequest request,
            @PathVariable String ingredientId,
            @RequestParam String schoolId,
            @RequestParam String canteenId) {
        roles.requireReader(request);
        return ApiResponse.ok(catalog.listIngredientUnits(
                scopes.require(request, schoolId, canteenId), ingredientId));
    }

    @GetMapping("/dishes")
    public ApiResponse<PageView<Dish>> listDishes(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        roles.requireReader(request);
        return ApiResponse.ok(PageView.from(catalog.listDishes(
                scopes.require(request, schoolId, canteenId), keyword, category, page, size)));
    }

    @PostMapping("/dishes")
    public ApiResponse<Dish> createDish(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody DishRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        CanteenScope scope = scopes.require(request, schoolId, canteenId);
        return ApiResponse.ok(catalog.saveDish(scope, body.toDomain(
                body.dishId() == null ? "DISH-" + UUID.randomUUID() : body.dishId(), 0), true));
    }

    @PutMapping("/dishes/{dishId}")
    public ApiResponse<Dish> updateDish(
            HttpServletRequest request,
            @PathVariable String dishId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody DishRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        CanteenScope scope = scopes.require(request, schoolId, canteenId);
        return ApiResponse.ok(catalog.saveDish(scope, body.toDomain(dishId, body.version()), false));
    }

    @GetMapping("/daily-menus")
    public ApiResponse<PageView<DailyMenu>> listDailyMenus(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        roles.requireReader(request);
        return ApiResponse.ok(PageView.from(dailyMenus.list(
                scopes.require(request, schoolId, canteenId), startDate, endDate, page, size)));
    }

    @PostMapping("/daily-menus")
    public ApiResponse<DailyMenu> saveDailyMenu(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody DailyMenuRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        CanteenScope scope = scopes.require(request, schoolId, canteenId);
        boolean create = body.menuId() == null || body.menuId().isBlank();
        String menuId = create ? "MENU-" + UUID.randomUUID() : body.menuId();
        return ApiResponse.ok(dailyMenus.save(scope, body.toDomain(menuId), create));
    }

    @PostMapping("/daily-menus/{menuId}/publish")
    public ApiResponse<DailyMenu> publishDailyMenu(
            HttpServletRequest request,
            @PathVariable String menuId,
            @RequestParam String schoolId,
            @RequestParam String canteenId) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(dailyMenus.publish(
                scopes.require(request, schoolId, canteenId), menuId));
    }

    @GetMapping("/suppliers")
    public ApiResponse<PageView<Supplier>> listSuppliers(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        roles.requireReader(request);
        return ApiResponse.ok(PageView.from(procurement.listSuppliers(
                scopes.require(request, schoolId, canteenId), keyword, page, size)));
    }

    @PostMapping("/suppliers")
    public ApiResponse<Supplier> createSupplier(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody SupplierRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(procurement.createSupplier(
                scopes.require(request, schoolId, canteenId), body.toDomain()));
    }

    @GetMapping("/purchase-orders")
    public ApiResponse<PageView<PurchaseOrder>> listPurchaseOrders(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        roles.requireReader(request);
        return ApiResponse.ok(PageView.from(procurement.listOrders(
                scopes.require(request, schoolId, canteenId), status, page, size)));
    }

    @PostMapping("/purchase-orders")
    public ApiResponse<PurchaseOrder> createPurchaseOrder(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody PurchaseOrderRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(procurement.createOrder(
                scopes.require(request, schoolId, canteenId),
                body.orderId(),
                body.orderNo(),
                body.supplierId(),
                body.orderType(),
                body.expectedDeliveryAt(),
                body.remark(),
                idempotencyKey,
                body.items().stream().map(PurchaseOrderItemRequest::toDomain).toList()));
    }

    @PostMapping("/purchase-orders/{orderId}/status")
    public ApiResponse<PurchaseOrder> transitionPurchaseOrder(
            HttpServletRequest request,
            @PathVariable String orderId,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody StatusRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(procurement.transition(
                scopes.require(request, schoolId, canteenId), orderId, body.status()));
    }

    @PostMapping("/purchase-orders/{orderId}/receive")
    public ApiResponse<OperationalStore.ReceiveResult> receivePurchaseOrder(
            HttpServletRequest request,
            @PathVariable String orderId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestBody(required = false) ReceiveRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        List<OperationalStore.ReceiveItem> items = body == null || body.items() == null
                ? List.of()
                : body.items().stream().map(ReceiveItemRequest::toPort).toList();
        return ApiResponse.ok(procurement.receive(
                scopes.require(request, schoolId, canteenId), orderId, idempotencyKey, items));
    }

    @GetMapping("/inventory")
    public ApiResponse<PageView<InventoryLine>> listInventory(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean warningOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        roles.requireReader(request);
        return ApiResponse.ok(PageView.from(procurement.listInventory(
                scopes.require(request, schoolId, canteenId), keyword, warningOnly, page, size)));
    }

    @PostMapping("/inventory/stock-outs")
    public ApiResponse<OperationalStore.StockOutResult> stockOut(
            HttpServletRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody StockOutRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(procurement.stockOut(
                scopes.require(request, schoolId, canteenId),
                idempotencyKey,
                body.reason(),
                body.items().stream().map(StockOutItemRequest::toPort).toList()));
    }

    @PostMapping("/ledger/records")
    public ApiResponse<LedgerRecordView> saveLedgerRecord(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @Valid @RequestBody LedgerRecordRequest body) {
        roles.requireAny(request, Role.SYSTEM_ADMIN, Role.SCHOOL_ADMIN, Role.CANTEEN_STAFF);
        return ApiResponse.ok(LedgerRecordView.from(ledgers.save(
                scopes.require(request, schoolId, canteenId),
                body.recordId(),
                body.cycleId(),
                body.ledgerCode(),
                body.recordTime(),
                body.recorderId(),
                body.content(),
                body.photos(),
                body.remark())));
    }

    @GetMapping("/ledger/records")
    public ApiResponse<PageView<LedgerRecordView>> listLedgerRecords(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(required = false) String cycleId,
            @RequestParam(required = false) String ledgerCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant startTime,
            @RequestParam(required = false) Instant endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        roles.requireReader(request);
        return ApiResponse.ok(PageView.from(ledgers.list(
                scopes.require(request, schoolId, canteenId), cycleId, ledgerCode, status,
                startTime, endTime, page, size), LedgerRecordView::from));
    }

    @GetMapping("/ledger/stats")
    public ApiResponse<OperationalStore.LedgerStats> ledgerStats(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        roles.requireReader(request);
        return ApiResponse.ok(ledgers.stats(
                scopes.require(request, schoolId, canteenId), startDate, endDate));
    }

    @GetMapping("/dashboard/summary")
    public ApiResponse<DashboardSummary> dashboardSummary(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(required = false) LocalDate date) {
        roles.requireReader(request);
        return ApiResponse.ok(dashboard.summary(
                scopes.require(request, schoolId, canteenId), date));
    }

    @GetMapping("/dashboard/risk")
    public ApiResponse<RiskAssessment> dashboardRisk(
            HttpServletRequest request,
            @RequestParam String schoolId,
            @RequestParam String canteenId,
            @RequestParam(required = false) LocalDate date) {
        roles.requireReader(request);
        return ApiResponse.ok(dashboard.risk(
                scopes.require(request, schoolId, canteenId), date));
    }

    @GetMapping("/traceability/{traceCode}")
    public ApiResponse<TraceabilityResult> traceability(
            HttpServletRequest request,
            @PathVariable String traceCode,
            @RequestParam String schoolId,
            @RequestParam String canteenId) {
        roles.requireReader(request);
        return ApiResponse.ok(dashboard.trace(
                scopes.require(request, schoolId, canteenId), traceCode));
    }

    public record PageView<T>(long total, long pages, int current, int size, List<T> records) {
        static <T> PageView<T> from(PageResult<T> page) {
            return new PageView<>(page.total(), page.pages(), page.current(), page.size(), page.records());
        }

        static <T, R> PageView<R> from(PageResult<T> page, Function<T, R> mapper) {
            return new PageView<>(
                    page.total(),
                    page.pages(),
                    page.current(),
                    page.size(),
                    page.records().stream().map(mapper).toList());
        }
    }

    public record LedgerRecordView(
            String recordId,
            String cycleId,
            String ledgerCode,
            Instant recordTime,
            String recorderId,
            Map<String, Object> content,
            List<String> photos,
            String status,
            String remark,
            Instant createdAt) {

        static LedgerRecordView from(OperationalLedgerRecord record) {
            return new LedgerRecordView(
                    record.id(),
                    record.cycleId(),
                    record.ledgerCode(),
                    record.recordTime(),
                    record.recorderId(),
                    record.content(),
                    record.photos(),
                    record.status(),
                    record.remark(),
                    record.createdAt());
        }
    }

    public record IngredientRequest(
            String ingredientId,
            @NotBlank String name,
            @NotBlank String category,
            @NotBlank String baseUnit,
            String specification,
            @DecimalMin("0") BigDecimal energyKcal,
            @DecimalMin("0") BigDecimal proteinG,
            @DecimalMin("0") BigDecimal fatG,
            @DecimalMin("0") BigDecimal carbohydrateG,
            @DecimalMin("0") BigDecimal warningThreshold,
            Boolean active,
            List<@Valid IngredientUnitRequest> units) {

        Ingredient toDomain(String id) {
            return new Ingredient(
                    id,
                    name,
                    category,
                    baseUnit,
                    specification,
                    new Nutrition(
                            defaultZero(energyKcal),
                            defaultZero(proteinG),
                            defaultZero(fatG),
                            defaultZero(carbohydrateG)),
                    defaultZero(warningThreshold),
                    active == null || active);
        }

        List<IngredientUnit> unitsDomain() {
            return units == null ? null : units.stream()
                    .map(IngredientUnitRequest::toDomain)
                    .toList();
        }
    }

    public record IngredientUnitRequest(
            @NotBlank String unitCode,
            @NotBlank String baseUnit,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal toBaseFactor,
            Boolean active) {

        IngredientUnit toDomain() {
            return new IngredientUnit(
                    unitCode,
                    baseUnit,
                    toBaseFactor,
                    active == null || active);
        }
    }

    public record DishRequest(
            String dishId,
            @NotBlank String name,
            @NotBlank String category,
            String description,
            String imageUrl,
            Boolean active,
            long version,
            @NotEmpty List<@Valid DishIngredientRequest> ingredients) {

        Dish toDomain(String id, long versionValue) {
            return new Dish(
                    id,
                    name,
                    category,
                    description,
                    imageUrl,
                    active == null || active,
                    versionValue,
                    ingredients.stream().map(DishIngredientRequest::toDomain).toList());
        }
    }

    public record DishIngredientRequest(
            @NotBlank String ingredientId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotBlank String unit) {
        DishIngredient toDomain() {
            return new DishIngredient(ingredientId, quantity, unit);
        }
    }

    public record DailyMenuRequest(
            String menuId,
            @NotNull LocalDate menuDate,
            @NotBlank String mealTime,
            long version,
            @NotEmpty List<@Valid DailyMenuItemRequest> items) {
        DailyMenu toDomain(String id) {
            return new DailyMenu(
                    id,
                    menuDate,
                    mealTime,
                    "DRAFT",
                    version,
                    items.stream().map(DailyMenuItemRequest::toDomain).toList());
        }
    }

    public record DailyMenuItemRequest(
            @NotBlank String dishId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal estimatedQuantity,
            int sortOrder) {
        DailyMenuItem toDomain() {
            return new DailyMenuItem(dishId, estimatedQuantity, sortOrder);
        }
    }

    public record SupplierRequest(
            String supplierId,
            @NotBlank String name,
            String contactName,
            String contactPhone,
            String licenseNo,
            Boolean active) {
        Supplier toDomain() {
            return new Supplier(
                    supplierId == null || supplierId.isBlank() ? "SUPPLIER-" + UUID.randomUUID() : supplierId,
                    name,
                    contactName,
                    contactPhone,
                    licenseNo,
                    active == null || active);
        }
    }

    public record PurchaseOrderRequest(
            String orderId,
            String orderNo,
            @NotBlank String supplierId,
            @NotBlank String orderType,
            Instant expectedDeliveryAt,
            String remark,
            @NotEmpty List<@Valid PurchaseOrderItemRequest> items) {
    }

    public record PurchaseOrderItemRequest(
            @NotBlank String ingredientId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotBlank String unit,
            @NotNull @DecimalMin("0") BigDecimal unitPrice) {
        PurchaseOrderItem toDomain() {
            return new PurchaseOrderItem(ingredientId, quantity, unit, unitPrice, null);
        }
    }

    public record StatusRequest(@NotBlank String status) {
    }

    public record ReceiveRequest(@Valid List<ReceiveItemRequest> items) {
    }

    public record ReceiveItemRequest(
            @NotBlank String ingredientId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotBlank String unit,
            String batchNo,
            @NotNull @DecimalMin("0") BigDecimal purchasePrice,
            LocalDate productionDate,
            LocalDate expiryDate) {
        OperationalStore.ReceiveItem toPort() {
            return new OperationalStore.ReceiveItem(
                    ingredientId, quantity, unit, batchNo, purchasePrice, productionDate, expiryDate);
        }
    }

    public record StockOutRequest(
            String reason,
            @NotEmpty List<@Valid StockOutItemRequest> items) {
    }

    public record StockOutItemRequest(
            @NotBlank String ingredientId,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotBlank String unit) {
        OperationalStore.StockOutItem toPort() {
            return new OperationalStore.StockOutItem(ingredientId, quantity, unit);
        }
    }

    public record LedgerRecordRequest(
            String recordId,
            @NotBlank String cycleId,
            @NotBlank String ledgerCode,
            Instant recordTime,
            String recorderId,
            Map<String, Object> content,
            List<String> photos,
            String remark) {
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
