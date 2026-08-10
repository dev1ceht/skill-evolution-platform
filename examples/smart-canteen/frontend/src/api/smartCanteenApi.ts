import type { AxiosInstance, AxiosResponse } from 'axios';
import type {
  LedgerAlert,
  LedgerAlertResponse,
  Menu,
  MenuResponse,
  ProcurementPlan,
  ProcurementPlanResponse,
  Receipt,
  ReceiptResponse,
} from './generated/client';

interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
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
  submitMenu(menuId: string): Promise<Menu>;
  decideMenuApproval(
    menuId: string,
    decision: 'APPROVE' | 'REJECT',
    comment: string,
  ): Promise<Menu>;
  generateProcurementPlan(menuId: string): Promise<ProcurementPlan>;
  receiveInventory(
    idempotencyKey: string,
    materialId: string,
    quantity: number,
    unit: string,
  ): Promise<Receipt>;
  completeLedgerRecord(ledgerCode: string): Promise<LedgerAlert>;
}

export class SmartCanteenApi implements SmartCanteenApiPort {
  constructor(private readonly client: AxiosInstance) {}

  async getCurrentLedgerAlert(): Promise<LedgerAlert> {
    const response = await this.client.get<LedgerAlertResponse>(
      '/api/v1/ledger-alerts/current',
    );
    return unwrap(response);
  }

  async submitMenu(menuId: string): Promise<Menu> {
    const response = await this.client.post<MenuResponse>(
      `/api/v1/menus/${encodeURIComponent(menuId)}/submit`,
    );
    return unwrap(response);
  }

  async decideMenuApproval(
    menuId: string,
    decision: 'APPROVE' | 'REJECT',
    comment: string,
  ): Promise<Menu> {
    const response = await this.client.post<MenuResponse>(
      `/api/v1/menu-approvals/${encodeURIComponent(menuId)}/decision`,
      { decision, comment },
    );
    return unwrap(response);
  }

  async generateProcurementPlan(menuId: string): Promise<ProcurementPlan> {
    const response = await this.client.post<ProcurementPlanResponse>(
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
  ): Promise<Receipt> {
    const response = await this.client.post<ReceiptResponse>(
      '/api/v1/inventory/receipts',
      { materialId, quantity, unit },
      { headers: { 'Idempotency-Key': idempotencyKey } },
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
}
