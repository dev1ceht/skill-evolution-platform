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
  it("getAgentMetrics sends GET /api/v1/agent/metrics", async () => {
    await client.getAgentMetrics('fixture', 'fixture', 'fixture', undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/agent/metrics");
    expect(options.method).toBe("GET");
    expect(options.headers["X-Request-Id"]).toBe('fixture');
  });
  it("startAgentRun sends POST /api/v1/agent/runs", async () => {
    await client.startAgentRun('fixture', 'fixture', 'fixture', {} as never, 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/agent/runs");
    expect(options.method).toBe("POST");
    expect(options.headers["Idempotency-Key"]).toBe('fixture');
    expect(options.headers["X-Request-Id"]).toBe('fixture');
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("getAgentRun sends GET /api/v1/agent/runs/{runId}", async () => {
    await client.getAgentRun('fixture', 'fixture', 'fixture', 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/agent/runs/fixture");
    expect(options.method).toBe("GET");
    expect(options.headers["X-Request-Id"]).toBe('fixture');
  });
  it("cancelAgentRun sends POST /api/v1/agent/runs/{runId}/cancel", async () => {
    await client.cancelAgentRun('fixture', 'fixture', 'fixture', 'fixture', {} as never, 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/agent/runs/fixture/cancel");
    expect(options.method).toBe("POST");
    expect(options.headers["Idempotency-Key"]).toBe('fixture');
    expect(options.headers["X-Request-Id"]).toBe('fixture');
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("decideAgentRun sends POST /api/v1/agent/runs/{runId}/decisions", async () => {
    await client.decideAgentRun('fixture', 'fixture', 'fixture', 'fixture', {} as never, 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/agent/runs/fixture/decisions");
    expect(options.method).toBe("POST");
    expect(options.headers["Idempotency-Key"]).toBe('fixture');
    expect(options.headers["X-Request-Id"]).toBe('fixture');
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("getAgentRunEvents sends GET /api/v1/agent/runs/{runId}/events", async () => {
    await client.getAgentRunEvents('fixture', 'fixture', 'fixture', 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/agent/runs/fixture/events");
    expect(options.method).toBe("GET");
    expect(options.headers["X-Request-Id"]).toBe('fixture');
  });
  it("resumeAgentRun sends POST /api/v1/agent/runs/{runId}/resume", async () => {
    await client.resumeAgentRun('fixture', 'fixture', 'fixture', 'fixture', {} as never, 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/agent/runs/fixture/resume");
    expect(options.method).toBe("POST");
    expect(options.headers["Idempotency-Key"]).toBe('fixture');
    expect(options.headers["X-Request-Id"]).toBe('fixture');
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("listAgentSkills sends GET /api/v1/agent/skills", async () => {
    await client.listAgentSkills();
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/agent/skills");
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
  it("getAssistantConversationHistory sends GET /api/v1/assistant/conversations/{conversationId}/messages", async () => {
    await client.getAssistantConversationHistory('fixture', 'fixture', 'fixture', 'fixture', undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/assistant/conversations/fixture/messages");
    expect(options.method).toBe("GET");
    expect(options.headers["X-Request-Id"]).toBe('fixture');
  });
  it("sendAssistantMessage sends POST /api/v1/assistant/conversations/{conversationId}/messages", async () => {
    await client.sendAssistantMessage('fixture', 'fixture', 'fixture', 'fixture', {} as never, 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/assistant/conversations/fixture/messages");
    expect(options.method).toBe("POST");
    expect(options.headers["Idempotency-Key"]).toBe('fixture');
    expect(options.headers["X-Request-Id"]).toBe('fixture');
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("listAuditLogs sends GET /api/v1/audit-logs", async () => {
    await client.listAuditLogs(undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/audit-logs");
    expect(options.method).toBe("GET");
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
  it("listCanteenShowcases sends GET /api/v1/canteen-showcases", async () => {
    await client.listCanteenShowcases('fixture', 'fixture', undefined, undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/canteen-showcases");
    expect(options.method).toBe("GET");
  });
  it("createCanteenShowcase sends POST /api/v1/canteen-showcases", async () => {
    await client.createCanteenShowcase('fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/canteen-showcases");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("getCanteenShowcase sends GET /api/v1/canteen-showcases/{showcaseId}", async () => {
    await client.getCanteenShowcase('fixture', 'fixture', 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/canteen-showcases/fixture");
    expect(options.method).toBe("GET");
  });
  it("updateCanteenShowcase sends PUT /api/v1/canteen-showcases/{showcaseId}", async () => {
    await client.updateCanteenShowcase('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/canteen-showcases/fixture");
    expect(options.method).toBe("PUT");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("listCanteenShowcaseHistory sends GET /api/v1/canteen-showcases/{showcaseId}/history", async () => {
    await client.listCanteenShowcaseHistory('fixture', 'fixture', 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/canteen-showcases/fixture/history");
    expect(options.method).toBe("GET");
  });
  it("publishCanteenShowcase sends POST /api/v1/canteen-showcases/{showcaseId}/publish", async () => {
    await client.publishCanteenShowcase('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/canteen-showcases/fixture/publish");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("reviewCanteenShowcase sends POST /api/v1/canteen-showcases/{showcaseId}/review", async () => {
    await client.reviewCanteenShowcase('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/canteen-showcases/fixture/review");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("revokeCanteenShowcase sends POST /api/v1/canteen-showcases/{showcaseId}/revoke", async () => {
    await client.revokeCanteenShowcase('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/canteen-showcases/fixture/revoke");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("submitCanteenShowcase sends POST /api/v1/canteen-showcases/{showcaseId}/submit", async () => {
    await client.submitCanteenShowcase('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/canteen-showcases/fixture/submit");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("createCanteenShowcaseVersion sends POST /api/v1/canteen-showcases/{showcaseId}/versions", async () => {
    await client.createCanteenShowcaseVersion('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/canteen-showcases/fixture/versions");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("listCanteens sends GET /api/v1/canteens", async () => {
    await client.listCanteens(undefined, undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/canteens");
    expect(options.method).toBe("GET");
  });
  it("createCanteen sends POST /api/v1/canteens", async () => {
    await client.createCanteen({} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/canteens");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("updateCanteen sends PUT /api/v1/canteens/{canteenId}", async () => {
    await client.updateCanteen('fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/canteens/fixture");
    expect(options.method).toBe("PUT");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("updateCanteenStatus sends POST /api/v1/canteens/{canteenId}/status", async () => {
    await client.updateCanteenStatus('fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/canteens/fixture/status");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("listComplianceRecords sends GET /api/v1/compliance-records", async () => {
    await client.listComplianceRecords('fixture', 'fixture', undefined, undefined, undefined, undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/compliance-records");
    expect(options.method).toBe("GET");
  });
  it("createComplianceRecord sends POST /api/v1/compliance-records", async () => {
    await client.createComplianceRecord('fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/compliance-records");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("scanComplianceExpiry sends POST /api/v1/compliance-records/expiry-scan", async () => {
    await client.scanComplianceExpiry('fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/compliance-records/expiry-scan");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("getComplianceRecord sends GET /api/v1/compliance-records/{recordId}", async () => {
    await client.getComplianceRecord('fixture', 'fixture', 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/compliance-records/fixture");
    expect(options.method).toBe("GET");
  });
  it("updateComplianceRecord sends PUT /api/v1/compliance-records/{recordId}", async () => {
    await client.updateComplianceRecord('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/compliance-records/fixture");
    expect(options.method).toBe("PUT");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("listComplianceHistory sends GET /api/v1/compliance-records/{recordId}/history", async () => {
    await client.listComplianceHistory('fixture', 'fixture', 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/compliance-records/fixture/history");
    expect(options.method).toBe("GET");
  });
  it("reviewComplianceRecord sends POST /api/v1/compliance-records/{recordId}/review", async () => {
    await client.reviewComplianceRecord('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/compliance-records/fixture/review");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("submitComplianceRecord sends POST /api/v1/compliance-records/{recordId}/submit", async () => {
    await client.submitComplianceRecord('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/compliance-records/fixture/submit");
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
  it("getDailyMenu sends GET /api/v1/daily-menus/{menuId}", async () => {
    await client.getDailyMenu('fixture', 'fixture', 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/daily-menus/fixture");
    expect(options.method).toBe("GET");
  });
  it("decideDailyMenu sends POST /api/v1/daily-menus/{menuId}/decision", async () => {
    await client.decideDailyMenu('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/daily-menus/fixture/decision");
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
  it("submitDailyMenu sends POST /api/v1/daily-menus/{menuId}/submit", async () => {
    await client.submitDailyMenu('fixture', 0, 'fixture', 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/daily-menus/fixture/submit");
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
  it("listIngredientUnits sends GET /api/v1/ingredients/{ingredientId}/units", async () => {
    await client.listIngredientUnits('fixture', 'fixture', 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/ingredients/fixture/units");
    expect(options.method).toBe("GET");
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
  it("listLedgerConfigurations sends GET /api/v1/ledger-configurations", async () => {
    await client.listLedgerConfigurations('fixture', 'fixture', undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/ledger-configurations");
    expect(options.method).toBe("GET");
  });
  it("createLedgerConfiguration sends POST /api/v1/ledger-configurations", async () => {
    await client.createLedgerConfiguration('fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/ledger-configurations");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("updateLedgerConfiguration sends PUT /api/v1/ledger-configurations/{configurationId}", async () => {
    await client.updateLedgerConfiguration('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/ledger-configurations/fixture");
    expect(options.method).toBe("PUT");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("startLedgerCycle sends POST /api/v1/ledger-cycles", async () => {
    await client.startLedgerCycle({} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/ledger-cycles");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("ensureConfiguredLedgerCycles sends POST /api/v1/ledger-cycles/configured/current", async () => {
    await client.ensureConfiguredLedgerCycles('fixture', 'fixture', undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/ledger-cycles/configured/current");
    expect(options.method).toBe("POST");
  });
  it("completeConfiguredLedger sends POST /api/v1/ledger-cycles/configured/{cycleId}/records", async () => {
    await client.completeConfiguredLedger('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/ledger-cycles/configured/fixture/records");
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
  it("listMealSuspensions sends GET /api/v1/meal-suspensions", async () => {
    await client.listMealSuspensions('fixture', 'fixture', undefined, undefined, undefined, undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/meal-suspensions");
    expect(options.method).toBe("GET");
  });
  it("createMealSuspension sends POST /api/v1/meal-suspensions", async () => {
    await client.createMealSuspension('fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/meal-suspensions");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("getMealSuspensionStats sends GET /api/v1/meal-suspensions/stats", async () => {
    await client.getMealSuspensionStats('fixture', 'fixture', undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/meal-suspensions/stats");
    expect(options.method).toBe("GET");
  });
  it("cancelMealSuspension sends POST /api/v1/meal-suspensions/{suspensionId}/cancel", async () => {
    await client.cancelMealSuspension('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/meal-suspensions/fixture/cancel");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("listMealSuspensionHistory sends GET /api/v1/meal-suspensions/{suspensionId}/history", async () => {
    await client.listMealSuspensionHistory('fixture', 'fixture', 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/meal-suspensions/fixture/history");
    expect(options.method).toBe("GET");
  });
  it("reviewMealSuspension sends POST /api/v1/meal-suspensions/{suspensionId}/review", async () => {
    await client.reviewMealSuspension('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/meal-suspensions/fixture/review");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
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
  it("listPermissions sends GET /api/v1/permissions", async () => {
    await client.listPermissions();
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/permissions");
    expect(options.method).toBe("GET");
  });
  it("listProcurementPlans sends GET /api/v1/procurement-plans", async () => {
    await client.listProcurementPlans('fixture', 'fixture', undefined, undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/procurement-plans");
    expect(options.method).toBe("GET");
  });
  it("generateProcurementPlan sends POST /api/v1/procurement-plans/generate", async () => {
    await client.generateProcurementPlan({} as never, undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/procurement-plans/generate");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("generateProcurementPlanRange sends POST /api/v1/procurement-plans/generate-range", async () => {
    await client.generateProcurementPlanRange('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/procurement-plans/generate-range");
    expect(options.method).toBe("POST");
    expect(options.headers["Idempotency-Key"]).toBe('fixture');
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("getProcurementPlan sends GET /api/v1/procurement-plans/{planId}", async () => {
    await client.getProcurementPlan('fixture', 'fixture', 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/procurement-plans/fixture");
    expect(options.method).toBe("GET");
  });
  it("cancelProcurementPlan sends POST /api/v1/procurement-plans/{planId}/cancel", async () => {
    await client.cancelProcurementPlan('fixture', 'fixture', 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/procurement-plans/fixture/cancel");
    expect(options.method).toBe("POST");
  });
  it("confirmProcurementPlan sends POST /api/v1/procurement-plans/{planId}/confirm", async () => {
    await client.confirmProcurementPlan('fixture', 'fixture', 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/procurement-plans/fixture/confirm");
    expect(options.method).toBe("POST");
  });
  it("adjustProcurementPlan sends PUT /api/v1/procurement-plans/{planId}/items", async () => {
    await client.adjustProcurementPlan('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/procurement-plans/fixture/items");
    expect(options.method).toBe("PUT");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("createPurchaseOrderFromPlan sends POST /api/v1/procurement-plans/{planId}/purchase-orders", async () => {
    await client.createPurchaseOrderFromPlan('fixture', 'fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/procurement-plans/fixture/purchase-orders");
    expect(options.method).toBe("POST");
    expect(options.headers["Idempotency-Key"]).toBe('fixture');
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
  it("listRoles sends GET /api/v1/roles", async () => {
    await client.listRoles();
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/roles");
    expect(options.method).toBe("GET");
  });
  it("replaceRolePermissions sends PUT /api/v1/roles/{roleCode}/permissions", async () => {
    await client.replaceRolePermissions('fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/roles/fixture/permissions");
    expect(options.method).toBe("PUT");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("listSchools sends GET /api/v1/schools", async () => {
    await client.listSchools(undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/schools");
    expect(options.method).toBe("GET");
  });
  it("createSchool sends POST /api/v1/schools", async () => {
    await client.createSchool({} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/schools");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("updateSchool sends PUT /api/v1/schools/{schoolId}", async () => {
    await client.updateSchool('fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/schools/fixture");
    expect(options.method).toBe("PUT");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("updateSchoolStatus sends POST /api/v1/schools/{schoolId}/status", async () => {
    await client.updateSchoolStatus('fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/schools/fixture/status");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("listSupplierComplaints sends GET /api/v1/supplier-complaints", async () => {
    await client.listSupplierComplaints('fixture', 'fixture', undefined, undefined, undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/supplier-complaints");
    expect(options.method).toBe("GET");
  });
  it("createSupplierComplaint sends POST /api/v1/supplier-complaints", async () => {
    await client.createSupplierComplaint('fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/supplier-complaints");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("getSupplierComplaint sends GET /api/v1/supplier-complaints/{complaintId}", async () => {
    await client.getSupplierComplaint('fixture', 'fixture', 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/supplier-complaints/fixture");
    expect(options.method).toBe("GET");
  });
  it("closeSupplierComplaint sends POST /api/v1/supplier-complaints/{complaintId}/close", async () => {
    await client.closeSupplierComplaint('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/supplier-complaints/fixture/close");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("listSupplierComplaintHistory sends GET /api/v1/supplier-complaints/{complaintId}/history", async () => {
    await client.listSupplierComplaintHistory('fixture', 'fixture', 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/supplier-complaints/fixture/history");
    expect(options.method).toBe("GET");
  });
  it("processSupplierComplaint sends POST /api/v1/supplier-complaints/{complaintId}/process", async () => {
    await client.processSupplierComplaint('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/supplier-complaints/fixture/process");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("replySupplierComplaint sends POST /api/v1/supplier-complaints/{complaintId}/reply", async () => {
    await client.replySupplierComplaint('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/supplier-complaints/fixture/reply");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("reviewSupplierComplaint sends POST /api/v1/supplier-complaints/{complaintId}/review", async () => {
    await client.reviewSupplierComplaint('fixture', 'fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/supplier-complaints/fixture/review");
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
  it("listUsers sends GET /api/v1/users", async () => {
    await client.listUsers(undefined, undefined, undefined);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/users");
    expect(options.method).toBe("GET");
  });
  it("createUser sends POST /api/v1/users", async () => {
    await client.createUser({} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/users");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("updateUser sends PUT /api/v1/users/{userId}", async () => {
    await client.updateUser('fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/users/fixture");
    expect(options.method).toBe("PUT");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("replaceUserRoles sends PUT /api/v1/users/{userId}/roles", async () => {
    await client.replaceUserRoles('fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/users/fixture/roles");
    expect(options.method).toBe("PUT");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("replaceUserScopes sends PUT /api/v1/users/{userId}/scopes", async () => {
    await client.replaceUserScopes('fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/users/fixture/scopes");
    expect(options.method).toBe("PUT");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("updateUserStatus sends POST /api/v1/users/{userId}/status", async () => {
    await client.updateUserStatus('fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/users/fixture/status");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
});
