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

## Stop and uninstall

Stop `pnpm dev` with Ctrl+C. You may delete locally generated `apps/web/node_modules` and `apps/web/dist`; do **not** remove existing Docker Compose stacks, `spark-postgres`, `spark-redis`, PostgreSQL schemas, shared volumes, audit data or keys.

Building a static preview is **not** permission to deploy a monitoring console. Browser login/session, REST/WSS scope checks, TLS/CSRF, and PostgreSQL integration acceptance must pass before connecting real data.
