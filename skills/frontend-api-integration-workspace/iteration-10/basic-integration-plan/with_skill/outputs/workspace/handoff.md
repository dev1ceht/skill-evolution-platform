# MenuApprovalPage API 集成交接

## 生成文件

- [api-ir.json](api-ir.json)：由 `frontend-api-integration` Skill 的 `normalize_openapi.py` 从 `contracts/menu.openapi.yaml` 生成。包含规范化 API IR、共享 `documentHash` 和每个 operation 的 JSON Pointer provenance。
- [integration-plan.json](integration-plan.json)：面向 `MenuApprovalPage` 的页面级依赖、接口、状态、mock 与验证计划。
- [handoff.md](handoff.md)：本交接说明。

## 契约证据

- 契约路径：`contracts/menu.openapi.yaml`
- 规范化 `documentHash`：`55dc458a68e6ad0588acc5b638e3732c34e9f6f4ee6a7a8278d74cf910c48c5f`
- `listMenus`：`GET /api/v1/menus`，provenance pointer 为 `/paths/~1api~1v1~1menus/get`；查询参数 `page`、`size` 均为可选 integer，minimum 为 1；请求体为空；仅记录了 200 `MenuPageResponse`。
- `submitMenu`：`POST /api/v1/menus/{menuId}/submit`，provenance pointer 为 `/paths/~1api~1v1~1menus~1{menuId}~1submit/post`；`menuId` 是必需 path string；请求体为空；记录了 200 `MenuResponse` 和 400 `ErrorResponse`。
- 契约根级 `/security` 声明 `bearerAuth`，其 scheme 为 HTTP bearer。没有新增 token 获取、header 注入或其他认证行为；如何在现有边界满足该要求仍需人工确认。

## 假设与边界

- 工作区当前没有 `MenuApprovalPage`、API client/hook、package manifest、TypeScript 配置或测试目录，因此本次只生成要求的 IR、集成计划和交接文件，没有声称页面代码已经接通。
- `src/api/httpClient.ts` 保留不变，计划要求复用其 `request<T>(input, init?)` 接口；没有修改共享 HTTP 基础设施。
- `page`/`size` 明确表明契约使用 page pagination，但契约没有给出默认值，所以没有写入默认分页值。
- 成功响应中的 `MenuPageResponse.data.items` 为空数组时规划为空状态；契约没有单独的 empty response 或查询错误 schema，计划没有补写这些字段。
- mock 只使用契约声明的 envelope、字段和 `Menu.status` 枚举，不添加请求体、错误码或认证行为。

## 测试证据

- 已运行规范化命令：`normalize_openapi.py contracts/menu.openapi.yaml --output api-ir.json`，结果为 `normalized 2 operations -> api-ir.json`。
- 已用 JSON 解析器验证 `api-ir.json`：通过。
- 已核对 IR 中两个 operation 的 pointer 与同一 `documentHash`。
- 契约原始文件字节的 SHA-256 为 `3a502747c9bfb787de5dad7a576b23d685b9ad6148c1c76845f876c5380d5370`；这与 IR 的规范化文档 hash 不同，因为 Skill 对解析后的 OpenAPI 文档做 canonical JSON hash，二者不应混用。
- 本次未运行 TypeScript、lint、contract test 或页面 mock test：工作区没有对应脚本、页面或测试 harness。
- `src/api/httpClient.ts` 本次未写入；生成前后应保持 SHA-256 `8c8f3b2ad8b29f665d0cabef6291fb5da957074f17716e3cdc3b4f543f52c803`。

## 剩余人工工作

1. 在仓库既有页面约定下找到或创建 `MenuApprovalPage`，并在 API/data-access 边界接入 `listMenus` 与 `submitMenu`。
2. 生成显式 TypeScript 请求/响应类型和适配器，复用 `src/api/httpClient.ts`；确认 bearerAuth 的 token 提供方式，不改变共享 HTTP 基础设施。
3. 实现列表查询与提交的 loading、empty、error 状态，并决定页面层如何展示契约未记录的查询失败形态。
4. 按 `integration-plan.json` 添加 contract tests、mock state tests，并在项目配置出现后运行 typecheck/lint。
