import { request as httpRequest, type ApiEnvelope } from "./src/api/httpClient";

export type Order = {
  id: string;
  status: "PENDING" | "PAID" | "CANCELLED";
  total: number;
};

export type OrderPage = {
  items: Order[];
  total: number;
  page: number;
  size: number;
};

export type CancelOrderRequest = {
  reason: string;
};

export type ErrorResponse = {
  code: number;
  message: string;
};

export type ListOrdersParams = {
  page?: number;
  size?: number;
  /** Caller-supplied Authorization header value for the contract's bearerAuth scheme. */
  authorization: string;
};

export type CancelOrderParams = {
  orderId: string;
  reason: string;
  /** Caller-supplied Authorization header value for the contract's bearerAuth scheme. */
  authorization: string;
};

export class ApiError extends Error {
  readonly code: number;

  constructor(error: ErrorResponse) {
    super(error.message);
    this.name = "ApiError";
    this.code = error.code;
    Object.setPrototypeOf(this, new.target.prototype);
  }
}

function unwrapData<T>(envelope: ApiEnvelope<T>): T {
  if (envelope.data === undefined) {
    throw new ApiError({ code: envelope.code, message: envelope.message });
  }
  return envelope.data;
}

function ordersPath(params: Pick<ListOrdersParams, "page" | "size">): string {
  const query = new URLSearchParams();
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));
  const queryString = query.toString();
  return queryString ? `/api/v1/orders?${queryString}` : "/api/v1/orders";
}

export async function listOrders(params: ListOrdersParams): Promise<OrderPage> {
  const envelope = await httpRequest<OrderPage>(ordersPath(params), {
    method: "GET",
    headers: {
      Authorization: params.authorization,
    },
  });
  return unwrapData(envelope);
}

export async function cancelOrder(params: CancelOrderParams): Promise<Order> {
  const encodedOrderId = encodeURIComponent(params.orderId);
  const path = `/api/v1/orders/${encodedOrderId}/cancel`;
  const body: CancelOrderRequest = { reason: params.reason };
  const envelope = await httpRequest<Order>(path, {
    method: "POST",
    headers: {
      Authorization: params.authorization,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
  return unwrapData(envelope);
}
