# 智慧食堂 × Frontend API Integration Skill

这是一个依据两份智慧食堂设计文档构建的可运行纵向切片。它不是整套生产系统，而是用最小代码验证最关键的业务链路与 Skill 自进化闭环：

```text
菜单草稿 → 提交审批 → 审批通过 → 计算采购缺口 → 入库 → 完成台账 → 清除预警
```

## 项目结构

- `contracts/`：OpenAPI 3.0 契约、API IR、接口任务计划和自动生成的 TypeScript 客户端/契约测试。
- `backend/`：Java 17 + Spring Boot 3，领域规则与 HTTP 适配分层，使用内存适配器便于演示。
- `frontend/`：Vue 3 + TypeScript + Vite + Axios，包含 loading、empty、error 和审批交互状态。
- `replay/`：由真实生成缺陷形成的候选、离线判定和提升证据。

生产设计中的 MySQL、Redis、消息队列、统一认证、明厨亮灶设备和外部采购平台没有伪造实现；它们应在后续作为端口接入。

## 启动项目

后端需要 Java 17 和 Maven：

```powershell
cd examples/smart-canteen/backend
mvn spring-boot:run
```

前端需要 Node.js 20+：

```powershell
cd examples/smart-canteen/frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`，依次操作“提交审批”“审批通过”“生成采购计划”“模拟入库”“完成采购验收台账”。Vite 会把 `/api` 代理到 `http://localhost:8080`。

## 在真实项目中显式调用 Skill

先把仓库中的 `skills/frontend-api-integration` 安装到 Codex 的 Skills 目录，或保持它作为团队仓库的项目 Skill。随后在真实 React/Vue 项目中明确给出契约、页面和验证目标，例如：

```text
$frontend-api-integration
请读取 examples/smart-canteen/contracts/smart-canteen.openapi.yaml，
为 MenuApprovalPage 对接 submitMenu 和 decideMenuApproval。
复用项目 Axios 实例与错误拦截器，生成类型、loading/empty/error 状态和 Vitest 契约测试；
先输出 api-ir 与页面任务计划，发现契约歧义时停止，不要猜字段。
```

本示例的实际执行顺序如下：

```powershell
$env:PYTHONPATH='src'
python skills/frontend-api-integration/scripts/normalize_openapi.py `
  examples/smart-canteen/contracts/smart-canteen.openapi.yaml `
  -o examples/smart-canteen/contracts/generated/api-ir.json
python skills/frontend-api-integration/scripts/generate_client.py `
  examples/smart-canteen/contracts/generated/api-ir.json `
  -o examples/smart-canteen/frontend/src/api/generated/client.ts
python skills/frontend-api-integration/scripts/generate_contract_tests.py `
  examples/smart-canteen/contracts/generated/api-ir.json `
  -o examples/smart-canteen/frontend/src/api/generated/client.contract.test.ts
```

自动生成层只负责契约事实；[`smartCanteenApi.ts`](frontend/src/api/smartCanteenApi.ts) 再把它适配到项目已有的 Axios 边界，统一处理 `{code,message,data}` 和业务错误。页面不能直接散落请求代码。

## Skill 如何增强业务逻辑

1. OpenAPI 让审批状态、幂等 Header、请求体和响应包装成为可审查契约。
2. 领域测试保证非法审批迁移、单位换算与负缺口不会被前端代码绕过。
3. 自动生成契约测试保证 URL、HTTP Method、路径参数和 Header 不因版本迭代漂移。
4. Vue 页面测试保证网络状态和业务状态都能被用户观察和恢复。
5. 联调反馈通过 pending window 关联到本次 episode，再经过候选抽取、相似检索、`add/merge/discard`、离线回放和提升。

## 验证

```powershell
python -m pytest -q -p no:cacheprovider --basetemp=tmp/pytest-all
cd examples/smart-canteen/backend
mvn test
cd ../frontend
npm test
npm run build
```

首个真实回放见 [`replay/path-parameter-feedback.json`](replay/path-parameter-feedback.json)，其修复前证据固化在 [`replay/baseline-generator-failure.json`](replay/baseline-generator-failure.json)：初版生成器不能解析参数 `$ref`，也不能正确处理 `{menuId}` 和 `Idempotency-Key`。修复必须同时通过生成器单测、自动契约测试、TypeScript 编译和候选离线判定，之后规则才写入 `SKILL.md`。
