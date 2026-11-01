# NXT-03 — source-generated contract and live read-only browser gate

**Status: NXT-03A compile/unit stage PASSED; NXT-03B generated-hook compile and offline cache tests PASSED; NXT-03B real browser/runtime acceptance NOT EXECUTED.** This is not an endorsement of production
login, real devices or continuous monitoring. NXT-01/02 UI remains
synthetic/unconnected by design.

## Build evidence

- [Java CI #41288584413](https://github.com/kunlong-luo/lanveriq/actions/runs/41288584413):
  actual Springdoc OpenAPI generation, Orval 8 model/client generation,
  original Web TanStack Query typecheck and new Next.js
  `tsconfig.contract.json` model-to-display typecheck, no database.
- [Next.js CI #41288628345](https://github.com/kunlong-luo/lanveriq/actions/runs/41288628345):
  Node 24 / frozen pnpm 12, staged projection and fake-fetch unit
  tests, production Next.js build and synthetic route smoke only.
- [Java CI #41289190664](https://github.com/kunlong-luo/lanveriq/actions/runs/41289190664):
  generated **complete Orval Fetch/React Query client** typechecks against
  the locked Next.js app's TanStack Query 5.105 and TypeScript runtime
  dependency graph (not merely the DTOs).
- [Next.js CI #41289252650](https://github.com/kunlong-luo/lanveriq/actions/runs/41289252650):
  real production build and synthetic fake-fetch tests show 401/403/429/503
  transitions can erase all prior session-scoped cached data.
- The candidate transport and generated client are **not imported** by any Next.js page.
  Route guard rejects mounting it prior to manual approval.

## Release-blocking manual checks

| Acceptance area | Required observation |
| --- | --- |
| PostgreSQL 18 | Rerun updated seven-class, >=58-case Testcontainers suite against real PG18; all pass with Git SHA, sanitized JUnit artifact and image provenance |
| Caddy HTTPS | Trusted CA and canonical DNS/SNI/Host isolation; no hostname/Origin bypass, forwarded host injection or TLS exceptions |
| Administrator login | Actual browser CSRF pre-login and post-fixation refresh, secure host-only HttpOnly SameSite session cookie, 401/403/429, logout and epoch revocation |
| Scope | A current enabled administrator only reads current authorized device groups/categories; disabled/revoked/wrong-group must show no rows |
| Empty vs unavailable | A genuine scoped database-empty list displays none; error/offline/invalid JSON never becomes empty or mock data |
| Browser origin | No credential-bearing network requests from localhost, alternate origin, public domain, or Next.js SSR/Server Actions |
| Projection | Never copy secret IP/MAC/employee IDs or unrelated inventory; no fabricated online/queue/heartbeat counts |
| Pagination | Bounded max 100, stable keyset, no cross-group leaks or duplicate/cursor/order errors |
| Reconnect | Session invalidation must clear queries, avoid stale data and require new authorization before resuming |
| Accessibility | Keyboard navigation, labels, responsive table and reduced-motion browser evidence |
| Next.js cutover | Switch default frontend only after route parity and separate browser security tests; remove Vite/proxy deliberately |

**Do not enable Orval React Query hooks or the staged transport in
pages before these release gates pass.** CI typechecking only is not
evidence of an authenticated HTTPS browser, real PostgreSQL, cookie
isolation or a connected device.
