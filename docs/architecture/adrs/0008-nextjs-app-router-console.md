# ADR-0008 — Next.js App Router as the future LAN console

**Date:** 2026-11-01  
**Status:** Maintainer-selected migration target; implementation and deployment gates **OPEN**  
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
