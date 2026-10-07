# 当前交付状态

状态：`active`
最后复核：2026-10-07

业务目标见 [agent.md](../agent.md)，结构说明见 [当前架构](CURRENT_ARCHITECTURE_ASSESSMENT.md)。本页维护当前状态；一次验收的具体数字与语料边界保留在对应报告中。

## 当前实现

当前工作区在 Java 21 / Spring Boot + Vue 上完成了知识库生命周期第一批改造。改动尚未提交、尚未生产部署；本地隔离测试服务已停止，不代表默认 9090/9091 正在运行新版。

| 能力 | 状态 | 证据及范围 |
| --- | --- | --- |
| 独立入库 job/outbox/消费与报告解耦 | 已实现，本地结构验收通过 | 上传后持久任务、MQ 到 READY、报告故障不破坏知识；真实基础设施与合成 AI |
| 一份内容、多目录引用 | 已实现，Vue/MCP 接入 | 添加/移动/移除指定引用、子目录查询、跨空间证据去重；不是跨成员共享 |
| 新 generation 发布与失败保留 | 已实现，局部故障验收 | 重建失败保留旧 READY、主动重试、查询期间版本切换；不覆盖所有文件替换 |
| Web/MCP 显式授权范围 | 已实现，本地 HTTP 验收 | owner、placement、目录子树与已发布版本过滤；无参数 search/ask 默认范围仍不同 |
| 阶段指标与管理员保护 | 已实现，本地验收 | ingest/query 计时及失败事件；指标需显式开启，无模型成本汇总 |
| 跨视频检索与单轮引用问答 | 原有能力保留 | 9 月真实技术语料记录；本次未重跑真实模型质量集 |
| BM25、reranker、Milvus 对照 | 未实现 | 当前 Qdrant dense + MySQL LIKE + RRF |
| 多轮会话、claim 级证据校验 | 未完成 | 单次 ask 与逐字引用匹配不能替代这些能力 |
| 文件 CHANGED 安全替换、旧版清理 | 未完成 | 目录扫描仍先删旧资产；旧 generation 暂保留 |
| 社区成员授权、多主体公开 MCP | 未实现 | 后端按用户所有权；所有 MCP 客户端共享一个上游账号 |

## 本次验证

2026-10-07：后端 **163**、MCP **13**、前端 **2** 个测试通过，前端生产构建通过。

- [架构验收报告](../eval/reports/architecture-smoke-20261007.json)：**33 项**检查通过；并发 8，90 次检索混合 18 次状态读取与 1 次索引重建。
- [MCP HTTP 验收报告](../eval/reports/mcp-architecture-smoke-20261007.json)：**11 项**检查通过，涵盖工具发现、目录状态、检索去重/过滤、证据和鉴权；没有在该轮调用真实模型问答。
- [Vue 截图](../eval/reports/architecture-ui-20261007.jpg)：同一视频添加/移除目录引用与混合检索；本次上传为合成字节，不能用于验证视频播放。
- [实现与验收说明](architecture/knowledge-lifecycle-20261007.md)：具体模块、迁移、运行参数和遗留。

基础设施 MySQL/Flyway、Redis、RocketMQ、Qdrant、MinIO、HTTP 为真实；提取、chunk、embedding 为可控替身。两条合成片段的延迟不能推导生产 P95、容量或真实 Recall。MQ 中断后的恢复由单测覆盖投递异常，未停止共享 MQ 做实机故障验证。

## 历史质量证据

[2026-09-28 tuned 报告](../eval/reports/eval-20260928-010627-tuned-anti-blank.json)使用 31 题，25 题可回答、6 题拒答：Top-5 至少命中一个预期来源 25/25；带预期来源引用的可回答题 17/25；拒答 6/6；文本存在性匹配 65/65；问答 P95 约 48.3 秒。

这些不是严格片段 Recall、语义正确率或全部必要视频覆盖。baseline 与 tuned 的 Golden MD5 不同，不能当作同一数据的 A/B。真实样例见 [9 月验收](acceptance/README.md)，本次改造后仍需重跑。

## 当前待补范围

工作项与关闭条件统一维护在 [MASTER_TODO.md](MASTER_TODO.md)，本页不重复维护另一套排序。生产上线前需验证 V9/V10、真实视频和问答回归、文件替换边界、MCP 账号范围与回退方案，见 [兼容与发布说明](BREAKING-CHANGES.md)。
