# VideoKB

**把课程视频、社区分享和学习笔记整理成可检索、可提问、可回看原片的知识库。**

Java 21 / Spring Boot 后端 + Vue 工作台。当前支持跨视频检索与单轮引用问答，并已在工作区完成独立入库、多目录引用和安全索引重建；完整多轮会话与社区成员共享仍待建设。

## 先读什么

- [文档入口](docs/README.md)：全部活动文档、历史材料与职责。
- [agent.md](agent.md)：业务场景、BR-01 至 BR-08 和验收契约。
- [当前交付状态](docs/CURRENT.md)：代码、测试、真实/合成验收及遗留。
- [当前后端架构](docs/CURRENT_ARCHITECTURE_ASSESSMENT.md)：上传、向量化、更新、目录和 MCP 的实际流程。
- [活跃队列](docs/MASTER_TODO.md)：剩余工作与关闭条件。

## 当前怎样工作

视频上传 → 持久知识入库任务 → RocketMQ 独立消费者 → 共享 ASR/OCR 与 Checkpoint → 分段和 embedding → Qdrant 写入 → 发布 READY 版本。

同一视频可同时归入“前端学习”和“后端学习”，共享内容与索引。目录操作修改引用；重建构造新版本，失败保留旧的已发布知识。Agent 报告由用户按需启动，不决定知识是否就绪。

检索使用 **Qdrant dense + MySQL LIKE + RRF**，按当前用户、目录归属和已发布版本过滤。BM25、reranker 和 Milvus 迁移尚未实现。固定时间窗与摘要聚合不能称为已完成的语义分块；逐字引用检查也不能证明每条结论正确。

MCP 提供五个只读工具：`list_knowledge_spaces`、`get_knowledge_catalog`、`search_video_knowledge`、`ask_video_knowledge`、`get_video_evidence`。客户端共用配置的上游账号，当前是个人/服务账号适配器；多人社区开放需要补主体绑定与空间授权。

## 技术栈

| 层次 | 现行实现 |
| --- | --- |
| 前端 | Vue 3、Vite、SSE、Marked |
| 后端 | Java 21、Spring Boot、MyBatis-Plus、LangChain4j |
| 任务与缓存 | RocketMQ、Redis、Redisson |
| 数据与媒体 | MySQL、MinIO |
| 检索 | Qdrant、dense embedding、MySQL LIKE、RRF |
| 视频提取 | FFmpeg、Tesseract、本项目配置的 ASR/文本/embedding provider |
| 监测 | Actuator、Micrometer、Prometheus；指标显式开启并要求管理员身份 |

模型与服务参数以 `.env.example` 及服务配置为准。Python 仅保留评测、验收和数据脚本，产品服务使用 Java。

## 快速开始

准备 JDK 21、Node、Docker Compose、FFmpeg 和 Tesseract，按 `.env.example` 配置环境后执行：

```bash
./scripts/dev-up.sh
```

默认访问前端 `http://localhost:5173`，后端 `9090`，MCP `9091`；以实际启动输出为准。没有配置 `MCP_CLIENT_TOKENS` 时启动脚本跳过 MCP。

1. 注册登录并上传视频，在知识库观察独立入库状态，等待 READY。
2. 使用“同时归入”整理多个主题；“移除此处引用”只解除当前归属。
3. “仅搜证据”查看命中片段；“提问”查看回答与引用，点击时间戳回看原片。
4. 需要单视频报告时，在视频工作台主动启动 Video Agent。
5. 接入外部助手时按 [MCP SOP](docs/MCP_SOP.md) 配置客户端令牌。

本地目录扫描、脚本/转写导入、Muku 批量链接接入仍使用已有入口。Muku CLI 需在 Java 运行环境可用，并配置 `MUKU_PATH/MUKU_WORK_DIR`。本地 CHANGED 文件尚未完成保留旧资产的安全替换，详见 [兼容说明](docs/BREAKING-CHANGES.md)。

## 验收与发布范围

本次架构检查使用真实基础设施与可控 AI 替身，报告、测试数量及历史真实语料结果统一见 [CURRENT](docs/CURRENT.md)。不把合成延迟当生产性能，不把来源命中当完整问答正确率。

当前生命周期改造仍在工作区，尚未生产部署。发布前必须按 [兼容与发布说明](docs/BREAKING-CHANGES.md) 验证迁移、真实问答/播放与回退边界。完整体验路径见 [体验 SOP](docs/EXPERIENCE_SOP.md)。

## License

[MIT](LICENSE)

浏览器扩展的安装与使用见 [扩展说明](extension/README.md)。
