# Evolution state machine

## Episode and candidate flow

```text
episode.awaiting_feedback
  -> episode.feedback_received
  -> candidate.pending | candidate.staged
  -> candidate.evaluated
  -> candidate.promoted
```

- 过期或已消费 episode 不再接收反馈。
- 低置信候选保持 `pending`，不能跳过 staging/evaluation。
- `discard` 仍应保留来源和决策审计。

## Promotion transaction

```text
verify base hash
  -> create promotion_intent.prepared
  -> atomically replace Skill file
  -> finalize immutable version + active head
  -> promotion_intent.completed + audit events
```

进程恢复时：

- 文件仍是 base hash：意图可安全恢复为未发布状态。
- 文件已是 after hash 但数据库未完成：恢复旧内容并记录 recovery。
- 文件与两者都不匹配：标记 `recovery_required`，停止自动写入。

## Evaluation evidence

至少保存：

- candidate/source episode IDs
- baseline 与 staged content hash
- replay 输入与期望行为
- judge 名称及可用的 judge provenance
- baseline checks、candidate checks、improvements、regressions
- promotion/rollback version ID、父版本和 active head

结构完整性只能证明 Skill 文件仍合法；业务提升必须重放原始失败或等价行为。

