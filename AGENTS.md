# Lanveriq — repository-wide development rules for coding assistants

These are **project rules** to read before proposing architecture, choosing dependencies, editing documentation, generating code or beginning a task. Scope: this repository and all future work performed **with these project files available**.

## Mandatory: reuse first, avoid reinventing wheels

1. **Before designing any general-purpose capability, check mature existing solutions.** Look in this order: (a) supported standard library / database / OS / browser built-in; (b) official framework features or SDK; (c) actively maintained, production-used, well-reviewed open-source library; (d) small integration glue; (e) custom infrastructure **only with written justification**. The right answer is not necessarily an extra dependency if PostgreSQL, the JDK or Web Crypto already does it reliably.
2. **Always propose an existing solution before proposing custom code** for UUID generation, timestamps, retry/backoff, HTTP/TLS, ORM, database migration, validation, typed API generation, icons/components, logging/tracing, service management, secure storage or media encoding. Do not invent home-grown versions by default.
3. **Evaluate rather than collect packages**: match actual upcoming product requirements; verify latest *stable and compatible* release using official upstream docs, platforms, security/CVE history, license/redistribution, transitive footprint, offline/LAN behavior, API lifecycle and upgrade path. Prefer one maintained solution per concern; do not add unused Kafka, Redis, InfluxDB, duplicate ORM/HTTP clients or multiple UI libraries merely because another project used them.
4. **Pin and verify** accepted dependencies with Gradle Wrapper/BOM, Cargo.lock and pnpm-lock.yaml when implementation begins. Record sources and decisions in docs and an ADR for architectural changes. Never claim compiled/tested/installed without evidence.
5. **Testing is not eliminated by reputable libraries.** Do not re-test library internals; do test *our* integration, configuration, security boundaries, idempotent ACKs, transaction/queue recovery, role scope, OS compatibility and failure modes. Keep early CI **real unit tests only** and manual high-risk checks; do not invent a green CI status.
6. **Domain-specific rules still need project code:** device enrollment and authorization, disclosed collection policy, immutable event/sequence semantics, SQLite durability/gaps, role-scoped monitoring and case-authorized playback. A mature dependency does not absolve product-specific verification.

### UUID/ID decisions (canonical concrete example)

| Context | Existing mechanism | No custom generator |
|---|---|---|
| Server `devices.id` / `device_id` | PostgreSQL 18 `uuidv7()` DB default at enrollment; JPA maps `java.util.UUID`; verify DB-generated ID mapping in PostgreSQL tests | Never MAC/IP/hostname, homemade snowflake or timestamp+random concat |
| Agent `event_id`, inventory `snapshot_id`, local batch IDs | Rust **`uuid` crate** (`v7` where sortable ID matters; `v4` for plain random IDs), generate **once** and persist across retries | No hand-coded RFC bit layout/randomness |
| Browser-only ephemeral correlation key | Secure-context Web Crypto `crypto.randomUUID()` (**v4**) where needed; canonical device/event IDs still come from server/Agent | No extra NPM library solely for browser v4 |
| `device_sequence` / `inventory_revision` | Transactional monotonic counter in Agent SQLite and unique constraints at PostgreSQL | **Not** interchangeable with a UUID and never derived from wall time |

UUIDs offer *practical* uniqueness, **not mathematical zero-collision proof**, authorization, privacy protection or idempotency by themselves; enforce constraints, authentication and immutable content checks.

## Current selected architecture

- Rust Agent: Tokio, Reqwest/rustls, SQLx SQLite WAL, Serde, `uuid` crate, sysinfo, OS-managed service; OS-permitted recording via visible user session later.
- Java: Java 25, **Gradle Groovy DSL** (not Maven), Spring Boot 4.1.2, Spring Data JPA/Hibernate, PostgreSQL 18, Flyway. No direct JdbcTemplate/JdbcClient application persistence layer.
- UI: React/Vite/TypeScript + pnpm, Tailwind/shadcn/Radix, Lucide, Motion, TanStack Query/Table, Orval-generated API clients. No Next.js requirement.
- Server: Caddy and single PostgreSQL instance; Agent→server HTTPS REST, browser→server REST+WSS hints. No Redis/Kafka or public cloud.
- Respect prior notice, scoped authorizations, privacy retention and OS capture permissions; no stealth collection, keylogging or bypass.

## Workflow constraints

**Docs-first D0 gate is still pending.** Do not start application code before explicit maintainer approval. Work directly on `main`, no mandatory PR; make reversible focused commits, never force-push. Update architecture, PRD, diagram and design documents together when the choice changes. Never suggest a homegrown utility without comparing an established solution and explaining why it is insufficient.

**Further reading:** [Reuse-first policy](docs/development/reuse-first-policy.md), [ADR-0007](docs/architecture/adrs/0007-mature-ecosystem-first.md), [Device identity](docs/architecture/device-identity.md), [D0 gate](docs/governance/documentation-gate.md).
