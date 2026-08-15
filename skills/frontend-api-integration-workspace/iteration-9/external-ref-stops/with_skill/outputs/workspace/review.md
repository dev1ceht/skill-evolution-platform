# external reference

操作 `getResource`（`GET /api/v1/resources/{resourceId}`）包含两个未解析的外部引用：

- 参数位置 `/paths/~1api~1v1~1resources~1{resourceId}/get/parameters/0` 引用 `./common.yaml#/components/parameters/ResourceId`。
- `200` 响应的 JSON schema 位置 `/paths/~1api~1v1~1resources~1{resourceId}/get/responses/200/content/application~1json/schema` 引用 `./schemas.yaml#/components/schemas/ResourceResponse`。

在这两个目标文件补齐前，不能安全确认 `ResourceId` 的参数名、位置、是否必填、类型或序列化规则，也不能确认 `ResourceResponse` 的字段、类型、必填性、可空性或响应封装形式。补齐后才能解析并校验这两个 `$ref` 的目标是否存在且与 OpenAPI 3.0.3 文档一致。

# impact

缺失的 `ResourceId` 参数定义阻断请求参数和路径参数客户端代码的生成；缺失的 `ResourceResponse` 定义阻断 `200 application/json` success schema、响应类型及其字段访问的生成。根据契约证据不能用 `resourceId` 的字段名、字符串类型或任意响应字段替代这些引用。

补齐 `./common.yaml` 与 `./schemas.yaml` 后，才能继续执行外部引用解析、OpenAPI/schema 校验，并据解析结果确认参数映射和成功响应类型；在此之前不应生成 `api-ir.json`、`client.ts` 或 `integration-plan.json`。

# required confirmation

请提供或确认以下外部定义的原文及其相对 `contracts/external-ref.openapi.yaml` 的路径保持不变：`./common.yaml#/components/parameters/ResourceId` 与 `./schemas.yaml#/components/schemas/ResourceResponse`。仅确认文件名而不确认目标节点内容，仍不足以安全生成参数或 success schema。

文件补齐后，才能验证 `ResourceId` 是否是可用于 `/api/v1/resources/{resourceId}` 的合法路径参数，并验证 `ResourceResponse` 是否完整定义 `200` 响应的 JSON 表示；当前审查不对缺失内容作任何推断。

# next step

补齐并保存上述两个文件后，再重新解析 `external-ref.openapi.yaml`，确认两个 `$ref` 均可解析，校验 `getResource` 的参数与 `200` success schema，并在这些验证通过后再决定是否生成后续集成产物。本次只保留本审查文件，未生成任何 IR、客户端或集成计划文件。
