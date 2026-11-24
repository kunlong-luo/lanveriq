> **NXT-28 compact navigation — 2026-11-21, FOUR SAME-SHA CI SUCCESSES:** The 520px-high desktop sidebar has its own scrolling with native internal bar hidden; the Base UI mobile sheet remains scrollable and focus-trapped, now visually matching the white `#FFFFFF` / ink `#172235` navigation. CSS authority: `apps/web/src/app/console-polish.css`. [functional SHA `e76cd345`](https://github.com/kunlong-luo/lanveriq/commit/e76cd34505dd8e49833ab9c0b61421288c43c353) passed FOUR same-SHA checks: [Next.js L1 #44882686781](https://github.com/kunlong-luo/lanveriq/actions/runs/44882686781), [real Chromium #44882686713](https://github.com/kunlong-luo/lanveriq/actions/runs/44882686713), [disconnected Pages build/deploy #44882686808](https://github.com/kunlong-luo/lanveriq/actions/runs/44882686808), and [RD-2 default-OFF #44882686775](https://github.com/kunlong-luo/lanveriq/actions/runs/44882686775). Chromium screenshot artifact: `nxt18-visual-qa-e76cd34505dd8e49833ab9c0b61421288c43c353` (ID `14698677524`). [NXT-28 contract](frontend-compact-navigation-nxt28-2026-11-21.md). No real business data or production approval.

> **NXT-27 UI primitive (2026-11-21, four exact-SHA CI successes):** `apps/web/src/components/ui/evidence-state.tsx` is the canonical, pure, stateless presentation component for missing evidence in monitoring and device detail. Use `EvidenceNotice` for a distinct *unconnected/restricted/unverified* note, and `EvidenceValue` for a factual unavailable value with contextual accessible label. The authoritative styling lives at the **end** of `console-polish.css`, not in route-specific duplicate blocks. These labels express privacy/data provenance boundaries only; no live auth decision, heartbeat state or device count is inferred. [Implementation and tests](frontend-evidence-state-nxt27-2026-11-21.md).

# Frontend design system — canonical Next.js App Router, live integration pending

**2026-11-21 唯一现行 UI 规范：** `apps/web` 是 Next.js 16.4.5 / React 19 / Tailwind 4 / **Base UI React 1.9.0** + 项目内 shadcn-style 组件（旧 Vite 已删除，`apps/web-next` 目录已改名）。**当前有效的 NXT-20/21 视觉 token 以 `apps/web/src/app/console-polish.css` 为准**：画布 `#F5F7FA`、卡片 `#FFFFFF`、墨色 `#172235`、靛蓝 `#4D5FC9`、浅薄荷 `#CEF4E5`。原 B+C `#F8F8FC` / `#6A5BFF` / 青柠方案与早期 Radix/Vite 提案均为历史，不能作为现行截图/主题验收基准。请参见 [NXT-21 实际视觉规范](../visuals/CONSOLE_CRAFTWORK_DIRECTION.md)、[NXT-24 迁移](nextjs-path-rename-nxt24-2026-11-21.md)。真正受授权的登录/设备查询依旧默认关闭，公开 Pages 仅断开预览。按 [v0.1 批次计划](../planning/linux-v01-reuse-first-delivery-path-2026-11-24.md) 暂缓非必要视觉微调；新增依赖以 [实际 manifest](../../apps/web/package.json) 为准。


**NXT-12 framework cutover (2026-11-07):** Use `apps/web` **Next.js 16.4.5 stable** as the only forward-development UI app. Its Next.js App Router pages, Tailwind 4/shadcn-style components, TanStack Query, build-time Orval types and GitHub Pages static showcase have passed relevant locked-build/source gates. The old Vite UI/proxy have been removed from the current tree (NXT-23), and the Next.js UI moved from `apps/web-next` to `apps/web` (NXT-24). They remain accessible only as historical Git content, not a runnable product endpoint. Do not copy Vite-only development proxy logic into Next.js, or mount a credentialed route to replicate historical demo data. No real session, business data, Agent telemetry or production proxy deployment is authorized by this framework cutover; retain NXT-10/NXT-11 Caddy/Spring security acceptance.



## Historical checkpoints (2026-10 to early 2026-11; not active instructions)

The following first-slice logs reflect source and CI **as of their original dates**. Directory names were reused during NXT-24; where an old paragraph mentions the Vite `apps/web`, it means the **deleted** Vite program, not today's Next.js package. Do not run earlier Vite build/proxy instructions or use stale old CSS colors.

**WEB-01 local health-only slice (2026-11-01):** The Vite React preview now uses an exact-path localhost development proxy for anonymous Spring Boot `GET /actuator/health`, with a non-polling TanStack Query and manual refresh. It never proxies `/api` or activates login/device endpoints. The source repo contains the shadcn/ui `components.json`, local Card composition and Tailwind 4 semantic tokens; this is an incremental style foundation, not a Next.js migration. It must display unavailable if Spring is down and must not present the synthetic metrics as live. The production static build has no Vite proxy. Real browser auth/business queries remain blocked by pending PG18/TLS/session acceptance.

**Status (2026-11-01):** A synthetic-only React/Vite preview has been implemented at `apps/web` and built in [Web CI #41274343864](https://github.com/kunlong-luo/lanveriq/actions/runs/41274343864), with working demo routes, search/filter and Radix Tabs. The frontend is **not connected to real Spring Boot APIs, Orval hooks, session, WSS or real device data**; production approval remains open. Product visuals are in [visual gallery](../visuals/README.md) and standardized by [STYLE_GUIDE](../visuals/STYLE_GUIDE.md). Visual drawings do not replace real components.

**V0.1 backend contract milestone:** The Spring Boot device-read controller now has a synthetic-only Springdoc OpenAPI generation and strict CI validation path. [Java CI #41190792718](https://github.com/kunlong-luo/lanveriq/actions/runs/41190792718) has **actually passed** Springdoc generation, JSON drift validation and contract artifact upload; that versioned `openapi.json` is suitable for a future Orval trial without opening the backend's docs endpoint. This is still not a completed React project or a production-accepted API; real PostgreSQL, HTTPS and cookie/session acceptance are separate.

**MON-01-OVERVIEW backend slice:** the planned monitoring-count endpoint now has a default-OFF controller, separate security chain, Postgres aggregate and a generated-OpenAPI contract test. A passing CI artifact is prerequisite for Orval use. No React code or real device data is present; this does not clear the manual database/TLS release gates.

**Source-generated frontend-client readiness check (2026-10-31):** A standalone `apps/server/contract-client-check` directory has exact versions and a checked-in pnpm lockfile for Orval 8 Fetch/TanStack Query, React 19 and TypeScript 7. The full frozen-install Java 25 pipeline [#41199936869](https://github.com/kunlong-luo/lanveriq/actions/runs/41199936869) has now passed: the source-generated three-route OpenAPI document produced real Orval Fetch/TanStack Query hooks and `tsc --noEmit` accepted them. Both the OpenAPI and the candidate client were uploaded as non-deployed workflow artifacts. This is **only a build verification harness** and does not create `apps/web`, React pages or live API access. When implementation starts, adopt the generated output instead of maintaining hand-written response types.

**Session bootstrap API readiness (source stage):** To avoid the React client guessing whether a stored Cookie still authenticates, the backend adds an opt-in `GET /api/v1/auth/session` with a minimal `login` response (no bearer tokens or permissions). The Springdoc-to-Orval gate is extended to include it. MockMvc tests simulate the Spring Security login/CSRF/cookie lifecycle, but actual browser, TLS and DB validation remains pending. [Java 25 CI #41261183351](https://github.com/kunlong-luo/lanveriq/actions/runs/41261183351) **passed** the full CSRF/login/session/authorization/logout MockMvc scenario, generated the four-route OpenAPI and typechecked the new Orval hooks. This remains a backend-only milestone; no frontend application has been created.

**Backend auth client contract preparation:** Real Spring Security form login and logout are now described through a conditional Springdoc customizer, and the real CSRF Controller exposes a typed response, allowing Orval to generate authenticated API mutations rather than writing bespoke login API calls in React. Neither production auth nor the frontend is enabled. [CI #41262871704](https://github.com/kunlong-luo/lanveriq/actions/runs/41262871704) passed the complete backend Springdoc/Orval/TypeScript gate for five GET operations plus login/logout POST mutations. Actual real browser/Caddy/TLS/PostgreSQL gates remain open.

**NXT-27 verified evidence:** [real Chromium and 15 screenshots #44880424398](https://github.com/kunlong-luo/lanveriq/actions/runs/44880424398), plus [Next.js L1 #44880424373](https://github.com/kunlong-luo/lanveriq/actions/runs/44880424373), [Pages #44880424353](https://github.com/kunlong-luo/lanveriq/actions/runs/44880424353) and [RD-2 OFF #44880424374](https://github.com/kunlong-luo/lanveriq/actions/runs/44880424374) on one functional SHA `ff66556d`. No production authorization.

## NXT-26 — active device-directory control standard

The canonical `apps/web` directory now uses **Base UI React 1.9 `Select`** for *both* group filtering and current-authorized-page sorting. Reuse `src/components/ui/console-select.tsx` and the final `console-polish.css` popup/trigger tokens; do not reintroduce the old `NativeSelect` or a homemade focus/menu implementation. Base UI owns keyboard, ARIA and popup positioning. The state stays entirely local to the **caller-provided authorized page**, and both controls are **disabled** in unconnected/signed-out/forbidden states. Use the existing React input for text search. The only enabled rows exist in an explicitly gated disposable GitHub Chromium fixture, never on Pages or the default local preview. See [NXT-26 acceptance](frontend-base-ui-select-nxt26.md).

## Stack — current manifests versus future options
- **Independent pnpm 12 package/lockfile in `apps/web`** (and a separate `apps/server/contract-client-check`); this repository does **not** use a root pnpm workspace to build all languages. Cargo owns Rust; **Gradle** owns Java. pnpm is a package manager, not an application framework.
- **Canonical today:** Next.js 16.4.5 App Router + React 19 + TypeScript, with Node.js 24 for local/private serving and a separate GitHub Pages static-only export. Vite/React Router are legacy migration material. Do not enable SSR authenticated-data fetching or production authorization by framework selection alone. [ADR-0008](../architecture/adrs/0008-nextjs-app-router-console.md).
- **Installed:** Tailwind CSS 4 + Base UI React 1.9.0 + source-owned shadcn-style components, not the historical Radix dependency. A shared UI package is not yet present or needed.
- **Lucide React** as primary stroke-based UI icon library; use explicitly approved/logos where necessary. Avoid installing multiple overlapping icon packs.
- **Installed behavior:** small native CSS transitions with `prefers-reduced-motion` support; **Motion is not installed** and is optional only after measured need.
- **Springdoc OpenAPI → Orval → generated TypeScript REST clients and TanStack Query hooks** rather than manually repeating request DTOs and API functions. Generation is done from an approved local OpenAPI artifact, not from an anonymously exposed production API endpoint. See [Orval](https://orval.dev/).
- **Installed but unconnected:** TanStack Query 5 has an isolated provider/state boundary; authenticated REST hooks and WSS invalidation are **not live**. **TanStack Table and Zustand are not installed**; current directory uses project-owned display components and only authorized in-memory rows.
- **Future candidates only (not installed):** Sonner, React Hook Form/Zod, Recharts, TanStack Table/Virtual, i18next and Motion. Add a dependency only with an approved feature, licensing review and corresponding tests.
- Keep strict API authorization on Spring Boot, not in React component visibility. A UI badge never proves monitoring or employee inactivity.

## UX token baseline — checked-in current CSS
- Canvas `#F5F7FA`, surfaces `#FFFFFF`, foreground/ink `#172235`, border `#E4E9F0`, indigo accent `#4D5FC9`, decorative mint `#CEF4E5`; authoritative location is `src/app/console-polish.css` imported **after** `lanveriq-bc.css` in `src/app/layout.tsx`.
- Unavailable/unknown is displayed as `—` or an explicit unconnected explanation, **never fake zeros or green online status**. Status is always communicated with words and icons, not color alone.
- Early blue/orange/lime and SVG palette proposals are **historical reference**, not the current UI CSS contract.

## Motion and performance
- Default button/hover transitions in CSS, typically 120–200 ms.
- Dialog/sheet transitions use small transforms and opacity, typically 160–240 ms; no flashy idle monitoring animation.
- WSS-driven notifications debounce/coalesce, and never animate each audit row. A disconnected WSS shows a visible degraded/connecting label and REST re-sync.
- Avoid unnecessary layout shifts; prefer skeletons and stable table heights. Virtualize large datasets only after profiling.

## Phase-controlled dependencies
| Phase | Actual need | Install when implementing |
|---|---|---|
| v0.1 (current) | Next.js App Router, pnpm 12, React19, Tailwind4, Base UI, Lucide; **Orval client only in offline build contract**; disabled login and disconnected directory | no new runtime deps or real API without security gates |
| v0.2 | Timeline charts, large table, internationalization | Recharts, Virtual/i18next as warranted |
| v0.3 | Tauri 2 desktop companion and authorized recording UX | optional shared UI package, no Electron |
| v1.0 | more sophisticated validation/testing | review/test expansion after MVP |

## Dependency governance
Use [ADR-0007](../architecture/adrs/0007-mature-ecosystem-first.md) for ecosystem admission; do not hand-edit generated API client files. **The existing Next.js package and lockfile are already checked in**; use frozen install and verified exact-SHA CI. Do not add undocumented packages or publish generated clients with credentials. Prefer official package documentation and licensing audit before commercialization. Keep examples synthetic and avoid copying Pagevela's unrelated editor/PWA modules.


**Historical Vite WEB-01 staged browser session transport:** the removed `apps/web/src/auth/sessionTransport.ts` (available in Git history only) used built-in fetch/URLSearchParams with HTTPS-only canonical origin, same-origin cookies, no-store, redirect rejection, CSRF refresh after session fixation, and fail-closed 401/403/429/503 handling. It is **not imported by the running UI** and the `/login` form remains disabled. Tests inject only in-memory HTTP stubs; real Origin/Cookie/Caddy/PG18 acceptance is still mandatory.

**Historical Vite WEB-01 lockfile (2026-11-01):** the original Vite-specific `apps/web/pnpm-lock.yaml` was generated by the first bootstrap and verified against Vite; it is **not** the current Next.js lockfile. The **current** `apps/web/pnpm-lock.yaml` belongs to Next.js and is independently locked/tested. The standard Web workflow must use `pnpm install --frozen-lockfile` and must keep GitHub Actions `contents: read`. The bootstrap workflow, which briefly required elevated write permission to pin the lockfile, must be removed. This does not close the browser login, Caddy/TLS, WSS, or genuine PostgreSQL integration gates.

**WEB-01-AUTH-PREVIEW + Orval compilation bridge (2026-11-01):** `/login` displays a standalone administrator sign-in screen, but both fields and submit button are disabled, with no credential state or network call. In backend Java CI, the actual Springdoc-generated OpenAPI is passed to Orval 8; the generated TypeScript is copied (uncommitted) into `apps/web/.generated-contract/`, then compiled with `apps/web/tsconfig.contract.json` against the actual locked Web versions of TypeScript and TanStack Query. `apps/web/contract-check/typecheck.ts` imports typed CSRF/login/logout/session and scoped health operations. This establishes **type compatibility only**, not an enabled browser authentication sequence. No real Caddy TLS, browser Origin/CSRF/cookie, RBAC, PostgreSQL or WSS acceptance is implied.

**Historical NXT-01 isolated Next.js candidate (2026-11-01):** The candidate, then at `apps/web-next` and now renamed into `apps/web`, provided a disabled-login/zero-real-data Next.js 16.4.3 App Router UI with its pnpm-generated and checked-in frozen lockfile. [CI #41287394823](https://github.com/kunlong-luo/lanveriq/actions/runs/41287394823) passed Node 24 install, unit tests, route guards, TypeScript, production build and actual preview HTTP smoke. A temporary write-capable lockfile bootstrap has been removed. At that historical checkpoint Vite still served the default preview. NXT-23/NXT-24 **later completed the UI-directory cutover**; authenticated Orval integration, real PG18/Caddy/browser production approval and physical Agent acceptance remain OPEN.

**NXT-02 Next.js UI port, automated portion complete (2026-11-01):**
The isolated `apps/web` now contains overview/monitoring fleet
panels with all metrics shown as **—** (unconnected, not zeros),
read-only device list with locally filtered *supplied* authorized rows,
detail tabs, registration flow, settings and disabled administrator
login. Actual CI verified routes, static/interactive component
contracts and production HTTP smoke at
[#41288117979](https://github.com/kunlong-luo/lanveriq/actions/runs/41288117979).
No fixture devices are imported into runtime. Responsive styling is
committed but manual cross-browser/keyboard/a11y checks are still
required before NXT-02 visual parity closure; no real Spring session
or Orval transport has been wired.

**NXT-03A Next.js type-safe, default-off read adapter (2026-11-01):**
The Next.js candidate includes a display-only validator, strict
HTTPS canonical browser-origin test double and no-store GET transport;
all unmounted from the actual pages. [Java CI
#41288584413](https://github.com/kunlong-luo/lanveriq/actions/runs/41288584413)
passed Springdoc/Orval code generation and a Next.js
`tsconfig.contract.json` check against **actual generated models**.
[Web Next CI #41288628345](https://github.com/kunlong-luo/lanveriq/actions/runs/41288628345)
passed normal non-network UI tests/build. Next.js Orval hooks are not
yet runtime dependencies and no authorized browser request has been
made. This distinction must be preserved in docs and UI until Caddy,
PostgreSQL, cookie/CSRF and RBAC are manually accepted.

**NXT-03B Query foundation and generated client compile (2026-11-01):**
The separate Next.js app now pins `@tanstack/react-query@5.105.0`
with a pnpm-produced lockfile. Its QueryClientProvider carries
conservative no-refetch, no-retry settings and a session-state clearing
utility exercised by fake-fetch permission/outage tests.
[Java CI #41289190664](https://github.com/kunlong-luo/lanveriq/actions/runs/41289190664)
passed compilation of complete Orval Fetch/Query hooks against the
**actual Next.js package**, and [Next.js CI #41289252650](https://github.com/kunlong-luo/lanveriq/actions/runs/41289252650)
passed UI tests and the production smoke. No runtime page imports
the generated client and no real business API has been enabled.
PostgreSQL 18, Caddy HTTPS and session/RBAC manual tests remain open.
