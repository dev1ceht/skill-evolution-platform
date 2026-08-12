---
name: smart-canteen-api-lifecycle
description: 治理智慧食堂 REST/OpenAPI 契约从设计、兼容性评审、版本发布、客户端迁移到弃用下线的完整生命周期。Use when修改 `/api/v1` 路径、请求/响应字段、统一 envelope、错误码、枚举、分页、认证、幂等 Header，新增 API 版本，或需要评估 breaking change、生成迁移计划和同步前后端契约时。
---

# Smart Canteen API Lifecycle

以提交的 OpenAPI 和可观察 HTTP 行为作为公共契约。先证明兼容性和消费者迁移路径，再修改生成物或下线旧行为。

## Workflow

1. 定位当前 OpenAPI、历史快照、实现 controller、生成的 API IR/client/tests 和所有已知消费者。
2. 读取 `references/compatibility-matrix.md`。规范化旧/新契约并记录哈希，对 operation、参数、body、response、错误、认证和分页逐项分类。
3. 将变更标为 `compatible`、`breaking` 或 `review-required`。文档未声明但消费者依赖的真实行为也算契约风险。
4. 对 breaking change 选择策略：保持 v1 兼容、增加可选字段、提供适配层、并行新版本或明确拒绝变更。不要悄悄改变既有路径语义。
5. 定义 provider 与 consumer 发布顺序、双版本窗口、弃用通知、观测指标、截止条件和回滚。列出受影响页面、adapter、mock、测试和外部调用方。
6. 更新 provider 实现和 OpenAPI，重新生成 API IR、TypeScript client 与 contract tests。手写 adapter 继续负责 `{code,message,data}` 和领域错误映射。
7. 运行 schema diff、HTTP 行为测试、生成客户端编译和页面回归。对旧客户端至少保留一个兼容性测试。
8. 弃用期间观测旧 operation 使用量和错误率；只有已知消费者迁移、流量归零或获得明确例外批准后才能删除。
9. 输出变更分类、contract hashes、迁移任务、发布顺序、验证证据和剩余消费者。

## Contract rules

- `/api/v1`、统一 envelope、原始 wire 名称和幂等 Header 是显式契约。
- 新增可选请求字段通常兼容；新增 response 字段只有在消费者安全忽略未知字段时才兼容。
- 收紧枚举、把可选改必填、更改 method/path、删除字段或更改认证方式都是 breaking。
- 错误码、分页、限流、排序和默认范围变化至少需要 review，不能当纯文档修改。
- 版本兼容不允许绕过学校/食堂授权或降低输入校验。

## Output contract

输出 old/new hashes、operation 级 diff、兼容性分类、消费者清单、迁移与弃用计划、生成物变更、验证矩阵和移除门禁。

