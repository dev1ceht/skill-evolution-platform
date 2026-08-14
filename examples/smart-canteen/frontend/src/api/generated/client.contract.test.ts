// Generated behavior tests for the typed API client.
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as client from './client';

const fetchMock = vi.fn();
vi.stubGlobal('fetch', fetchMock);
vi.stubGlobal('window', { location: { origin: 'https://contract.test' } });

describe("Smart Canteen Workflow API contract", () => {
  beforeEach(() => {
    fetchMock.mockReset();
    fetchMock.mockResolvedValue({ ok: true, json: async () => ({}) });
  });
  it("reportExternalAlert sends POST /alarmApi/warn/report", async () => {
    await client.reportExternalAlert({} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/alarmApi/warn/report");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("disposeExternalAlert sends POST /alarmApi/warnResult/report", async () => {
    await client.disposeExternalAlert({} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/alarmApi/warnResult/report");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("queryExternalAlerts sends GET /alarmWarn/school/queryPage", async () => {
    await client.queryExternalAlerts(undefined, undefined, undefined, undefined, undefined, undefined, undefined, undefined, undefined, undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/alarmWarn/school/queryPage");
    expect(options.method).toBe("GET");
  });
  it("queryAlerts sends GET /api/v1/alerts", async () => {
    await client.queryAlerts(undefined, undefined, undefined, undefined, undefined, undefined, undefined, undefined, undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/alerts");
    expect(options.method).toBe("GET");
  });
  it("reportAlert sends POST /api/v1/alerts", async () => {
    await client.reportAlert({} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/alerts");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("disposeAlert sends POST /api/v1/alerts/{warnId}/disposal", async () => {
    await client.disposeAlert('fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/alerts/fixture/disposal");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("login sends POST /api/v1/auth/login", async () => {
    await client.login({} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/auth/login");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("logout sends POST /api/v1/auth/logout", async () => {
    await client.logout({} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/auth/logout");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("currentUser sends GET /api/v1/auth/me", async () => {
    await client.currentUser();
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/auth/me");
    expect(options.method).toBe("GET");
  });
  it("refreshToken sends POST /api/v1/auth/refresh-token", async () => {
    await client.refreshToken({} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/auth/refresh-token");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("listDailyMenus sends GET /api/v1/daily-menus", async () => {
    await client.listDailyMenus('fixture', 'fixture', undefined, undefined, undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/daily-menus");
    expect(options.method).toBe("GET");
  });
  it("saveDailyMenu sends POST /api/v1/daily-menus", async () => {
    await client.saveDailyMenu('fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/daily-menus");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("publishDailyMenu sends POST /api/v1/daily-menus/{menuId}/publish", async () => {
    await client.publishDailyMenu('fixture', 'fixture', 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/daily-menus/fixture/publish");
    expect(options.method).toBe("POST");
  });
  it("getDashboardRisk sends GET /api/v1/dashboard/risk", async () => {
    await client.getDashboardRisk('fixture', 'fixture', undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/dashboard/risk");
    expect(options.method).toBe("GET");
  });
  it("getDashboardSummary sends GET /api/v1/dashboard/summary", async () => {
    await client.getDashboardSummary('fixture', 'fixture', undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/dashboard/summary");
    expect(options.method).toBe("GET");
  });
  it("listDishes sends GET /api/v1/dishes", async () => {
    await client.listDishes('fixture', 'fixture', undefined, undefined, undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/dishes");
    expect(options.method).toBe("GET");
  });
  it("createDish sends POST /api/v1/dishes", async () => {
    await client.createDish('fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/dishes");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("updateDish sends PUT /api/v1/dishes/{dishId}", async () => {
    await client.updateDish('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/dishes/fixture");
    expect(options.method).toBe("PUT");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("listIngredients sends GET /api/v1/ingredients", async () => {
    await client.listIngredients('fixture', 'fixture', undefined, undefined, undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/ingredients");
    expect(options.method).toBe("GET");
  });
  it("createIngredient sends POST /api/v1/ingredients", async () => {
    await client.createIngredient('fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/ingredients");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("updateIngredient sends PUT /api/v1/ingredients/{ingredientId}", async () => {
    await client.updateIngredient('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/ingredients/fixture");
    expect(options.method).toBe("PUT");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("listInventory sends GET /api/v1/inventory", async () => {
    await client.listInventory('fixture', 'fixture', undefined, undefined, undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/inventory");
    expect(options.method).toBe("GET");
  });
  it("receiveInventory sends POST /api/v1/inventory/receipts", async () => {
    await client.receiveInventory('fixture', {} as never, undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/inventory/receipts");
    expect(options.method).toBe("POST");
    expect(options.headers["Idempotency-Key"]).toBe('fixture');
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("stockOutInventory sends POST /api/v1/inventory/stock-outs", async () => {
    await client.stockOutInventory('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/inventory/stock-outs");
    expect(options.method).toBe("POST");
    expect(options.headers["Idempotency-Key"]).toBe('fixture');
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("getCurrentLedgerAlert sends GET /api/v1/ledger-alerts/current", async () => {
    await client.getCurrentLedgerAlert();
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/ledger-alerts/current");
    expect(options.method).toBe("GET");
  });
  it("startLedgerCycle sends POST /api/v1/ledger-cycles", async () => {
    await client.startLedgerCycle({} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/ledger-cycles");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("getScopedLedgerAlert sends GET /api/v1/ledger-cycles/{cycleId}/alerts/current", async () => {
    await client.getScopedLedgerAlert('fixture', 'fixture', 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/ledger-cycles/fixture/alerts/current");
    expect(options.method).toBe("GET");
  });
  it("completeScopedLedgerRecord sends POST /api/v1/ledger-cycles/{cycleId}/records", async () => {
    await client.completeScopedLedgerRecord('fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/ledger-cycles/fixture/records");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("completeLedgerRecord sends POST /api/v1/ledger-records", async () => {
    await client.completeLedgerRecord({} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/ledger-records");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("listOperationalLedgerRecords sends GET /api/v1/ledger/records", async () => {
    await client.listOperationalLedgerRecords('fixture', 'fixture', undefined, undefined, undefined, undefined, undefined, undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/ledger/records");
    expect(options.method).toBe("GET");
  });
  it("saveOperationalLedgerRecord sends POST /api/v1/ledger/records", async () => {
    await client.saveOperationalLedgerRecord('fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/ledger/records");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("getOperationalLedgerStats sends GET /api/v1/ledger/stats", async () => {
    await client.getOperationalLedgerStats('fixture', 'fixture', undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/ledger/stats");
    expect(options.method).toBe("GET");
  });
  it("decideMenuApproval sends POST /api/v1/menu-approvals/{menuId}/decision", async () => {
    await client.decideMenuApproval('fixture', {} as never, undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/menu-approvals/fixture/decision");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("importMenuRecipe sends POST /api/v1/menus/{menuId}/recipe", async () => {
    await client.importMenuRecipe('fixture', {} as never, undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/menus/fixture/recipe");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("submitMenu sends POST /api/v1/menus/{menuId}/submit", async () => {
    await client.submitMenu('fixture', undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/menus/fixture/submit");
    expect(options.method).toBe("POST");
  });
  it("generateProcurementPlan sends POST /api/v1/procurement-plans/generate", async () => {
    await client.generateProcurementPlan({} as never, undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/procurement-plans/generate");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("listPurchaseOrders sends GET /api/v1/purchase-orders", async () => {
    await client.listPurchaseOrders('fixture', 'fixture', undefined, undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/purchase-orders");
    expect(options.method).toBe("GET");
  });
  it("createPurchaseOrder sends POST /api/v1/purchase-orders", async () => {
    await client.createPurchaseOrder('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/purchase-orders");
    expect(options.method).toBe("POST");
    expect(options.headers["Idempotency-Key"]).toBe('fixture');
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("receivePurchaseOrder sends POST /api/v1/purchase-orders/{orderId}/receive", async () => {
    await client.receivePurchaseOrder('fixture', 'fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/purchase-orders/fixture/receive");
    expect(options.method).toBe("POST");
    expect(options.headers["Idempotency-Key"]).toBe('fixture');
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("transitionPurchaseOrder sends POST /api/v1/purchase-orders/{orderId}/status", async () => {
    await client.transitionPurchaseOrder('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/purchase-orders/fixture/status");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("listSuppliers sends GET /api/v1/suppliers", async () => {
    await client.listSuppliers('fixture', 'fixture', undefined, undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/suppliers");
    expect(options.method).toBe("GET");
  });
  it("createSupplier sends POST /api/v1/suppliers", async () => {
    await client.createSupplier('fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/suppliers");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("traceIngredientBatch sends GET /api/v1/traceability/{traceCode}", async () => {
    await client.traceIngredientBatch('fixture', 'fixture', 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/traceability/fixture");
    expect(options.method).toBe("GET");
  });
});
