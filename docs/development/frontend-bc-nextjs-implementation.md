# NXT-14 — B+C approved design prototype to Next.js implementation

**2026-11-07. Scope:** First implementation pass translating the standalone `lanveriq-bc-interactive-prototype.html` into the canonical `apps/web-next` Next.js 16.4.5 App Router. The user explicitly requested design-before-code, reviewed the B+C prototype and then asked to continue. **Visual implementation does not constitute business or production approval.**

## Source of truth and intent

- **B:** Enterprise information architecture, accurate device inventory, readable monitor and security status, keyboard and mobile flows.
- **C:** Original lavender/blue/lime identity, light workspace, restrained bento cards, distinct hierarchy and CSS-only geometric artwork.
- The supplied local standalone HTML remains a **separate design interaction sample**. It provides browser-only synthetic fixture interactions, not a license to turn on real or synthetic device data in the public production-candidate app.
- Do not copy Craftwork paid assets, templates, images or fonts; visual language is original. Do not import third-party code simply to recreate a CSS visual.

## Implemented public Next.js routes

| Path | Purpose | Runtime |
| --- | --- | --- |
| `/overview` | Approved light B+C hero, four unknown metrics, empty activity trend, inventory, directory | Disconnected read-only |
| `/devices` | Asset browsing layout, explicit disabled search/actions until authorized data | Disconnected read-only |
| `/devices/[id]` | Existing UUID-format checked detail and disabled operations | Disconnected read-only |
| `/monitoring` | Connection, trend and freshness design; empty real state | Disconnected read-only |
| `/enrollment` | Existing safety-gated mTLS enrollment explanation | Disabled |
| `/settings` | Existing public-safe engineering status and release gates | Read-only |
| `/alerts` | Alert architecture / exact disconnected state | **Planned design-only** |
| `/policies` | Policy workflow architecture / exact disconnected state | **Planned design-only** |
| `/reports` | Authorized reporting architecture / no fake graph | **Planned design-only** |
| `/design-system` | Brand color, typography, spacing and component/semantic-state design | **Design-only** |
| `/login` | Independent branded split visual and inert credential fields | Disabled; NO form submission |

The four planned modules are visually identified in the canonical `src/lib/console-navigation.ts` as `planned:true`. Their route existence is **not** an indication that Spring endpoints, permissions, database schemas, reports or alert jobs exist.

## Component and styling architecture

- `src/app/lanveriq-bc.css` is a **temporary override layer** imported after the existing `craftwork-theme.css`. This preserves historical pass gates while translating the approved prototype. Once browser screenshot comparison is complete, consolidate the stacked style sheets into an isolated token/system layer instead of accumulating additional overrides.
- `src/components/console-shell.tsx` drives all navigation from one typed list, maintains accessible `aria-current`, supports a dismissible mobile drawer with Escape and a backdrop, and keeps global search / real notifications disabled.
- `src/app/(console)/overview/page.tsx` uses CSS-only, `aria-hidden` decorative laptop/shield/server artwork and **does not** show real device figures.
- `src/components/health-sections.tsx` adds `ActivityTrend`, an accessible labeled **empty** trend frame (no fabricated curve), while keeping connections, inventory and queue independent.
- `src/components/planned-console-page.tsx` provides a reusable, server-rendered informational layout for planned-only modules without synthetic rows, actions, credentials, external data or browser storage.
- `src/components/login-preview.tsx` preserves `type="text" disabled`, `type="password" disabled`, the disabled login button, explicit no-credentials banner and **no `form`**.

## Acceptance and non-goals

- GitHub Actions Next.js preview workflow automatically exercises Next.js typecheck, unit tests, production SSR HTTP, root redirect, all 11 routes, disabled credential and API boundaries and Pages static-export build.
- Actual Playwright Chromium gate checks desktop and 320 / 390 / 768 / 1024 / 1440px viewports, keyboard navigation, reduced-motion preferences, mobile drawer open/route-close behavior, real B+C light hero style, four unknown metrics, empty chart, planned-only disabled routes and zero cookies/external API/WebSocket requests.
- Isolated Caddy + Spring + PostgreSQL 18 + real Chromium security acceptance runs for changes to canonical routes, shell and the final B+C theme; this is **synthetic-only**.
- Pending after this phase: subjective visual-parity screenshot review, full assistive-technology checks, local demo fixture interactions moved to production-compatible architecture only under an explicitly isolated opt-in design-preview boundary, production Node/Caddy topology, real user login, authorized device reads and real Agent operations.
- **Never connect public Pages to real sessions or business data.** The GitHub Pages deployment is only a public disconnected static design preview; standalone private Node deployment and original synthetic security probes are separate.

## Next implementation sequence

1. Inspect and polish live screenshots at desktop, tablet and mobile, repair any visual overflow and consolidate tokens.
2. Improve React component reuse for tables/filters/drawer focus semantics, while keeping unauthorized data invisible and actions disabled.
3. Review product flows / roles / organizational privacy with the maintainer.
4. Prepare actual authenticated read-only integration under separate security approval and only then consider real operational pages.
