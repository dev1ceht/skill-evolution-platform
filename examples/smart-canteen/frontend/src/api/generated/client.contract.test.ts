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
  it("receiveInventory sends POST /api/v1/inventory/receipts", async () => {
    await client.receiveInventory('fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/inventory/receipts");
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
  it("completeLedgerRecord sends POST /api/v1/ledger-records", async () => {
    await client.completeLedgerRecord({} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/ledger-records");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("decideMenuApproval sends POST /api/v1/menu-approvals/{menuId}/decision", async () => {
    await client.decideMenuApproval('fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/menu-approvals/fixture/decision");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("submitMenu sends POST /api/v1/menus/{menuId}/submit", async () => {
    await client.submitMenu('fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/menus/fixture/submit");
    expect(options.method).toBe("POST");
  });
  it("generateProcurementPlan sends POST /api/v1/procurement-plans/generate", async () => {
    await client.generateProcurementPlan({} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/procurement-plans/generate");
    expect(options.method).toBe("POST");
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
});
