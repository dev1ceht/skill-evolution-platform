import type { AxiosInstance, AxiosResponse } from 'axios';
import type {
  LedgerAlert,
  LedgerAlertResponse,
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
  getCurrentLedgerAlert(): Promise<LedgerAlert>;
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
  completeLedgerRecord(ledgerCode: string): Promise<LedgerAlert>;
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
}

export class SmartCanteenApi implements SmartCanteenApiPort {
  constructor(private readonly client: AxiosInstance) {}

  async getCurrentLedgerAlert(): Promise<LedgerAlert> {
    const response = await this.client.get<LedgerAlertResponse>(
      '/api/v1/ledger-alerts/current',
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

  async completeLedgerRecord(ledgerCode: string): Promise<LedgerAlert> {
    const response = await this.client.post<LedgerAlertResponse>(
      '/api/v1/ledger-records',
      { ledgerCode },
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
