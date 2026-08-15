契约确认只有两个 operation：

- `GET /api/v1/orders`：可选 `page`、`size`，成功数据为 `OrderPage`，文档化 `401`。
- `POST /api/v1/orders/{orderId}/cancel`：必需 `orderId` 和 `reason`，成功数据为 `Order`，文档化 `409`。

仓库目前没有 `OrderListPage` 源码或测试配置，只有 `src/api/httpClient.ts`；因此本次会生成可供页面调用的边界层与契约测试，并在交接中标明页面接线仍需人工完成。`httpClient` 当前不暴露 HTTP status，我会保留契约错误体的 `code/message`，不猜测具体错误码。
