# Reuse-first engineering policy / 成熟方案优先、避免重复造轮子

**Project-wide maintainer development principle, 2026-10-30.** This rule applies to **future** Lanveriq architectural decisions, implementation discussions, dependency review, design documents and coding tasks. It supplements [ADR-0007](../architecture/adrs/0007-mature-ecosystem-first.md) and is referenced from repository-root [AGENTS.md](../../AGENTS.md) so coding assistants examining the project can follow it.

> **先查成熟方案，再写代码。优先选择标准库、官方 API、可靠的数据库能力或经过长期维护的开源依赖；不要为成熟生态已经解决的问题重新实现基础设施。** 追求正确、可维护和交付效率，不追求依赖数量。

## Mandatory decision order

1. Define precisely **what the feature must do**: correctness, volume, privilege, offline behavior, platform compatibility, performance and acceptance criteria.
2. Search in order: **language standard library / stable platform or DB feature → framework official feature / SDK → mature third-party maintained library → slim glue/adapters → bespoke implementation only if objectively required**.
3. Prefer the solution that has reliable upstream ownership, stable compatible release, clear license, security response, tests, sufficient documentation, reasonable binary/build footprint and works **without cloud** on supported Windows 11 and Pop!_OS KDE.
4. For proposed custom infrastructure, record the *existing* options evaluated, their shortcomings against specific Lanveriq requirements, and a narrow reason for unavoidable code. No bare claim like "easy enough to write ourselves."
5. Use the library's public API and official migrations/types; never fork or modify vendor internals for convenience. Keep wrappers thin and boundaries testable. Pin to **tested stable compatible versions** via Gradle BOM/Wrapper, Cargo.lock, pnpm-lock.yaml.
6. Integrate with canonical documents and diagrams; record breaking architecture decisions in ADRs. Review licensing, security advisories and update cadence before adopting a package, even if widely used.

## UUID case study (reuse, not invention)

| ID | Source / library | Rules |
|---|---|---|
| `devices.id` canonical persistent device identity | **PostgreSQL 18 built-in `uuidv7()`**, JPA Java type `UUID` | DB allocates on successful enrollment. No third-party Java UUID generator needed for this ID. Validate JPA mapping / INSERT RETURNING in a real PG18 test |
| Agent event `event_id` and snapshot/batch IDs | Maintained **Rust `uuid` crate 1.x** with `v7` and/or `v4` features, `serde` as needed | Create once before SQLite transaction and persist with event/snapshot for all retries. No manual UUID bit arithmetic |
| UI-only temporary correlation IDs | **Browser Web Crypto `crypto.randomUUID()`** from secure HTTPS context | Generates **v4**, not a server canonical key; don't introduce an npm UUID library merely for this |
| `device_sequence` and `inventory_revision` | SQLite persistent monotonic counters | These are **ordering/idempotency state**, not UUIDs; allocate in one transaction and enforce DB uniqueness |
| User-facing display name / asset tag | Editable application field | Not a unique identity; IP/MAC/hostname/serial change and are not auth credentials |

**Implementation hints (illustrative only):**
- PostgreSQL schema: `id uuid PRIMARY KEY DEFAULT uuidv7()`.
- Rust: `uuid = { version = "1", features = ["v7", "serde"] }`; `Uuid::now_v7()`. If pure random is sufficient, enable `v4` and call `Uuid::new_v4()`.
- Browser: `crypto.randomUUID()` (**v4**, secure context); for network-visible records the server or Agent must still assign canonical IDs.

**Don't promise 100% uniqueness or perfect monotonicity across different clocks.** Database uniqueness constraints, credential-bound identity, immutable event digest, PostgreSQL ACK and SQLite transactions remain required. UUIDs are identifiers, **not secrets, authentication tokens or user permission grants**.

## Where reuse is expected

| Concern | First choice | Do not build |
|---|---|---|
| Java persistence | Spring Data JPA/Hibernate + Flyway | Handwritten JDBC connection/repository framework |
| Rust embedded DB | SQLx SQLite + official migration machinery | Custom FFI, threading/transaction/migration framework |
| HTTP/TLS | Reqwest/rustls, Spring Security, Caddy | Own TLS, signature format or HTTP stack |
| API types | Springdoc OpenAPI → Orval TS/Query client | Duplicate hand-maintained DTOs/API clients |
| Frontend UI | shadcn/Radix/Lucide/Motion, TanStack | Custom base UI, data table, cache or animation engines |
| Config/validation | Jakarta Bean Validation, Serde/Zod | Custom schema/parser for common types |
| OS services / inventory | windows-service, systemd, sysinfo and approved native OS APIs | Daemon manager, raw common OS inventory parser |
| UUIDs/times | PostgreSQL `uuidv7()`, Rust `uuid`, JDK `UUID`, Web Crypto, established time libs | Timestamp concatenations / homemade RNG |

## How to review an accepted dependency

**Required evidence, not a package popularity contest:** official docs/repo links; latest verified stable version and compatibility with pinned toolchains; maintenance activity and security advisories; license and acceptable redistribution; binary/transitive cost; absence of cloud telemetry and network dependency; test/benchmark results for high-risk local interactions.

**Critical exception to “fewer tests”:** library maintainers test their own code, so **we do not duplicate low-level unit tests of their internals**. We **must** still verify:
- Our transactional event+cursor persist and all-or-nothing ACK after server commit
- Correct UUID ownership/unique constraints across process crash/retry
- TLS trust chain, credential revocation, admin RBAC and role/group scope
- Inventory changes, SQLite WAL patch/runtime, disk exhaustion and reconnect
- OS capability (Windows 11 and the actual Pop!_OS/X11-or-Wayland session), notification and capture permissions
- Scope-restricted recording playback and deletion (later milestone)

Early GitHub CI is **unit-only as previously decided**; integration/OS/manual validation remains mandatory before any real-data pilot, even if not run in early CI. A mature dependency reduces infrastructure risk **but does not prove correct product integration**.

## Applying this in future discussion

For every nontrivial implementation recommendation, explicitly say:
- **Reuse choice:** established built-in or dependency and what it replaces
- **Fit:** why it satisfies Lanveriq's scale, OS, security, privacy and lifespan
- **Version / license / maintenance:** what was verified vs still to be checked
- **What we still own:** smallest product-specific logic and necessary verification
- **Changes:** update documentation and relevant diagrams together; no unsupported claims of implemented functionality

This is now a **repository-resident development rule**, not merely a one-turn preference. Reading root [AGENTS.md](../../AGENTS.md), [CONTRIBUTING.md](../../CONTRIBUTING.md) and this policy is the reliable way to carry it into future coding-agent sessions. The repository cannot itself force every unrelated conversation to load these files.
