# ADR-0007: Mature maintained ecosystem components before bespoke infrastructure

**Date:** 2026-10-30. **Status:** accepted reuse-first policy after D0 approval; **frontend framework clause updated 2026-11-08 under [ADR-0008](0008-nextjs-app-router-console.md)**. **Scope:** frontend, Rust Agent and dependency governance. **Supersedes** ADR-0002's choice of the `rusqlite` blocking worker but **not** its PostgreSQL-server + SQLite-WAL-endpoint architecture.

## Problem
Lanveriq is a LAN-only, 1–10 managed-device product, not an exercise in implementing OS libraries, API type generators, websocket reconnection, TLS or ORM internals. Earlier documents included duplicate handwritten responsibilities and framework mismatches (notably a frontend document mentioning Maven after Gradle had been selected).

## Decision

**Maintainer mandate:** This reuse-first ordering is a persistent **project engineering rule**, not just a suggestion for the current task: **stable standard/platform/DB built-in → official SDK/framework → maintained third-party library → minimal glue → custom solution only with a documented gap analysis**. See [project policy](../../development/reuse-first-policy.md) and root [AGENTS.md](../../../AGENTS.md). No wheel-reinvention by default, but no dependency bloat without a concrete need.

1. **Reuse before build:** search official libraries/references; prioritize projects with active releases, stable APIs, OS compatibility, a suitable license, security advisory process and predictable transitive dependencies. We own product behavior, NOT a custom HTTP/TLS implementation, database adapter, schema generator, icon engine or filesystem-level service manager.
2. **Frontend (superseded framework choice):** Current canonical UI is **Next.js 16.4.5 App Router + React 19 / TypeScript 7 / pnpm 12 / Tailwind 4 / Base UI React + source-owned shadcn-style components**; legacy Vite/Radix entries are 2026-10-30 historical candidates, **not current installation requirements**. Use Lucide and TanStack Query already pinned; TanStack Table, forms, Sonner and Motion are future options **only when needed**. Springdoc→Orval remains the generated typed Console REST contract; do not hand-maintain duplicate DTOs. See [ADR-0008](0008-nextjs-app-router-console.md) and actual [manifest](../../../apps/web-next/package.json).
3. **Rust Agent:** prefer **SQLx 0.9 SQLite** over writing a `rusqlite` async blocking worker; SQLx migrations, bundled SQLite, one-connection write pool and tested transaction invariants. Use Reqwest/rustls, Tokio, Serde, sysinfo/os_info, windows-service, systemd/sd-notify and tracing rather than implementing replacements. Validate actual SQLite linked version (official 3.54.1 stable on decision date, WAL fix >=3.51.3).
4. **Java server:** keep maintainer's Gradle + Spring Boot + Spring Data JPA/Hibernate + Flyway choice; use JPA native PostgreSQL statements only where required for correctness, not a parallel JdbcClient layer.
5. **Later capture:** OS-approved user-session permissions, Tauri official plugins and existing video tools; no custom capture/codec/permission-bypass stack. Real screen streaming is not currently scheduled.
6. **Avoid replacing** the entire product with osquery/Fleet/Wazuh merely because they have more features: any full platform must meet LAN-only, prior notice, no arbitrary remote commands, restricted event sources, local server footprint, licensing and recording-approval requirements. Evaluate only when a demonstrated need warrants its deployment.

## Concrete UUID example: do not invent an ID generator
- **Device ID:** PostgreSQL 18's built-in `uuidv7()` generates `devices.id`; Java JPA maps it to `java.util.UUID` after a real DB mapping check. No special UUID library is necessary in the Java server for this primary key.
- **Rust event/snapshot ID:** maintained `uuid` crate with `v7` or `v4`; create once and persist the generated ID for retries.
- **Browser-only ephemeral ID:** secure-context `crypto.randomUUID()` (v4), never a substitute for server-owned IDs.
- **Sequence/revisions:** persist separate SQLite counters and enforce PostgreSQL unique constraints. UUIDs are not credentials or a zero-collision promise. [UUID policy](../../development/reuse-first-policy.md).

## Boundary: what still needs domain code?
Unique `device_id`, enrollment/mTLS, exact event delivery ACK, collection policy/scope, health versus source gap, inventory revision, RBAC, authorized recording review and retention are **product obligations**. No library supplies these as a single secure, compatible contract. Use library primitives; **do not re-test vendored algorithms**, but keep our boundary and integration/failure-path tests; do not claim a library removes the need for relevant validation.

## Dependency admission checklist
- Is a feature actually in the next milestone? Is the upstream release **stable** (not latest beta) and active?
- Are APIs, licenses, source/SBOM, CVE disclosure and transitive dep paths reviewed? Is commercial redistribution allowed?
- Does it build on Java 25, Rust stable/Windows 11/Pop!_OS KDE, React 19 and supported browser versions?
- Are defaults safe? No hidden capture, no disabled TLS, no network-dependent telemetry, no uncontrolled auto updates.
- Can version be pinned (Gradle BOM/Wrapper, Cargo.lock, pnpm-lock.yaml) and upgraded by documented review?
- Does it improve maintainability **without violating existing data durability/transaction or permission invariants**?

**Current delivery (2026-11-08):** JDK/Gradle, Rust/Cargo and Next.js/Node dependencies are installed and isolated CI has executed; **not** a production data plane. The Agent runtime stays idle; default-OFF backend/device flows and disconnected frontend are source-level/provisional. Latest authority: [v0.1 batches](../../planning/v0.1-batched-delivery-plan-2026-11-08.md) and [device transport stack](../device-communication-stack.md). [Frontend design](../../development/frontend-design-system.md) · [Rust stack](../../development/rust-agent-stack.md) · [Backend stack](../../development/backend-stack.md).
