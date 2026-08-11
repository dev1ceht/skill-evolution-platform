# 第一阶段需求追溯

| 需求 | 实现 | 接口/迁移 | 验证 | 状态 |
| --- | --- | --- | --- | --- |
| ARCH-001 | `LedgerMonitoring` 公共端口、`LedgerStore` 持久化端口、`JdbcLedgerStore` 适配器 | 模块化单体内的应用/领域/基础设施分层 | `LedgerAlertServiceTest`、HTTP 测试 | 部分实现：台账/预警已独立，菜单/采购/库存留到下一阶段 |
| API-001 | 统一 `ApiResponse`、参数校验、业务异常处理 | `POST /api/v1/ledger-cycles`、`POST /api/v1/ledger-cycles/{cycleId}/records`、`GET /api/v1/ledger-cycles/{cycleId}/alerts/current` | `LedgerCycleHttpTest` | 已实现 |
| LEDGER-001 | `LedgerScope`、`LedgerCycleRequest`、`LedgerState` | 复合键 `(school_id, canteen_id, cycle_id)` 贯穿周期、要求和预警表 | 作用域隔离 HTTP 测试（同一 cycleId 可跨作用域并存） | 已实现 |
| LEDGER-002 | 缺项快照与 `OPEN/CLEARED` 计算 | V2 迁移中的周期要求和预警状态 | 周期完成 HTTP 测试、重启测试 | 已实现 |
| LEDGER-003 | `UPDATE ... WHERE completed = FALSE`，周期定义冲突拒绝 | 周期要求复合主键 `(school_id, canteen_id, cycle_id, ledger_code)` | 重复提交测试、未知编码测试、MySQL 并发测试 | 已实现 |
| DATA-001 | Flyway V2 从 V1 增量迁移，JdbcTemplate 持久化 | `V2__add_scoped_ledger_cycles.sql` | H2 文件库应用重启测试；MySQL 测试由环境变量启用 | 已实现（MySQL 需环境） |
| TEST-001 | 需求校验脚本和四类后端测试 | `skills/smart-canteen-backend/scripts/validate_requirements.py` | 测试命令记录见 `verification.json` | 已实现 |

## 明确未纳入本阶段

认证/RBAC、Redis 缓存、RabbitMQ 事件、设备和第三方平台适配器没有伪装成已完成能力；它们保留在下一阶段的端口设计中。
