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
