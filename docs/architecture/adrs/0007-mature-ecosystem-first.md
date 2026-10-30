# ADR-0007: Mature maintained ecosystem components before bespoke infrastructure

**Date:** 2026-10-30. **Status:** selected technical direction / awaiting overall D0 approval. **Scope:** frontend, Rust Agent and dependency governance. **Supersedes** ADR-0002's choice of the `rusqlite` blocking worker but **not** its PostgreSQL-server + SQLite-WAL-endpoint architecture.

## Problem
Lanveriq is a LAN-only, 1–10 managed-device product, not an exercise in implementing OS libraries, API type generators, websocket reconnection, TLS or ORM internals. Earlier documents included duplicate handwritten responsibilities and framework mismatches (notably a frontend document mentioning Maven after Gradle had been selected).

## Decision

1. **Reuse before build:** search official libraries/references; prioritize projects with active releases, stable APIs, OS compatibility, a suitable license, security advisory process and predictable transitive dependencies. We own product behavior, NOT a custom HTTP/TLS implementation, database adapter, schema generator, icon engine or filesystem-level service manager.
2. **Frontend:** keep React 19 / Vite 8 / TypeScript / pnpm. Use Tailwind 4 + shadcn/ui/Radix for UI, Lucide for icons, Motion for animation, TanStack Query for server cache, TanStack Table for device/audit grids, React Hook Form + Zod for forms, Sonner for notices. Adopt **Orval** from Springdoc OpenAPI to generate typed REST client and Query Hooks; **don't independently hand-maintain request DTOs and duplicated API wrappers**. Do not change to Next.js solely for component availability.
3. **Rust Agent:** prefer **SQLx 0.9 SQLite** over writing a `rusqlite` async blocking worker; SQLx migrations, bundled SQLite, one-connection write pool and tested transaction invariants. Use Reqwest/rustls, Tokio, Serde, sysinfo/os_info, windows-service, systemd/sd-notify and tracing rather than implementing replacements. Validate actual SQLite linked version (official 3.54.1 stable on decision date, WAL fix >=3.51.3).
4. **Java server:** keep maintainer's Gradle + Spring Boot + Spring Data JPA/Hibernate + Flyway choice; use JPA native PostgreSQL statements only where required for correctness, not a parallel JdbcClient layer.
5. **Later capture:** OS-approved user-session permissions, Tauri official plugins and existing video tools; no custom capture/codec/permission-bypass stack. Real screen streaming is not currently scheduled.
6. **Avoid replacing** the entire product with osquery/Fleet/Wazuh merely because they have more features: any full platform must meet LAN-only, prior notice, no arbitrary remote commands, restricted event sources, local server footprint, licensing and recording-approval requirements. Evaluate only when a demonstrated need warrants its deployment.

## Boundary: what still needs domain code?
Unique `device_id`, enrollment/mTLS, exact event delivery ACK, collection policy/scope, health versus source gap, inventory revision, RBAC, authorized recording review and retention are **product obligations**. No library supplies these as a single secure, compatible contract. Use library primitives; do not claim they remove the need for explicit tests.

## Dependency admission checklist
- Is a feature actually in the next milestone? Is the upstream release **stable** (not latest beta) and active?
- Are APIs, licenses, source/SBOM, CVE disclosure and transitive dep paths reviewed? Is commercial redistribution allowed?
- Does it build on Java 25, Rust stable/Windows 11/Pop!_OS KDE, React 19 and supported browser versions?
- Are defaults safe? No hidden capture, no disabled TLS, no network-dependent telemetry, no uncontrolled auto updates.
- Can version be pinned (Gradle BOM/Wrapper, Cargo.lock, pnpm-lock.yaml) and upgraded by documented review?
- Does it improve maintainability **without violating existing data durability/transaction or permission invariants**?

**Current delivery:** documentation only. No dependencies installed, no binaries built, no OS/browser execution. [Frontend design](../../development/frontend-design-system.md) · [Rust stack](../../development/rust-agent-stack.md) · [Backend stack](../../development/backend-stack.md).
