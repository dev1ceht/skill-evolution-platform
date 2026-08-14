import type { AxiosInstance, AxiosResponse } from 'axios';
import type {
  AuthTokens,
  AuthTokensResponse,
  CurrentUser,
  CurrentUserResponse,
  DashboardSummary,
  DashboardSummaryResponse,
  LedgerAlert,
  LedgerAlertResponse,
  RiskAssessment,
  RiskAssessmentResponse,
  InventoryLine,
  InventoryPageResponse,
  Supplier,
  SupplierPageResponse,
  Menu,
  MenuResponse,
  Recipe,
  RecipeResponse,
  ProcurementPlan,
  ProcurementPlanResponse,
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
  Receipt,
  ReceiptResponse,
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
  SupplierComplaint,
  SupplierComplaintStatus,
  SupplierComplaintPageResponse,
  SupplierComplaintResponse,
  ComplaintRequest,
  ComplaintReviewRequest,
  ComplaintReplyRequest,
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
  getCurrentLedgerAlert(scope?: CanteenScope): Promise<LedgerAlert>;
  submitMenu(menuId: string, scope?: CanteenScope): Promise<Menu>;
  importMenuRecipe(
    menuId: string,
    requirements: Array<{ materialId: string; quantity: number; unit: string }>,
    scope?: CanteenScope,
  ): Promise<Recipe>;
  decideMenuApproval(
    menuId: string,
    decision: 'APPROVE' | 'REJECT',
    comment: string,
    scope?: CanteenScope,
  ): Promise<Menu>;
  generateProcurementPlan(menuId: string, scope?: CanteenScope): Promise<ProcurementPlan>;
  receiveInventory(
    idempotencyKey: string,
    materialId: string,
    quantity: number,
    unit: string,
    scope?: CanteenScope,
  ): Promise<Receipt>;
  completeLedgerRecord(ledgerCode: string, scope?: CanteenScope): Promise<LedgerAlert>;
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
  listPurchaseOrders?(scope: CanteenScope, status?: string): Promise<PurchaseOrder[]>;
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

  async listPurchaseOrders(scope: CanteenScope, status?: string): Promise<PurchaseOrder[]> {
    const response = await this.client.get<PurchaseOrderPageResponse>('/api/v1/purchase-orders', {
      params: { ...scope, page: 1, size: 100, ...(status ? { status } : {}) },
    });
    return unwrap(response).records;
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

  async getCurrentLedgerAlert(scope?: CanteenScope): Promise<LedgerAlert> {
    const response = await this.client.get<LedgerAlertResponse>(
      '/api/v1/ledger-alerts/current',
      scope ? { params: scope } : undefined,
    );
    return unwrap(response);
  }

  async submitMenu(menuId: string, scope?: CanteenScope): Promise<Menu> {
    const path = `/api/v1/menus/${encodeURIComponent(menuId)}/submit`;
    const response = scope
      ? await this.client.post<MenuResponse>(path, undefined, { params: scope })
      : await this.client.post<MenuResponse>(path);
    return unwrap(response);
  }

  async importMenuRecipe(
    menuId: string,
    requirements: Array<{ materialId: string; quantity: number; unit: string }>,
    scope?: CanteenScope,
  ): Promise<Recipe> {
    const path = `/api/v1/menus/${encodeURIComponent(menuId)}/recipe`;
    const response = scope
      ? await this.client.post<RecipeResponse>(
          path,
          { requirements },
          { params: scope },
        )
      : await this.client.post<RecipeResponse>(path, { requirements });
    return unwrap(response);
  }

  async decideMenuApproval(
    menuId: string,
    decision: 'APPROVE' | 'REJECT',
    comment: string,
    scope?: CanteenScope,
  ): Promise<Menu> {
    const path = `/api/v1/menu-approvals/${encodeURIComponent(menuId)}/decision`;
    const body = { decision, comment };
    const response = scope
      ? await this.client.post<MenuResponse>(path, body, { params: scope })
      : await this.client.post<MenuResponse>(path, body);
    return unwrap(response);
  }

  async generateProcurementPlan(
    menuId: string,
    scope?: CanteenScope,
  ): Promise<ProcurementPlan> {
    const response = scope
      ? await this.client.post<ProcurementPlanResponse>(
          '/api/v1/procurement-plans/generate',
          { menuId },
          { params: scope },
        )
      : await this.client.post<ProcurementPlanResponse>(
          '/api/v1/procurement-plans/generate',
          { menuId },
        );
    return unwrap(response);
  }

  async receiveInventory(
    idempotencyKey: string,
    materialId: string,
    quantity: number,
    unit: string,
    scope?: CanteenScope,
  ): Promise<Receipt> {
    const response = await this.client.post<ReceiptResponse>(
      '/api/v1/inventory/receipts',
      { materialId, quantity, unit },
      {
        headers: { 'Idempotency-Key': idempotencyKey },
        ...(scope ? { params: scope } : {}),
      },
    );
    return unwrap(response);
  }

  async completeLedgerRecord(ledgerCode: string, scope?: CanteenScope): Promise<LedgerAlert> {
    const response = await this.client.post<LedgerAlertResponse>(
      '/api/v1/ledger-records',
      { ledgerCode },
      scope ? { params: scope } : undefined,
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
