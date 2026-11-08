# Component boundaries

**当前组件真实性（2026-11-08）：** 表格是**领域职责划分**，不意味着每行都已有独立 crate/package 或已可运行。当前实际仓库是 `apps/agent` 单 crate、`apps/server` 一个 Gradle 模块、`apps/web-next` Next.js 主线；`agent-audit`、`server-media`、录制和正式 `server-ws` 等仍是未来业务域。Agent→Caddy→Spring 应复用受审 mTLS/REST/SQLx，Next.js 走受限 REST + 非权威 WSS。见 [设备协议](device-communication-stack.md)、[组件/技术现状](../development/technology.md)。


| Component | Responsibilities | Must NOT |
|---|---|---|
| agent-core (Rust) | device identity, heartbeat scheduling, config, lifecycle | capture private content |
| agent-audit (Rust) | platform allowlisted event adapters, filtering/redaction | bypass OS permissions |
| agent-store (Rust) | dedicated SQLite writer, migration, ordered durable outbox | rely on network filesystem |
| agent-transport (Rust) | authenticated upload, retry, schema versioning | mark sent without durable ack |
| desktop (Tauri, optional) | visible notice, enrollment/settings, recording status | install hidden monitoring |
| capture (optional) | explicit user-session OS-authorized recording, health | secretly record locked/protected screens |
| server-identity (Java) | admins, roles, enrollment, device credentials | trust browser role claims |
| server-devices (Java) | inventory, heartbeat status | interpret absence as user inactivity |
| server-events (Java) | ingest, validation, deduplication, queries | accept arbitrary event payloads |
| server-media (Java) | upload manifests, authorization, playback | expose raw NAS URL |
| server-policy (Java) | collection allowlists, retention, approval | silently expand collection scope |
| server-ws (Java, in-process) | Upgrade authentication, scoped subscriptions, post-commit small notifications, bounded queues | provide a durable ACK channel, send raw employee activity/video |
| dashboard (React) | UI state and queries | enforce RBAC only in browser |

## Dependency rules
- Platform adapters implement contracts from core but do not import UI packages.
- Agent-store uses a **single-connection SQLx SQLite write pool** with SQLx migrations instead of a custom blocking SQLite driver; it is the **sole** SQLite write gateway. This borrows Pagevela's ordered persistence-coordinator principle and avoids competing writers.
- Server controllers call domain services; services own `@Transactional` units; repositories use **Spring Data JPA/Hibernate** for bounded CRUD and parameter-bound **JPA native SQL** for idempotent event batches, inventory revisions and scoped aggregate queries. All remain inside the same persistence/transaction layer—**no separate JdbcClient or JdbcTemplate repositories**. Flyway owns PostgreSQL schema changes, Hibernate validates mappings and disables Open Session in View. See [backend stack](../development/backend-stack.md).
- UI feature pages depend on typed REST API clients; Zustand stores ephemeral selection/layout, TanStack Query owns server state. WSS carries only scoped notification hints to invalidate/refetch TanStack Query. The dashboard never treats WSS messages as source of truth.

## Concurrency and performance
Rust async network work uses Tokio; SQLx SQLite handles its blocking work internally and exposes async transactions with serialized writes (pool max_connections=1); no custom blocking thread. Filesystem video upload works on **closed** segments. Client and server retry safely with immutable event IDs; backpressure is explicit.

**Visual:** [component diagram](../diagrams/component-architecture.md). **Contract:** [dashboard WSS](websocket-contract.md).

## Future evolution
Maintainer amendment (2026-11-01): allow an **optional, default-OFF Redis 8** integration via Spring Data Redis/Lettuce for ephemeral TTL-only refresh hints and health against the existing `spark-redis`. **Do not** make it the authority for sessions, RBAC, device status, audit, ACK, distributed locks or WebSocket delivery. Do not introduce Kafka, microservices, Kubernetes, AI or a browser PWA offline write queue unless measured requirements justify them.
