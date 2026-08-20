# Context Map

智慧食堂是本仓库的主项目，前端、后端、OpenAPI、基础设施和业务 Skill 共同服务同一个业务上下文。

## 主项目上下文

- [Smart Canteen Domain](./CONTEXT.md)：学校、食堂、食材、食谱、采购、库存、台账、预警和溯源的统一业务语言。
- [Backend](./backend)：落实领域不变量、权限范围、事务和持久化。
- [Frontend](./frontend)：承载操作人员的业务操作和状态反馈。
- [Business Skills](./skills)：菜单、采购、库存、台账、食品安全和溯源的直接业务操作入口。
- [Business SOP Skill](./skills/smart-canteen-sop/SKILL.md)：把多个业务 Skill 组合为受约束的业务闭环运行。

## 关系

- **用户/运营人员 → Agent**：以自然语言或页面操作提出业务意图、范围和必要输入。
- **Agent → Business Skill**：根据用户任务选择菜单、采购、库存、台账、食品安全或溯源 Skill，检查触发条件、角色、风险、审批和前置状态。
- **Business Skill → Backend API / Adapter**：只调用 Manifest 声明的业务端口，遵守幂等、超时、回滚和证据约束。
- **Backend → MySQL**：在学校/食堂范围内持久化业务事实、审计记录和溯源链路。
- **External Adapter → Alert/Traceability domain**：先完成认证、规范化和来源唯一性校验，再进入核心领域。

## 不属于主领域的内容

通用开发工具只作为辅助脚本存在，不拥有菜单、采购或库存业务对象；业务 Skill 运行记录也不会自动修改 Skill 或生产规则。
