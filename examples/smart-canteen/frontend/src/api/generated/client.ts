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
