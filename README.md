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

本地管理员由 `BOOTSTRAP_ADMIN_USERNAME` 和 `BOOTSTRAP_ADMIN_PASSWORD` 配置；生产环境应通过部署环境变量或密钥管理系统覆盖本地默认值。

### 3. 启动前端

在第三个窗口执行：

```powershell
cd frontend
npm ci
npm run dev
```

前端地址为 `http://localhost:5173`。Vite 会把 `/api` 请求代理到 `http://localhost:8080`，因此浏览器访问前端地址即可使用后端 API。

### 导入智慧食堂学习数据

项目提供了一批可重复加载的本地研究数据，覆盖菜单、菜品、配方、库存批次、供应商、采购计划、预警和追溯链路。数据定义见 [`data/study/smart-canteen-study-dataset.yaml`](data/study/smart-canteen-study-dataset.yaml)。

确认 MySQL 已启动且后端 Flyway 已完成迁移后，在仓库根目录执行：

```powershell
.\scripts\load-study-dataset.ps1
```

脚本默认读取 `infra/.env`；首次使用时先按上文复制 `infra/.env.example` 并配置数据库连接信息。

当前默认开关中，后端鉴权开启，Agent Scheduler、Agent 高风险写入和 Assistant 后端能力关闭；需要启用时通过 `application.yml` 对应的环境变量显式配置。

SC-003 已加入 AgentScope Java 2.0 HarnessAgent 的可选意图解析通道，默认仍使用现有的
`deepseek-http` 适配器。角色上下文、启用方式和当前边界见
[`docs/smart-canteen/agentscope-runtime.md`](docs/smart-canteen/agentscope-runtime.md)。

SC-004 已接入员工/学生菜单只读查询。助手请求会沿用服务端学校/食堂范围，通过
`menu.query` Tool 查询业务服务；按 `menuId` 查询返回单个菜单，按“今天”或 `YYYY-MM-DD`
查询返回已发布菜单列表，草稿和审批中的菜单不会作为公开结果返回。示例消息：

```text
今天有什么菜？
查询 2026-08-17 午餐菜单
请查询 M001 的菜单
```

SC-005 已接入运营人员/管理者的库存只读查询。`inventory.query` 通过现有库存业务服务
返回库存数量、单位、预警阈值和确定性的 `warning` 标记；“查询库存”和“哪些食材库存不足”
不会创建采购、入库或出库动作。员工/学生角色不拥有 `INVENTORY_READ` 权限。

SC-006 已接入菜单原料缺口只读分析。`procurement.gap.query` 根据指定日期的已发布菜单、
菜品 Recipe/BOM、库存快照和未完成采购快照，返回每种原料的需求、库存、在途和缺口；“检查
明天的菜单有没有原材料不足”不会创建采购计划。缺口计算由 `ProcurementPlanService` 确定性
完成，模型只负责理解请求和解释结果；员工/学生角色不拥有 `PROCUREMENT_ANALYSIS_READ` 权限。

SC-007 已接入运营人员/管理者的客流预测与备餐建议只读分析。`traffic.forecast.query` 读取
`traffic_forecasts` 中版本化的学习数据事实；`meal_plan.query` 再按已发布菜单的
`estimatedQuantity` 比例，以最大余数法分配预测人数。没有预测事实或已发布菜单时返回不可用原因，
不会让模型猜测人数，也不会创建备餐计划、采购计划或库存写入；员工/学生角色不拥有
`TRAFFIC_FORECAST_READ` 和 `MEAL_PLAN_ANALYSIS_READ` 权限。示例消息：

```text
查询 2026-08-22 午餐预计有多少人用餐
分析 2026-08-22 午餐备餐应该准备多少份
```

> `infra/verify-stack.ps1` 只负责启动和验收基础设施，不会启动前端或后端。

## 快速验证

需要 Python 3.11+、Java 17、Maven、Node.js 20+；Docker 验收还需要 Docker Desktop 或兼容 Docker Engine。

```powershell
python -m pip install -e ".[dev]"
python -m pytest
python skills/smart-canteen-sop/scripts/validate_sop_manifest.py `
  --run sop-runs/menu-to-traceability.yaml
python skills/canteen-order/scripts/generate_order.py --help
python skills/canteen-order/scripts/batch_order.py --help
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
