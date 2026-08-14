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

export async function startLedgerCycle(body: LedgerCycleRequest): Promise<LedgerAlertResponse> {
  const path = "/api/v1/ledger-cycles";
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<LedgerAlertResponse>;
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
