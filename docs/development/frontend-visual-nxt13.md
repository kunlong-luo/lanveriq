# NXT-13 — Next.js management console visual refresh

**Date:** 2026-11-07  
**Status:** Original visual refactor implemented, automatically built, deployed as an isolated public preview and verified in real synthetic-only Chromium. **Not production-console approval.**

## Inspiration and rights boundary

The maintainer requested inspiration from [Craftwork](https://craftwork.design/), a design resource catalog with strong editorial hierarchy, roomy layouts and modern curated UI cards. Lanveriq adopts those **general design principles**, not any Craftwork template, copied page, licensed graphic, proprietary font, paid UI kit or screenshot.

All new visual elements are **first-party CSS/JSX**. No remote fonts, images, external CDN scripts, user telemetry, network requests or paid assets were added. The existing React 19 / Next.js 16.4.5 / Tailwind 4 / Lucide / editable Card stack remains pinned; no additional UI package was required.

## Files and design decisions

- `apps/web-next/src/app/craftwork-theme.css` is a separate, reversible presentation layer imported **after** `globals.css` from the root layout. Reuse its warm neutral surfaces, charcoal headings, lilac accents and lime highlight rather than adding another competing CSS theme.
- The navigation layout retains the canonical `src/lib/console-navigation.ts`, real Next.js `Link` navigation, current-page `aria-current`, keyboard skip link and focus-visible rings. The light sidebar and warm header share spacing/radius tokens.
- `src/app/(console)/overview/page.tsx` uses an editorial bento hierarchy: decorative **CSS-only**, screen-reader-hidden hero artwork; prominent heading and two real local navigation links; three grouped sections (status, insights, assets).
- `src/components/health-sections.tsx` retains actual **unknown/unconnected** metrics with `—` placeholders, clearly labeled `未连接` badges and no invented device records, percentages, heartbeat timestamps or counters.
- The other existing App Router pages inherit new card, panel, table, form, status and login-preview styling, while their read-only, disabled, scoped-data security behavior remains unchanged.
- Reduced-motion preferences, visible focus, desktop and 320–1440px responsive layouts remain required. Never make interactive controls purely decorative or remove a disabled business operation to make screenshots cleaner.

## Automated acceptance

- [Next.js source, TypeScript, tests, production HTTP build #42368529860](https://github.com/kunlong-luo/lanveriq/actions/runs/42368529860) **SUCCESS** after the style and settings updates.
- [Real Chromium UI navigation, keyboard, 320–1440px, reduced motion, unknown metrics #42368529843](https://github.com/kunlong-luo/lanveriq/actions/runs/42368529843) **SUCCESS**. The browser gate checks one decorative hero hidden from assistive tech, the exact page heading, four `— / 未连接` metrics and no horizontal overflow, external network, cookies or live API.
- [GitHub Pages public disconnected build & deploy #42368529816](https://github.com/kunlong-luo/lanveriq/actions/runs/42368529816) **SUCCESS**. A live public HTTP fetch confirmed `/overview/` and `/settings/` returned 200 with new hero/layout section copy, the disabled-data disclaimer and production blockers still visible.
- [Real disposable Caddy/Spring/Tomcat/PostgreSQL 18/Chromium security suite #42368550040](https://github.com/kunlong-luo/lanveriq/actions/runs/42368550040) **SUCCESS**. Pass markers include direct Next.js origin isolation, Caddy protected HTML/RSC Spring checks, two synthetic administrator scopes, public JS identity neutrality and logout/session boundary.

Automated browser rendering and DOM/style assertions **are not a substitute for subjective cross-browser visual review**, accessibility testing with assistive technology or a staffed UX acceptance. Those larger release gates remain open.

## Public Pages vs private production

[https://kunlong-luo.github.io/lanveriq/](https://kunlong-luo.github.io/lanveriq/) is an independently exported **public, read-only** Next.js static preview with disabled login and no real device data. Never connect Spring sessions, RSC device data, secrets, credentials, employee monitoring, Agent collection or business APIs to that Pages host. Private Caddy / standalone Next.js / Spring integration requires separately approved production host/network and organizational privacy safeguards.

## Next stages

1. Review responsive screenshots and navigation with real humans / assistive technology and refine spacing or contrast without changing security semantics.
2. Resolve production private Node/Caddy network topology and exact cache isolation on the real deployment host.
3. Only after separate approval, prepare a production-grade administrator login and authenticated read-only data connection backed by Spring Security, generated Orval contracts and explicit RBAC; **default OFF until all release gates pass**.
