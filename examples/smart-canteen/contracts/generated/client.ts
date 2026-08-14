// Generated from API IR. Review before production use.

export type LoginRequest = {
  username: string;
  password: string;
  loginType?: string;
};

export type RefreshTokenRequest = {
  refreshToken: string;
};

export type LogoutRequest = {
  refreshToken?: string;
};

export type AuthUserInfo = {
  userId: string;
  username: string;
  nickname: string;
  role: string;
  roles?: Array<string>;
  schoolId?: string;
  canteenId?: string;
};

export type AuthTokens = {
  token: string;
  refreshToken: string;
  expiresIn: number;
  userInfo: AuthUserInfo;
};

export type CurrentUser = {
  userId: string;
  username: string;
  nickname: string;
  role: string;
  roles: Array<string>;
  permissions: Array<string>;
  schoolId?: string;
  canteenId?: string;
};

export type EmptyResponse = {
  code: number;
  message: string;
  data?: string;
};

export type AuthTokensResponse = {
  code: number;
  message: string;
  data: AuthTokens;
};

export type CurrentUserResponse = {
  code: number;
  message: string;
  data: CurrentUser;
};

export type School = {
  id: string;
  name: string;
  regionCode: string;
  active: boolean;
};

export type SchoolRequest = {
  id?: string;
  name: string;
  regionCode: string;
  active?: boolean;
};

export type Canteen = {
  id: string;
  schoolId: string;
  name: string;
  address?: string;
  regionCode: string;
  active: boolean;
};

export type CanteenRequest = {
  id?: string;
  schoolId?: string;
  name: string;
  address?: string;
  active?: boolean;
};

export type FoundationStatusRequest = {
  active: boolean;
};

export type RoleDefinition = {
  code: string;
  name: string;
  description?: string;
  systemRole: boolean;
  active: boolean;
  permissions: Array<string>;
};

export type PermissionDefinition = {
  code: string;
  name: string;
  resource: string;
  action: string;
  description?: string;
};

export type RolePermissionRequest = {
  permissionCodes?: Array<string>;
};

export type ScopeGrant = {
  assignmentId: string;
  userId: string;
  "type": string;
  regionCode?: string;
  schoolId?: string;
  canteenId?: string;
};

export type ScopeGrantRequest = {
  assignmentId?: string;
  "type": string;
  regionCode?: string;
  schoolId?: string;
  canteenId?: string;
};

export type ManagedUser = {
  userId: string;
  username: string;
  displayName: string;
  primaryRole: string;
  roles: Array<string>;
  schoolId?: string;
  canteenId?: string;
  active: boolean;
  scopeGrants: Array<ScopeGrant>;
};

export type CreateUserRequest = {
  username: string;
  password: string;
  displayName: string;
  primaryRole: string;
  roles?: Array<string>;
  schoolId?: string;
  canteenId?: string;
  active?: boolean;
  scopeGrants?: Array<ScopeGrantRequest>;
};

export type UpdateUserRequest = {
  displayName?: string;
  primaryRole?: string;
  roles?: Array<string>;
  schoolId?: string;
  canteenId?: string;
  active?: boolean;
  password?: string;
  scopeGrants?: Array<ScopeGrantRequest>;
};

export type RoleAssignmentRequest = {
  roles: Array<string>;
};

export type ScopeAssignmentRequest = {
  scopeGrants: Array<ScopeGrantRequest>;
};

export type AuditLog = {
  auditId: string;
  actorUserId?: string;
  action: string;
  resourceType: string;
  resourceId?: string;
  schoolId?: string;
  canteenId?: string;
  outcome: string;
  detail?: string;
  requestId?: string;
  createdAt: string;
};

export type SchoolListResponse = {
  code: number;
  message: string;
  data: Array<School>;
};

export type SchoolResponse = {
  code: number;
  message: string;
  data: School;
};

export type CanteenListResponse = {
  code: number;
  message: string;
  data: Array<Canteen>;
};

export type CanteenResponse = {
  code: number;
  message: string;
  data: Canteen;
};

export type RoleListResponse = {
  code: number;
  message: string;
  data: Array<RoleDefinition>;
};

export type PermissionListResponse = {
  code: number;
  message: string;
  data: Array<PermissionDefinition>;
};

export type RoleResponse = {
  code: number;
  message: string;
  data: RoleDefinition;
};

export type ManagedUserListResponse = {
  code: number;
  message: string;
  data: Array<ManagedUser>;
};

export type ManagedUserResponse = {
  code: number;
  message: string;
  data: ManagedUser;
};

export type AuditLogListResponse = {
  code: number;
  message: string;
  data: Array<AuditLog>;
};

export type Nutrition = {
  energyKcal: number;
  proteinG: number;
  fatG: number;
  carbohydrateG: number;
};

export type Ingredient = {
  id: string;
  name: string;
  category: string;
  baseUnit: string;
  specification?: string;
  nutrition: Nutrition;
  warningThreshold: number;
  active: boolean;
};

export type IngredientRequest = {
  ingredientId?: string;
  name: string;
  category: string;
  baseUnit: string;
  specification?: string;
  energyKcal?: number;
  proteinG?: number;
  fatG?: number;
  carbohydrateG?: number;
  warningThreshold?: number;
  active?: boolean;
  units?: Array<IngredientUnitRequest>;
};

export type IngredientUnit = {
  unitCode: string;
  baseUnit: string;
  toBaseFactor: number;
  active: boolean;
};

export type IngredientUnitRequest = {
  unitCode: string;
  baseUnit: string;
  toBaseFactor: number;
  active?: boolean;
};

export type DishIngredient = {
  ingredientId: string;
  quantity: number;
  unit: string;
};

export type DishIngredientRequest = {
  ingredientId: string;
  quantity: number;
  unit: string;
};

export type Dish = {
  id: string;
  name: string;
  category: string;
  description?: string;
  imageUrl?: string;
  active: boolean;
  version: number;
  ingredients: Array<DishIngredient>;
};

export type DishRequest = {
  dishId?: string;
  name: string;
  category: string;
  description?: string;
  imageUrl?: string;
  active?: boolean;
  version?: number;
  ingredients: Array<DishIngredientRequest>;
};

export type DailyMenuItem = {
  dishId: string;
  estimatedQuantity: number;
  sortOrder: number;
};

export type DailyMenu = {
  id: string;
  menuDate: string;
  mealTime: string;
  status: string;
  version: number;
  items: Array<DailyMenuItem>;
};

export type DailyMenuRequest = {
  menuId?: string;
  menuDate: string;
  mealTime: string;
  version: number;
  items: Array<DailyMenuItem>;
};

export type Supplier = {
  id: string;
  name: string;
  contactName?: string;
  contactPhone?: string;
  licenseNo?: string;
  active: boolean;
};

export type SupplierRequest = {
  supplierId?: string;
  name: string;
  contactName?: string;
  contactPhone?: string;
  licenseNo?: string;
  active?: boolean;
};

export type PurchaseOrderItem = {
  ingredientId: string;
  quantity: number;
  unit: string;
  unitPrice: number;
  amount: number;
};

export type PurchaseOrderItemRequest = {
  ingredientId: string;
  quantity: number;
  unit: string;
  unitPrice: number;
};

export type PurchaseOrder = {
  id: string;
  orderNo: string;
  supplierId: string;
  orderType: string;
  status: string;
  expectedDeliveryAt?: string;
  totalAmount: number;
  remark?: string;
  createdAt?: string;
  items: Array<PurchaseOrderItem>;
};

export type PurchaseOrderRequest = {
  orderId?: string;
  orderNo?: string;
  supplierId: string;
  orderType: string;
  expectedDeliveryAt?: string;
  remark?: string;
  items: Array<PurchaseOrderItemRequest>;
};

export type StatusRequest = {
  status: string;
};

export type ReceiveItemRequest = {
  ingredientId: string;
  quantity: number;
  unit: string;
  batchNo?: string;
  purchasePrice: number;
  productionDate?: string;
  expiryDate?: string;
};

export type ReceiveRequest = {
  items?: Array<ReceiveItemRequest>;
};

export type ReceiveResult = {
  orderId: string;
  receiptId: string;
  traceCodes: Array<string>;
};

export type StockOutItemRequest = {
  ingredientId: string;
  quantity: number;
  unit: string;
};

export type StockOutRequest = {
  reason?: string;
  items: Array<StockOutItemRequest>;
};

export type StockOutResult = {
  stockOutId: string;
  items: Array<StockOutItemRequest>;
};

export type InventoryLine = {
  ingredientId: string;
  ingredientName: string;
  category: string;
  quantity: number;
  unit: string;
  warningThreshold: number;
  warning: boolean;
  lastUpdateTime?: string;
};

export type LedgerRecordRequest = {
  recordId?: string;
  cycleId: string;
  ledgerCode: string;
  recordTime?: string;
  recorderId?: string;
  content?: Record<string, unknown>;
  photos?: Array<string>;
  remark?: string;
};

export type LedgerRecordView = {
  recordId: string;
  cycleId: string;
  ledgerCode: string;
  recordTime: string;
  recorderId?: string;
  content?: Record<string, unknown>;
  photos?: Array<string>;
  status: string;
  remark?: string;
  createdAt: string;
};

export type LedgerStats = {
  expected: number;
  completed: number;
  missing: number;
};

export type DashboardSummary = {
  date: string;
  todayMenuCount: number;
  publishedMenuCount: number;
  pendingPurchaseOrderCount: number;
  inventoryWarningCount: number;
  openLedgerAlertCount: number;
  openExternalAlertCount: number;
  purchaseAmount: number;
};

export type RiskAssessment = {
  score: number;
  level: string;
  factors: Array<string>;
};

export type TraceabilityResult = {
  traceCode: string;
  batchId: string;
  orderId: string;
  ingredientId: string;
  ingredientName?: string;
  supplierId: string;
  supplierName?: string;
  quantity: number;
  unit: string;
  receivedAt: string;
};

export type PageViewIngredient = {
  total: number;
  pages: number;
  current: number;
  size: number;
  records: Array<Ingredient>;
};

export type PageViewDish = {
  total: number;
  pages: number;
  current: number;
  size: number;
  records: Array<Dish>;
};

export type PageViewDailyMenu = {
  total: number;
  pages: number;
  current: number;
  size: number;
  records: Array<DailyMenu>;
};

export type PageViewSupplier = {
  total: number;
  pages: number;
  current: number;
  size: number;
  records: Array<Supplier>;
};

export type PageViewPurchaseOrder = {
  total: number;
  pages: number;
  current: number;
  size: number;
  records: Array<PurchaseOrder>;
};

export type PageViewInventory = {
  total: number;
  pages: number;
  current: number;
  size: number;
  records: Array<InventoryLine>;
};

export type PageViewLedgerRecord = {
  total: number;
  pages: number;
  current: number;
  size: number;
  records: Array<LedgerRecordView>;
};

export type IngredientPageResponse = {
  code: number;
  message: string;
  data: PageViewIngredient;
};

export type DishPageResponse = {
  code: number;
  message: string;
  data: PageViewDish;
};

export type DailyMenuPageResponse = {
  code: number;
  message: string;
  data: PageViewDailyMenu;
};

export type SupplierPageResponse = {
  code: number;
  message: string;
  data: PageViewSupplier;
};

export type PurchaseOrderPageResponse = {
  code: number;
  message: string;
  data: PageViewPurchaseOrder;
};

export type InventoryPageResponse = {
  code: number;
  message: string;
  data: PageViewInventory;
};

export type LedgerRecordPageResponse = {
  code: number;
  message: string;
  data: PageViewLedgerRecord;
};

export type IngredientResponse = {
  code: number;
  message: string;
  data: Ingredient;
};

export type DishResponse = {
  code: number;
  message: string;
  data: Dish;
};

export type DailyMenuResponse = {
  code: number;
  message: string;
  data: DailyMenu;
};

export type SupplierResponse = {
  code: number;
  message: string;
  data: Supplier;
};

export type PurchaseOrderResponse = {
  code: number;
  message: string;
  data: PurchaseOrder;
};

export type ReceiveResponse = {
  code: number;
  message: string;
  data: ReceiveResult;
};

export type StockOutResponse = {
  code: number;
  message: string;
  data: StockOutResult;
};

export type LedgerRecordResponse = {
  code: number;
  message: string;
  data: LedgerRecordView;
};

export type LedgerStatsResponse = {
  code: number;
  message: string;
  data: LedgerStats;
};

export type DashboardSummaryResponse = {
  code: number;
  message: string;
  data: DashboardSummary;
};

export type RiskAssessmentResponse = {
  code: number;
  message: string;
  data: RiskAssessment;
};

export type TraceabilityResponse = {
  code: number;
  message: string;
  data: TraceabilityResult;
};

export type Menu = {
  id: string;
  status: string;
  decisionComment?: string;
};

export type ApprovalDecision = {
  decision: string;
  comment: string;
};

export type GenerateProcurementRequest = {
  menuId: string;
};

export type RecipeImportRequest = {
  requirements: Array<RecipeRequirement>;
};

export type RecipeRequirement = {
  materialId: string;
  quantity: number;
  unit: string;
};

export type ProcurementItem = {
  materialId: string;
  requiredBaseQuantity: number;
  shortageBaseQuantity: number;
  baseUnit: string;
};

export type ProcurementPlan = {
  menuId: string;
  items: Array<ProcurementItem>;
};

export type ProcurementPlanAggregateItem = {
  ingredientId: string;
  requiredBaseQuantity: number;
  inventoryBaseQuantity: number;
  openOrderBaseQuantity: number;
  shortageBaseQuantity: number;
  plannedBaseQuantity: number;
  baseUnit: string;
};

export type ProcurementPlanAggregate = {
  id: string;
  planNo: string;
  periodStart: string;
  periodEnd: string;
  status: string;
  version: number;
  createdAt?: string;
  sourceMenuIds: Array<string>;
  items: Array<ProcurementPlanAggregateItem>;
  orderIds: Array<string>;
};

export type GenerateProcurementRangeRequest = {
  periodStart: string;
  periodEnd: string;
};

export type AdjustProcurementPlanRequest = {
  version: number;
  items: Array<ProcurementPlanItemAdjustment>;
};

export type ProcurementPlanItemAdjustment = {
  ingredientId: string;
  quantity: number;
  unit: string;
};

export type CreateProcurementOrderRequest = {
  supplierId: string;
  orderType: string;
  expectedDeliveryAt?: string;
  remark?: string;
  items: Array<PurchaseOrderItemRequest>;
};

export type InventoryReceipt = {
  materialId: string;
  quantity: number;
  unit: string;
};

export type AlertReportRequest = {
  source?: string;
  thirdWarnId: string;
  schoolId: string;
  schoolName?: string;
  areaCode?: string;
  deviceId?: string;
  deviceName?: string;
  canteenId?: string;
  warnHappenTime: string;
  alarmEventId: string;
  warnFullPic?: string;
  warnContent: string;
};

export type ExternalAlertReportRequest = {
  source?: string;
  thirdWarnId?: string;
  schoolId: string;
  schoolName?: string;
  areaCode?: string;
  deviceId?: string;
  deviceName?: string;
  canteenId?: string;
  warnHappenTime: string;
  alarmEventId: string;
  warnFullPic?: string;
  warnContent?: string;
};

export type AlertDisposalRequest = {
  source?: string;
  thirdWarnId?: string;
  processStatus: number;
  processTime?: string;
  processUser?: string;
  processContent?: string;
  processFile?: string;
};

export type ExternalAlertDisposalRequest = {
  source?: string;
  thirdWarnId?: string;
  warnId?: string;
  processStatus: number;
  processTime?: string;
  processUser?: string;
  processContent?: string;
  processFile?: string;
};

export type AlertRecord = {
  warnId: string;
  source: string;
  thirdWarnId: string;
  schoolId: string;
  schoolName?: string;
  areaCode?: string;
  deviceId?: string;
  deviceName?: string;
  canteenId?: string;
  warnHappenTime: string;
  alarmEventId: string;
  warnFullPic?: string;
  warnContent: string;
  status: string;
  processStatus: number;
  createdAt: string;
  processTime?: string;
  processUser?: string;
  processContent?: string;
  processFile?: string;
};

export type AlertPage = {
  records: Array<AlertRecord>;
  pageNum: number;
  pageSize: number;
  total: number;
};

export type AlertResponse = {
  code: number;
  message: string;
  data: AlertRecord;
};

export type AlertPageResponse = {
  code: number;
  message: string;
  data: AlertPage;
};

export type Receipt = {
  materialId: string;
  quantityBase: number;
  baseUnit: string;
};

export type LedgerRecord = {
  ledgerCode: string;
};

export type LedgerCycleRequest = {
  schoolId: string;
  canteenId: string;
  cycleId: string;
  ledgerCodes: Array<string>;
  periodStart?: string;
  periodEnd?: string;
};

export type ScopedLedgerRecord = {
  schoolId: string;
  canteenId: string;
  ledgerCode: string;
};

export type LedgerAlert = {
  schoolId: string;
  canteenId: string;
  cycleId: string;
  status: string;
  cleared: boolean;
  missingLedgerCodes: Array<string>;
};

export type MenuResponse = {
  code: number;
  message: string;
  data: Menu;
};

export type ProcurementPlanResponse = {
  code: number;
  message: string;
  data: ProcurementPlan;
};

export type ProcurementPlanAggregateResponse = {
  code: number;
  message: string;
  data: ProcurementPlanAggregate;
};

export type PageViewProcurementPlanAggregate = {
  total: number;
  pages: number;
  current: number;
  size: number;
  records: Array<ProcurementPlanAggregate>;
};

export type ProcurementPlanAggregatePageResponse = {
  code: number;
  message: string;
  data: PageViewProcurementPlanAggregate;
};

export type IngredientUnitListResponse = {
  code: number;
  message: string;
  data: Array<IngredientUnit>;
};

export type RecipeResponse = {
  code: number;
  message: string;
  data: Recipe;
};

export type Recipe = {
  menuId: string;
  requirements: Array<RecipeRequirement>;
};

export type ReceiptResponse = {
  code: number;
  message: string;
  data: Receipt;
};

export type LedgerAlertResponse = {
  code: number;
  message: string;
  data: LedgerAlert;
};

export type LedgerFrequency = "DAILY" | "WEEKLY" | "MONTHLY" | "CUSTOM";

export type LedgerConfigurationStatus = "ACTIVE" | "DISABLED";

export type LedgerConfiguration = {
  id: string;
  code: string;
  name: string;
  frequency: LedgerFrequency;
  periodDays?: number;
  requiredFields: Array<string>;
  template?: Record<string, unknown>;
  responsibleRole?: string;
  reminderDays: number;
  status: LedgerConfigurationStatus;
  version: number;
  createdAt?: string;
  updatedAt?: string;
};

export type LedgerConfigurationRequest = {
  configurationId?: string;
  code: string;
  name: string;
  frequency: LedgerFrequency;
  periodDays?: number;
  requiredFields: Array<string>;
  template?: Record<string, unknown>;
  responsibleRole?: string;
  reminderDays?: number;
  status?: LedgerConfigurationStatus;
  version?: number;
};

export type ConfiguredLedgerCycle = {
  cycleId: string;
  configurationId: string;
  ledgerCode: string;
  periodStart: string;
  periodEnd: string;
  status: string;
  missingLedgerCodes: Array<string>;
};

export type ConfiguredLedgerRecordRequest = {
  recordId?: string;
  ledgerCode: string;
  recordTime?: string;
  recorderId?: string;
  content?: Record<string, unknown>;
  photos?: Array<string>;
  remark?: string;
};

export type LedgerConfigurationResponse = {
  code: number;
  message: string;
  data: LedgerConfiguration;
};

export type LedgerConfigurationListResponse = {
  code: number;
  message: string;
  data: Array<LedgerConfiguration>;
};

export type ConfiguredLedgerCycleListResponse = {
  code: number;
  message: string;
  data: Array<ConfiguredLedgerCycle>;
};

export type ComplianceCategory = "LICENSE" | "HEALTH_CERTIFICATE" | "MANAGEMENT_DOCUMENT" | "SUPPLIER_QUALIFICATION" | "WASTE_RECYCLER_QUALIFICATION";

export type ComplianceRecordStatus = "DRAFT" | "SUBMITTED" | "APPROVED" | "REJECTED";

export type ComplianceRecord = {
  id: string;
  category: ComplianceCategory;
  subjectType: string;
  subjectId: string;
  subjectName: string;
  title: string;
  credentialNo?: string;
  validFrom: string;
  validTo: string;
  attachmentRefs: Array<string>;
  status: ComplianceRecordStatus;
  reviewRemark?: string;
  version: number;
  createdAt?: string;
  updatedAt?: string;
  submittedAt?: string;
  reviewedAt?: string;
  reviewedBy?: string;
};

export type ComplianceRecordRequest = {
  recordId?: string;
  category: ComplianceCategory;
  subjectType: string;
  subjectId: string;
  subjectName: string;
  title: string;
  credentialNo?: string;
  validFrom: string;
  validTo: string;
  attachmentRefs?: Array<string>;
  status?: ComplianceRecordStatus;
  reviewRemark?: string;
  version?: number;
};

export type ComplianceReviewRequest = {
  version: number;
  status: string;
  reviewRemark: string;
};

export type VersionRequest = {
  version: number;
};

export type ExpiryScanRequest = {
  asOf?: string;
  windowDays?: number;
};

export type ComplianceRecordResponse = {
  code: number;
  message: string;
  data: ComplianceRecord;
};

export type PageViewComplianceRecord = {
  total: number;
  pages: number;
  current: number;
  size: number;
  records: Array<ComplianceRecord>;
};

export type ComplianceRecordPageResponse = {
  code: number;
  message: string;
  data: PageViewComplianceRecord;
};

export type ComplianceHistory = {
  historyId: string;
  recordId: string;
  action: string;
  status: ComplianceRecordStatus;
  snapshot: Record<string, unknown>;
  actorId: string;
  occurredAt: string;
};

export type ComplianceHistoryListResponse = {
  code: number;
  message: string;
  data: Array<ComplianceHistory>;
};

export type AlertRecordListResponse = {
  code: number;
  message: string;
  data: Array<AlertRecord>;
};

export type CanteenShowcaseStatus = "DRAFT" | "SUBMITTED" | "APPROVED" | "REJECTED" | "PUBLISHED" | "REVOKED";

export type CanteenShowcase = {
  id: string;
  title: string;
  content: string;
  photos: Array<string>;
  status: CanteenShowcaseStatus;
  previousVersionId?: string;
  version: number;
  createdAt?: string;
  updatedAt?: string;
  reviewRemark?: string;
  reviewedAt?: string;
  reviewedBy?: string;
  publishedAt?: string;
};

export type ShowcaseRequest = {
  showcaseId?: string;
  title: string;
  content: string;
  photos?: Array<string>;
  status?: CanteenShowcaseStatus;
  previousVersionId?: string;
  version?: number;
};

export type ShowcaseReviewRequest = {
  version: number;
  status: string;
  reviewRemark: string;
};

export type CanteenShowcaseResponse = {
  code: number;
  message: string;
  data: CanteenShowcase;
};

export type PageViewCanteenShowcase = {
  total: number;
  pages: number;
  current: number;
  size: number;
  records: Array<CanteenShowcase>;
};

export type CanteenShowcasePageResponse = {
  code: number;
  message: string;
  data: PageViewCanteenShowcase;
};

export type MealSuspensionStatus = "SUBMITTED" | "APPROVED" | "REJECTED" | "CANCELLED";

export type MealPeriod = "BREAKFAST" | "LUNCH" | "DINNER" | "SNACK";

export type MealSuspension = {
  id: string;
  mealDate: string;
  mealPeriod: MealPeriod;
  reason: string;
  status: MealSuspensionStatus;
  reviewRemark?: string;
  version: number;
  createdAt?: string;
  updatedAt?: string;
  reviewedAt?: string;
  reviewedBy?: string;
};

export type MealSuspensionRequest = {
  suspensionId?: string;
  mealDate: string;
  mealPeriod: MealPeriod;
  reason: string;
};

export type MealReviewRequest = {
  version: number;
  status: string;
  reviewRemark: string;
};

export type MealSuspensionResponse = {
  code: number;
  message: string;
  data: MealSuspension;
};

export type PageViewMealSuspension = {
  total: number;
  pages: number;
  current: number;
  size: number;
  records: Array<MealSuspension>;
};

export type MealSuspensionPageResponse = {
  code: number;
  message: string;
  data: PageViewMealSuspension;
};

export type MealSuspensionStatsResponse = {
  code: number;
  message: string;
  data: Record<string, unknown>;
};

export type SupplierComplaintStatus = "SUBMITTED" | "ACCEPTED" | "PROCESSING" | "REPLIED" | "CLOSED" | "REJECTED";

export type SupplierComplaint = {
  id: string;
  supplierId: string;
  subject: string;
  description: string;
  attachmentRefs: Array<string>;
  deadline?: string;
  status: SupplierComplaintStatus;
  reply?: string;
  version: number;
  createdBy: string;
  assignedTo?: string;
  createdAt?: string;
  updatedAt?: string;
  acceptedAt?: string;
  closedAt?: string;
};

export type ComplaintRequest = {
  complaintId?: string;
  supplierId: string;
  subject: string;
  description: string;
  attachmentRefs?: Array<string>;
  deadline?: string;
};

export type ComplaintReviewRequest = {
  version: number;
  status: string;
  note?: string;
};

export type ComplaintReplyRequest = {
  version: number;
  reply: string;
};

export type SupplierComplaintResponse = {
  code: number;
  message: string;
  data: SupplierComplaint;
};

export type PageViewSupplierComplaint = {
  total: number;
  pages: number;
  current: number;
  size: number;
  records: Array<SupplierComplaint>;
};

export type SupplierComplaintPageResponse = {
  code: number;
  message: string;
  data: PageViewSupplierComplaint;
};

export type GovernanceHistory = {
  historyId: string;
  entityType: string;
  entityId: string;
  action: string;
  status: string;
  snapshot: Record<string, unknown>;
  actorId: string;
  occurredAt: string;
};

export type GovernanceHistoryListResponse = {
  code: number;
  message: string;
  data: Array<GovernanceHistory>;
};

export type ErrorResponse = {
  code: number;
  message: string;
  data?: string;
};

export async function reportExternalAlert(body: ExternalAlertReportRequest): Promise<AlertResponse> {
  const path = "/alarmApi/warn/report";
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<AlertResponse>;
}

export async function disposeExternalAlert(body: ExternalAlertDisposalRequest): Promise<AlertResponse> {
  const path = "/alarmApi/warnResult/report";
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<AlertResponse>;
}

export async function queryExternalAlerts(schoolId?: string, canteenId?: string, source?: string, status?: string, alarmEventId?: string, warnStatus?: string, deviceName?: string, startDate?: string, endDate?: string, pageNum?: number, pageSize?: number): Promise<AlertPageResponse> {
  const path = "/alarmWarn/school/queryPage";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  if (source !== undefined) url.searchParams.set("source", String(source));
  if (status !== undefined) url.searchParams.set("status", String(status));
  if (alarmEventId !== undefined) url.searchParams.set("alarmEventId", String(alarmEventId));
  if (warnStatus !== undefined) url.searchParams.set("warnStatus", String(warnStatus));
  if (deviceName !== undefined) url.searchParams.set("deviceName", String(deviceName));
  if (startDate !== undefined) url.searchParams.set("startDate", String(startDate));
  if (endDate !== undefined) url.searchParams.set("endDate", String(endDate));
  if (pageNum !== undefined) url.searchParams.set("pageNum", String(pageNum));
  if (pageSize !== undefined) url.searchParams.set("pageSize", String(pageSize));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<AlertPageResponse>;
}

export async function queryAlerts(schoolId?: string, canteenId?: string, source?: string, status?: string, alarmEventId?: string, deviceName?: string, startDate?: string, endDate?: string, pageNum?: number, pageSize?: number): Promise<AlertPageResponse> {
  const path = "/api/v1/alerts";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  if (source !== undefined) url.searchParams.set("source", String(source));
  if (status !== undefined) url.searchParams.set("status", String(status));
  if (alarmEventId !== undefined) url.searchParams.set("alarmEventId", String(alarmEventId));
  if (deviceName !== undefined) url.searchParams.set("deviceName", String(deviceName));
  if (startDate !== undefined) url.searchParams.set("startDate", String(startDate));
  if (endDate !== undefined) url.searchParams.set("endDate", String(endDate));
  if (pageNum !== undefined) url.searchParams.set("pageNum", String(pageNum));
  if (pageSize !== undefined) url.searchParams.set("pageSize", String(pageSize));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<AlertPageResponse>;
}

export async function reportAlert(body: AlertReportRequest): Promise<AlertResponse> {
  const path = "/api/v1/alerts";
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<AlertResponse>;
}

export async function disposeAlert(warnId: string, body: AlertDisposalRequest): Promise<AlertResponse> {
  const encodedWarnId = encodeURIComponent(String(warnId));
  let path = "/api/v1/alerts/{warnId}/disposal";
  path = path.replace("{warnId}", encodedWarnId);
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<AlertResponse>;
}

export async function listAuditLogs(limit?: number): Promise<AuditLogListResponse> {
  const path = "/api/v1/audit-logs";
  const url = new URL(path, window.location.origin);
  if (limit !== undefined) url.searchParams.set("limit", String(limit));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<AuditLogListResponse>;
}

export async function login(body: LoginRequest): Promise<AuthTokensResponse> {
  const path = "/api/v1/auth/login";
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<AuthTokensResponse>;
}

export async function logout(body: LogoutRequest): Promise<EmptyResponse> {
  const path = "/api/v1/auth/logout";
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<EmptyResponse>;
}

export async function currentUser(): Promise<CurrentUserResponse> {
  const path = "/api/v1/auth/me";
  const url = new URL(path, window.location.origin);
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<CurrentUserResponse>;
}

export async function refreshToken(body: RefreshTokenRequest): Promise<AuthTokensResponse> {
  const path = "/api/v1/auth/refresh-token";
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<AuthTokensResponse>;
}

export async function listCanteenShowcases(schoolId: string, canteenId: string, status?: CanteenShowcaseStatus, page?: number, size?: number): Promise<CanteenShowcasePageResponse> {
  const path = "/api/v1/canteen-showcases";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  if (status !== undefined) url.searchParams.set("status", String(status));
  if (page !== undefined) url.searchParams.set("page", String(page));
  if (size !== undefined) url.searchParams.set("size", String(size));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<CanteenShowcasePageResponse>;
}

export async function createCanteenShowcase(schoolId: string, canteenId: string, body: ShowcaseRequest): Promise<CanteenShowcaseResponse> {
  const path = "/api/v1/canteen-showcases";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<CanteenShowcaseResponse>;
}

export async function getCanteenShowcase(showcaseId: string, schoolId: string, canteenId: string): Promise<CanteenShowcaseResponse> {
  const encodedShowcaseId = encodeURIComponent(String(showcaseId));
  let path = "/api/v1/canteen-showcases/{showcaseId}";
  path = path.replace("{showcaseId}", encodedShowcaseId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<CanteenShowcaseResponse>;
}

export async function updateCanteenShowcase(showcaseId: string, schoolId: string, canteenId: string, body: ShowcaseRequest): Promise<CanteenShowcaseResponse> {
  const encodedShowcaseId = encodeURIComponent(String(showcaseId));
  let path = "/api/v1/canteen-showcases/{showcaseId}";
  path = path.replace("{showcaseId}", encodedShowcaseId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'PUT', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<CanteenShowcaseResponse>;
}

export async function listCanteenShowcaseHistory(showcaseId: string, schoolId: string, canteenId: string): Promise<GovernanceHistoryListResponse> {
  const encodedShowcaseId = encodeURIComponent(String(showcaseId));
  let path = "/api/v1/canteen-showcases/{showcaseId}/history";
  path = path.replace("{showcaseId}", encodedShowcaseId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<GovernanceHistoryListResponse>;
}

export async function publishCanteenShowcase(showcaseId: string, schoolId: string, canteenId: string, body: VersionRequest): Promise<CanteenShowcaseResponse> {
  const encodedShowcaseId = encodeURIComponent(String(showcaseId));
  let path = "/api/v1/canteen-showcases/{showcaseId}/publish";
  path = path.replace("{showcaseId}", encodedShowcaseId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<CanteenShowcaseResponse>;
}

export async function reviewCanteenShowcase(showcaseId: string, schoolId: string, canteenId: string, body: ShowcaseReviewRequest): Promise<CanteenShowcaseResponse> {
  const encodedShowcaseId = encodeURIComponent(String(showcaseId));
  let path = "/api/v1/canteen-showcases/{showcaseId}/review";
  path = path.replace("{showcaseId}", encodedShowcaseId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<CanteenShowcaseResponse>;
}

export async function revokeCanteenShowcase(showcaseId: string, schoolId: string, canteenId: string, body: VersionRequest): Promise<CanteenShowcaseResponse> {
  const encodedShowcaseId = encodeURIComponent(String(showcaseId));
  let path = "/api/v1/canteen-showcases/{showcaseId}/revoke";
  path = path.replace("{showcaseId}", encodedShowcaseId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<CanteenShowcaseResponse>;
}

export async function submitCanteenShowcase(showcaseId: string, schoolId: string, canteenId: string, body: VersionRequest): Promise<CanteenShowcaseResponse> {
  const encodedShowcaseId = encodeURIComponent(String(showcaseId));
  let path = "/api/v1/canteen-showcases/{showcaseId}/submit";
  path = path.replace("{showcaseId}", encodedShowcaseId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<CanteenShowcaseResponse>;
}

export async function createCanteenShowcaseVersion(showcaseId: string, schoolId: string, canteenId: string, body: ShowcaseRequest): Promise<CanteenShowcaseResponse> {
  const encodedShowcaseId = encodeURIComponent(String(showcaseId));
  let path = "/api/v1/canteen-showcases/{showcaseId}/versions";
  path = path.replace("{showcaseId}", encodedShowcaseId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<CanteenShowcaseResponse>;
}

export async function listCanteens(schoolId?: string, keyword?: string, includeInactive?: boolean): Promise<CanteenListResponse> {
  const path = "/api/v1/canteens";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (keyword !== undefined) url.searchParams.set("keyword", String(keyword));
  if (includeInactive !== undefined) url.searchParams.set("includeInactive", String(includeInactive));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<CanteenListResponse>;
}

export async function createCanteen(body: CanteenRequest): Promise<CanteenResponse> {
  const path = "/api/v1/canteens";
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<CanteenResponse>;
}

export async function updateCanteen(canteenId: string, body: CanteenRequest): Promise<CanteenResponse> {
  const encodedCanteenId = encodeURIComponent(String(canteenId));
  let path = "/api/v1/canteens/{canteenId}";
  path = path.replace("{canteenId}", encodedCanteenId);
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'PUT', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<CanteenResponse>;
}

export async function updateCanteenStatus(canteenId: string, body: FoundationStatusRequest): Promise<CanteenResponse> {
  const encodedCanteenId = encodeURIComponent(String(canteenId));
  let path = "/api/v1/canteens/{canteenId}/status";
  path = path.replace("{canteenId}", encodedCanteenId);
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<CanteenResponse>;
}

export async function listComplianceRecords(schoolId: string, canteenId: string, category?: ComplianceCategory, status?: ComplianceRecordStatus, expiringWithinDays?: number, page?: number, size?: number): Promise<ComplianceRecordPageResponse> {
  const path = "/api/v1/compliance-records";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  if (category !== undefined) url.searchParams.set("category", String(category));
  if (status !== undefined) url.searchParams.set("status", String(status));
  if (expiringWithinDays !== undefined) url.searchParams.set("expiringWithinDays", String(expiringWithinDays));
  if (page !== undefined) url.searchParams.set("page", String(page));
  if (size !== undefined) url.searchParams.set("size", String(size));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ComplianceRecordPageResponse>;
}

export async function createComplianceRecord(schoolId: string, canteenId: string, body: ComplianceRecordRequest): Promise<ComplianceRecordResponse> {
  const path = "/api/v1/compliance-records";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ComplianceRecordResponse>;
}

export async function scanComplianceExpiry(schoolId: string, canteenId: string, body?: ExpiryScanRequest): Promise<AlertRecordListResponse> {
  const path = "/api/v1/compliance-records/expiry-scan";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<AlertRecordListResponse>;
}

export async function getComplianceRecord(recordId: string, schoolId: string, canteenId: string): Promise<ComplianceRecordResponse> {
  const encodedRecordId = encodeURIComponent(String(recordId));
  let path = "/api/v1/compliance-records/{recordId}";
  path = path.replace("{recordId}", encodedRecordId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ComplianceRecordResponse>;
}

export async function updateComplianceRecord(recordId: string, schoolId: string, canteenId: string, body: ComplianceRecordRequest): Promise<ComplianceRecordResponse> {
  const encodedRecordId = encodeURIComponent(String(recordId));
  let path = "/api/v1/compliance-records/{recordId}";
  path = path.replace("{recordId}", encodedRecordId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'PUT', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ComplianceRecordResponse>;
}

export async function listComplianceHistory(recordId: string, schoolId: string, canteenId: string): Promise<ComplianceHistoryListResponse> {
  const encodedRecordId = encodeURIComponent(String(recordId));
  let path = "/api/v1/compliance-records/{recordId}/history";
  path = path.replace("{recordId}", encodedRecordId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ComplianceHistoryListResponse>;
}

export async function reviewComplianceRecord(recordId: string, schoolId: string, canteenId: string, body: ComplianceReviewRequest): Promise<ComplianceRecordResponse> {
  const encodedRecordId = encodeURIComponent(String(recordId));
  let path = "/api/v1/compliance-records/{recordId}/review";
  path = path.replace("{recordId}", encodedRecordId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ComplianceRecordResponse>;
}

export async function submitComplianceRecord(recordId: string, schoolId: string, canteenId: string, body: VersionRequest): Promise<ComplianceRecordResponse> {
  const encodedRecordId = encodeURIComponent(String(recordId));
  let path = "/api/v1/compliance-records/{recordId}/submit";
  path = path.replace("{recordId}", encodedRecordId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ComplianceRecordResponse>;
}

export async function listDailyMenus(schoolId: string, canteenId: string, startDate?: string, endDate?: string, page?: number, size?: number): Promise<DailyMenuPageResponse> {
  const path = "/api/v1/daily-menus";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  if (startDate !== undefined) url.searchParams.set("startDate", String(startDate));
  if (endDate !== undefined) url.searchParams.set("endDate", String(endDate));
  if (page !== undefined) url.searchParams.set("page", String(page));
  if (size !== undefined) url.searchParams.set("size", String(size));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<DailyMenuPageResponse>;
}

export async function saveDailyMenu(schoolId: string, canteenId: string, body: DailyMenuRequest): Promise<DailyMenuResponse> {
  const path = "/api/v1/daily-menus";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<DailyMenuResponse>;
}

export async function publishDailyMenu(menuId: string, schoolId: string, canteenId: string): Promise<DailyMenuResponse> {
  const encodedMenuId = encodeURIComponent(String(menuId));
  let path = "/api/v1/daily-menus/{menuId}/publish";
  path = path.replace("{menuId}", encodedMenuId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const response = await fetch(url, { method: 'POST' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<DailyMenuResponse>;
}

export async function getDashboardRisk(schoolId: string, canteenId: string, date?: string): Promise<RiskAssessmentResponse> {
  const path = "/api/v1/dashboard/risk";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  if (date !== undefined) url.searchParams.set("date", String(date));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<RiskAssessmentResponse>;
}

export async function getDashboardSummary(schoolId: string, canteenId: string, date?: string): Promise<DashboardSummaryResponse> {
  const path = "/api/v1/dashboard/summary";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  if (date !== undefined) url.searchParams.set("date", String(date));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<DashboardSummaryResponse>;
}

export async function listDishes(schoolId: string, canteenId: string, keyword?: string, category?: string, page?: number, size?: number): Promise<DishPageResponse> {
  const path = "/api/v1/dishes";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  if (keyword !== undefined) url.searchParams.set("keyword", String(keyword));
  if (category !== undefined) url.searchParams.set("category", String(category));
  if (page !== undefined) url.searchParams.set("page", String(page));
  if (size !== undefined) url.searchParams.set("size", String(size));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<DishPageResponse>;
}

export async function createDish(schoolId: string, canteenId: string, body: DishRequest): Promise<DishResponse> {
  const path = "/api/v1/dishes";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<DishResponse>;
}

export async function updateDish(dishId: string, schoolId: string, canteenId: string, body: DishRequest): Promise<DishResponse> {
  const encodedDishId = encodeURIComponent(String(dishId));
  let path = "/api/v1/dishes/{dishId}";
  path = path.replace("{dishId}", encodedDishId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'PUT', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<DishResponse>;
}

export async function listIngredients(schoolId: string, canteenId: string, keyword?: string, category?: string, page?: number, size?: number): Promise<IngredientPageResponse> {
  const path = "/api/v1/ingredients";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  if (keyword !== undefined) url.searchParams.set("keyword", String(keyword));
  if (category !== undefined) url.searchParams.set("category", String(category));
  if (page !== undefined) url.searchParams.set("page", String(page));
  if (size !== undefined) url.searchParams.set("size", String(size));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<IngredientPageResponse>;
}

export async function createIngredient(schoolId: string, canteenId: string, body: IngredientRequest): Promise<IngredientResponse> {
  const path = "/api/v1/ingredients";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<IngredientResponse>;
}

export async function updateIngredient(ingredientId: string, schoolId: string, canteenId: string, body: IngredientRequest): Promise<IngredientResponse> {
  const encodedIngredientId = encodeURIComponent(String(ingredientId));
  let path = "/api/v1/ingredients/{ingredientId}";
  path = path.replace("{ingredientId}", encodedIngredientId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'PUT', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<IngredientResponse>;
}

export async function listIngredientUnits(ingredientId: string, schoolId: string, canteenId: string): Promise<IngredientUnitListResponse> {
  const encodedIngredientId = encodeURIComponent(String(ingredientId));
  let path = "/api/v1/ingredients/{ingredientId}/units";
  path = path.replace("{ingredientId}", encodedIngredientId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<IngredientUnitListResponse>;
}

export async function listInventory(schoolId: string, canteenId: string, keyword?: string, warningOnly?: boolean, page?: number, size?: number): Promise<InventoryPageResponse> {
  const path = "/api/v1/inventory";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  if (keyword !== undefined) url.searchParams.set("keyword", String(keyword));
  if (warningOnly !== undefined) url.searchParams.set("warningOnly", String(warningOnly));
  if (page !== undefined) url.searchParams.set("page", String(page));
  if (size !== undefined) url.searchParams.set("size", String(size));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<InventoryPageResponse>;
}

export async function receiveInventory(idempotencyKey: string, body: InventoryReceipt, schoolId?: string, canteenId?: string): Promise<ReceiptResponse> {
  const path = "/api/v1/inventory/receipts";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Idempotency-Key"] = String(idempotencyKey);
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ReceiptResponse>;
}

export async function stockOutInventory(schoolId: string, canteenId: string, idempotencyKey: string, body: StockOutRequest): Promise<StockOutResponse> {
  const path = "/api/v1/inventory/stock-outs";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Idempotency-Key"] = String(idempotencyKey);
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<StockOutResponse>;
}

export async function getCurrentLedgerAlert(): Promise<LedgerAlertResponse> {
  const path = "/api/v1/ledger-alerts/current";
  const url = new URL(path, window.location.origin);
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<LedgerAlertResponse>;
}

export async function listLedgerConfigurations(schoolId: string, canteenId: string, includeDisabled?: boolean): Promise<LedgerConfigurationListResponse> {
  const path = "/api/v1/ledger-configurations";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  if (includeDisabled !== undefined) url.searchParams.set("includeDisabled", String(includeDisabled));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<LedgerConfigurationListResponse>;
}

export async function createLedgerConfiguration(schoolId: string, canteenId: string, body: LedgerConfigurationRequest): Promise<LedgerConfigurationResponse> {
  const path = "/api/v1/ledger-configurations";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<LedgerConfigurationResponse>;
}

export async function updateLedgerConfiguration(configurationId: string, schoolId: string, canteenId: string, body: LedgerConfigurationRequest): Promise<LedgerConfigurationResponse> {
  const encodedConfigurationId = encodeURIComponent(String(configurationId));
  let path = "/api/v1/ledger-configurations/{configurationId}";
  path = path.replace("{configurationId}", encodedConfigurationId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'PUT', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<LedgerConfigurationResponse>;
}

export async function startLedgerCycle(body: LedgerCycleRequest): Promise<LedgerAlertResponse> {
  const path = "/api/v1/ledger-cycles";
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<LedgerAlertResponse>;
}

export async function ensureConfiguredLedgerCycles(schoolId: string, canteenId: string, asOf?: string): Promise<ConfiguredLedgerCycleListResponse> {
  const path = "/api/v1/ledger-cycles/configured/current";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  if (asOf !== undefined) url.searchParams.set("asOf", String(asOf));
  const response = await fetch(url, { method: 'POST' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ConfiguredLedgerCycleListResponse>;
}

export async function completeConfiguredLedger(cycleId: string, schoolId: string, canteenId: string, body: ConfiguredLedgerRecordRequest): Promise<LedgerRecordResponse> {
  const encodedCycleId = encodeURIComponent(String(cycleId));
  let path = "/api/v1/ledger-cycles/configured/{cycleId}/records";
  path = path.replace("{cycleId}", encodedCycleId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<LedgerRecordResponse>;
}

export async function getScopedLedgerAlert(cycleId: string, schoolId: string, canteenId: string): Promise<LedgerAlertResponse> {
  const encodedCycleId = encodeURIComponent(String(cycleId));
  let path = "/api/v1/ledger-cycles/{cycleId}/alerts/current";
  path = path.replace("{cycleId}", encodedCycleId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<LedgerAlertResponse>;
}

export async function completeScopedLedgerRecord(cycleId: string, body: ScopedLedgerRecord): Promise<LedgerAlertResponse> {
  const encodedCycleId = encodeURIComponent(String(cycleId));
  let path = "/api/v1/ledger-cycles/{cycleId}/records";
  path = path.replace("{cycleId}", encodedCycleId);
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<LedgerAlertResponse>;
}

export async function completeLedgerRecord(body: LedgerRecord): Promise<LedgerAlertResponse> {
  const path = "/api/v1/ledger-records";
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<LedgerAlertResponse>;
}

export async function listOperationalLedgerRecords(schoolId: string, canteenId: string, cycleId?: string, ledgerCode?: string, status?: string, startTime?: string, endTime?: string, page?: number, size?: number): Promise<LedgerRecordPageResponse> {
  const path = "/api/v1/ledger/records";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  if (cycleId !== undefined) url.searchParams.set("cycleId", String(cycleId));
  if (ledgerCode !== undefined) url.searchParams.set("ledgerCode", String(ledgerCode));
  if (status !== undefined) url.searchParams.set("status", String(status));
  if (startTime !== undefined) url.searchParams.set("startTime", String(startTime));
  if (endTime !== undefined) url.searchParams.set("endTime", String(endTime));
  if (page !== undefined) url.searchParams.set("page", String(page));
  if (size !== undefined) url.searchParams.set("size", String(size));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<LedgerRecordPageResponse>;
}

export async function saveOperationalLedgerRecord(schoolId: string, canteenId: string, body: LedgerRecordRequest): Promise<LedgerRecordResponse> {
  const path = "/api/v1/ledger/records";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<LedgerRecordResponse>;
}

export async function getOperationalLedgerStats(schoolId: string, canteenId: string, startDate?: string, endDate?: string): Promise<LedgerStatsResponse> {
  const path = "/api/v1/ledger/stats";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  if (startDate !== undefined) url.searchParams.set("startDate", String(startDate));
  if (endDate !== undefined) url.searchParams.set("endDate", String(endDate));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<LedgerStatsResponse>;
}

export async function listMealSuspensions(schoolId: string, canteenId: string, fromParameter?: string, to?: string, status?: MealSuspensionStatus, page?: number, size?: number): Promise<MealSuspensionPageResponse> {
  const path = "/api/v1/meal-suspensions";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  if (fromParameter !== undefined) url.searchParams.set("from", String(fromParameter));
  if (to !== undefined) url.searchParams.set("to", String(to));
  if (status !== undefined) url.searchParams.set("status", String(status));
  if (page !== undefined) url.searchParams.set("page", String(page));
  if (size !== undefined) url.searchParams.set("size", String(size));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<MealSuspensionPageResponse>;
}

export async function createMealSuspension(schoolId: string, canteenId: string, body: MealSuspensionRequest): Promise<MealSuspensionResponse> {
  const path = "/api/v1/meal-suspensions";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<MealSuspensionResponse>;
}

export async function getMealSuspensionStats(schoolId: string, canteenId: string, fromParameter?: string, to?: string): Promise<MealSuspensionStatsResponse> {
  const path = "/api/v1/meal-suspensions/stats";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  if (fromParameter !== undefined) url.searchParams.set("from", String(fromParameter));
  if (to !== undefined) url.searchParams.set("to", String(to));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<MealSuspensionStatsResponse>;
}

export async function cancelMealSuspension(suspensionId: string, schoolId: string, canteenId: string, body: VersionRequest): Promise<MealSuspensionResponse> {
  const encodedSuspensionId = encodeURIComponent(String(suspensionId));
  let path = "/api/v1/meal-suspensions/{suspensionId}/cancel";
  path = path.replace("{suspensionId}", encodedSuspensionId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<MealSuspensionResponse>;
}

export async function listMealSuspensionHistory(suspensionId: string, schoolId: string, canteenId: string): Promise<GovernanceHistoryListResponse> {
  const encodedSuspensionId = encodeURIComponent(String(suspensionId));
  let path = "/api/v1/meal-suspensions/{suspensionId}/history";
  path = path.replace("{suspensionId}", encodedSuspensionId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<GovernanceHistoryListResponse>;
}

export async function reviewMealSuspension(suspensionId: string, schoolId: string, canteenId: string, body: MealReviewRequest): Promise<MealSuspensionResponse> {
  const encodedSuspensionId = encodeURIComponent(String(suspensionId));
  let path = "/api/v1/meal-suspensions/{suspensionId}/review";
  path = path.replace("{suspensionId}", encodedSuspensionId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<MealSuspensionResponse>;
}

export async function decideMenuApproval(menuId: string, body: ApprovalDecision, schoolId?: string, canteenId?: string): Promise<MenuResponse> {
  const encodedMenuId = encodeURIComponent(String(menuId));
  let path = "/api/v1/menu-approvals/{menuId}/decision";
  path = path.replace("{menuId}", encodedMenuId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<MenuResponse>;
}

export async function importMenuRecipe(menuId: string, body: RecipeImportRequest, schoolId?: string, canteenId?: string): Promise<RecipeResponse> {
  const encodedMenuId = encodeURIComponent(String(menuId));
  let path = "/api/v1/menus/{menuId}/recipe";
  path = path.replace("{menuId}", encodedMenuId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<RecipeResponse>;
}

export async function submitMenu(menuId: string, schoolId?: string, canteenId?: string): Promise<MenuResponse> {
  const encodedMenuId = encodeURIComponent(String(menuId));
  let path = "/api/v1/menus/{menuId}/submit";
  path = path.replace("{menuId}", encodedMenuId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const response = await fetch(url, { method: 'POST' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<MenuResponse>;
}

export async function listPermissions(): Promise<PermissionListResponse> {
  const path = "/api/v1/permissions";
  const url = new URL(path, window.location.origin);
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<PermissionListResponse>;
}

export async function listProcurementPlans(schoolId: string, canteenId: string, status?: string, page?: number, size?: number): Promise<ProcurementPlanAggregatePageResponse> {
  const path = "/api/v1/procurement-plans";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  if (status !== undefined) url.searchParams.set("status", String(status));
  if (page !== undefined) url.searchParams.set("page", String(page));
  if (size !== undefined) url.searchParams.set("size", String(size));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ProcurementPlanAggregatePageResponse>;
}

export async function generateProcurementPlan(body: GenerateProcurementRequest, schoolId?: string, canteenId?: string): Promise<ProcurementPlanResponse> {
  const path = "/api/v1/procurement-plans/generate";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ProcurementPlanResponse>;
}

export async function generateProcurementPlanRange(idempotencyKey: string, schoolId: string, canteenId: string, body: GenerateProcurementRangeRequest): Promise<ProcurementPlanAggregateResponse> {
  const path = "/api/v1/procurement-plans/generate-range";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Idempotency-Key"] = String(idempotencyKey);
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ProcurementPlanAggregateResponse>;
}

export async function getProcurementPlan(planId: string, schoolId: string, canteenId: string): Promise<ProcurementPlanAggregateResponse> {
  const encodedPlanId = encodeURIComponent(String(planId));
  let path = "/api/v1/procurement-plans/{planId}";
  path = path.replace("{planId}", encodedPlanId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ProcurementPlanAggregateResponse>;
}

export async function cancelProcurementPlan(planId: string, schoolId: string, canteenId: string): Promise<ProcurementPlanAggregateResponse> {
  const encodedPlanId = encodeURIComponent(String(planId));
  let path = "/api/v1/procurement-plans/{planId}/cancel";
  path = path.replace("{planId}", encodedPlanId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const response = await fetch(url, { method: 'POST' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ProcurementPlanAggregateResponse>;
}

export async function confirmProcurementPlan(planId: string, schoolId: string, canteenId: string): Promise<ProcurementPlanAggregateResponse> {
  const encodedPlanId = encodeURIComponent(String(planId));
  let path = "/api/v1/procurement-plans/{planId}/confirm";
  path = path.replace("{planId}", encodedPlanId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const response = await fetch(url, { method: 'POST' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ProcurementPlanAggregateResponse>;
}

export async function adjustProcurementPlan(planId: string, schoolId: string, canteenId: string, body: AdjustProcurementPlanRequest): Promise<ProcurementPlanAggregateResponse> {
  const encodedPlanId = encodeURIComponent(String(planId));
  let path = "/api/v1/procurement-plans/{planId}/items";
  path = path.replace("{planId}", encodedPlanId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'PUT', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ProcurementPlanAggregateResponse>;
}

export async function createPurchaseOrderFromPlan(planId: string, idempotencyKey: string, schoolId: string, canteenId: string, body: CreateProcurementOrderRequest): Promise<PurchaseOrderResponse> {
  const encodedPlanId = encodeURIComponent(String(planId));
  let path = "/api/v1/procurement-plans/{planId}/purchase-orders";
  path = path.replace("{planId}", encodedPlanId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Idempotency-Key"] = String(idempotencyKey);
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<PurchaseOrderResponse>;
}

export async function listPurchaseOrders(schoolId: string, canteenId: string, status?: string, page?: number, size?: number): Promise<PurchaseOrderPageResponse> {
  const path = "/api/v1/purchase-orders";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  if (status !== undefined) url.searchParams.set("status", String(status));
  if (page !== undefined) url.searchParams.set("page", String(page));
  if (size !== undefined) url.searchParams.set("size", String(size));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<PurchaseOrderPageResponse>;
}

export async function createPurchaseOrder(schoolId: string, canteenId: string, idempotencyKey: string, body: PurchaseOrderRequest): Promise<PurchaseOrderResponse> {
  const path = "/api/v1/purchase-orders";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Idempotency-Key"] = String(idempotencyKey);
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<PurchaseOrderResponse>;
}

export async function receivePurchaseOrder(orderId: string, schoolId: string, canteenId: string, idempotencyKey: string, body: ReceiveRequest): Promise<ReceiveResponse> {
  const encodedOrderId = encodeURIComponent(String(orderId));
  let path = "/api/v1/purchase-orders/{orderId}/receive";
  path = path.replace("{orderId}", encodedOrderId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Idempotency-Key"] = String(idempotencyKey);
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ReceiveResponse>;
}

export async function transitionPurchaseOrder(orderId: string, schoolId: string, canteenId: string, body: StatusRequest): Promise<PurchaseOrderResponse> {
  const encodedOrderId = encodeURIComponent(String(orderId));
  let path = "/api/v1/purchase-orders/{orderId}/status";
  path = path.replace("{orderId}", encodedOrderId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<PurchaseOrderResponse>;
}

export async function listRoles(): Promise<RoleListResponse> {
  const path = "/api/v1/roles";
  const url = new URL(path, window.location.origin);
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<RoleListResponse>;
}

export async function replaceRolePermissions(roleCode: string, body: RolePermissionRequest): Promise<RoleResponse> {
  const encodedRoleCode = encodeURIComponent(String(roleCode));
  let path = "/api/v1/roles/{roleCode}/permissions";
  path = path.replace("{roleCode}", encodedRoleCode);
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'PUT', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<RoleResponse>;
}

export async function listSchools(keyword?: string, includeInactive?: boolean): Promise<SchoolListResponse> {
  const path = "/api/v1/schools";
  const url = new URL(path, window.location.origin);
  if (keyword !== undefined) url.searchParams.set("keyword", String(keyword));
  if (includeInactive !== undefined) url.searchParams.set("includeInactive", String(includeInactive));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<SchoolListResponse>;
}

export async function createSchool(body: SchoolRequest): Promise<SchoolResponse> {
  const path = "/api/v1/schools";
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<SchoolResponse>;
}

export async function updateSchool(schoolId: string, body: SchoolRequest): Promise<SchoolResponse> {
  const encodedSchoolId = encodeURIComponent(String(schoolId));
  let path = "/api/v1/schools/{schoolId}";
  path = path.replace("{schoolId}", encodedSchoolId);
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'PUT', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<SchoolResponse>;
}

export async function updateSchoolStatus(schoolId: string, body: FoundationStatusRequest): Promise<SchoolResponse> {
  const encodedSchoolId = encodeURIComponent(String(schoolId));
  let path = "/api/v1/schools/{schoolId}/status";
  path = path.replace("{schoolId}", encodedSchoolId);
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<SchoolResponse>;
}

export async function listSupplierComplaints(schoolId: string, canteenId: string, status?: SupplierComplaintStatus, supplierId?: string, page?: number, size?: number): Promise<SupplierComplaintPageResponse> {
  const path = "/api/v1/supplier-complaints";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  if (status !== undefined) url.searchParams.set("status", String(status));
  if (supplierId !== undefined) url.searchParams.set("supplierId", String(supplierId));
  if (page !== undefined) url.searchParams.set("page", String(page));
  if (size !== undefined) url.searchParams.set("size", String(size));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<SupplierComplaintPageResponse>;
}

export async function createSupplierComplaint(schoolId: string, canteenId: string, body: ComplaintRequest): Promise<SupplierComplaintResponse> {
  const path = "/api/v1/supplier-complaints";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<SupplierComplaintResponse>;
}

export async function getSupplierComplaint(complaintId: string, schoolId: string, canteenId: string): Promise<SupplierComplaintResponse> {
  const encodedComplaintId = encodeURIComponent(String(complaintId));
  let path = "/api/v1/supplier-complaints/{complaintId}";
  path = path.replace("{complaintId}", encodedComplaintId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<SupplierComplaintResponse>;
}

export async function closeSupplierComplaint(complaintId: string, schoolId: string, canteenId: string, body: VersionRequest): Promise<SupplierComplaintResponse> {
  const encodedComplaintId = encodeURIComponent(String(complaintId));
  let path = "/api/v1/supplier-complaints/{complaintId}/close";
  path = path.replace("{complaintId}", encodedComplaintId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<SupplierComplaintResponse>;
}

export async function listSupplierComplaintHistory(complaintId: string, schoolId: string, canteenId: string): Promise<GovernanceHistoryListResponse> {
  const encodedComplaintId = encodeURIComponent(String(complaintId));
  let path = "/api/v1/supplier-complaints/{complaintId}/history";
  path = path.replace("{complaintId}", encodedComplaintId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<GovernanceHistoryListResponse>;
}

export async function processSupplierComplaint(complaintId: string, schoolId: string, canteenId: string, body: VersionRequest): Promise<SupplierComplaintResponse> {
  const encodedComplaintId = encodeURIComponent(String(complaintId));
  let path = "/api/v1/supplier-complaints/{complaintId}/process";
  path = path.replace("{complaintId}", encodedComplaintId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<SupplierComplaintResponse>;
}

export async function replySupplierComplaint(complaintId: string, schoolId: string, canteenId: string, body: ComplaintReplyRequest): Promise<SupplierComplaintResponse> {
  const encodedComplaintId = encodeURIComponent(String(complaintId));
  let path = "/api/v1/supplier-complaints/{complaintId}/reply";
  path = path.replace("{complaintId}", encodedComplaintId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<SupplierComplaintResponse>;
}

export async function reviewSupplierComplaint(complaintId: string, schoolId: string, canteenId: string, body: ComplaintReviewRequest): Promise<SupplierComplaintResponse> {
  const encodedComplaintId = encodeURIComponent(String(complaintId));
  let path = "/api/v1/supplier-complaints/{complaintId}/review";
  path = path.replace("{complaintId}", encodedComplaintId);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<SupplierComplaintResponse>;
}

export async function listSuppliers(schoolId: string, canteenId: string, keyword?: string, page?: number, size?: number): Promise<SupplierPageResponse> {
  const path = "/api/v1/suppliers";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  if (keyword !== undefined) url.searchParams.set("keyword", String(keyword));
  if (page !== undefined) url.searchParams.set("page", String(page));
  if (size !== undefined) url.searchParams.set("size", String(size));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<SupplierPageResponse>;
}

export async function createSupplier(schoolId: string, canteenId: string, body: SupplierRequest): Promise<SupplierResponse> {
  const path = "/api/v1/suppliers";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<SupplierResponse>;
}

export async function traceIngredientBatch(traceCode: string, schoolId: string, canteenId: string): Promise<TraceabilityResponse> {
  const encodedTraceCode = encodeURIComponent(String(traceCode));
  let path = "/api/v1/traceability/{traceCode}";
  path = path.replace("{traceCode}", encodedTraceCode);
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<TraceabilityResponse>;
}

export async function listUsers(schoolId?: string, canteenId?: string, active?: boolean): Promise<ManagedUserListResponse> {
  const path = "/api/v1/users";
  const url = new URL(path, window.location.origin);
  if (schoolId !== undefined) url.searchParams.set("schoolId", String(schoolId));
  if (canteenId !== undefined) url.searchParams.set("canteenId", String(canteenId));
  if (active !== undefined) url.searchParams.set("active", String(active));
  const response = await fetch(url, { method: 'GET' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ManagedUserListResponse>;
}

export async function createUser(body: CreateUserRequest): Promise<ManagedUserResponse> {
  const path = "/api/v1/users";
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ManagedUserResponse>;
}

export async function updateUser(userId: string, body: UpdateUserRequest): Promise<ManagedUserResponse> {
  const encodedUserId = encodeURIComponent(String(userId));
  let path = "/api/v1/users/{userId}";
  path = path.replace("{userId}", encodedUserId);
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'PUT', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ManagedUserResponse>;
}

export async function replaceUserRoles(userId: string, body: RoleAssignmentRequest): Promise<ManagedUserResponse> {
  const encodedUserId = encodeURIComponent(String(userId));
  let path = "/api/v1/users/{userId}/roles";
  path = path.replace("{userId}", encodedUserId);
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'PUT', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ManagedUserResponse>;
}

export async function replaceUserScopes(userId: string, body: ScopeAssignmentRequest): Promise<ManagedUserResponse> {
  const encodedUserId = encodeURIComponent(String(userId));
  let path = "/api/v1/users/{userId}/scopes";
  path = path.replace("{userId}", encodedUserId);
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'PUT', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ManagedUserResponse>;
}

export async function updateUserStatus(userId: string, body: FoundationStatusRequest): Promise<ManagedUserResponse> {
  const encodedUserId = encodeURIComponent(String(userId));
  let path = "/api/v1/users/{userId}/status";
  path = path.replace("{userId}", encodedUserId);
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ManagedUserResponse>;
}
