# Lanveriq NXT-01 — isolated Next.js App Router preview

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
