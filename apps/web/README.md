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

Stack: React 19, Vite 8, TypeScript 7, Tailwind 4, Radix Tabs, Lucide, React Router, TanStack Query. **No live TanStack queries are used yet.** The reviewed Springdoc → Orval client harness is still maintained separately in `apps/server/contract-client-check`; do not reimplement production DTOs.

## Stop and uninstall

Stop `pnpm dev` with Ctrl+C. You may delete locally generated `apps/web/node_modules` and `apps/web/dist`; do **not** remove existing Docker Compose stacks, `spark-postgres`, `spark-redis`, PostgreSQL schemas, shared volumes, audit data or keys.

Building a static preview is **not** permission to deploy a monitoring console. Browser login/session, REST/WSS scope checks, TLS/CSRF, and PostgreSQL integration acceptance must pass before connecting real data.
