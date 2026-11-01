# Frontend design system and dependency selection — documentation only

**Status (2026-11-01):** A synthetic-only React/Vite preview has been implemented at `apps/web` and built in [Web CI #41274343864](https://github.com/kunlong-luo/lanveriq/actions/runs/41274343864), with working demo routes, search/filter and Radix Tabs. The frontend is **not connected to real Spring Boot APIs, Orval hooks, session, WSS or real device data**; production approval remains open. Product visuals are in [visual gallery](../visuals/README.md) and standardized by [STYLE_GUIDE](../visuals/STYLE_GUIDE.md). Visual drawings do not replace real components.

**V0.1 backend contract milestone:** The Spring Boot device-read controller now has a synthetic-only Springdoc OpenAPI generation and strict CI validation path. [Java CI #41190792718](https://github.com/kunlong-luo/lanveriq/actions/runs/41190792718) has **actually passed** Springdoc generation, JSON drift validation and contract artifact upload; that versioned `openapi.json` is suitable for a future Orval trial without opening the backend's docs endpoint. This is still not a completed React project or a production-accepted API; real PostgreSQL, HTTPS and cookie/session acceptance are separate.

**MON-01-OVERVIEW backend slice:** the planned monitoring-count endpoint now has a default-OFF controller, separate security chain, Postgres aggregate and a generated-OpenAPI contract test. A passing CI artifact is prerequisite for Orval use. No React code or real device data is present; this does not clear the manual database/TLS release gates.

**Source-generated frontend-client readiness check (2026-10-31):** A standalone `apps/server/contract-client-check` directory has exact versions and a checked-in pnpm lockfile for Orval 8 Fetch/TanStack Query, React 19 and TypeScript 7. The full frozen-install Java 25 pipeline [#41199936869](https://github.com/kunlong-luo/lanveriq/actions/runs/41199936869) has now passed: the source-generated three-route OpenAPI document produced real Orval Fetch/TanStack Query hooks and `tsc --noEmit` accepted them. Both the OpenAPI and the candidate client were uploaded as non-deployed workflow artifacts. This is **only a build verification harness** and does not create `apps/web`, React pages or live API access. When implementation starts, adopt the generated output instead of maintaining hand-written response types.

**Session bootstrap API readiness (source stage):** To avoid the React client guessing whether a stored Cookie still authenticates, the backend adds an opt-in `GET /api/v1/auth/session` with a minimal `login` response (no bearer tokens or permissions). The Springdoc-to-Orval gate is extended to include it. MockMvc tests simulate the Spring Security login/CSRF/cookie lifecycle, but actual browser, TLS and DB validation remains pending. [Java 25 CI #41261183351](https://github.com/kunlong-luo/lanveriq/actions/runs/41261183351) **passed** the full CSRF/login/session/authorization/logout MockMvc scenario, generated the four-route OpenAPI and typechecked the new Orval hooks. This remains a backend-only milestone; no frontend application has been created.

**Backend auth client contract preparation:** Real Spring Security form login and logout are now described through a conditional Springdoc customizer, and the real CSRF Controller exposes a typed response, allowing Orval to generate authenticated API mutations rather than writing bespoke login API calls in React. Neither production auth nor the frontend is enabled. [CI #41262871704](https://github.com/kunlong-luo/lanveriq/actions/runs/41262871704) passed the complete backend Springdoc/Orval/TypeScript gate for five GET operations plus login/logout POST mutations. Actual real browser/Caddy/TLS/PostgreSQL gates remain open.

## Stack
- **pnpm Workspace** for JS/TS dependencies in a monorepo; Cargo owns Rust; **Gradle** owns Java. pnpm is a package manager, **not** an application framework.
- **React 19 + Vite 8 + TypeScript**, React Router for the LAN Dashboard. A Next.js SSR server is unnecessary. These UI libraries are not exclusive to Next.js.
- **Tailwind CSS 4 + shadcn/ui + Radix UI** for accessible editable components; retain a modest shared UI package only once both Dashboard and Tauri require it.
- **Lucide React** as primary stroke-based UI icon library; use explicitly approved/logos where necessary. Avoid installing multiple overlapping icon packs.
- **Motion** as the single intentional animation library; CSS transitions for simple hover/focus, Motion for dialogs or route transitions. Respect `prefers-reduced-motion`.
- **Springdoc OpenAPI → Orval → generated TypeScript REST clients and TanStack Query hooks** rather than manually repeating request DTOs and API functions. Generation is done from an approved local OpenAPI artifact, not from an anonymously exposed production API endpoint. See [Orval](https://orval.dev/).
- **TanStack Query** owns REST server state; WSS status hints invalidate/refetch Query. **TanStack Table** for sortable/filterable device lists; avoid a second state copy in Zustand.
- **Sonner** for notifications; **React Hook Form + Zod** only for nontrivial forms. **Recharts** for v0.2 event/status trends, **TanStack Virtual** only when row counts justify it. **i18next** for bilingual UI when locale selection becomes a feature.
- Keep strict API authorization on Spring Boot, not in React component visibility. A UI badge never proves monitoring or employee inactivity.

## UX token baseline
Primary blue `#2563EB`, green `#159A69` (healthy), orange `#E88816` (warning), red `#DC4A55` (failure), violet `#7C3AED` (dashboard accent), navy `#0B2447`, background `#F3F7FD`. Use labels **and** colors for status. The conceptual SVG tokens are **targets** and may require accessibility contrast adjustments in real UI.

## Motion and performance
- Default button/hover transitions in CSS, typically 120–200 ms.
- Dialog/sheet transitions use small transforms and opacity, typically 160–240 ms; no flashy idle monitoring animation.
- WSS-driven notifications debounce/coalesce, and never animate each audit row. A disconnected WSS shows a visible degraded/connecting label and REST re-sync.
- Avoid unnecessary layout shifts; prefer skeletons and stable table heights. Virtualize large datasets only after profiling.

## Phase-controlled dependencies
| Phase | Actual need | Install when implementing |
|---|---|---|
| v0.1 | React/Vite, pnpm, Tailwind, shadcn UI, Lucide, **Orval-generated Query client**, Router, forms for enrollment/login, Sonner, targeted Motion | only required packages/components |
| v0.2 | Timeline charts, large table, internationalization | Recharts, Virtual/i18next as warranted |
| v0.3 | Tauri 2 desktop companion and authorized recording UX | optional shared UI package, no Electron |
| v1.0 | more sophisticated validation/testing | review/test expansion after MVP |

## Dependency governance
Use [ADR-0007](../architecture/adrs/0007-mature-ecosystem-first.md) for ecosystem admission; do not hand-edit generated API client files. Pin versions/lockfiles **when app scaffolds are actually added**; no pretend package.json or GitHub CI jobs before implementation. Prefer official package documentation and licensing audit before commercialization. Keep examples synthetic and avoid copying Pagevela's unrelated editor/PWA modules.
