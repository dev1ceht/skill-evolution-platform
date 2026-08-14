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
  Menu,
  MenuResponse,
  Recipe,
  RecipeResponse,
  ProcurementPlan,
  ProcurementPlanResponse,
  Receipt,
  ReceiptResponse,
  AlertRecord,
  AlertResponse,
  AlertPage,
  AlertPageResponse,
  AlertReportRequest,
  AlertDisposalRequest,
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
