# 兼容变更与发布说明

状态：`active`
最后复核：2026-10-07

本页说明本次知识生命周期改造对客户端、数据与运行的影响。尚未生产部署；代码与本地证据见 [CURRENT](CURRENT.md)。原 V8 台账与 MD5 去重说明保留在 [历史兼容记录](archive/批处理兼容变更-V8.md)，不沿用其中的数据库回滚命令作为 V9/V10 操作。

## 行为与接口变化

| 接入路径 | 当前变化 | 调用方适配 |
| --- | --- | --- |
| 文件/分片/URL 上传 | 来源与持久知识 job 受理，不再默认生成 Agent 报告 | 上传响应仍是媒体；通过独立 job/来源状态等待知识，报告按需申请 |
| 本地目录导入 | 接收的视频持久化知识 job；旧 `analyze` 字段不再决定默认报告 | 不用分析台账代替入库状态；CHANGED 文件仍有先删旧限制 |
| Muku 批量接入 | 默认知识入库；显式自定义目标才提交可选报告 | 未提交报告的警告不应把已受理知识当导入失败 |
| 目录组织 | 一份 source 可有多个 placement | 来源响应新增 `placementId`；移动/移除应带当前 placement |
| 旧 location / attach 接口 | 保留主归属投影语义 | 不把它当“新增第二个引用”；新引用用 placements POST |
| 索引重建 | 构建新 generation 后发布，失败保留旧 READY | job 失败与旧知识 READY 分开显示；旧 reindex 仍同步 |
| MCP | 新增目录工具，search 支持 collectionId 与去重 | 五工具发现；目录查询显式传 spaceId；默认 search/ask 均为默认空间 |

新增 HTTP 路径：

- `GET/POST /knowledge/sources/{sourceId}/placements`。
- `PATCH/DELETE /knowledge/sources/{sourceId}/placements/{placementId}`。
- `GET /knowledge/ingest-jobs`、`POST /knowledge/ingest-jobs/sources/{sourceId}/retry`。
- `GET /knowledge/spaces/{spaceId}/catalog`。

接口沿用后端统一响应与 Bearer/owner 校验。placement 解除不删除内容；删除含引用的目录被阻止；移除最后一处引用被拒绝。目录树和来源列表仍是不同数据结构。

## 数据迁移

[V9](../server/src/main/resources/db/migration/V9__knowledge_placements_and_ingest_jobs.sql) 新建多归属表与持久 job/outbox，从未删除来源回填主归属，从 PENDING 视频来源回填任务；READY 旧来源不默认重跑。旧 FAILED 来源需显式重试，不是迁移后全部自动恢复。

[V10](../server/src/main/resources/db/migration/V10__ingest_explicit_rebuild.sql) 新增 `force_rebuild`，将重复投递与主动重建区分。来源主位置列继续保留，历史 generation 暂不清理。迁移是 Flyway 增量，不能删除已执行版本记录或直接 DROP 表伪装回滚。

## 配置与监测

知识队列默认 topic `knowledge-ingest`、group `knowledge-ingest-workers`，可用 `KNOWLEDGE_INGEST_TOPIC/GROUP` 覆盖，必须避免测试与产品队列混用。默认知识消费者与原报告消费者独立，但共享进程和提取资源。

Actuator 默认仅 health；`MANAGEMENT_EXPOSURE=health,prometheus,metrics` 显式开启指标时，匿名拒绝、普通账号拒绝、管理员可读。原业务 MVC 拦截器不能单独保护 Actuator，本次增加专用过滤器。开启暴露不等于指标公开。

## 发布与回退边界

上线前需备份 MySQL 与关键对象/Checkpoint，在目标版本副本验证 V9/V10 回填、来源/引用计数、任务与真实问答/播放；确认独立 topic/group 与 MCP 上游账号范围。已有本地测试不代替这些发布前检查。

新目录操作不再同步旧 Qdrant `spaceId/collectionId` payload，查询使用 MySQL placements 解析范围。旧后端依赖旧 payload，直接回滚二进制可能返回错误位置；需要修复/重建该投影并验证旧查询后才可回退。新增 schema 能保留，不表示旧行为一定兼容；具体回退需按发布数据制定。

本轮未部署、未执行生产迁移或回退。未实现历史向量垃圾回收、CHANGED 稳定资产替换、跨版本会话引用契约或社区主体授权；不要把局部结构验收作为完整上线批准。

## 独立评测运行（2026-10-08）

增加 `knowledge.ingest.dispatch-enabled`（默认 true）。独立快照评测设 false，禁止 outbox 调度和失联任务恢复；正常产品保持 true。它不禁止显式 HTTP 写入或消费者收取已有消息，需独立 topic/group 和快照数据。质量基准不改变当前产品查询默认行为。

## 查询范围契约（2026-10-08）

MCP `search_video_knowledge` 从数组改为 `{scope,hits,warnings}`，省略 spaceId 从最多十个空间改为后端默认空间。更新外部客户端读取 hits；需要其他空间时先发现再显式指定。ask 省略范围也由后端解析；collectionId 必须与 spaceId 同传。HTTP 新增 `/knowledge/search/details` 对象契约，旧 `/knowledge/search` 保留数组。ask/stream 增加 scope；未就绪或失败可返回 NOT_READY，客户端应显示处理状态。Web 已更新，当前 MCP 共享主体边界不变。无需数据库迁移。
