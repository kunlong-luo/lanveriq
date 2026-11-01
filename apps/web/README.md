# Lanveriq Web — synthetic dashboard preview (WEB-01 first slice)

**Development preview only. No real login, connected API, real device, WSS, remote actions, recording, background polling or production deployment.** All five displayed devices and metrics are invented and labeled on every route.

## Run locally (Node.js 24 + pnpm 12)

~~~bash
cd apps/web
corepack enable
corepack prepare pnpm@12.0.0 --activate
pnpm install --frozen-lockfile
pnpm test
pnpm build
pnpm dev
~~~

Open **http://127.0.0.1:5173**. The server binds localhost only. Stop with Ctrl+C.

The complete dependency tree is pinned by the reviewed and committed `pnpm-lock.yaml`; CI installs with `pnpm install --frozen-lockfile` and rejects manifest/lockfile drift. Use the exact Node.js 24 and pnpm 12 toolchain from the instructions.

## Synthetic routes

- `/overview`: demo KPI cards, connectivity split, sample device table.
- `/monitoring`: operational health and queue layout, **not** a screen-monitoring view.
- `/devices`: interactive local-only name/OS/group filter.
- `/devices/:id`: read-only fake hardware/health view with accessible Radix tabs; no real IP/MAC.
- `/enrollment`: disabled enrollment with the planned flow explained.
- `/settings`: technology stack and outstanding security acceptance gates.
- `/login`: designed administrator sign-in screen. Username/password inputs and submit button are **intentionally disabled**; no credentials are accepted, transmitted or persisted. Click the `DEMO` avatar to open this screen.

Stack: React 19, Vite 8, TypeScript 7, Tailwind 4, Radix Tabs, Lucide, React Router, TanStack Query. **No live TanStack queries are used yet.** Never implement production DTOs manually.

## Springdoc → Orval → real Web dependency typecheck (no HTTP requests)

The backend Java CI produces the **actual** generated `apps/server/build/contracts/openapi.json`; the existing `apps/server/contract-client-check` harness runs Orval 8 and its own typecheck. The same CI then copies that **generated** TypeScript output into the ignored `apps/web/.generated-contract/` directory and runs `pnpm exec tsc --noEmit --project tsconfig.contract.json` against the Web app's pinned React Query and TypeScript versions. The typed login, logout, CSRF, session, device and monitoring contracts are verified via `contract-check/typecheck.ts`. Neither generated files nor passwords are committed. Web normal preview CI uses `pnpm test` and `pnpm build`; the backend CI is responsible for the cross-project OpenAPI gate.

A local developer can reproduce the cross-project gate **without starting a server or Docker** after `./gradlew test` in `apps/server` and `pnpm install --frozen-lockfile && pnpm verify` in `apps/server/contract-client-check`:

~~~bash
mkdir -p apps/web/.generated-contract
cp -a apps/server/contract-client-check/generated/. apps/web/.generated-contract/
cd apps/web
pnpm install --frozen-lockfile
pnpm exec tsc --noEmit --project tsconfig.contract.json
~~~

**Build-time compatibility is not authenticated browser integration.** Orval's fetch functions do **not** by themselves implement the cookie, Origin, CSRF refresh, session invalidation or status/permission-check choreography. Real credential handling and same-origin HTTPS must remain disabled until separate Caddy/PG18/browser acceptance passes.

## Staged same-origin browser session request adapter (DEFAULT OFF)

`src/auth/sessionTransport.ts` now has an **unwired, opt-in-by-code-only** `createSessionTransport` primitive for eventual browser acceptance. It is **not imported by `App.tsx` or `LoginPage.tsx`** and performs no requests in the running preview. The administrator login inputs stay disabled.

The transport uses native browser `fetch` (no hand-written HTTP stack or duplicate Springdoc DTO), rejects noncanonical or non-HTTPS origins before requesting, applies `credentials: same-origin`, `mode: same-origin`, `redirect: error`, `cache: no-store` and `referrerPolicy: no-referrer`. It obtains an uncached CSRF token, submits URL-encoded credentials **only in the POST body**, requires HTTP 204, refreshes CSRF after Spring session fixation, and verifies the authenticated `GET /api/v1/auth/session` result before returning a username. Logout gets a new CSRF token. Missing/invalid token, 401/403/429/503 and unexpected redirects fail closed. It never uses localStorage, sessionStorage, an invented bearer token or JavaScript-set `Origin`; real browsers supply Origin for eligible POST requests.

`src/auth/sessionTransport.test.ts` runs these scenarios with **in-memory fake fetch responses only**; it does not test actual cookie delivery, Caddy TLS, Spring/PG18 transactions, browser redirect/CORS semantics, or real session revocation. Do not wire this into a functioning login button before the independent [console session acceptance](../../docs/testing/console-session-manual.md) and [PG18 manual gate](../../docs/testing/server-postgres18-manual.md) are completed and approved.

## Stop and uninstall

Stop `pnpm dev` with Ctrl+C. You may delete locally generated `apps/web/node_modules` and `apps/web/dist`; do **not** remove existing Docker Compose stacks, `spark-postgres`, `spark-redis`, PostgreSQL schemas, shared volumes, audit data or keys.

Building a static preview is **not** permission to deploy a monitoring console. Browser login/session, REST/WSS scope checks, TLS/CSRF, and PostgreSQL integration acceptance must pass before connecting real data.
