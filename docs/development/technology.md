# Tech stack and dependency policy

**Status:** Selected design directions; no code/dependencies installed. The maintainer explicitly selected **Gradle (Groovy DSL), not Maven** on 2026-10-30. Verified stable Java stack: Java **25 LTS**, Gradle **9.8.2**, Spring Boot **4.1.2**, dependency-management plugin **1.1.7**, JaCoCo **0.8.16**. See [backend stack](backend-stack.md) and [Groovy DSL reference](backend-gradle-reference.md).

| Layer | Direction | Why |
|---|---|---|
| Endpoint daemon | Rust stable + Cargo workspace | portable, predictable footprint, native OS access |
| Async network | Tokio 1.x + reqwest | managed async IO and HTTPS |
| Endpoint embedded DB | **SQLx 0.9 + bundled SQLite WAL** | ordered durable outbox with no external DB |
| Optional desktop | Tauri 2 + React | system-tray/status/settings and OS permission UX |
| API backend | **Java 25 LTS + Spring Boot 4.1.2 + Gradle 9.8.2 Wrapper** | Groovy DSL `build.gradle`, single deployable modular monolith; no Maven build |
| Access/data | Spring Security **7.1.2** + **Spring Data JPA / Hibernate ORM** + Flyway **12.4.0** | JPA repositories for CRUD; JPA native SQL + DB transactions/constraints for event dedup; no separate JDBC client/Redis/Kafka |
| Server DB | PostgreSQL 18.x | concurrent multi-user indexed audit and transactional idempotency |
| Management UI | React 19 + Vite 8 + TypeScript | simple static LAN UI, no SSR requirement |
| JS workspace | pnpm Workspace | package manager, **not** a framework; one lockfile for dashboard, optional Tauri UI and reusable packages |
| Frontend UI, icons, animation | Tailwind CSS 4 + shadcn/ui/Radix + Lucide React + Motion | editable design system, accessible controls, one main icon and animation system; dependencies added as features need them |
| Data/table/forms | **Orval OpenAPI-generated hooks** + TanStack Query + Table, React Hook Form + Zod (as required) | scoped queries, typed forms, tables; avoid installing unused packages |
| CSS/components | Tailwind CSS 4 + accessible shared components | consistent custom enterprise UI |
| Server state | TanStack Query | request/cache ownership |
| UI state | Zustand 5 (only ephemeral layout/selection) | minimal global client state |
| Video later | OS capture APIs + tested FFmpeg, H.264 | portable segmented format |
| Proxy/deploy | Caddy + Docker Compose | HTTPS and single-host management |
| v0.1 admin live status | Spring Boot native WebSocket (WSS) + TanStack Query | scoped after-commit notification; refetch authenticated REST on connect/reconnect; see [ADR-0005](../architecture/adrs/0005-rest-plus-websocket.md) |
| Tests | cargo test, **JUnit 6.0.3 + Mockito 5.23.0 + JaCoCo 0.8.16**, Vitest | Java versions from Boot BOM; unit-only early CI |

**Backend dependency policy:** `java`, `jacoco`, Spring Boot and dependency-management Gradle plugins; webmvc, websocket, security, validation, **data-jpa (Hibernate)**, Flyway, PostgreSQL JDBC driver, Actuator, springdoc OpenAPI **3.2.0** and unit-test dependencies only. The user-supplied IoT example's Redis, Kafka, InfluxDB, geo libraries and OTel agent are **not automatically included**. See [fully justified choices](backend-stack.md).

The Rust Agent deliberately stays **HTTPS REST** for durable delivery and media segments; the browser alone maintains a WSS session in v0.1. See [WS protocol](../architecture/websocket-contract.md) and [ADR-0006](../architecture/adrs/0006-agent-rest-dashboard-ws.md).

**No Next.js requirement:** UI components and icon/animation ecosystems work with React + Vite; we do not need SSR, public SEO or an extra runtime. See [frontend design system](frontend-design-system.md), [product visuals](../visuals/README.md) and [design tokens](../visuals/STYLE_GUIDE.md).

**Reuse sources:** [ADR-0007](../architecture/adrs/0007-mature-ecosystem-first.md), [Rust Agent crate selection](rust-agent-stack.md), [frontend API generator](frontend-design-system.md). Do not implement custom TLS/HTTP transports, migration engines or DTO generators.

## Initial version and provenance discipline
Use stable upstream releases verified when implementation actually starts; do not assume any given patch is already tested on both operating systems. Pin Rust crate lockfile, Java Gradle Wrapper/BOM, Node/pnpm lockfile and container tags before a usable pilot. The Agent SQLite bundled library must include the [WAL-reset fix](https://sqlite.org/wal.html), which appeared in 3.51.3 and later or selected fixed backports. Latest official SQLite stable is 3.54.1 as of 2026-10-30; check the actual linked version.

## What carries over from Pagevela
React/Vite/TypeScript, Tailwind, component reuse, feature/domain directory boundaries, Zustand discipline, isolated persistence coordination and unit-testable pure functions. See [Pagevela reference](pagevela-reference.md).

## Not copied from Pagevela
- Browser IndexedDB/Dexie as endpoint DB (Rust SQLite selected).
- Pagevela's resume editing store, PDF libs, PWA/service-worker cache and web-only persistence IDs.
- Its large browser E2E matrix, nightly runners, releases or heavy checks during Lanveriq's early MVP.
- Bundling a Chromium via Electron (Tauri remains optional, Rust service handles always-on work).

## Dependency hygiene
Use stable maintained packages with version lockfiles, explicit licenses, security patch review and minimal features. Avoid dependency count growth without a requirement. CI may remain unit-only early; local formatter/type/build commands are development hygiene rather than GitHub gates.
