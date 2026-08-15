# OrderListPage API handoff

## Contract evidence

- Source: `contracts/order.openapi.yaml`
- Normalized document hash: `7e787f187593b83af4b20b91bc718b5d6164ae98fa97ff152703723b434a4309`
- `GET /api/v1/orders` (`listOrders`) accepts optional `page` and `size`, returns `OrderPage` in the success `data`, and documents HTTP `401` with `{code, message}`.
- `POST /api/v1/orders/{orderId}/cancel` (`cancelOrder`) requires the path `orderId` and JSON `{reason}`, returns `Order` in the success `data`, and documents HTTP `409` with `{code, message}`.
- The document declares the `bearerAuth` HTTP security scheme. The generated functions therefore require a caller-supplied `authorization` header value and pass it through as `Authorization`; no token storage, refresh, or token-formatting mechanism is included.

## HTTP boundary and page data

`client.ts` calls the existing `src/api/httpClient.ts` `request<T>` function. It does not implement another raw-fetch path. The transport response is handled as `ApiEnvelope<OrderPage>` or `ApiEnvelope<Order>` and `unwrapData` removes the `{code, message, data}` envelope at the HTTP boundary. Consequently, `listOrders` resolves to `OrderPage` and `cancelOrder` resolves to `Order`; an `OrderListPage` caller does not handle the transport envelope.

When the response has no `data`, the client throws `ApiError` while retaining the contract's `code` and `message` from `ErrorResponse`. It does not map or invent business error codes. The shared `httpClient.request` currently parses JSON without exposing `Response.status`, so the contract tests exercise the documented HTTP `401` and `409` responses while asserting the preserved business error body. Status-specific UI branching would require an approved enhancement to the shared boundary, which was intentionally left unchanged.

## Remaining human work

- Wire the (currently absent) `OrderListPage` to `listOrders({ page, size, authorization })` and render loading, empty, retry, and `ApiError` states.
- Wire the cancel action to `cancelOrder({ orderId, reason, authorization })`, then refresh or invalidate the affected order list after a successful cancellation.
- Decide where the page obtains the already-formed `Authorization` header value. No credential persistence or refresh behavior is prescribed by the contract or added here.
- Add the repository's typecheck/test command when the surrounding frontend configuration is available; the workspace currently contains no package or test configuration.
