# Component boundaries

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
Do not introduce Redis, Kafka, microservices, Kubernetes, AI or a browser PWA offline write queue unless measured requirements justify them.
