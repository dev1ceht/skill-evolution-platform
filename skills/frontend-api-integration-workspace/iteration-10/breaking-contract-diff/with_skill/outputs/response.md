已完成，使用 `frontend-api-integration` Skill 处理：

- [api-ir.before.json](C:/Users/th/AppData/Local/Temp/skill-up-1282122738/api-ir.before.json)
- [api-ir.after.json](C:/Users/th/AppData/Local/Temp/skill-up-1282122738/api-ir.after.json)
- [contract-diff.json](C:/Users/th/AppData/Local/Temp/skill-up-1282122738/contract-diff.json)
- [migration-plan.md](C:/Users/th/AppData/Local/Temp/skill-up-1282122738/migration-plan.md)

`contract-diff.json` 已将 `status` 变为必填及 enum 收窄均分类为 breaking，并记录 `beforeHash`、`afterHash`。workspace 中没有现成 typed client，因此未直接更新。所有输出已通过一致性校验。
