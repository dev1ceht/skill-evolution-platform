# Item API 1.0.0 → 1.1.0 迁移计划

## 变更结论

`GET /api/v1/items`（`listItems`）存在 breaking change：

- `status` 查询参数由可选改为必填。
- `status` 的 enum 由 `ACTIVE | INACTIVE` 收窄为仅 `ACTIVE`。

契约哈希：

- before：`ae3604a06f69abdbf99292a146cdaf5987a782447ec8b5677b1b25547dee9b43`
- after：`d0aa8ad646473fdf3b4b6820dd8d182f3e137c11c095b5cbcc6f15d5a6d5701e`

在迁移任务完成并获得批准前，不直接更新 typed client。

## 受影响范围

### 页面

当前 workspace 没有页面源码，因此无法绑定到具体文件。所有调用 `listItems` 的 Item 列表页、列表筛选组件及其数据层都受影响：

- 请求必须始终传入 `status`，默认值应为 `ACTIVE`。
- UI、路由状态或缓存键不能再产生 `INACTIVE`。
- 重新验证 loading、success、empty 和 error 状态，尤其是缺少 `status` 或发送非法 enum 时的错误展示。

### Client

目标 typed client 的 `listItems` 方法需要把 `status` 改为必填，并限制为 `ACTIVE`。当前 workspace 没有 typed client；若在消费者仓库存在生成 client，必须先完成本计划并取得批准后再重新生成或手工适配。

### Mock

- 将 `listItems` 的成功请求 fixture 改为带 `?status=ACTIVE`。
- 删除或标记所有 `status=INACTIVE` 的成功 fixture。
- 增加缺少 `status` 和非法 enum 的错误 fixture，覆盖后端 4xx 映射。
- 保留 loading、success、empty、error 四类页面状态 fixture。

### Tests

- Contract/client：断言请求方法为 `GET`、路径为 `/api/v1/items`，且始终发送 `status=ACTIVE`；类型检查应拒绝缺参和 `INACTIVE`。
- Mock/page-state：覆盖默认 ACTIVE、成功、空列表、加载中及 4xx 错误状态。
- Integration：在页面数据边界验证筛选状态到 query 参数的映射和错误处理。
- E2E：仅对使用 Item 列表的关键跨系统流程增加或更新用例。
- 在 client 更新前先完成 fixture 与测试改造，避免测试继续固化旧契约。

## 迁移任务与发布顺序

1. **批准与盘点**：建立 breaking migration task，枚举所有 `listItems` 调用方、页面、缓存/query key、mock 和测试；评审本文件及 `contract-diff.json`。
2. **先改验证资产**：更新 contract fixtures、mock 和页面边界测试；加入缺参/非法 enum 的负向覆盖。此阶段不提交 typed client 变更。
3. **迁移调用方**：批准后让所有页面和数据层显式传入 `status: ACTIVE`，移除 `INACTIVE` 分支；随后更新/生成 typed client，并运行类型检查、lint、contract、mock 和 integration 测试。
4. **前端先行发布**：在后端仍处于兼容窗口时发布已迁移的页面/client，确认线上请求不再省略 `status` 或发送 `INACTIVE`。
5. **后端 enforcement**：确认所有调用方完成迁移后，再启用 `status` 必填及仅 `ACTIVE` 的服务端校验，并发布 OpenAPI 1.1.0。
6. **观察与收尾**：监控 4xx、query 参数缺失、非法 enum 和 Item 列表错误率；稳定后关闭兼容窗口并清理旧 fixture/分支。

## 回滚注意事项

- 最安全的回滚顺序是先恢复后端兼容行为（允许省略 `status` 且暂时接受 `INACTIVE`），再回滚前端/client；否则旧 client 可能因新校验直接失败。
- 保留旧契约及其 hash，回滚依据为 `api-ir.before.json` 与 `contracts/old.openapi.yaml`，不要仅回滚生成 client。
- 若后端 enforcement 已启用，不要直接把前端回滚到会省略 `status` 或发送 `INACTIVE` 的版本；必须先恢复后端兼容窗口。
- 回滚或切换期间继续保留缺参/非法 enum 的监控和告警，确认 4xx 恢复后再关闭迁移开关。

## 完成标准

所有 `listItems` 调用方均传入 `ACTIVE`，typed client 变更已获批准，mock/contract/integration 测试通过，后端 enforcement 已按顺序发布，且未观察到由缺参或旧 enum 导致的 4xx 回归。
