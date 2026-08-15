## external reference

`contracts/external-ref.openapi.yaml#/paths/~1api~1v1~1resources~1{resourceId}/get/parameters/0/$ref` 指向缺失文件 `./common.yaml#/components/parameters/ResourceId`。因此无法确认 `resourceId` 的参数名、`in: path` 位置、`required` 属性、数据类型或序列化规则，也不能安全地生成该请求参数。

同一操作的 `200` 响应 schema 位于 `contracts/external-ref.openapi.yaml#/paths/~1api~1v1~1resources~1{resourceId}/get/responses/200/content/application~1json/schema/$ref`，指向缺失文件 `./schemas.yaml#/components/schemas/ResourceResponse`。因此无法确认 success schema 的字段、类型、必填约束或响应封装，也不能安全地生成成功响应类型。

补齐两个文件后，必须先验证这两个 JSON Pointer 均能解析到声明的 component，且引用目标分别确实是 Parameter Object 和 Schema Object。

## impact

缺失的 `./common.yaml#/components/parameters/ResourceId` 使 `/api/v1/resources/{resourceId}` 的路径参数契约不完整；即使路径模板已经出现 `resourceId`，也不能据此猜测其类型、必填性或 wire serialization。缺失的 `./schemas.yaml#/components/schemas/ResourceResponse` 使 `GET /api/v1/resources/{resourceId}` 的 `200 application/json` success schema 不完整；不能用响应描述或操作名推断返回结构。

在引用解析前不能安全地完成 API IR、客户端参数类型或 success response 类型生成。本次按要求不生成 `api-ir.json`、`client.ts` 或 `integration-plan.json`。文件补齐后，验证动作必须包括重新解析契约、确认参数与 `{resourceId}` 路径变量一致，并确认 `200` 响应 schema 可完整解析且无继续悬空的 `$ref`。

## required confirmation

需要提供并确认以下精确引用目标：`./common.yaml#/components/parameters/ResourceId` 的完整 Parameter Object，以及 `./schemas.yaml#/components/schemas/ResourceResponse` 的完整 Schema Object。不能以同名但不同位置的参数或 schema 替代，也不能根据当前契约补写缺失字段。

确认文件到位后，必须执行引用解析和 OpenAPI 结构校验：检查 `ResourceId` 的 `name`、`in`、`required`、`schema`/序列化定义能支持该路径参数；检查 `ResourceResponse` 能作为 `200` JSON 响应的 schema 解析，并验证其所有内部引用也可解析。只有这些检查通过，才可继续生成 success schema、参数以及后续集成产物。

## next step

先将 `common.yaml` 与 `schemas.yaml` 放在相对 `contracts/external-ref.openapi.yaml` 的正确位置，并保留上述 component 路径。补齐后重新运行 frontend API integration 的规范化/引用解析验证，核对 `getResource` 的参数和 `200` success schema 的 provenance；验证通过后再决定是否生成客户端和集成计划。本次停止继续生成，原因是外部引用仍缺失。
