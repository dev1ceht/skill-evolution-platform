import type { AxiosInstance, AxiosResponse } from 'axios';
import type {
  AuthTokens,
  AuthTokensResponse,
  CurrentUser,
  CurrentUserResponse,
  DashboardSummary,
  DashboardSummaryResponse,
  RiskAssessment,
  RiskAssessmentResponse,
  InventoryLine,
  InventoryPageResponse,
  Supplier,
  SupplierPageResponse,
  DailyMenu,
  DailyMenuResponse,
  ProcurementPlanAggregate,
  ProcurementPlanAggregateResponse,
  ProcurementPlanAggregatePageResponse,
  AdjustProcurementPlanRequest,
  CreateProcurementOrderRequest,
  PurchaseOrder,
  PurchaseOrderPageResponse,
  PurchaseOrderResponse,
  ReceiveRequest,
  ReceiveResponse,
  ReceiveResult,
  AlertRecord,
  AlertResponse,
  AlertPage,
  AlertPageResponse,
  AlertReportRequest,
  AlertDisposalRequest,
  LedgerConfiguration,
  LedgerConfigurationListResponse,
  ConfiguredLedgerCycle,
  ConfiguredLedgerCycleListResponse,
  ConfiguredLedgerRecordRequest,
  LedgerRecordResponse,
  ComplianceCategory,
  ComplianceRecordStatus,
  ComplianceRecord,
  ComplianceRecordPageResponse,
  ComplianceRecordResponse,
  ComplianceRecordRequest,
  ComplianceReviewRequest,
  ExpiryScanRequest,
  AlertRecordListResponse,
  CanteenShowcase,
  CanteenShowcaseStatus,
  CanteenShowcasePageResponse,
  CanteenShowcaseResponse,
  ShowcaseRequest,
  ShowcaseReviewRequest,
  MealSuspension,
  MealSuspensionStatus,
  MealSuspensionPageResponse,
  MealSuspensionResponse,
  MealSuspensionRequest,
  MealReviewRequest,
  MealReview,
  MealReviewPageResponse,
  MealReviewResponse,
  EmployeeMealReviewRequest,
  DinerComplaint,
  DinerComplaintPageResponse,
  DinerComplaintResponse,
  DinerComplaintRequest,
  SupplierComplaint,
  SupplierComplaintStatus,
  SupplierComplaintPageResponse,
  SupplierComplaintResponse,
  ComplaintRequest,
  ComplaintReviewRequest,
  ComplaintReplyRequest,
  AgentRun,
  AgentRunResponse,
  AgentRunEvent,
  AgentRunEventListResponse,
  AgentMetrics,
  AgentMetricsResponse,
  AssistantConversationHistory,
  AssistantConversationHistoryResponse,
  AssistantTurn,
  AssistantTurnResponse,
  Ingredient,
  IngredientRequest,
  IngredientPageResponse,
  IngredientResponse,
  IngredientUnit,
  IngredientUnitListResponse,
  Dish,
  DishRequest,
  DishPageResponse,
  DishResponse,
  DailyMenuPageResponse,
  DailyMenuRequest,
  DailyMenuDecisionRequest,
  LedgerRecordPageResponse,
  LedgerRecordRequest,
  LedgerStatsResponse,
  TraceabilityResponse,
  SupplierRequest,
  PurchaseOrderRequest,
  StockOutRequest,
  StockOutResponse,
} from './generated/client';

interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
}

export interface CanteenScope {
  schoolId: string;
  canteenId: string;
}

export interface AuthSession {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  userInfo: AuthTokens['userInfo'];
}

export interface DinerMenuItem {
  dishId: string;
  name: string;
  category: string;
  description: string;
  imageUrl: string | null;
}

export interface DinerMenu {
  id: string;
  menuDate: string;
  mealTime: string;
  items: DinerMenuItem[];
}

export interface MealOrderItem {
  dishId: string;
  dishName: string;
  quantity: number;
  unitPrice: number;
  amount: number;
}

export interface MealOrder {
  id: string;
  orderNo: string;
  actorUserId: string;
  menuId: string;
  mealDate: string;
  mealTime: string;
  status: 'CREATED' | 'CANCELLED' | string;
  paymentStatus: 'UNPAID';
  totalAmount: number;
  items: MealOrderItem[];
  version: number;
  createdAt: string;
  updatedAt: string;
}

export interface MealOrderRequest {
  menuId?: string;
  menuDate?: string;
  mealTime?: string;
  items: Array<{ dishId: string; quantity: number }>;
}

export class ApiBusinessError extends Error {
  readonly code: number;

  constructor(code: number, message: string) {
    super(message);
    this.name = 'ApiBusinessError';
    this.code = code;
  }
}

function unwrap<T>(response: AxiosResponse<ApiEnvelope<T>>): T {
  const envelope = response.data;
  if (envelope.code !== 0) {
    throw new ApiBusinessError(envelope.code, envelope.message);
  }
  return envelope.data;
}

export interface SmartCanteenApiPort {
  listDinerMenus?(
    scope: CanteenScope,
    date?: string,
    mealTime?: string,
  ): Promise<DinerMenu[]>;
  listMealOrders?(scope: CanteenScope, status?: string): Promise<MealOrder[]>;
  createMealOrder?(
    request: MealOrderRequest,
    idempotencyKey: string,
    scope: CanteenScope,
  ): Promise<MealOrder>;
  cancelMealOrder?(orderId: string, scope: CanteenScope): Promise<MealOrder>;
  listMealReviews?(scope: CanteenScope): Promise<MealReview[]>;
  createMealReview?(
    request: EmployeeMealReviewRequest,
    idempotencyKey: string,
    scope: CanteenScope,
  ): Promise<MealReview>;
  listDinerComplaints?(
    scope: CanteenScope,
    status?: 'SUBMITTED',
  ): Promise<DinerComplaint[]>;
  createDinerComplaint?(
    request: DinerComplaintRequest,
    idempotencyKey: string,
    scope: CanteenScope,
  ): Promise<DinerComplaint>;
  listIngredients?(scope: CanteenScope, keyword?: string, category?: string): Promise<Ingredient[]>;
  createIngredient?(request: IngredientRequest, scope: CanteenScope): Promise<Ingredient>;
  updateIngredient?(
    ingredientId: string,
    request: IngredientRequest,
    scope: CanteenScope,
  ): Promise<Ingredient>;
  listIngredientUnits?(ingredientId: string, scope: CanteenScope): Promise<IngredientUnit[]>;
  replaceIngredientUnits?(
    ingredientId: string,
    units: IngredientUnit[],
    scope: CanteenScope,
  ): Promise<IngredientUnit[]>;
  listDishes?(scope: CanteenScope, keyword?: string, category?: string): Promise<Dish[]>;
  createDish?(request: DishRequest, scope: CanteenScope): Promise<Dish>;
  updateDish?(dishId: string, request: DishRequest, scope: CanteenScope): Promise<Dish>;
  listDailyMenus?(
    scope: CanteenScope,
    from?: string,
    to?: string,
    status?: string,
  ): Promise<import('./generated/client').DailyMenu[]>;
  saveDailyMenu?(
    request: DailyMenuRequest,
    scope: CanteenScope,
  ): Promise<import('./generated/client').DailyMenu>;
  submitDailyMenu?(
    menuId: string,
    version: number,
    scope: CanteenScope,
  ): Promise<import('./generated/client').DailyMenu>;
  decideDailyMenu?(
    menuId: string,
    request: DailyMenuDecisionRequest,
    scope: CanteenScope,
  ): Promise<import('./generated/client').DailyMenu>;
  publishDailyMenu?(
    menuId: string,
    scope: CanteenScope,
  ): Promise<import('./generated/client').DailyMenu>;
  getDailyMenu?(menuId: string, scope: CanteenScope): Promise<DailyMenu>;
  startAgentTraceability?(
    traceCode: string,
    scope: CanteenScope,
    idempotencyKey: string,
    requestId?: string,
  ): Promise<AgentRun>;
  startAgentRun?(
    intent: string,
    input: Record<string, unknown>,
    scope: CanteenScope,
    idempotencyKey: string,
    requestId?: string,
  ): Promise<AgentRun>;
  getAgentRun?(
    runId: string,
    scope: CanteenScope,
    requestId?: string,
  ): Promise<AgentRun>;
  decideAgentRun?(
    runId: string,
    decisionType: 'RUN_CONFIRM' | 'RUN_REJECT' | 'RUN_CANCEL',
    version: number,
    scope: CanteenScope,
    comment?: string,
    requestId?: string,
    idempotencyKey?: string,
  ): Promise<AgentRun>;
  cancelAgentRun?(
    runId: string,
    version: number,
    scope: CanteenScope,
    requestId?: string,
    idempotencyKey?: string,
  ): Promise<AgentRun>;
  resumeAgentRun?(
    runId: string,
    version: number,
    scope: CanteenScope,
    requestId?: string,
    idempotencyKey?: string,
  ): Promise<AgentRun>;
  getAgentRunEvents?(
    runId: string,
    scope: CanteenScope,
    requestId?: string,
  ): Promise<AgentRunEvent[]>;
  getAgentMetrics?(
    scope: CanteenScope,
    from?: string,
    to?: string,
    requestId?: string,
  ): Promise<AgentMetrics>;
  sendAssistantMessage?(
    conversationId: string,
    message: string,
    scope: CanteenScope,
    idempotencyKey: string,
    requestId?: string,
  ): Promise<AssistantTurn>;
  getAssistantHistory?(
    conversationId: string,
    scope: CanteenScope,
    limit?: number,
    requestId?: string,
  ): Promise<AssistantConversationHistory>;
  reportAlert?(request: AlertReportRequest): Promise<AlertRecord>;
  disposeAlert?(warnId: string, request: AlertDisposalRequest): Promise<AlertRecord>;
  queryAlerts?(filters?: {
    schoolId?: string;
    canteenId?: string;
    source?: string;
    status?: string;
    deviceName?: string;
    startDate?: string;
    endDate?: string;
    pageNum?: number;
    pageSize?: number;
  }): Promise<AlertPage>;
  getDashboardSummary?(scope: CanteenScope, date?: string): Promise<DashboardSummary>;
  getDashboardRisk?(scope: CanteenScope, date?: string): Promise<RiskAssessment>;
  listInventory?(scope: CanteenScope, warningOnly?: boolean): Promise<InventoryLine[]>;
  listSuppliers?(scope: CanteenScope, keyword?: string): Promise<Supplier[]>;
  createSupplier?(request: SupplierRequest, scope: CanteenScope): Promise<Supplier>;
  listPurchaseOrders?(scope: CanteenScope, status?: string): Promise<PurchaseOrder[]>;
  createPurchaseOrder?(
    request: PurchaseOrderRequest,
    idempotencyKey: string,
    scope: CanteenScope,
  ): Promise<PurchaseOrder>;
  transitionPurchaseOrder?(
    orderId: string,
    status: string,
    scope: CanteenScope,
  ): Promise<PurchaseOrder>;
  receivePurchaseOrder?(
    orderId: string,
    idempotencyKey: string,
    request: ReceiveRequest,
    scope: CanteenScope,
  ): Promise<ReceiveResult>;
  stockOut?(
    request: StockOutRequest,
    idempotencyKey: string,
    scope: CanteenScope,
  ): Promise<import('./generated/client').StockOutResult>;
  listProcurementPlans?(scope: CanteenScope, status?: string): Promise<ProcurementPlanAggregate[]>;
  generateProcurementPlanRange?(
    periodStart: string,
    periodEnd: string,
    idempotencyKey: string,
    scope: CanteenScope,
  ): Promise<ProcurementPlanAggregate>;
  adjustProcurementPlan?(
    planId: string,
    request: AdjustProcurementPlanRequest,
    scope: CanteenScope,
  ): Promise<ProcurementPlanAggregate>;
  confirmProcurementPlan?(
    planId: string,
    scope: CanteenScope,
  ): Promise<ProcurementPlanAggregate>;
  createPurchaseOrderFromPlan?(
    planId: string,
    idempotencyKey: string,
    request: CreateProcurementOrderRequest,
    scope: CanteenScope,
  ): Promise<import('./generated/client').PurchaseOrder>;
  cancelProcurementPlan?(planId: string, scope: CanteenScope): Promise<ProcurementPlanAggregate>;
  listLedgerConfigurations?(scope: CanteenScope): Promise<LedgerConfiguration[]>;
  ensureConfiguredLedgerCycles?(scope: CanteenScope, asOf?: string): Promise<ConfiguredLedgerCycle[]>;
  completeConfiguredLedger?(
    cycleId: string,
    request: ConfiguredLedgerRecordRequest,
    scope: CanteenScope,
  ): Promise<import('./generated/client').LedgerRecordView>;
  listLedgerRecords?(
    scope: CanteenScope,
    filters?: { cycleId?: string; ledgerCode?: string; status?: string },
  ): Promise<import('./generated/client').LedgerRecordView[]>;
  saveLedgerRecord?(
    request: LedgerRecordRequest,
    scope: CanteenScope,
  ): Promise<import('./generated/client').LedgerRecordView>;
  getLedgerStats?(
    scope: CanteenScope,
    from?: string,
    to?: string,
  ): Promise<import('./generated/client').LedgerStats>;
  traceability?(traceCode: string, scope: CanteenScope): Promise<import('./generated/client').TraceabilityResult>;
  listComplianceRecords?(
    scope: CanteenScope,
    filters?: {
      category?: ComplianceCategory;
      status?: ComplianceRecordStatus;
      expiringWithinDays?: number;
    },
  ): Promise<ComplianceRecord[]>;
  createComplianceRecord?(
    request: ComplianceRecordRequest,
    scope: CanteenScope,
  ): Promise<ComplianceRecord>;
  submitComplianceRecord?(
    recordId: string,
    version: number,
    scope: CanteenScope,
  ): Promise<ComplianceRecord>;
  reviewComplianceRecord?(
    recordId: string,
    request: ComplianceReviewRequest,
    scope: CanteenScope,
  ): Promise<ComplianceRecord>;
  scanComplianceExpiry?(
    scope: CanteenScope,
    request?: ExpiryScanRequest,
  ): Promise<AlertRecord[]>;
  listCanteenShowcases?(
    scope: CanteenScope,
    status?: CanteenShowcaseStatus,
  ): Promise<CanteenShowcase[]>;
  createCanteenShowcase?(
    request: ShowcaseRequest,
    scope: CanteenScope,
  ): Promise<CanteenShowcase>;
  submitCanteenShowcase?(
    showcaseId: string,
    version: number,
    scope: CanteenScope,
  ): Promise<CanteenShowcase>;
  reviewCanteenShowcase?(
    showcaseId: string,
    request: ShowcaseReviewRequest,
    scope: CanteenScope,
  ): Promise<CanteenShowcase>;
  publishCanteenShowcase?(
    showcaseId: string,
    version: number,
    scope: CanteenScope,
  ): Promise<CanteenShowcase>;
  listMealSuspensions?(
    scope: CanteenScope,
    filters?: { from?: string; to?: string; status?: MealSuspensionStatus },
  ): Promise<MealSuspension[]>;
  createMealSuspension?(
    request: MealSuspensionRequest,
    scope: CanteenScope,
  ): Promise<MealSuspension>;
  reviewMealSuspension?(
    suspensionId: string,
    request: MealReviewRequest,
    scope: CanteenScope,
  ): Promise<MealSuspension>;
  cancelMealSuspension?(
    suspensionId: string,
    version: number,
    scope: CanteenScope,
  ): Promise<MealSuspension>;
  listSupplierComplaints?(
    scope: CanteenScope,
    filters?: { status?: SupplierComplaintStatus; supplierId?: string },
  ): Promise<SupplierComplaint[]>;
  createSupplierComplaint?(
    request: ComplaintRequest,
    scope: CanteenScope,
  ): Promise<SupplierComplaint>;
  reviewSupplierComplaint?(
    complaintId: string,
    request: ComplaintReviewRequest,
    scope: CanteenScope,
  ): Promise<SupplierComplaint>;
  processSupplierComplaint?(
    complaintId: string,
    version: number,
    scope: CanteenScope,
  ): Promise<SupplierComplaint>;
  replySupplierComplaint?(
    complaintId: string,
    request: ComplaintReplyRequest,
    scope: CanteenScope,
  ): Promise<SupplierComplaint>;
  closeSupplierComplaint?(
    complaintId: string,
    version: number,
    scope: CanteenScope,
  ): Promise<SupplierComplaint>;
}

export class SmartCanteenApi implements SmartCanteenApiPort {
  private accessToken: string | null = null;
  private session: AuthSession | null = null;

  constructor(private readonly client: AxiosInstance) {
    this.session = readSession();
    this.accessToken = this.session?.accessToken ?? null;
    const requestInterceptor = this.client.interceptors?.request;
    if (requestInterceptor?.use) {
      requestInterceptor.use((config) => {
        if (this.accessToken) {
          config.headers = config.headers ?? {};
          config.headers.Authorization = `Bearer ${this.accessToken}`;
        }
        return config;
      });
    }
  }

  hasSession(): boolean {
    return Boolean(this.accessToken);
  }

  getSession(): AuthSession | null {
    return this.session;
  }

  async login(username: string, password: string): Promise<AuthSession> {
    const response = await this.client.post<AuthTokensResponse>('/api/v1/auth/login', {
      username,
      password,
      loginType: 'account',
    });
    const tokens = unwrap(response);
    this.setSession(tokens);
    return this.sessionOf(tokens);
  }

  async refreshSession(refreshToken: string): Promise<AuthSession> {
    const response = await this.client.post<AuthTokensResponse>('/api/v1/auth/refresh-token', {
      refreshToken,
    });
    const tokens = unwrap(response);
    this.setSession(tokens);
    return this.sessionOf(tokens);
  }

  async logout(refreshToken?: string): Promise<void> {
    try {
      await this.client.post('/api/v1/auth/logout', refreshToken ? { refreshToken } : undefined);
    } finally {
      this.clearSession();
    }
  }

  async currentUser(): Promise<CurrentUser> {
    const response = await this.client.get<CurrentUserResponse>('/api/v1/auth/me');
    return unwrap(response);
  }

  async startAgentTraceability(
    traceCode: string,
    scope: CanteenScope,
    idempotencyKey: string,
    requestId?: string,
  ): Promise<AgentRun> {
    const response = await this.client.post<AgentRunResponse>(
      '/api/v1/agent/runs',
      { intent: 'traceability.query', input: { traceCode } },
      {
        headers: {
          'Idempotency-Key': idempotencyKey,
          ...(requestId ? { 'X-Request-Id': requestId } : {}),
        },
        params: scope,
      },
    );
    return unwrap(response);
  }

  async sendAssistantMessage(
    conversationId: string,
    message: string,
    scope: CanteenScope,
    idempotencyKey: string,
    requestId?: string,
  ): Promise<AssistantTurn> {
    const response = await this.client.post<AssistantTurnResponse>(
      `/api/v1/assistant/conversations/${encodeURIComponent(conversationId)}/messages`,
      { message },
      {
        headers: {
          'Idempotency-Key': idempotencyKey,
          ...(requestId ? { 'X-Request-Id': requestId } : {}),
        },
        params: scope,
      },
    );
    return unwrap(response);
  }

  async getAssistantHistory(
    conversationId: string,
    scope: CanteenScope,
    limit = 50,
    requestId?: string,
  ): Promise<AssistantConversationHistory> {
    const response = await this.client.get<AssistantConversationHistoryResponse>(
      `/api/v1/assistant/conversations/${encodeURIComponent(conversationId)}/messages`,
      {
        headers: requestId ? { 'X-Request-Id': requestId } : undefined,
        params: { ...scope, limit },
      },
    );
    return unwrap(response);
  }

  async startAgentRun(
    intent: string,
    input: Record<string, unknown>,
    scope: CanteenScope,
    idempotencyKey: string,
    requestId?: string,
  ): Promise<AgentRun> {
    const response = await this.client.post<AgentRunResponse>(
      '/api/v1/agent/runs',
      { intent, input },
      {
        headers: {
          'Idempotency-Key': idempotencyKey,
          ...(requestId ? { 'X-Request-Id': requestId } : {}),
        },
        params: scope,
      },
    );
    return unwrap(response);
  }

  async getAgentRun(
    runId: string,
    scope: CanteenScope,
    requestId?: string,
  ): Promise<AgentRun> {
    const response = await this.client.get<AgentRunResponse>(
      `/api/v1/agent/runs/${encodeURIComponent(runId)}`,
      {
      headers: requestId ? { 'X-Request-Id': requestId } : undefined,
        params: scope,
      },
    );
    return unwrap(response);
  }

  async decideAgentRun(
    runId: string,
    decisionType: 'RUN_CONFIRM' | 'RUN_REJECT' | 'RUN_CANCEL',
    version: number,
    scope: CanteenScope,
    comment?: string,
    requestId?: string,
    idempotencyKey?: string,
  ): Promise<AgentRun> {
    const response = await this.client.post<AgentRunResponse>(
      `/api/v1/agent/runs/${encodeURIComponent(runId)}/decisions`,
      { version, decisionType, ...(comment ? { comment } : {}) },
      {
        headers: {
          'Idempotency-Key': idempotencyKey
            ?? `agent-decision-${runId}-${version}-${decisionType}`,
          ...(requestId ? { 'X-Request-Id': requestId } : {}),
        },
        params: scope,
      },
    );
    return unwrap(response);
  }

  async cancelAgentRun(
    runId: string,
    version: number,
    scope: CanteenScope,
    requestId?: string,
    idempotencyKey?: string,
  ): Promise<AgentRun> {
    const response = await this.client.post<AgentRunResponse>(
      `/api/v1/agent/runs/${encodeURIComponent(runId)}/cancel`,
      { version },
      {
        headers: {
          'Idempotency-Key': idempotencyKey ?? `agent-cancel-${runId}-${version}`,
          ...(requestId ? { 'X-Request-Id': requestId } : {}),
        },
        params: scope,
      },
    );
    return unwrap(response);
  }

  async resumeAgentRun(
    runId: string,
    version: number,
    scope: CanteenScope,
    requestId?: string,
    idempotencyKey?: string,
  ): Promise<AgentRun> {
    const response = await this.client.post<AgentRunResponse>(
      `/api/v1/agent/runs/${encodeURIComponent(runId)}/resume`,
      { version },
      {
        headers: {
          'Idempotency-Key': idempotencyKey ?? `agent-resume-${runId}-${version}`,
          ...(requestId ? { 'X-Request-Id': requestId } : {}),
        },
        params: scope,
      },
    );
    return unwrap(response);
  }

  async getAgentRunEvents(
    runId: string,
    scope: CanteenScope,
    requestId?: string,
  ): Promise<AgentRunEvent[]> {
    const response = await this.client.get<AgentRunEventListResponse>(
      `/api/v1/agent/runs/${encodeURIComponent(runId)}/events`,
      {
        headers: requestId ? { 'X-Request-Id': requestId } : undefined,
        params: scope,
      },
    );
    return unwrap(response);
  }

  async getAgentMetrics(
    scope: CanteenScope,
    from?: string,
    to?: string,
    requestId?: string,
  ): Promise<AgentMetrics> {
    const response = await this.client.get<AgentMetricsResponse>('/api/v1/agent/metrics', {
      headers: requestId ? { 'X-Request-Id': requestId } : undefined,
      params: { ...scope, ...(from ? { from } : {}), ...(to ? { to } : {}) },
    });
    return unwrap(response);
  }

  async getDashboardSummary(scope: CanteenScope, date?: string): Promise<DashboardSummary> {
    const response = await this.client.get<DashboardSummaryResponse>('/api/v1/dashboard/summary', {
      params: { ...scope, ...(date ? { date } : {}) },
    });
    return unwrap(response);
  }

  async getDashboardRisk(scope: CanteenScope, date?: string): Promise<RiskAssessment> {
    const response = await this.client.get<RiskAssessmentResponse>('/api/v1/dashboard/risk', {
      params: { ...scope, ...(date ? { date } : {}) },
    });
    return unwrap(response);
  }

  async listIngredients(
    scope: CanteenScope,
    keyword?: string,
    category?: string,
  ): Promise<Ingredient[]> {
    const response = await this.client.get<IngredientPageResponse>('/api/v1/ingredients', {
      params: {
        ...scope,
        page: 1,
        size: 100,
        ...(keyword ? { keyword } : {}),
        ...(category ? { category } : {}),
      },
    });
    return unwrap(response).records;
  }

  async createIngredient(request: IngredientRequest, scope: CanteenScope): Promise<Ingredient> {
    const response = await this.client.post<IngredientResponse>('/api/v1/ingredients', request, {
      params: scope,
    });
    return unwrap(response);
  }

  async updateIngredient(
    ingredientId: string,
    request: IngredientRequest,
    scope: CanteenScope,
  ): Promise<Ingredient> {
    const response = await this.client.put<IngredientResponse>(
      `/api/v1/ingredients/${encodeURIComponent(ingredientId)}`,
      request,
      { params: scope },
    );
    return unwrap(response);
  }

  async listIngredientUnits(ingredientId: string, scope: CanteenScope): Promise<IngredientUnit[]> {
    const response = await this.client.get<IngredientUnitListResponse>(
      `/api/v1/ingredients/${encodeURIComponent(ingredientId)}/units`,
      { params: scope },
    );
    return unwrap(response);
  }

  async replaceIngredientUnits(
    ingredientId: string,
    units: IngredientUnit[],
    scope: CanteenScope,
  ): Promise<IngredientUnit[]> {
    const response = await this.client.put<IngredientUnitListResponse>(
      `/api/v1/ingredients/${encodeURIComponent(ingredientId)}/units`,
      { units },
      { params: scope },
    );
    return unwrap(response);
  }

  async listDishes(scope: CanteenScope, keyword?: string, category?: string): Promise<Dish[]> {
    const response = await this.client.get<DishPageResponse>('/api/v1/dishes', {
      params: {
        ...scope,
        page: 1,
        size: 100,
        ...(keyword ? { keyword } : {}),
        ...(category ? { category } : {}),
      },
    });
    return unwrap(response).records;
  }

  async createDish(request: DishRequest, scope: CanteenScope): Promise<Dish> {
    const response = await this.client.post<DishResponse>('/api/v1/dishes', request, {
      params: scope,
    });
    return unwrap(response);
  }

  async updateDish(dishId: string, request: DishRequest, scope: CanteenScope): Promise<Dish> {
    const response = await this.client.put<DishResponse>(
      `/api/v1/dishes/${encodeURIComponent(dishId)}`,
      request,
      { params: scope },
    );
    return unwrap(response);
  }

  async listDinerMenus(
    scope: CanteenScope,
    date?: string,
    mealTime?: string,
  ): Promise<DinerMenu[]> {
    const response = await this.client.get<ApiEnvelope<{
      records: DinerMenu[];
    }>>('/api/v1/diner/menus', {
      params: {
        ...scope,
        page: 1,
        size: 100,
        ...(date ? { date } : {}),
        ...(mealTime ? { mealTime } : {}),
      },
    });
    return unwrap(response).records;
  }

  async listMealOrders(scope: CanteenScope, status?: string): Promise<MealOrder[]> {
    const response = await this.client.get<ApiEnvelope<{
      records: MealOrder[];
    }>>('/api/v1/meal-orders', {
      params: { ...scope, page: 1, size: 100, ...(status ? { status } : {}) },
    });
    return unwrap(response).records;
  }

  async createMealOrder(
    request: MealOrderRequest,
    idempotencyKey: string,
    scope: CanteenScope,
  ): Promise<MealOrder> {
    const response = await this.client.post<ApiEnvelope<MealOrder>>(
      '/api/v1/meal-orders',
      request,
      { params: scope, headers: { 'Idempotency-Key': idempotencyKey } },
    );
    return unwrap(response);
  }

  async cancelMealOrder(orderId: string, scope: CanteenScope): Promise<MealOrder> {
    const response = await this.client.post<ApiEnvelope<MealOrder>>(
      `/api/v1/meal-orders/${encodeURIComponent(orderId)}/cancel`,
      undefined,
      { params: scope },
    );
    return unwrap(response);
  }

  async listMealReviews(scope: CanteenScope): Promise<MealReview[]> {
    const response = await this.client.get<MealReviewPageResponse>('/api/v1/meal-reviews', {
      params: { ...scope, page: 1, size: 100 },
    });
    return unwrap(response).records;
  }

  async createMealReview(
    request: EmployeeMealReviewRequest,
    idempotencyKey: string,
    scope: CanteenScope,
  ): Promise<MealReview> {
    const response = await this.client.post<MealReviewResponse>(
      '/api/v1/meal-reviews',
      request,
      { params: scope, headers: { 'Idempotency-Key': idempotencyKey } },
    );
    return unwrap(response);
  }

  async listDinerComplaints(
    scope: CanteenScope,
    status?: 'SUBMITTED',
  ): Promise<DinerComplaint[]> {
    const response = await this.client.get<DinerComplaintPageResponse>('/api/v1/diner-complaints', {
      params: { ...scope, page: 1, size: 100, ...(status ? { status } : {}) },
    });
    return unwrap(response).records;
  }

  async createDinerComplaint(
    request: DinerComplaintRequest,
    idempotencyKey: string,
    scope: CanteenScope,
  ): Promise<DinerComplaint> {
    const response = await this.client.post<DinerComplaintResponse>(
      '/api/v1/diner-complaints',
      request,
      { params: scope, headers: { 'Idempotency-Key': idempotencyKey } },
    );
    return unwrap(response);
  }

  async listDailyMenus(
    scope: CanteenScope,
    from?: string,
    to?: string,
    status?: string,
  ): Promise<DailyMenu[]> {
    const response = await this.client.get<DailyMenuPageResponse>('/api/v1/daily-menus', {
      params: {
        ...scope,
        page: 1,
        size: 100,
        ...(from ? { startDate: from } : {}),
        ...(to ? { endDate: to } : {}),
        ...(status ? { status } : {}),
      },
    });
    return unwrap(response).records;
  }

  async saveDailyMenu(
    request: DailyMenuRequest,
    scope: CanteenScope,
  ): Promise<DailyMenu> {
    const response = await this.client.post<DailyMenuResponse>('/api/v1/daily-menus', request, {
      params: scope,
    });
    return unwrap(response);
  }

  async submitDailyMenu(
    menuId: string,
    version: number,
    scope: CanteenScope,
  ): Promise<DailyMenu> {
    const response = await this.client.post<DailyMenuResponse>(
      `/api/v1/daily-menus/${encodeURIComponent(menuId)}/submit`,
      undefined,
      { params: { ...scope, version } },
    );
    return unwrap(response);
  }

  async decideDailyMenu(
    menuId: string,
    request: DailyMenuDecisionRequest,
    scope: CanteenScope,
  ): Promise<DailyMenu> {
    const response = await this.client.post<DailyMenuResponse>(
      `/api/v1/daily-menus/${encodeURIComponent(menuId)}/decision`,
      request,
      { params: scope },
    );
    return unwrap(response);
  }

  async publishDailyMenu(menuId: string, scope: CanteenScope): Promise<DailyMenu> {
    const response = await this.client.post<DailyMenuResponse>(
      `/api/v1/daily-menus/${encodeURIComponent(menuId)}/publish`,
      undefined,
      { params: scope },
    );
    return unwrap(response);
  }

  async listInventory(scope: CanteenScope, warningOnly = false): Promise<InventoryLine[]> {
    const response = await this.client.get<InventoryPageResponse>('/api/v1/inventory', {
      params: { ...scope, warningOnly, page: 1, size: 100 },
    });
    return unwrap(response).records;
  }

  async listSuppliers(scope: CanteenScope, keyword?: string): Promise<Supplier[]> {
    const response = await this.client.get<SupplierPageResponse>('/api/v1/suppliers', {
      params: { ...scope, page: 1, size: 100, ...(keyword ? { keyword } : {}) },
    });
    return unwrap(response).records;
  }

  async createSupplier(request: SupplierRequest, scope: CanteenScope): Promise<Supplier> {
    const response = await this.client.post<import('./generated/client').SupplierResponse>(
      '/api/v1/suppliers',
      request,
      { params: scope },
    );
    return unwrap(response);
  }

  async listPurchaseOrders(scope: CanteenScope, status?: string): Promise<PurchaseOrder[]> {
    const response = await this.client.get<PurchaseOrderPageResponse>('/api/v1/purchase-orders', {
      params: { ...scope, page: 1, size: 100, ...(status ? { status } : {}) },
    });
    return unwrap(response).records;
  }

  async createPurchaseOrder(
    request: PurchaseOrderRequest,
    idempotencyKey: string,
    scope: CanteenScope,
  ): Promise<PurchaseOrder> {
    const response = await this.client.post<PurchaseOrderResponse>(
      '/api/v1/purchase-orders',
      request,
      { headers: { 'Idempotency-Key': idempotencyKey }, params: scope },
    );
    return unwrap(response);
  }

  async transitionPurchaseOrder(
    orderId: string,
    status: string,
    scope: CanteenScope,
  ): Promise<PurchaseOrder> {
    const response = await this.client.post<PurchaseOrderResponse>(
      `/api/v1/purchase-orders/${encodeURIComponent(orderId)}/status`,
      { status },
      { params: scope },
    );
    return unwrap(response);
  }

  async receivePurchaseOrder(
    orderId: string,
    idempotencyKey: string,
    request: ReceiveRequest,
    scope: CanteenScope,
  ): Promise<ReceiveResult> {
    const response = await this.client.post<ReceiveResponse>(
      `/api/v1/purchase-orders/${encodeURIComponent(orderId)}/receive`,
      request,
      { headers: { 'Idempotency-Key': idempotencyKey }, params: scope },
    );
    return unwrap(response);
  }

  async stockOut(
    request: StockOutRequest,
    idempotencyKey: string,
    scope: CanteenScope,
  ): Promise<import('./generated/client').StockOutResult> {
    const response = await this.client.post<StockOutResponse>(
      '/api/v1/inventory/stock-outs',
      request,
      { headers: { 'Idempotency-Key': idempotencyKey }, params: scope },
    );
    return unwrap(response);
  }

  async listProcurementPlans(
    scope: CanteenScope,
    status?: string,
  ): Promise<ProcurementPlanAggregate[]> {
    const response = await this.client.get<ProcurementPlanAggregatePageResponse>(
      '/api/v1/procurement-plans',
      { params: { ...scope, page: 1, size: 50, ...(status ? { status } : {}) } },
    );
    return unwrap(response).records;
  }

  async generateProcurementPlanRange(
    periodStart: string,
    periodEnd: string,
    idempotencyKey: string,
    scope: CanteenScope,
  ): Promise<ProcurementPlanAggregate> {
    const response = await this.client.post<ProcurementPlanAggregateResponse>(
      '/api/v1/procurement-plans/generate-range',
      { periodStart, periodEnd },
      { headers: { 'Idempotency-Key': idempotencyKey }, params: scope },
    );
    return unwrap(response);
  }

  async adjustProcurementPlan(
    planId: string,
    request: AdjustProcurementPlanRequest,
    scope: CanteenScope,
  ): Promise<ProcurementPlanAggregate> {
    const response = await this.client.put<ProcurementPlanAggregateResponse>(
      `/api/v1/procurement-plans/${encodeURIComponent(planId)}/items`,
      request,
      { params: scope },
    );
    return unwrap(response);
  }

  async confirmProcurementPlan(
    planId: string,
    scope: CanteenScope,
  ): Promise<ProcurementPlanAggregate> {
    const response = await this.client.post<ProcurementPlanAggregateResponse>(
      `/api/v1/procurement-plans/${encodeURIComponent(planId)}/confirm`,
      undefined,
      { params: scope },
    );
    return unwrap(response);
  }

  async cancelProcurementPlan(
    planId: string,
    scope: CanteenScope,
  ): Promise<ProcurementPlanAggregate> {
    const response = await this.client.post<ProcurementPlanAggregateResponse>(
      `/api/v1/procurement-plans/${encodeURIComponent(planId)}/cancel`,
      undefined,
      { params: scope },
    );
    return unwrap(response);
  }

  async createPurchaseOrderFromPlan(
    planId: string,
    idempotencyKey: string,
    request: CreateProcurementOrderRequest,
    scope: CanteenScope,
  ): Promise<import('./generated/client').PurchaseOrder> {
    const response = await this.client.post<import('./generated/client').PurchaseOrderResponse>(
      `/api/v1/procurement-plans/${encodeURIComponent(planId)}/purchase-orders`,
      request,
      { headers: { 'Idempotency-Key': idempotencyKey }, params: scope },
    );
    return unwrap(response);
  }

  async listLedgerConfigurations(scope: CanteenScope): Promise<LedgerConfiguration[]> {
    const response = await this.client.get<LedgerConfigurationListResponse>(
      '/api/v1/ledger-configurations',
      { params: scope },
    );
    return unwrap(response);
  }

  async ensureConfiguredLedgerCycles(
    scope: CanteenScope,
    asOf?: string,
  ): Promise<ConfiguredLedgerCycle[]> {
    const response = await this.client.post<ConfiguredLedgerCycleListResponse>(
      '/api/v1/ledger-cycles/configured/current',
      undefined,
      { params: { ...scope, ...(asOf ? { asOf } : {}) } },
    );
    return unwrap(response);
  }

  async completeConfiguredLedger(
    cycleId: string,
    request: ConfiguredLedgerRecordRequest,
    scope: CanteenScope,
  ): Promise<import('./generated/client').LedgerRecordView> {
    const response = await this.client.post<LedgerRecordResponse>(
      `/api/v1/ledger-cycles/configured/${encodeURIComponent(cycleId)}/records`,
      request,
      { params: scope },
    );
    return unwrap(response);
  }

  async listLedgerRecords(
    scope: CanteenScope,
    filters: { cycleId?: string; ledgerCode?: string; status?: string } = {},
  ): Promise<import('./generated/client').LedgerRecordView[]> {
    const response = await this.client.get<LedgerRecordPageResponse>('/api/v1/ledger/records', {
      params: { ...scope, page: 1, size: 200, ...filters },
    });
    return unwrap(response).records;
  }

  async saveLedgerRecord(
    request: LedgerRecordRequest,
    scope: CanteenScope,
  ): Promise<import('./generated/client').LedgerRecordView> {
    const response = await this.client.post<LedgerRecordResponse>(
      '/api/v1/ledger/records',
      request,
      { params: scope },
    );
    return unwrap(response);
  }

  async getLedgerStats(
    scope: CanteenScope,
    from?: string,
    to?: string,
  ): Promise<import('./generated/client').LedgerStats> {
    const response = await this.client.get<LedgerStatsResponse>('/api/v1/ledger/stats', {
      params: {
        ...scope,
        ...(from ? { startDate: from } : {}),
        ...(to ? { endDate: to } : {}),
      },
    });
    return unwrap(response);
  }

  async traceability(
    traceCode: string,
    scope: CanteenScope,
  ): Promise<import('./generated/client').TraceabilityResult> {
    const response = await this.client.get<TraceabilityResponse>(
      `/api/v1/traceability/${encodeURIComponent(traceCode)}`,
      { params: scope },
    );
    return unwrap(response);
  }

  async listComplianceRecords(
    scope: CanteenScope,
    filters: {
      category?: ComplianceCategory;
      status?: ComplianceRecordStatus;
      expiringWithinDays?: number;
    } = {},
  ): Promise<ComplianceRecord[]> {
    const response = await this.client.get<ComplianceRecordPageResponse>(
      '/api/v1/compliance-records',
      { params: { ...scope, page: 1, size: 100, ...filters } },
    );
    return unwrap(response).records;
  }

  async createComplianceRecord(
    request: ComplianceRecordRequest,
    scope: CanteenScope,
  ): Promise<ComplianceRecord> {
    const response = await this.client.post<ComplianceRecordResponse>(
      '/api/v1/compliance-records',
      request,
      { params: scope },
    );
    return unwrap(response);
  }

  async submitComplianceRecord(
    recordId: string,
    version: number,
    scope: CanteenScope,
  ): Promise<ComplianceRecord> {
    const response = await this.client.post<ComplianceRecordResponse>(
      `/api/v1/compliance-records/${encodeURIComponent(recordId)}/submit`,
      { version },
      { params: scope },
    );
    return unwrap(response);
  }

  async reviewComplianceRecord(
    recordId: string,
    request: ComplianceReviewRequest,
    scope: CanteenScope,
  ): Promise<ComplianceRecord> {
    const response = await this.client.post<ComplianceRecordResponse>(
      `/api/v1/compliance-records/${encodeURIComponent(recordId)}/review`,
      request,
      { params: scope },
    );
    return unwrap(response);
  }

  async scanComplianceExpiry(
    scope: CanteenScope,
    request?: ExpiryScanRequest,
  ): Promise<AlertRecord[]> {
    const response = await this.client.post<AlertRecordListResponse>(
      '/api/v1/compliance-records/expiry-scan',
      request,
      { params: scope },
    );
    return unwrap(response);
  }

  async listCanteenShowcases(
    scope: CanteenScope,
    status?: CanteenShowcaseStatus,
  ): Promise<CanteenShowcase[]> {
    const response = await this.client.get<CanteenShowcasePageResponse>(
      '/api/v1/canteen-showcases',
      { params: { ...scope, page: 1, size: 100, ...(status ? { status } : {}) } },
    );
    return unwrap(response).records;
  }

  async createCanteenShowcase(
    request: ShowcaseRequest,
    scope: CanteenScope,
  ): Promise<CanteenShowcase> {
    const response = await this.client.post<CanteenShowcaseResponse>(
      '/api/v1/canteen-showcases',
      request,
      { params: scope },
    );
    return unwrap(response);
  }

  async submitCanteenShowcase(
    showcaseId: string,
    version: number,
    scope: CanteenScope,
  ): Promise<CanteenShowcase> {
    const response = await this.client.post<CanteenShowcaseResponse>(
      `/api/v1/canteen-showcases/${encodeURIComponent(showcaseId)}/submit`,
      { version },
      { params: scope },
    );
    return unwrap(response);
  }

  async reviewCanteenShowcase(
    showcaseId: string,
    request: ShowcaseReviewRequest,
    scope: CanteenScope,
  ): Promise<CanteenShowcase> {
    const response = await this.client.post<CanteenShowcaseResponse>(
      `/api/v1/canteen-showcases/${encodeURIComponent(showcaseId)}/review`,
      request,
      { params: scope },
    );
    return unwrap(response);
  }

  async publishCanteenShowcase(
    showcaseId: string,
    version: number,
    scope: CanteenScope,
  ): Promise<CanteenShowcase> {
    const response = await this.client.post<CanteenShowcaseResponse>(
      `/api/v1/canteen-showcases/${encodeURIComponent(showcaseId)}/publish`,
      { version },
      { params: scope },
    );
    return unwrap(response);
  }

  async listMealSuspensions(
    scope: CanteenScope,
    filters: { from?: string; to?: string; status?: MealSuspensionStatus } = {},
  ): Promise<MealSuspension[]> {
    const response = await this.client.get<MealSuspensionPageResponse>(
      '/api/v1/meal-suspensions',
      { params: { ...scope, page: 1, size: 100, ...filters } },
    );
    return unwrap(response).records;
  }

  async createMealSuspension(
    request: MealSuspensionRequest,
    scope: CanteenScope,
  ): Promise<MealSuspension> {
    const response = await this.client.post<MealSuspensionResponse>(
      '/api/v1/meal-suspensions',
      request,
      { params: scope },
    );
    return unwrap(response);
  }

  async reviewMealSuspension(
    suspensionId: string,
    request: MealReviewRequest,
    scope: CanteenScope,
  ): Promise<MealSuspension> {
    const response = await this.client.post<MealSuspensionResponse>(
      `/api/v1/meal-suspensions/${encodeURIComponent(suspensionId)}/review`,
      request,
      { params: scope },
    );
    return unwrap(response);
  }

  async cancelMealSuspension(
    suspensionId: string,
    version: number,
    scope: CanteenScope,
  ): Promise<MealSuspension> {
    const response = await this.client.post<MealSuspensionResponse>(
      `/api/v1/meal-suspensions/${encodeURIComponent(suspensionId)}/cancel`,
      { version },
      { params: scope },
    );
    return unwrap(response);
  }

  async listSupplierComplaints(
    scope: CanteenScope,
    filters: { status?: SupplierComplaintStatus; supplierId?: string } = {},
  ): Promise<SupplierComplaint[]> {
    const response = await this.client.get<SupplierComplaintPageResponse>(
      '/api/v1/supplier-complaints',
      { params: { ...scope, page: 1, size: 100, ...filters } },
    );
    return unwrap(response).records;
  }

  async createSupplierComplaint(
    request: ComplaintRequest,
    scope: CanteenScope,
  ): Promise<SupplierComplaint> {
    const response = await this.client.post<SupplierComplaintResponse>(
      '/api/v1/supplier-complaints',
      request,
      { params: scope },
    );
    return unwrap(response);
  }

  async reviewSupplierComplaint(
    complaintId: string,
    request: ComplaintReviewRequest,
    scope: CanteenScope,
  ): Promise<SupplierComplaint> {
    const response = await this.client.post<SupplierComplaintResponse>(
      `/api/v1/supplier-complaints/${encodeURIComponent(complaintId)}/review`,
      request,
      { params: scope },
    );
    return unwrap(response);
  }

  async processSupplierComplaint(
    complaintId: string,
    version: number,
    scope: CanteenScope,
  ): Promise<SupplierComplaint> {
    const response = await this.client.post<SupplierComplaintResponse>(
      `/api/v1/supplier-complaints/${encodeURIComponent(complaintId)}/process`,
      { version },
      { params: scope },
    );
    return unwrap(response);
  }

  async replySupplierComplaint(
    complaintId: string,
    request: ComplaintReplyRequest,
    scope: CanteenScope,
  ): Promise<SupplierComplaint> {
    const response = await this.client.post<SupplierComplaintResponse>(
      `/api/v1/supplier-complaints/${encodeURIComponent(complaintId)}/reply`,
      request,
      { params: scope },
    );
    return unwrap(response);
  }

  async closeSupplierComplaint(
    complaintId: string,
    version: number,
    scope: CanteenScope,
  ): Promise<SupplierComplaint> {
    const response = await this.client.post<SupplierComplaintResponse>(
      `/api/v1/supplier-complaints/${encodeURIComponent(complaintId)}/close`,
      { version },
      { params: scope },
    );
    return unwrap(response);
  }

  setSession(tokens: AuthTokens): void {
    this.accessToken = tokens.token;
    this.session = this.sessionOf(tokens);
    writeSession(this.session);
  }

  clearSession(): void {
    this.accessToken = null;
    this.session = null;
    if (typeof window !== 'undefined') {
      window.localStorage.removeItem('smart-canteen.session');
    }
  }

  private sessionOf(tokens: AuthTokens): AuthSession {
    return {
      accessToken: tokens.token,
      refreshToken: tokens.refreshToken,
      expiresIn: tokens.expiresIn,
      userInfo: tokens.userInfo,
    };
  }

  async getDailyMenu(menuId: string, scope: CanteenScope): Promise<DailyMenu> {
    const response = await this.client.get<DailyMenuResponse>(
      `/api/v1/daily-menus/${encodeURIComponent(menuId)}`,
      { params: scope },
    );
    return unwrap(response);
  }

  async reportAlert(request: AlertReportRequest): Promise<AlertRecord> {
    const response = await this.client.post<AlertResponse>('/api/v1/alerts', request);
    return unwrap(response);
  }

  async disposeAlert(
    warnId: string,
    request: AlertDisposalRequest,
  ): Promise<AlertRecord> {
    const path = `/api/v1/alerts/${encodeURIComponent(warnId)}/disposal`;
    const response = await this.client.post<AlertResponse>(path, request);
    return unwrap(response);
  }

  async queryAlerts(filters: {
    schoolId?: string;
    canteenId?: string;
    source?: string;
    status?: string;
    deviceName?: string;
    startDate?: string;
    endDate?: string;
    pageNum?: number;
    pageSize?: number;
  } = {}): Promise<AlertPage> {
    const response = await this.client.get<AlertPageResponse>('/api/v1/alerts', {
      params: filters,
    });
    return unwrap(response);
  }
}

function readSession(): AuthSession | null {
  if (typeof window === 'undefined') {
    return null;
  }
  try {
    const raw = window.localStorage.getItem('smart-canteen.session');
    if (!raw) return null;
    const session = JSON.parse(raw) as Partial<AuthSession>;
    return typeof session.accessToken === 'string'
        && session.accessToken.length > 0
        && typeof session.refreshToken === 'string'
        && session.userInfo
      ? session as AuthSession
      : null;
  } catch {
    return null;
  }
}

function writeSession(session: AuthSession): void {
  if (typeof window === 'undefined') {
    return;
  }
  window.localStorage.setItem('smart-canteen.session', JSON.stringify(session));
}
