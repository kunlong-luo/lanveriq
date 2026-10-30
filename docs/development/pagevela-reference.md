# Pagevela engineering reference and transfer decisions

**Reviewed source:** [kunlong-luo/pagevela](https://github.com/kunlong-luo/pagevela), default branch as reviewed 2026-10-30. This is a *pattern study*, not a plan to copy Pagevela source or force identical dependencies.

## Facts observed
- React + Vite + TypeScript + Tailwind CSS in `package.json` and `vite.config.ts`.
- `src/components/`, `src/hooks/`, `src/lib/`, `src/store/`, `src/i18n/` separate interface, reusable behavior, services and state.
- Zustand currently owns client in-memory editor state (`src/store/useResumeStore.ts`).
- Pagevela's `docs/PERSISTENCE_ARCHITECTURE_20261026.md` documents a **single serialized active-snapshot writer**: debounced saves and immediate Ctrl/Cmd+S use one coordinator, pending operations are acknowledged after successful IndexedDB commit, and pure/injectable unit tests cover write ordering and failures.
- Pagevela `.github/workflows/ci.yml` contains quality/build checks plus full browser E2E and cross-browser jobs; this is **not** the desired early Lanveriq workflow.
- Pagevela branded UI contract promotes reuse, consistent visual interactions, keyboard accessibility and named shared widgets; Lanveriq adopts those design goals without forbidding all native UI primitives.
- Pagevela uses Vite PWA, Dexie, resume/PDF-specific modules and extensive site release automation; these are product-specific and do not transfer directly.

## Transfer matrix
| Pattern | Verdict | Lanveriq implementation |
|---|---|---|
| Feature-focused components and reusable UI primitives | Adopt | dashboard/features/{devices,activity,audit,recording,settings}, shared ui |
| React + Vite + Tailwind | Adopt | static LAN admin UI |
| TypeScript strict mode and typed API boundary | Adopt | generated/openapi-aligned API client when contract stable |
| Zustand for UI-only state | Adopt with limit | selection/modal/filter state; TanStack Query for server data |
| One ordered persistence coordinator | **Core architectural lesson** | one SQLite writer for event+cursor transactions, durable outbox |
| Separate source and derived state | Adopt | avoid parallel copies of server event state in UI store |
| Strong idempotency and compatibility IDs | Adopt | event UUID and per-device sequence; schema migrations, stable device identity |
| Unit tests on pure logic and failure cases | Adopt immediately | Rust, Java, Vitest |
| UI design system consistency and accessibility | Adopt | shared components, keyboard and status affordances |
| PWA worker / IndexedDB / Dexie | Reject for core agent | local Rust SQLite WAL |
| Browser-heavy CI/E2E sharding | Defer | unit tests only in early CI |
| PDF/export resume code | Reject | domain mismatch |

## Anti-patterns to prevent
Pagevela's persistence document flags large editor/store modules and remaining concurrent draft-history paths. Lanveriq should avoid a monolithic Tauri app or a single thousand-line global Zustand store. Define domain-owned packages and persistence gateway early. Avoid unsafe direct SQLite writes from collectors/capture modules. Centralize schemas and contract versions before accumulating installed clients.

## Validation limits
This audit is based on inspected GitHub repo files, not a full benchmark or complete dependency/security audit. It does not establish that every Pagevela implementation pattern is correct for Rust or enterprise endpoint security.
