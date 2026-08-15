import { beforeEach, describe, expect, it, vi } from "vitest";
import * as client from "./client";

const fetchMock = vi.fn();
vi.stubGlobal("fetch", fetchMock);

const authorization = "Bearer fixture";
const page = {
  items: [{ id: "order-1", status: "PENDING", total: 12.5 }],
  total: 1,
  page: 2,
  size: 25,
};
const order = { id: "order-1", status: "CANCELLED", total: 12.5 };

describe("Order API contract", () => {
  beforeEach(() => {
    fetchMock.mockReset();
  });

  it("listOrders sends GET with the documented path and query parameters", async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ code: 1, message: "ok", data: page }),
    });

    const result = await client.listOrders({
      page: 2,
      size: 25,
      authorization,
    });
    const [requestUrl, options] = fetchMock.mock.calls[0] as [string, RequestInit];
    const url = new URL(requestUrl, "https://contract.test");
    const headers = options.headers as Record<string, string>;

    expect(url.pathname).toBe("/api/v1/orders");
    expect(url.searchParams.get("page")).toBe("2");
    expect(url.searchParams.get("size")).toBe("25");
    expect(options.method).toBe("GET");
    expect(headers.Authorization).toBe(authorization);
    expect(result).toEqual(page);
    expect(result).not.toHaveProperty("code");
    expect(result).not.toHaveProperty("message");
  });

  it("cancelOrder sends POST with an encoded path parameter and the documented body", async () => {
    fetchMock.mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({ code: 1, message: "ok", data: order }),
    });

    const result = await client.cancelOrder({
      orderId: "order/1",
      reason: "duplicate",
      authorization,
    });
    const [requestUrl, options] = fetchMock.mock.calls[0] as [string, RequestInit];
    const url = new URL(requestUrl, "https://contract.test");
    const headers = options.headers as Record<string, string>;

    expect(url.pathname).toBe("/api/v1/orders/order%2F1/cancel");
    expect(options.method).toBe("POST");
    expect(headers.Authorization).toBe(authorization);
    expect(headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(String(options.body))).toEqual({ reason: "duplicate" });
    expect(result).toEqual(order);
    expect(result).not.toHaveProperty("code");
    expect(result).not.toHaveProperty("message");
  });

  it("retains the documented 401 error body from listOrders", async () => {
    const error = { code: 1001, message: "not authenticated" };
    fetchMock.mockResolvedValue({
      ok: false,
      status: 401,
      json: async () => error,
    });

    await expect(client.listOrders({ authorization })).rejects.toMatchObject({
      name: "ApiError",
      code: error.code,
      message: error.message,
    });
  });

  it("retains the documented 409 error body from cancelOrder", async () => {
    const error = { code: 1002, message: "order state conflict" };
    fetchMock.mockResolvedValue({
      ok: false,
      status: 409,
      json: async () => error,
    });

    await expect(
      client.cancelOrder({
        orderId: "order-1",
        reason: "duplicate",
        authorization,
      }),
    ).rejects.toMatchObject({
      name: "ApiError",
      code: error.code,
      message: error.message,
    });
  });
});
