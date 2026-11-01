# ADR-0008 — Next.js App Router as the future LAN console

**Date:** 2026-11-01  
**Status:** NXT-01 candidate and NXT-02 core UI automated checks passed; NXT-03A type/projection staging verified, NXT-03B real runtime, browser, PostgreSQL 18 and cutover gates **OPEN**  
**Scope:** WEB-01 / FE-02 only. No change to agent transport, backend
authorization, database authority or consent requirements.

## Context and decision

The first runnable `apps/web` preview was implemented with React 19,
Vite 8 and pnpm. It uses only synthetic devices and an exact-path anonymous
Spring Boot health check. The maintainer subsequently elected to move
the management frontend to **Next.js App Router**, retaining the established
React, TypeScript, Tailwind, shadcn/ui, Radix, Lucide, TanStack Query and
Springdoc → Orval client approach. This supersedes the earlier *frontend
framework target*, not the D0 transport/privacy/security decisions.

The Next.js migration is a **separate reviewed development milestone**,
not a claim that `apps/web` is already a Next.js application. No change
to production access is authorized by selecting a framework.

## LAN topology and trust boundary

- Caddy remains the independently trusted, purpose-separated HTTPS
  entrypoint. Serve the console hostname with a single authoritative
  origin; preserve strict SNI/Host rules and h1/h2 negative tests.
- Next.js is a presentation server behind Caddy, not an alternative
  authentication provider or a replacement for Spring Security.
- Only the explicitly approved console API paths may be routed by Caddy
  to Spring Boot. **Never** create a blanket `/api/**` Next.js rewrite
  or a catch-all Next.js Route Handler/Server Action that forwards a
  browser's session cookie, CSRF token or password.
- For V0.1, use browser client components for interactive console and
  credential-bearing business reads. The Spring session cookie remains
  Secure, HttpOnly, host-only, SameSite=Strict and owned by Spring;
  Spring enforces CSRF, Origin, RBAC and per-device-group scope on every
  read. Do not use server-rendered authenticated data or static cached
  HTML for administrator/device information.
- Keep business data cache and automatic refresh disabled until the
  actual browser session, authorization and PostgreSQL 18 gates pass.
  When enabled, use TanStack Query only for authenticated, no-store
  server state. Treat WSS as invalidation hints, not authoritative data.
- Redis stays optional and non-authoritative. No external cloud service,
  analytics, worker, real device collection or remote operation is
  implied by introducing Next.js.

## Migration / acceptance sequence

1. **NXT-01:** Create an isolated Next.js App Router candidate, pin an
   officially supported Next.js version with Node 24, React, TypeScript
   and a generated `pnpm-lock.yaml`. Preserve the working Vite preview.
   Do not manually fabricate dependency resolution or bypass a frozen
   install. Establish genuine Next.js build/unit/test CI.
2. **NXT-02:** Port layout, navigation and existing shadcn/ui components;
   keep the synthetic-mode banner and disabled login/operations. Assert
   parity for all six preview routes and responsive layouts. No live API.
3. **NXT-03:** Port the source-generated Orval contract check and staged
   read-only data projection. Accept only canonical same-origin HTTPS
   browser requests; reject malformed pages with no demo fallback. Do
   not introduce SSR credential access.
4. **NXT-04 (separate manual security gate):** Run updated PostgreSQL 18
   tests, Caddy/Spring/browser HTTPS session/CSRF/cookie/RBAC/Origin tests
   and UI end-to-end tests with synthetic administrators. Only then seek
   explicit approval to enable real administrator/device reads.
5. **Cutover:** Switch the default frontend only after the candidate's
   frozen dependency CI, accessibility and route-parity checks succeed.
   Remove Vite dependencies/proxy in the same reviewed cutover, update
   operations documentation, and retain a reversible path. Never claim
   cutover on the basis of source files alone.

## Consequences

Next.js adds a Node.js runtime and potential server-side caching/trust
boundaries to a LAN-only project. These must be measured and hardened rather
than assumed safe. shadcn/ui is React-based and does not itself require
Next.js; migration is a maintainer product preference, **not** a fix for
backend authorization or a shortcut around the currently failing manual
PostgreSQL gate. The existing React/Vite code remains the running preview
until the candidate passes its own tests and is explicitly switched.

## References

- [Frontend design system](../../development/frontend-design-system.md)
- [Console browser authentication acceptance](../../testing/console-session-manual.md)
- [PostgreSQL 18 acceptance](../../testing/server-postgres18-manual.md)
- [Next.js App Router docs](https://nextjs.org/docs/app)
- [Next.js self-hosting and reverse proxy guidance](https://nextjs.org/docs/app/guides/self-hosting)

## NXT-01 observed verification (2026-11-01)

The isolated `apps/web-next` Next.js 16.4.3 App Router application now exists with Node 24, pnpm 12, React 19, TypeScript, Tailwind 4 and editable shadcn/ui-compatible Card components. Its actual `pnpm-lock.yaml` was generated by pnpm in a one-time GitHub Actions job, then committed on `main`. The temporary write-capable bootstrap workflow was **deleted** and replaced with a read-only frozen-install workflow.

[Next.js CI #41287394823](https://github.com/kunlong-luo/lanveriq/actions/runs/41287394823) succeeded at SHA `8add324b869a14488822c4a3076efb77eb257835`: Node 24/pnpm 12 frozen install, route guards, component tests, TypeScript typecheck, Next.js production build and running HTTP smoke tests of seven preview paths, no business proxy and disabled login. This is source/preview acceptance only; it does **not** verify Caddy HTTPS, cookies, Spring Security or real PostgreSQL 18. The original `apps/web` Vite preview remains the default and has not been cut over.

## NXT-02 core-page automated checkpoint (2026-11-01)

NXT-02 implementation now includes usable static/interactable UI
components for all major V0.1 management surfaces. The runtime uses
accurate **unconnected**, not fake **empty**, states; 0 displayed
devices would require a real authorized empty server response, which
has not been enabled. The device table can search/group-filter only
caller-supplied authorized display rows and the detail layout renders
hardware/network/health tabs without private network information.
Registration, certificate revocation, refresh and login remain
disabled. The responsive sidebar/toolbar/table CSS is now migrated,
with a keyboard skip link.

[Automated Next.js CI #41288117979](https://github.com/kunlong-luo/lanveriq/actions/runs/41288117979)
succeeded on `427242286b0af9e5bb3b4d151484c47d296560f8`
including production HTTP route checks and UI state unit tests. NXT-02
still requires actual browser/mobile viewport and keyboard/a11y
acceptance before claiming **full visual parity**. See
[manual NXT-02 matrix](../../testing/nextjs-nxt02-manual.md).
NXT-03 authenticated API integration, PG18/TLS and cutover remain blocked.

## NXT-03A generated-contract staging (2026-11-01)

The Next.js candidate now has dedicated unmounted read-only device-page
projection and HTTPS/canonical-origin transport with explicit 401/403/429
failure states, strict keyset/UUID integrity and no sensitive field
leakage or local fake data. Springdoc is still the only authoritative
wire schema. The [Java CI #41288584413](https://github.com/kunlong-luo/lanveriq/actions/runs/41288584413)
actually succeeded with the generated Springdoc → Orval client and
`apps/web-next/contract-check/typecheck.ts` using the Orval-generated
**model types** against the current Next.js view projection. The Next.js
application does **not** yet have runtime Orval React Query hooks or
session logic; its pages cannot make device API requests. Its source
route guard actively prevents an accidental staged transport import.
This is an implementation milestone, **not NXT-03B runtime acceptance**.
See [manual NXT-03 matrix](../../testing/nextjs-nxt03-manual.md).

## NXT-03B client compilation and offline session policy (2026-11-01)

The Next.js candidate now pins **TanStack Query 5.105** using a
pnpm-generated lockfile. The one-time temporary write-capable dependency
bootstrap was removed. A client-side QueryClient provider is mounted
without queries; by default retries, focus/reconnect refetch and
automatic mount refetch are disabled. A separate helper cancels queries
and clears their cache after a session/principal/permission change;
synthetic tests exercise 401/403/429/503 cleanup with a fake response.
**This is not implemented real browser authentication.**

[Java CI #41289190664](https://github.com/kunlong-luo/lanveriq/actions/runs/41289190664)
has now compiled the **full generated Orval Fetch/TanStack Query
client** with the locked Next.js types and dependencies. [Next.js CI
#41289252650](https://github.com/kunlong-luo/lanveriq/actions/runs/41289252650)
has built the candidate and run in-memory session-cache tests.
The generated client is still CI-only, never committed as stale source
or imported by any Next.js page; it has not been exercised with
Caddy, an authenticated browser, real PostgreSQL or live device data.
NXT-03B runtime acceptance and cutover remain **blocked**.
