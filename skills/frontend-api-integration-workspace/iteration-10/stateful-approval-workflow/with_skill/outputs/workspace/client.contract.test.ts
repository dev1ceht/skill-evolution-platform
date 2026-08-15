// Generated behavior tests for the typed API client.
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as client from './client';

const fetchMock = vi.fn();
vi.stubGlobal('fetch', fetchMock);
vi.stubGlobal('window', { location: { origin: 'https://contract.test' } });

describe("Recipe Approval API contract", () => {
  beforeEach(() => {
    fetchMock.mockReset();
    fetchMock.mockResolvedValue({ ok: true, json: async () => ({}) });
  });
  it("clearRecipeAlert sends POST /api/v1/recipes/{recipeId}/alerts/{alertId}/clear", async () => {
    await client.clearRecipeAlert('fixture', 'fixture', 'fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/recipes/fixture/alerts/fixture/clear");
    expect(options.method).toBe("POST");
    expect(options.headers["Idempotency-Key"]).toBe('fixture');
  });
  it("getRecipeApproval sends GET /api/v1/recipes/{recipeId}/approval", async () => {
    await client.getRecipeApproval('fixture');
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/recipes/fixture/approval");
    expect(options.method).toBe("GET");
  });
  it("approveRecipe sends POST /api/v1/recipes/{recipeId}/approval/approve", async () => {
    await client.approveRecipe('fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/recipes/fixture/approval/approve");
    expect(options.method).toBe("POST");
    expect(options.headers["Idempotency-Key"]).toBe('fixture');
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
  it("receiveInventory sends POST /api/v1/recipes/{recipeId}/inventory/receive", async () => {
    await client.receiveInventory('fixture', 'fixture', {} as never);
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe("/api/v1/recipes/fixture/inventory/receive");
    expect(options.method).toBe("POST");
    expect(options.headers["Idempotency-Key"]).toBe('fixture');
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({});
  });
});
