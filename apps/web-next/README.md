# Lanveriq Web — primary Next.js App Router frontend

## NXT-12 frontend framework cutover (2026-11-07)

The canonical UI development entry point is **`apps/web-next`** running the reviewed **Next.js 16.4.5 stable security patch** with React 19, Node.js 24, pnpm 12, TypeScript, Tailwind 4, Lucide and shadcn-style UI components. This project now owns all main navigation routes, the GitHub Pages static showcase, TypeScript API contract compilation and subsequent UI development. The preceding Vite project `apps/web` is a **legacy migration/reference** only. The `console-navigation.ts` module owns the five workspace destinations; corresponding unit tests verify nested routes and reject API/credential pages. Existing page/route-isolation and production HTTP smoke tests remain enforced.

```bash
cd apps/web-next
corepack enable
corepack prepare pnpm@12.0.0 --activate
pnpm install --frozen-lockfile
pnpm run verify:routes
pnpm test
pnpm typecheck
pnpm build
pnpm dev
```

The default private Next.js server is localhost-only (`127.0.0.1:3100`). **The migration is framework/UI-only, not authorization for production:** real sessions, backend data, Agent collection, device operations and live Caddy/Node deployment remain OFF. The publicly available `https://kunlong-luo.github.io/lanveriq/` is a disconnected `output: 'export'` preview; it never receives real secrets or APIs. Historical synthetic acceptance records follow below.

## Public GitHub Pages showcase — deployed (static and disconnected)

**Live public URL:** https://kunlong-luo.github.io/lanveriq/

[Automated Pages build and deployment #42364974627](https://github.com/kunlong-luo/lanveriq/actions/runs/42364974627) completed **SUCCESS** on SHA `d99b2baefc864ba958a79739e9f28f082a8c9e6f`. A subsequent uncached public HTTP check returned **200** for `/`, `/overview/` and `/login/`; the root, overview and login pages retained no-real-data disclaimers, disconnected status and disabled credential fields.

The `pages-static-preview.yml` workflow runs automatically on relevant `main` changes and publishes **only** `apps/web-next/out` after frozen-lock install, route-import isolation, component tests, TypeScript checks, an opt-in Next.js `output: 'export'` build and fail-closed export assertions. Only the isolated Pages build adds `basePath: '/lanveriq'` and disables unsupported server-only Next.js headers/redirects. In the disposable Actions workspace only, the dynamic `/devices/[id]` route is omitted rather than fabricating device identities. `.nojekyll` preserves `_next` assets. The normal `output: 'standalone'` build and its NXT-11 private-origin `nosniff` guard are unchanged.

**GitHub Pages is publicly accessible** and provides *no* Next.js SSR/session authorization, Spring Boot API, PostgreSQL, Redis, private Caddy edge, real login, RBAC, Agent or device data. Never place user passwords, certificates, secrets or real employee/terminal information in this static site or its build inputs. All real-data production-release, actual login UX and network-topology gates remain OPEN.


## NXT-11 first static asset boundary subgate — synthetic PASS; production OPEN

[Automatic full Caddy + Spring/Tomcat + PostgreSQL 18 + Chromium #42362704645](https://github.com/kunlong-luo/lanveriq/actions/runs/42362704645) **passed** on code SHA `a73844ba41a96bf2df22b30c6381120b9fa41f89`, retaining NXT-10 direct-origin, HTML/RSC forward-auth, scoped multi-admin and logout/revocation checks. `scripts/verify-synthetic-nextjs-origin.py` now also inspects built public Next.js static artifacts and samples a real `/_next/static` JS response: successful anonymous access, correct JavaScript MIME, public immutable caching, `X-Content-Type-Options: nosniff`, exact served-build digest and identical content/headers despite forged identity claims; the per-run internal edge proof must not appear in any static artifact. The first full test [#42362499510](https://github.com/kunlong-luo/lanveriq/actions/runs/42362499510) revealed a missing private-origin `nosniff` header. A targeted `next.config.ts` security header repaired it without relaxing the verifier. [Next.js build/source #42362691430](https://github.com/kunlong-luo/lanveriq/actions/runs/42362691430) passed; the full rerun then succeeded.

This is **only the first NXT-11 subgate**. Public immutable JS/CSS is distinct from protected no-store HTML/RSC/session/API data. Deployment network namespaces/firewalls, private asset/cache variants, production Caddy routing, legitimate cross-principal browser static behavior, actual accessible login/lockout flows and privacy/operational approvals still require separate acceptance. No production app routes, live employees/devices, real administrator sessions or Agent collection were enabled.


## NXT-10 synthetic direct-origin and RSC/edge-bypass checks — PASS

[Auto-triggered Caddy 2.11.7 / real Spring-Tomcat / PG18 / Chromium #42335190613](https://github.com/kunlong-luo/lanveriq/actions/runs/42335190613) completed **SUCCESS** and logged `SYNTHETIC NEXTJS ORIGIN ISOLATION PASS`, `SYNTHETIC EDGE HTML AUTH PASS`, and `SYNTHETIC EDGE RSC AUTH PASS` in one real network test. In the fourth-gated CI runner only, `next start --hostname 127.0.0.1 --port 3100` keeps the Next.js origin off nonloopback interfaces. A generated `src/proxy.ts` and separate page check use Node's `timingSafeEqual` to verify an ephemeral 256-bit internal Caddy-to-Next proof (never exposed to JS, the browser, source control or Actions logs). Calling the inner origin directly for protected HTML/RSC, even with forged session cookies or browser-supplied edge headers, yields 404. Caddy rejects a caller's claimed proof header, checks the actual Spring Session through upstream mTLS `forward_auth`, and adds the private proof to Next only for allowed routes. Public Caddy 401 responses block anonymous/disabled/revoked sessions from protected HTML and RSC; valid A/B synthetic accounts still receive the correct scoped data, no-store protected HTML and same-canonical-route RSC response with `text/x-component` MIME.

The real-browser prefetch verifier now uses `fetch(...redirect: 'follow')` to accommodate Next.js normalization redirects **without relaxing** same-origin/target path, successful HTTP 200, RSC MIME, no-store, or unauthorized-RSC negative assertions. The companion [source regression run #42335206947](https://github.com/kunlong-luo/lanveriq/actions/runs/42335206947) passed. These changes are exclusively synthetic: normal `src/app` does not contain the Proxy or credentialed pages, `deploy/caddy/Caddyfile.reference` is still disabled, and actual `/login` and device/monitoring routes remain unconnected. Running in a disposable local listener does **not** approve real deployment topology, all static asset paths, use of employee data or Agent collection. CI is automatic on relevant `main` pushes.


## NXT-09 disposable Caddy forward-auth HTML boundary: real Chromium PASS

The previously accepted NXT-08 client Session revalidation is now supplemented by **server-side Caddy authorization before protected HTML reaches Next.js**—but solely inside the fourth-gated CI runner. The test-only Caddyfile generated by `apps/server/scripts/verify-agent-upstream-tls.sh` defines a dedicated `handle /synthetic-console-route/*` that invokes Caddy's built-in `forward_auth` through trusted **Spring TLS/mTLS** to `GET /api/v1/auth/session` and then sends verified sessions to the loopback Next.js listener. **No live Next.js proxy/middleware, production route handler, or real login is turned on.** A denied user sees Spring's HTTP 401, not even a Next.js document shell; authorized sessions see no-store HTML and are again checked by the staged client before any device/monitoring fetch. The temporary synthetic candidate login route remains outside the protected URL prefix only so the throwaway browser can log in.

The [automatic real Chromium + Spring/PG18/Caddy run #41944796254](https://github.com/kunlong-luo/lanveriq/actions/runs/41944796254) **passed** `SYNTHETIC EDGE HTML AUTH PASS`, verifying anonymous/forged/post-logout/disabled account edge 401 with no SSR, valid synthetic A/B session 200, and continued group RBAC, zero-data revocation, CSRF/TLS and tab lifecycle. The [Server source CI #41944859643](https://github.com/kunlong-luo/lanveriq/actions/runs/41944859643) passed fail-closed checks requiring the checked-in `deploy/caddy/Caddyfile.reference` to remain disabled. **This is not a deployed production edge nor proof of isolating a separately exposed Next.js origin**: keep Caddy as the exclusive user entry point before activation, review RSC/assets and access-policy UX, and obtain approvals before real employees, devices or Agent telemetry are permitted. Normal `/login` and device/monitoring previews are still inactive. Tests run automatically on relevant main pushes.


## NXT-08-A: three genuine disposable App Router URLs, safe Spring Session handoff

The new **unmounted** `src/staged-console-route-boundary.tsx` is a multi-route read-only Console integration candidate. The guarded synthetic installer creates `src/app/synthetic-console-route/{overview,devices,monitoring}/page.tsx` **only in four-gated disposable CI**, not regular `src/app` or production builds. A separate Next.js SSR shell starts with zero identity, business rows, counters and authorized links. On client mount, each full document navigation requests **fresh** Spring `/auth/session` first, then checks authorized directory/overview responses against the same session's current group scope, without passing old principal/query cache/storage between pages. A failed session, grant or HTTP response does not display stale data; navigating away aborts in-flight requests and clears local cache. A real Spring CSRF POST is still required for explicit login and logout; route navigation sends no credentials in URLs.

**Passed real E2E:** [automated Caddy/Spring/PostgreSQL 18/Chromium #41943184722](https://github.com/kunlong-luo/lanveriq/actions/runs/41943184722) emitted `SYNTHETIC MULTI-ROUTE SESSION PASS`. It tested anonymous 401/empty shell, A's two devices across three real URLs, logout, direct anonymous subroute denial, B's one device, role-scope revocation and account disable. [Next.js source/build #41943065488](https://github.com/kunlong-luo/lanveriq/actions/runs/41943065488) and [Server source regression #41943222802](https://github.com/kunlong-luo/lanveriq/actions/runs/41943222802) also passed. Normal `verify:routes` rejects a checked-in synthetic URL tree and the transitive AST import guard blocks reaching this session boundary from the ordinary previews. Normal `/login`, `/overview`, `/devices`, `/monitoring` remain **inert**. Spring API RBAC is verified; **this alone is not server-side HTML route protection**, nor authorization for real accounts/devices/Agent/production deployment. No manual GitHub run is required for routine CI.


## NXT-06-C route dependency integrity — verified source gate

Ordinary Next.js login, device and monitoring routes still render disconnected previews. The default `pnpm run verify:routes` now executes `scripts/verify-runtime-isolation.mjs`, which traverses **all runtime imports** from committed `src/app` and `src/components` through internal `src/lib` helpers. It refuses a transitive path to `staged-console-auth`, `staged-console-read`, `staged-console-workspace`, test-only credentialed components or Orval's generated contract. It covers TypeScript import/export declarations (including type imports and reexports), `@/` and relative aliases, literal dynamic imports and `require`; computed module loading and unresolved/escaping local paths are rejected, not silently accepted. This is a **static route isolation** control, not server authentication; actual RBAC and CSRF remain enforced by Spring.

Project TypeScript **7.0.2** is retained for native production typechecking and Next.js; the safety analysis alone imports `typescript-ast`, a pinned `npm:typescript@6.0.3` JavaScript Compiler API alias, because native TypeScript 7.0 deliberately does **not** expose the old JS parser. The alias is locked in `pnpm-lock.yaml`. CI runs the AST regression in `scripts/test_runtime_isolation.mjs` before `verify:routes` and builds. [Next.js source/build CI #41824276231](https://github.com/kunlong-luo/lanveriq/actions/runs/41824276231) **passed**, including the frozen lock, AST regression and ordinary disabled-login production build. A later full disposable Caddy/Spring/PG18/Chromium run is a separate requirement; source success alone cannot authorize actual user-facing routing or employee/device access. All relevant main-branch changes continue to trigger synthetic tests automatically.


## NXT-06-B keyboard and accessible session-lock gate: synthetic Chromium PASS

[Automatic Console HTTPS workflow #41814699267](https://github.com/kunlong-luo/lanveriq/actions/runs/41814699267) **passed** real browser checks for keyboard Tab order across the candidate login fields and submit, accessible form labels, a locked-screen `role=status` message and explicit logout after a **synthetically dispatched** hidden-document event. It retained nonempty A/B group isolation and no persisted rows/metrics after invalidation. This supplements, but does NOT replace, human keyboard/screen-reader/visual/contrast testing, user-role sign-off, and the still-default-disabled real Next.js routing.


## NXT-06-B authenticated view lifecycle: fail closed, test-only

The unmounted `src/staged-console-workspace.tsx` now **locks** when Chromium reports a hidden document or pagehide and immediately erases currently rendered devices, current principal, aggregate metrics and TanStack Query cache. A locked UI does not show a credential form and requires a user-initiated Spring logout to verify return to `signed_out`. In-flight login, directory or overview network responses use an operation epoch and may **not** restore data after backgrounding, switching or unmounting. A first page with no next cursor must have exactly the number of entries reported by the separate authorized server overview; with a next cursor the total must be larger. These checks reject known torn snapshots on live scope changes, never fake zero values, and never make hidden-tab API requests. They do not replace Spring's authorization policy.

The [automatic synthetic Chromium suite #41814258021](https://github.com/kunlong-luo/lanveriq/actions/runs/41814258021) **passed** `SYNTHETIC CONSOLE LIFECYCLE PASS` after dispatching a **synthetic** hidden-page visibility event to the real Chromium React app, then verifying the clearing/explicit-logout flow against live disposable Spring/PG18. [Next.js unit/compile CI #41814315219](https://github.com/kunlong-luo/lanveriq/actions/runs/41814315219) and [Server source checks #41814337308](https://github.com/kunlong-luo/lanveriq/actions/runs/41814337308) also passed. This is an acceptance candidate, not approved live routing or production login.


## NXT-06-A staged product-shaped Console — automatic real-browser synthetic E2E PASSED

`src/staged-console-workspace.tsx` is the actual read-only Console **candidate component**. It composes the already-tested `staged-console-auth.ts` and `staged-console-read.ts` into a React login form, server-validated read-only monitoring counters, authorized `DeviceDirectory`, explicit refresh and logout, and fail-closed session/data reset. No background polling, browser storage, fake data, recording or Agent collection is enabled. `staged-console-overview.ts` refuses contradictory counters or invalid server timestamps; API errors never display as verified zeros. The temporary candidate disables detail links to unmounted pages.

**Actual proof:** [auto-triggered synthetic Caddy + Spring + PG18 + Chromium #41812852436](https://github.com/kunlong-luo/lanveriq/actions/runs/41812852436) **PASS** with the marker `SYNTHETIC STAGED CONSOLE UI PASS`. It rendered two A-group rows then one B-group row with server-scoped counts in the real React UI, changed identities without stale rows, removed visible rows after grant revocation, and removed metrics and identity after account disable. It retained the independent TLS/CSRF/CDP, multi-user cookies and 404 checks. No user needs to run the synthetic workflow manually: relevant `main` pushes trigger all gates automatically.

**Not an active login product:** this file is **NOT imported by any checked-in App Router route**. The guarded installer mounts it only as `src/app/synthetic-console-candidate/page.tsx` inside a disposable GitHub Actions workspace with all four synthetic test flags. The default `verify:routes` rejects that route if checked into production. The ordinary `/login`, `/devices`, `/overview` and `/monitoring` are still static/unconnected, and real device/employee access or deployment requires separate authorization and UX/security review.


## NXT-05-B historical test rollout (manual and automatic four-flag runs passed)

The original one-admin empty-database Next.js client test has **passed real Chromium** in [#41803890599](https://github.com/kunlong-luo/lanveriq/actions/runs/41803890599). The next candidate is `src/synthetic-only-multi-admin-probe.tsx`, an **unmounted** component that is never available through normal App Router pages. The fourth `synthetic_multi_admin_probe` manual input defaults to `false` and requires **all three** earlier browser/Next.js/synthetic-auth inputs explicitly enabled. Only in that four-flag ephemeral CI job does the installer add a `/synthetic-multi-admin-probe` page alongside the single-admin temporary fixture, and the browser script seed a fresh disposable PG18 with an owner group (two devices), B group (one device), and two fresh synthetic browser-login identities. The two accounts share only the randomly generated **one-use test hash** inside the disposable database; this is not a production user-creation design.

The browser candidate explicitly tests separate cookies and nonempty group-scoped reads, out-of-group detail 404, same-context A-to-B switch with TanStack Query cache purge, live permission removal and account disable. **Only the synthetic test component** overrides the ordinary `gcTime=0` cache setting to hold rows long enough to prove the wipe; the normal app keeps its original safe defaults. The old `/login` page is still disabled and the real Caddy reference is untouched. The [Next.js source/build job #41806654340](https://github.com/kunlong-luo/lanveriq/actions/runs/41806654340) passed; the subsequent [four-switch real Chromium #41810697843](https://github.com/kunlong-luo/lanveriq/actions/runs/41810697843) and [automatic equivalent #41810887203](https://github.com/kunlong-luo/lanveriq/actions/runs/41810887203) **both passed**, including `MANUAL SYNTHETIC NEXT.JS MULTI-ADMIN RBAC PASS` (legacy marker). No manual rerun is required for ordinary development. This remains synthetic test instrumentation, not real-user/Agent/production authorization.


## NXT-05-A test-only browser login bridge (accepted synthetic test, NOT deployed)

After passing the independent Caddy/Spring/Chromium and nonempty two-group PG18 Servlet RBAC synthetic gates, the next candidate can exercise **real Next.js client-side fetch** without enabling the application's normal login route. `src/synthetic-only-auth-probe.tsx` is an **unmounted test fixture**, reusing the already unit-tested `staged-console-auth.ts`, `staged-console-read.ts`, and session cache-clearing policy. Nothing in `src/app` or `src/components` imports it on the normal build. `/login` still has disabled username/password controls, and there is no Next.js API proxy or live device collection.

An operator-triggered `console-https-synthetic.yml` workflow may set `browser_probe=true`, `nextjs_same_origin_probe=true`, and the new **`synthetic_auth_ui_probe=true`** (defaults to `false`). Only when **all three** gates are explicitly selected does the runner create a **temporary** `src/app/synthetic-auth-probe/page.tsx` *after* its normal committed-route safety check and before building its disposable Next.js preview. That route is never committed; the ordinary `pnpm run verify:routes` rejects it. The installer also refuses to execute outside a GitHub Actions runner with the three explicit test flags. It never changes `deploy/caddy/Caddyfile.reference`.

Once the new manual gate is actually executed successfully, the synthetic-only Chromium test can prove that a real Next.js client page submits disposable credentials through browser-generated same-origin POST headers, receives Spring's rotated HttpSession/CSRF, reads an **authorized empty** device list and zero monitoring overview from disposable PG18, then logs out and observes post-logout 401. Synthetic credentials are passed through the existing inherited FD3 and are not printed or placed in URLs. This stage **does not** prove multiple browser principals, nonempty Next.js device/group cache isolation, production login page activation, visual accessibility sign-off or real user/Agent authorization. The source checks and manual runtime must be treated as separate evidence; **the new manual credentialed UI gate is pending**.


**Do not use for real device management.** This is a separate candidate at
`apps/web-next`; the existing React/Vite preview under `apps/web` is
untouched. This candidate uses Next.js 16.4.3, React 19, TypeScript,
Tailwind 4 and editable shadcn/ui-compatible Card components, with
a local Node.js runtime.

All routes are **synthetic-only**, show no live device counts and never
issue login or business API requests. `/login` only renders disabled
controls, no password submission. Caddy TLS, PostgreSQL 18, CSRF,
Origin, Cookie and group-scoped authorization are independent pending
manual gates. No Next.js Route Handlers, API proxies, credentials,
server-side authenticated data fetch or WSS are enabled.

Candidate routes: `/` redirects to `/overview`;
`/overview`, `/monitoring`, `/devices`, `/devices/[id]`,
`/enrollment`, `/settings`, `/login`.

## Candidate-only local check (Node 24, pnpm 12)

```sh
cd apps/web-next
corepack enable
corepack prepare pnpm@12.0.0 --activate
pnpm install --frozen-lockfile
pnpm run verify:routes
pnpm test
pnpm typecheck
pnpm build
pnpm dev
```

Open `http://127.0.0.1:3100` for this **unauthenticated preview
only**. Never enter passwords. This address is not a valid HTTPS
business console origin.

The lockfile was **actually generated by pnpm 12 in CI**, committed
at [ddc0a61](https://github.com/kunlong-luo/lanveriq/commit/ddc0a61ff14d50fbc84501e3ca3dbfc6ee529bbe)
and checked with a read-only `--frozen-lockfile` build. The one-time
write-capable bootstrap was removed. [Next.js candidate CI #41287394823](https://github.com/kunlong-luo/lanveriq/actions/runs/41287394823)
passed Node 24, component tests, TypeScript, the Next.js production
build, and seven live preview HTTP pages with no business API proxy.
This **does not** enable real credentials, sessions or device data.

See [ADR-0008](../../docs/architecture/adrs/0008-nextjs-app-router-console.md)
for migration plan and trust boundaries. This candidate deliberately
does not yet implement full Vite visual/route parity or replace
`apps/web`.

## NXT-02 core management UI (preview only)

The candidate now has the actual page structures of the V0.1 management
console: fleet metrics (displaying **—**, never synthetic counts), a
connectivity breakdown, inventory/heartbeat freshness, a read-only
directory table with controlled local search and group selection,
device detail hardware/network/health tabs, registration instructions,
settings acceptance and the completely disabled administrator login.

The default `/devices` view is **unconnected**, not an authenticated
empty list. The table can display only authorized, caller-supplied rows
and its unit-test fixtures are test-only; there is no network transport
or real data in this Next.js runtime. Device IDs passing the shared
UUID formatter are **not** authorization or proof of existence.
Sensitive network inventory is withheld, and no mutation action is
available.

[Next.js CI #41288117979](https://github.com/kunlong-luo/lanveriq/actions/runs/41288117979)
verified candidate build/route, safety and component behavior. For
actual visual/mobile/keyboard review see
[manual NXT-02 acceptance matrix](../../docs/testing/nextjs-nxt02-manual.md).
This stage does not replace Vite, clear the PostgreSQL 18 manual
test gate, authenticate browsers, or enable real device requests.

## NXT-03A source-generated API contract stage (unmounted)

This candidate now has `src/lib/console-device-projection.ts`:
a fail-closed allowlist of displayed fields from the opt-in Spring
device-list API (no MAC, IP, employee identifiers, raw event/credential
fields). A verified authenticated empty list stays empty, whereas a
missing required member, invalid UUID, non-ascending/duplicate
page, invalid cursor or queue value blocks the entire page.
Heartbeat absence is **unknown**, and even a present receipt time
does **not** prove the endpoint is online.

`src/lib/staged-console-read.ts` accepts a canonical HTTPS console
origin, requires the current browser origin to match **exactly**, and
can only construct three hardcoded read paths. It requires no-store
JSON and same-origin fetch/cookie policies, rejects redirects and
maps 401/403/429 to explicit no-data states. It does **not** run in
the deployed Next.js preview: `scripts/verify-route-contract.mjs`
rejects runtime page imports of the staged client.

After server unit tests emit the *actual* Springdoc OpenAPI document,
Orval generates typed model files and its React Query Fetch client.
[Java CI #41288584413](https://github.com/kunlong-luo/lanveriq/actions/runs/41288584413)
passed a **build-only Next.js generated-model type check** through
`contract-check/typecheck.ts`. The generated model directory is copied
temporarily to `.generated-contract/` and is gitignored; never commit
manually duplicated or stale API DTOs. Runtime Orval hooks are **not
yet installed or wired in Next.js**.

NXT-03B is still blocked pending the [real manual browser
acceptance](../../docs/testing/nextjs-nxt03-manual.md), 58-case
PostgreSQL 18 gate, and server-side Caddy/TLS/session/RBAC security.
The existing Vite app remains the default and no production
monitoring feature is enabled.

## NXT-03B: locked Query foundation, full Orval hook compile (still no live REST)

The candidate now pins `@tanstack/react-query@5.105.0` in an
actually pnpm-generated `pnpm-lock.yaml`, with a read-only frozen CI.
An earlier one-time write-capable dependency regeneration workflow
was **removed immediately** after successful generation, tests and
lockfile commit.

`PreviewQueryProvider` supplies an otherwise inert `QueryClient`
to console routes; it does **not** mount Orval hooks or make network
requests. In `src/lib/console-query-policy.ts`, automatic retries,
focus/reconnect/mount refetch and cache persistence are disabled.
The `clearConsoleSessionState` helper cancels and clears all queries.
Unit tests with **synthetic-only fake fetch** verify that permission
denial, session expiry, throttling and service errors cannot preserve
previous cached device data when clearing that session.

[Java CI #41289190664](https://github.com/kunlong-luo/lanveriq/actions/runs/41289190664)
generated and typechecked the *complete* Springdoc → Orval Fetch/React
Query client with the candidate's exact dependencies and TypeScript.
[Next.js CI #41289252650](https://github.com/kunlong-luo/lanveriq/actions/runs/41289252650)
verified the candidate's frozen build, fake-fetch tests and production
preview routes. All generated clients remain in ignored,
ephemeral build-only directories; they are **not** part of the Next.js
deployed code. The Next.js runtime **still does not log in or request
devices**. PostgreSQL 18 manual tests, Caddy and real browser
security acceptance must finish before a separate runtime enablement.
