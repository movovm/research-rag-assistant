# SalesMentor V1 能力说明

## 已实现

| 能力 | 实现边界 |
|---|---|
| 经验证据 | 抽取结果带 `evidenceQuote` 与 offset，并校验引用来自案例原文。 |
| 人工准入 | 经验按 `GENERATED → VERIFIED → PUBLISHED` 流转；人工审核不是自动优秀判定。 |
| 检索准入 | 只有 `PUBLISHED + INDEXED` 经验可检索；经验使用 Dense/BM25 加权 RRF。 |
| 产品来源 | 产品知识工具只接受白名单 synthetic source、产品类型、`PUBLISHED + INDEXED` 文档。 |
| 受控 Agent | 两只白名单只读 Tool；规则 Planner 固定选择；Runtime 串行、最多两次调用、无重试和并行。 |
| 报告归属 | `CUR-*`、`EXP-*`、`DOC-*` 分别绑定当前沟通、历史经验、产品知识，并校验 quote/offset/excerpt。 |
| 任务执行 | MySQL 任务事实源、version CAS、独立有界提交、任务级脱敏 Trace、SSE 状态快照。 |
| 超时收口 | 基于 `started_at` 的固定周期扫描，以严格 CAS 写入安全超时失败；迟到结果不能覆盖终态。 |

## 当前限制

- 复盘规划、证据汇总和报告组装是确定性 Java 逻辑，未接入 LLM；不能描述为 LangChain4j 自动 Tool Calling。
- 产品知识检索是可信 synthetic 来源上的确定性词项检索，不等同于完整产品知识库或实时商业报价。
- 引用归属校验只能证明引用来自允许的当前结果，不能证明每条建议在语义上正确、有效或提升成交率。
- Trace 目前只有任务级事件，不声称记录了每次 Tool 调用、Tool 耗时或完整中间计划。
- SSE 只提供最新状态通知，不提供 Token 流或完整历史事件回放；`Last-Event-ID` 不能恢复所有阶段。
- 执行器队列在进程内，`ACCEPTED` 不代表持久化消息投递；重启不会自动重投 `PENDING` 或自动重跑 Agent。
- 超时任务会进入 `FAILED`，但不保证底层调用已经终止；当前没有强杀线程机制。
- `DATETIME` 与 `Clock` 使用本地时间语义，部署需要保持 JVM 默认时区与数据库读写约定一致。
- 尚未实现复杂权限模型；不能描述为可直接公开部署的生产 SaaS。
- 不声称自动发现优秀经验、提高成交率或拥有真实商业报价。

## 后续扩展

- 增加任务恢复/运维接口与明确的租约或重投策略。
- 在不破坏现有归属校验的前提下，显式暴露可审计的 Planner/Runtime 观测结果，扩展 Tool 级 Trace。
- 评估统一 UTC 时间模型、连接/读取超时和资源取消策略。
- 在权限、审计、容量和数据保留策略明确后，再考虑生产化部署。
