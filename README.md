# 智慧食堂 Agent 与业务 Skill

这是一个以智慧食堂为主项目的可运行业务系统，围绕“菜单 → 采购 → 入库 → 台账/预警 → 溯源”构建前后端闭环，并将真实业务 SOP 封装为可触发、可执行、可审计的 Agent Skill。

## 项目边界

- `backend/`：Java 17 + Spring Boot 3，负责菜单、采购、库存、台账、预警、溯源、权限和审计等业务规则。
- `frontend/`：Vue 3 + TypeScript + Vite，承载运营人员的登录、审批、采购、库存和安全治理操作。
- `contracts/`：智慧食堂 OpenAPI 契约、API IR、生成客户端和契约测试。
- `infra/`：MySQL、Redis、RabbitMQ 的本地 Docker 编排和运行验收脚本。
- `skills/smart-canteen-sop/`：主业务 Skill，声明触发条件、权限、风险、审批、幂等、超时、回滚、Adapter 边界和证据要求。
- `docs/smart-canteen/`：阶段计划、需求追踪、架构决策和验证证据。
- `docs/smart-canteen/agent-runtime-execution-plan.md`：Agent / Skill 运行时的完整构建计划、MVP 切片和验收标准。
- `sop-runs/`：一次“菜单到溯源”组合流程的运行记录样例。

## 业务 Agent 如何调用 Skill

用户在页面或对话中提出“发布今天的食谱”“按已发布食谱生成采购计划”“验收入库”“处理预警”或“查询溯源”等操作意图后，Agent 按以下边界执行：

```text
用户意图
  → 选择匹配 SOP Skill
  → 校验 schoolId/canteenId、角色、审批和前置状态
  → 调用业务 API 或声明的 Adapter
  → 按幂等、事务和回滚规则执行
  → 返回业务结果、风险提示和运行证据
```

当前已沉淀的业务 SOP 包括菜单审批、采购履约、库存、台账、预警处置和食品溯源；组合流程见 [`docs/smart-canteen/sop-manifests.yaml`](docs/smart-canteen/sop-manifests.yaml)。第三方平台、明厨亮灶和晨检设备在真实合同、凭据和网络策略具备前保持 `port-only`，不会伪装成已接通。

## 本地启动

项目目前没有根目录的一键启动命令，需要按“基础设施 → 后端 → 前端”的顺序启动，建议使用三个 PowerShell 窗口。

### 1. 启动基础设施

首次启动时，在 `infra/` 下复制环境文件并替换其中的占位密码：

```powershell
cd infra
Copy-Item .env.example .env
# 编辑 .env，替换所有 replace-with-* 值
docker compose --env-file .env up -d --build --wait
```

该命令启动 MySQL、Redis 和 RabbitMQ。当前后端启动时实际依赖 MySQL；Redis 和 RabbitMQ 已纳入本地基础设施编排，但尚未接入后端运行链路。

### 2. 启动后端

在第二个窗口执行：

```powershell
cd backend
mvn spring-boot:run
```

后端启动时会读取 `src/main/resources/application.yml`，连接 MySQL，并自动执行和校验 `src/main/resources/db/migration/` 下的 Flyway 迁移。默认监听 `http://localhost:8080`。

健康检查地址：

```text
http://localhost:8080/actuator/health
```

本地管理员由 `SMART_CANTEEN_BOOTSTRAP_ADMIN_USERNAME` 和 `SMART_CANTEEN_BOOTSTRAP_ADMIN_PASSWORD` 配置；生产环境应通过部署环境变量或密钥管理系统覆盖本地默认值。

### 3. 启动前端

在第三个窗口执行：

```powershell
cd frontend
npm ci
npm run dev
```

前端地址为 `http://localhost:5173`。Vite 会把 `/api` 请求代理到 `http://localhost:8080`，因此浏览器访问前端地址即可使用后端 API。

当前默认开关中，后端鉴权开启，Agent Scheduler、Agent 高风险写入和 Assistant 后端能力关闭；需要启用时通过 `application.yml` 对应的环境变量显式配置。

> `infra/verify-stack.ps1` 只负责启动和验收基础设施，不会启动前端或后端。

## 快速验证

需要 Python 3.11+、Java 17、Maven、Node.js 20+；Docker 验收还需要 Docker Desktop 或兼容 Docker Engine。

```powershell
python -m pip install -e ".[dev]"
python -m pytest
python skills/smart-canteen-sop/scripts/validate_sop_manifest.py `
  --run sop-runs/menu-to-traceability.yaml
python skills/frontend-api-integration/scripts/normalize_openapi.py `
  contracts/smart-canteen.openapi.yaml `
  -o contracts/generated/api-ir.json
```

分别验证应用：

```powershell
cd backend
mvn --batch-mode test

cd ../frontend
npm ci
npm test
npm run build

cd ../infra
docker compose --env-file .env.example config --quiet
```

需要运行完整本地中间件验收时，先复制 `infra/.env.example` 为 `infra/.env` 并配置密码，再执行 `infra/verify-stack.ps1` 和 `infra/verify-mysql-workflow.ps1`。

## 业务 Skill 入口

使用 [`skills/smart-canteen-sop/SKILL.md`](skills/smart-canteen-sop/SKILL.md) 时，给 Agent 提供用户意图、学校/食堂范围、操作者角色以及必要的业务输入。例如：

```text
$smart-canteen-sop
请为 SCHOOL-001/CANTEEN-001 执行“发布 2026-08-17 午餐食谱”，
当前操作者是 CANTEEN_STAFF。先检查菜品、配方和审批前置条件，
只在满足审批规则时发布，并输出状态变化、幂等结果和证据路径。
```

Skill 只负责业务流程的触发、判断、执行边界与证据要求；最终写入仍由后端服务和受控 Adapter 完成。
