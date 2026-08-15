我会使用 `frontend-api-integration` Skill：先解析并展开本地 `components.parameters` 引用生成 API IR，再据此生成请求层与契约测试，最后运行可用的校验。重点会保留 wire 名称 `resource-id` 与 `Idempotency-Key`，并对路径参数做 `encodeURIComponent`。
