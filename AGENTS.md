# Project working rules

## Product implementation direction

- `DOVideo-AI` is the sole active product engineering project. Its product backend is Java 21 / Spring Boot and its web client is Vue.
- Do not move product implementation to Python or Node.js because the user has used those languages in other projects. Follow the explicit Java backend direction for this project.
- `https://github.com/qianzhu18/VideoKB` is the existing Python 3.11 / FastAPI project that the user wants converted to Java. Preserve the project and its capabilities; do not treat it as archive-only, discard it, or continue building product features in Python.
- The current GitHub archived/read-only flag is repository metadata, not the user's desired project outcome. The Java migration target location must follow the user's explicit direction; when not specified, ask whether the port belongs in `DOVideo-AI` or in the `VideoKB` repository itself.
- Python files may remain only as evaluation/data assets during migration. Port product capabilities to Java rather than extending the Python implementation.
- When a README, TODO, or report claims a capability is complete, verify it in source code and distinguish code presence from tests, real-data evaluation, and end-to-end acceptance.
- Treat Qdrant as the current transitional vector store. The user selected Milvus as the preferred future stack on 2026-10-09; prioritize Milvus for new vector-backend design and experiments. Cutover still requires dual-write, backfill, shadow-query, authorization, recovery, and benchmark gates; do not describe a planned migration as implemented.

## Knowledge-base acceptance

- A backend endpoint is not a completed user-facing feature until the Vue workflow calls it and displays the answer, source, timestamp, evidence, and refusal state as applicable.
- Retrieval quality claims must name the corpus, metric, and evaluation artifact. The existing 30-case set and Python runner are evaluation assets, not proof of real-corpus readiness.
- Do not claim local-folder ingestion, MCP, or real-video processing is accepted based only on unit tests or documentation; require the corresponding end-to-end evidence.

## Documentation contract

- Read `agent.md` for product requirements, `docs/CURRENT.md` for delivery status, `docs/MASTER_TODO.md` for remaining work, and `docs/README.md` for navigation before changing knowledge-base behavior.
- `agent.md` owns business requirements and acceptance criteria; it is different from this `AGENTS.md`, which owns agent working rules. Do not use interview notes, resume targets, or proposed architecture as proof of implementation.
- Keep active engineering and product documents self-contained inside this repository. Workspace notes outside the checkout are reference material, not mandatory specifications for repository readers.
- A behavior change is not complete until its corresponding BR status, current status, active work item, API/user instructions, migration/compatibility notes, and evidence links are updated as applicable. Follow `docs/governance/文档协作规范.md`.
- Describe the current content/placement, durable ingest job/outbox, generation publication, and query authorization boundaries accurately. Do not reintroduce report-dependent ingest or delete-first index rebuild descriptions as current behavior.
- State Qdrant dense + MySQL LIKE + RRF as the current retrieval implementation. Do not claim BM25, reranking, full vector-store replaceability, multi-turn conversations, or community member authorization until implemented and verified.
- MCP clients currently share one configured upstream account. Directory discovery and multiple client bearer tokens do not constitute independent community-user authorization. Document default search/ask scope differences until resolved.
- Separate source code, unit tests, deterministic AI on real infrastructure, real media/model evaluation, production deployment, and user research. Synthetic student scenarios cannot be reported as real interviews or observed community demand.
- Preserve historical records with their dates and replacement links under `docs/archive/`; keep current queues to at most ten actionable items. Do not discard user edits during documentation cleanup.
- Before finishing, check Markdown links/anchors and inspect current entry points for stale statements. Documentation-only changes require link and consistency checks, not an unrelated model evaluation run.
