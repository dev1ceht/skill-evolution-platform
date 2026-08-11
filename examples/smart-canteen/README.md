# 智慧食堂 × Frontend API Integration Skill

这是一个依据两份智慧食堂设计文档构建的可运行纵向切片。它不是整套生产系统，而是用最小代码验证最关键的业务链路与 Skill 自进化闭环：

```text
菜单草稿 → 提交审批 → 审批通过 → 计算采购缺口 → 入库 → 完成台账 → 清除预警
```

## 项目结构

- `contracts/`：OpenAPI 3.0 契约、API IR、接口任务计划和自动生成的 TypeScript 客户端/契约测试。
- `backend/`：Java 17 + Spring Boot 3，领域规则、事务边界与 JDBC 持久化适配分层，Flyway 管理数据库版本。
- `frontend/`：Vue 3 + TypeScript + Vite + Axios，包含 loading、empty、error 和审批交互状态。
- `infra/`：MySQL、Redis、RabbitMQ 各自的 Dockerfile，以及统一的 Compose 编排、健康检查与本地数据卷。
- `replay/`：由真实生成缺陷形成的候选、离线判定和提升证据。

MySQL 已接入当前纵向业务切片；Redis 与 RabbitMQ 已提供可复现的本地中间件环境，但尚未伪造缓存或异步消费逻辑。统一认证、明厨亮灶设备和外部采购平台仍应在后续作为真实端口接入。

## 第一阶段：后端台账模块

第一阶段已经把原来的“全局台账完成标记”改成可持久化的后端模块，核心数据按
`schoolId + canteenId + cycleId` 隔离。后端现在提供三类周期接口：

```text
POST /api/v1/ledger-cycles
POST /api/v1/ledger-cycles/{cycleId}/records
GET  /api/v1/ledger-cycles/{cycleId}/alerts/current
```

输入周期和待完成的 `ledgerCodes` 后，系统返回当前缺项；重复完成同一项是幂等的，完成最后一项后返回 `status=CLEARED` 和空缺项列表。数据库由 Flyway `V2__add_scoped_ledger_cycles.sql` 建立学校、食堂、周期、周期要求和预警状态表，应用重启后状态仍可恢复。

本阶段的需求目录、实施计划、验证记录和需求追溯在 [`docs/smart-canteen/`](../../docs/smart-canteen/)；后端 Skill 在 [`skills/smart-canteen-backend/`](../../skills/smart-canteen-backend/)。认证/RBAC、Redis、RabbitMQ、设备及第三方平台接入明确留到后续阶段。

## 启动项目

先启动中间件。需要 Docker Desktop 或兼容的 Docker Engine；每个中间件都由仓库内 Dockerfile 构建：

```powershell
cd examples/smart-canteen/infra
Copy-Item .env.example .env
# 修改 .env 中的 replace-with-* 密码后再启动
docker compose up -d --build
docker compose ps
```

也可以运行 `./verify-stack.ps1`，它会从三个 Dockerfile 构建镜像，并等待 MySQL、Redis、RabbitMQ 全部通过健康检查；任何服务未就绪都会以非零状态退出。

容器健康后，运行真实 MySQL 工作流验收。脚本会创建随机命名的隔离数据库，执行 Flyway、并发幂等、单位回滚和跨应用重启测试，并在结束时删除该测试数据库：

```powershell
./verify-mysql-workflow.ps1
```

两条验收链路会分别更新 `outputs/verification/smart-canteen-runtime-latest.json` 和 `outputs/verification/smart-canteen-mysql-workflow-latest.json`，记录时间、镜像摘要、健康状态、MySQL/Flyway 版本、测试结论和隔离数据库清理结果；不写入任何密码。

仓库根目录的 `.github/workflows/ci.yml` 会在 Python、Vue 和 Spring 基础回归全部通过后运行这两条 Docker 验收链路，并上传本次运行产生的 evidence。CI 会先移除仓库中的历史 evidence，避免失败运行误上传旧结果。

端口只绑定在本机回环地址。MySQL 为 `127.0.0.1:3306`，Redis 为 `127.0.0.1:6379`，RabbitMQ AMQP/管理台为 `127.0.0.1:5672/15672`；均可在 `.env` 中调整。

后端需要 Java 17 和 Maven。后端环境变量与 `infra/.env` 是两个显式边界；如果修改了 Compose 的端口、数据库名或用户，必须同步修改以下三项：

```powershell
cd examples/smart-canteen/backend
$env:SMART_CANTEEN_DB_URL='jdbc:mysql://localhost:3306/smart_canteen?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:SMART_CANTEEN_DB_USERNAME='smart_canteen'
$env:SMART_CANTEEN_DB_PASSWORD='<与 infra/.env 中 MYSQL_PASSWORD 一致>'
mvn spring-boot:run
```

前端需要 Node.js 20+：

```powershell
cd examples/smart-canteen/frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`，依次操作“提交审批”“审批通过”“生成采购计划”“模拟入库”“完成采购验收台账”。Vite 会把 `/api` 代理到 `http://localhost:8080`。

停止中间件使用 `docker compose down`。数据默认保留在命名卷中；只有明确需要清空本地业务数据时才使用 `docker compose down -v`。

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
cd ../infra
docker compose --env-file .env.example config --quiet
```

首个真实回放见 [`replay/path-parameter-feedback.json`](replay/path-parameter-feedback.json)，其修复前证据固化在 [`replay/baseline-generator-failure.json`](replay/baseline-generator-failure.json)：初版生成器不能解析参数 `$ref`，也不能正确处理 `{menuId}` 和 `Idempotency-Key`。修复必须同时通过生成器单测、自动契约测试、TypeScript 编译和候选离线判定，之后规则才写入 `SKILL.md`。
