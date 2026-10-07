# VideoKB 文档入口

状态：`active`
最后复核：2026-10-07

本目录是 Java/Vue 工程的文档入口。先读 [当前交付状态](CURRENT.md)，再按问题进入对应文档。仓库外的项目笔记保留为研究与设计参考，当前产品规格不能依赖仓库外文件才能读完整。

## 事实归属与阅读顺序

| 要回答的问题 | 唯一活动位置 | 用途 |
| --- | --- | --- |
| 为谁解决什么、怎样才算完成 | [agent.md](../agent.md) | 业务场景、BR 要求、验收标准与不变量 |
| 现在交付到哪里、证据在哪里 | [CURRENT.md](CURRENT.md) | 当前状态和验证边界 |
| 还有哪些工作要推进 | [MASTER_TODO.md](MASTER_TODO.md) | 活跃事项与关闭条件；不包含历史流水账 |
| 后端怎样运转、数据如何组织 | [当前后端架构](CURRENT_ARCHITECTURE_ASSESSMENT.md) | 内容、目录、任务、版本、查询和 MCP 的现行关系 |
| 长期要补齐哪些能力 | [目标架构](VIDEO_KB_TARGET_ARCHITECTURE.md) | 候选设计；不能据此认定代码已实现 |
| 怎样体验和接入 | [体验 SOP](EXPERIENCE_SOP.md)、[MCP SOP](MCP_SOP.md) | 当前操作路径与验收步骤 |
| 怎样升级、哪些行为改变 | [兼容与发布说明](BREAKING-CHANGES.md) | V9/V10、客户端变化与回退限制 |
| 本次改造具体做了什么 | [架构证据索引](architecture/README.md) | 实现报告、测试和隔离验收 |
| AI 修改后必须维护什么 | [文档协作规范](governance/文档协作规范.md)、[AGENTS.md](../AGENTS.md) | 同步规则与事实口径 |
| 怎样规划后续 Git 分支与 PR | [工程交付计划](governance/Git分支与工程交付计划-2026-10-07.md) | 已批准的依赖顺序、分支范围与合并门槛 |

## 文档分区

| 分区或文档 | 状态 | 说明 |
| --- | --- | --- |
| [architecture/](architecture/README.md) | `active` | 结构改造报告与代码/验收证据 |
| [governance/](governance/README.md) | `active` | 文档职责、状态与更新规则 |
| [archive/](archive/README.md) | `archived` | 被当前说明取代的业务、架构、SOP 与兼容历史 |
| [acceptance/](acceptance/README.md) | `reference` | 9 月真实语料与页面验收，保留当时范围 |
| [CAPABILITY_AUDIT.md](CAPABILITY_AUDIT.md) | `reference` | 9 月能力核查快照 |
| [knowledge-service-v1.md](knowledge-service-v1.md) | `reference` | 早期资产组织模型与接口记录 |
| [interview-qa-v2.md](interview-qa-v2.md) | `reference` | 旧面试材料，不作为实现事实源 |
| [interview-qa-six-pillars.md](interview-qa-six-pillars.md) | `reference` | 旧能力表达，不作为产品规格 |

## 待分拣池

这里记录尚无批准范围或验收标准的方向，不等于已排期：

| 来源 | 方向 | 分拣需要的证据 |
| --- | --- | --- |
| 社区学习场景讨论 | 学生之间分享课程、公共知识空间 | 实际共享对象、成员权限、资料来源和撤销规则 |
| 学习助手开放需求 | 社区多主体 MCP、配额与外部客户端管理 | 客户端与用户绑定、可见空间、审计要求 |
| 长课程目标设计 | 约 20 小时单文件、直传对象存储 | 真实文件、上传大小、处理时长与恢复要求 |
| 检索增强方向 | VLM、知识图谱、复杂 Agent 协作 | 现有检索无法解决的真实案例与对照收益 |

Milvus、BM25 和 reranker 的检索对照已有业务验收方向，见 [活跃队列](MASTER_TODO.md)；没有证据不直接切换默认库。学生问题可以用来设计合成测试，但合成问题必须标注来源，不能称为真实用户反馈。
