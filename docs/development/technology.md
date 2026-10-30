# Tech stack and dependency policy

**Status:** Chosen directions, not installed dependencies. Pin exact patch versions from official release sources **when development starts**; don't invent tested versions.

| Layer | Direction | Why |
|---|---|---|
| Endpoint daemon | Rust stable + Cargo workspace | portable, predictable footprint, native OS access |
| Async network | Tokio 1.x + reqwest | managed async IO and HTTPS |
| Endpoint embedded DB | rusqlite + bundled SQLite WAL | ordered durable outbox with no external DB |
| Optional desktop | Tauri 2 + React | system-tray/status/settings and OS permission UX |
| API backend | Java 25 LTS + Spring Boot 4 | maintainable modular monolith, developer expertise |
| Access/data | Spring Security + Spring Data JDBC + Flyway | security, explicit SQL schema evolution |
| Server DB | PostgreSQL 18.x | concurrent multi-user indexed audit and transactional idempotency |
| Management UI | React 19 + Vite 8 + TypeScript | simple static LAN UI, no SSR requirement |
| JS workspace | pnpm Workspace | package manager, **not** a framework; one lockfile for dashboard, optional Tauri UI and reusable packages |
| Frontend UI, icons, animation | Tailwind CSS 4 + shadcn/ui/Radix + Lucide React + Motion | editable design system, accessible controls, one main icon and animation system; dependencies added as features need them |
| Data/table/forms | TanStack Query + TanStack Table, React Hook Form + Zod (as required) | scoped queries, typed forms, tables; avoid installing unused packages |
| CSS/components | Tailwind CSS 4 + accessible shared components | consistent custom enterprise UI |
| Server state | TanStack Query | request/cache ownership |
| UI state | Zustand 5 (only ephemeral layout/selection) | minimal global client state |
| Video later | OS capture APIs + tested FFmpeg, H.264 | portable segmented format |
| Proxy/deploy | Caddy + Docker Compose | HTTPS and single-host management |
| v0.1 admin live status | Spring Boot native WebSocket (WSS) + TanStack Query | scoped after-commit notification; refetch authenticated REST on connect/reconnect; see [ADR-0005](../architecture/adrs/0005-rest-plus-websocket.md) |
| Tests | cargo test, JUnit 5, Vitest | fast unit-only CI during initial MVP |

The Rust Agent deliberately stays **HTTPS REST** for durable delivery and media segments; the browser alone maintains a WSS session in v0.1. See [WS protocol](../architecture/websocket-contract.md) and [ADR-0006](../architecture/adrs/0006-agent-rest-dashboard-ws.md).

**No Next.js requirement:** UI components and icon/animation ecosystems work with React + Vite; we do not need SSR, public SEO or an extra runtime. See [frontend design system](frontend-design-system.md), [product visuals](../visuals/README.md) and [design tokens](../visuals/STYLE_GUIDE.md).

## Initial version and provenance discipline
Use stable upstream releases verified when implementation actually starts; do not assume any given patch is already tested on both operating systems. Pin Rust crate lockfile, Java dependencies, Node/pnpm lockfile and container tags before a usable pilot. The Agent SQLite bundled library must include the [WAL-reset fix](https://sqlite.org/wal.html), which appeared in 3.51.3 and later or selected fixed backports.

## What carries over from Pagevela
React/Vite/TypeScript, Tailwind, component reuse, feature/domain directory boundaries, Zustand discipline, isolated persistence coordination and unit-testable pure functions. See [Pagevela reference](pagevela-reference.md).

## Not copied from Pagevela
- Browser IndexedDB/Dexie as endpoint DB (Rust SQLite selected).
- Pagevela's resume editing store, PDF libs, PWA/service-worker cache and web-only persistence IDs.
- Its large browser E2E matrix, nightly runners, releases or heavy checks during Lanveriq's early MVP.
- Bundling a Chromium via Electron (Tauri remains optional, Rust service handles always-on work).

## Dependency hygiene
Use stable maintained packages with version lockfiles, explicit licenses, security patch review and minimal features. Avoid dependency count growth without a requirement. CI may remain unit-only early; local formatter/type/build commands are development hygiene rather than GitHub gates.
