// Generated from API IR. Review before production use.

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

export type Receipt = {
  materialId: string;
  quantityBase: number;
  baseUnit: string;
};

export type LedgerRecord = {
  ledgerCode: string;
};

export type LedgerAlert = {
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

export async function receiveInventory(idempotencyKey: string, body: InventoryReceipt): Promise<ReceiptResponse> {
  const path = "/api/v1/inventory/receipts";
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Idempotency-Key"] = String(idempotencyKey);
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ReceiptResponse>;
}

export async function getCurrentLedgerAlert(): Promise<LedgerAlertResponse> {
  const path = "/api/v1/ledger-alerts/current";
  const url = new URL(path, window.location.origin);
  const response = await fetch(url, { method: 'GET' });
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

export async function decideMenuApproval(menuId: string, body: ApprovalDecision): Promise<MenuResponse> {
  const encodedMenuId = encodeURIComponent(String(menuId));
  let path = "/api/v1/menu-approvals/{menuId}/decision";
  path = path.replace("{menuId}", encodedMenuId);
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<MenuResponse>;
}

export async function submitMenu(menuId: string): Promise<MenuResponse> {
  const encodedMenuId = encodeURIComponent(String(menuId));
  let path = "/api/v1/menus/{menuId}/submit";
  path = path.replace("{menuId}", encodedMenuId);
  const url = new URL(path, window.location.origin);
  const response = await fetch(url, { method: 'POST' });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<MenuResponse>;
}

export async function generateProcurementPlan(body: GenerateProcurementRequest): Promise<ProcurementPlanResponse> {
  const path = "/api/v1/procurement-plans/generate";
  const url = new URL(path, window.location.origin);
  const headers: Record<string, string> = {};
  headers["Content-Type"] = "application/json";
  const response = await fetch(url, { method: 'POST', headers: headers, body: JSON.stringify(body) });
  if (!response.ok) throw new Error(`API request failed: ${response.status}`);
  return response.json() as Promise<ProcurementPlanResponse>;
}
