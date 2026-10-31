# Backend OpenAPI → Orval contract smoke (NOT a frontend application)

Only the **real Springdoc-generated** `../build/contracts/openapi.json` is accepted
as input. Run the Java 25 / Gradle `test` task first, then from this
directory run `pnpm install --frozen-lockfile` against the committed
`pnpm-lock.yaml`, followed by `pnpm verify`.

This temporary build-only harness uses the mature Orval 8 Fetch + TanStack
Query generator and TypeScript; it does not include React pages, live login,
Caddy TLS, PostgreSQL sessions or production permission. Generated output
is ignored, and the CI artifact is **not deployed**.

A valid result means only that Orval can generate and compile a **candidate**
typed client from the backend contract. It is not a UI or API acceptance.
