## external reference

在 `contracts/external-ref.openapi.yaml:10`，操作 JSON Pointer `/paths/~1api~1resources~1{resourceId}/get/parameters/0` 引用了 `./common.yaml#/components/parameters/ResourceId`；按该合同位置解析后的文件应为 `contracts/common.yaml`，当前缺失。在 `contracts/external-ref.openapi.yaml:17`，同一操作的 `200` 响应又引用了 `./schemas.yaml#/components/schemas/ResourceResponse`；解析后的文件应为 `contracts/schemas.yaml`，当前同样缺失。

因此无法安全生成该操作的参数定义或 `200` success schema：`ResourceId` 的 `name`、`in`、`required` 和 schema 未知，`ResourceResponse` 的类型、字段、必填性和可空性也未知。补齐文件后，才能按上述文件路径和 JSON Pointer 解析两处引用，并确认引用可达且无进一步未解析引用。

## impact

`getResource`（`GET /api/v1/resources/{resourceId}`）依赖 `./common.yaml#/components/parameters/ResourceId` 来描述路径参数。缺少该定义时，不能确认它是否确实是名为 `resourceId` 的必需 path 参数，也不能安全生成请求参数或序列化规则。它同时依赖 `./schemas.yaml#/components/schemas/ResourceResponse` 描述 `200` 的 `application/json` 响应；缺少该定义时，不能安全生成返回类型、success schema、mock 或响应断言。

补齐文件后才能继续验证：先解析外部引用并重新检查 `getResource` 的参数与 `200` 响应，再执行 schema/合同校验，确认没有未解析引用且路径参数与响应内容均完整。

## required confirmation

需要提供并确认以下两个精确目标，而不是根据名称推断内容：`contracts/common.yaml#/components/parameters/ResourceId`（对应源引用 `./common.yaml#/components/parameters/ResourceId`）和 `contracts/schemas.yaml#/components/schemas/ResourceResponse`（对应源引用 `./schemas.yaml#/components/schemas/ResourceResponse`）。在确认前，无法安全确定 `resourceId` 的参数位置、必填性、值 schema，也无法安全确定 `200` success schema 的结构。

文件补齐后才能继续的验证动作是：解析器按相对路径加载两个文件，校验两个 fragment 存在且类型正确；随后核对参数与 `/api/v1/resources/{resourceId}` 的 path template 兼容，并校验 `200` 的 `application/json` schema 可解析、无循环或悬空引用。

## next step

请将 `common.yaml` 和 `schemas.yaml` 放置在 `contracts/` 下，并分别实现上述 `ResourceId` 与 `ResourceResponse` 目标。当前审查到此停止，未生成 `api-ir.json`、`client.ts` 或 `integration-plan.json`。

补齐后再运行外部引用解析、OpenAPI 合同/schema 校验和最小规范化检查；只有这些检查确认参数与 `200` success schema 完整、引用全部解析后，才可继续生成后续集成产物。
