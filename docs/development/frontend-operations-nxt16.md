# NXT-16 — Operational density and canonical B+C tokens

**Date:** 2026-11-07  
**Runtime:** `apps/web-next` Next.js 16.4.5 / React 19.2 / Base UI 1.9.0 / Tailwind 4 / shadcn-style source-owned components.  
**Scope:** Public isolated design preview only. **No production login, real API, business data, authorized session or Agent functions enabled.**

## Product design decision

The accepted B+C direction remains the product-wide visual language, with **two distinct screen priorities**:

- **`/overview`** is the editorial entry point: original lavender geometric artwork, status cards, empty chart and navigation.
- **`/devices`** is a daily operational workspace: compact header, clear read-only/connection state and authorized-list UI **above the fold**, instead of a second oversized hero graphic.
- Monitoring remains an evidence-driven status view. No line chart or percentage is invented when actual backend evidence is absent.

This is deliberate enterprise information hierarchy; copying a showcase/marketing homepage to every business screen reduces task throughput.

## Implemented changes

1. `src/app/globals.css` now defines only base CSS reset and font smoothing. `src/app/craftwork-theme.css` keeps original presentation rules but no longer declares conflicting brand variables.
2. `src/app/lanveriq-bc.css` is the **single source** of semantic tokens including colors, spacing radii, font fallback and easing. The canonical build-time `scripts/verify-route-contract.mjs` now fails if a token is duplicated in an older theme or removed from B+C. **Note:** This normalizes the theme token source, **not** the remaining CSS selector layers. Do not claim all CSS has been merged.
3. `src/app/(console)/devices/page.tsx` replaces the redundant large illustration with an operations-first header and one useful local route to enrollment. Add device stays explicitly disabled.
4. `src/app/lanveriq-bc.css` tightens table row/heading spacing, places overflow inside the semantic table container and preserves narrow-screen readability. At mobile widths the page itself must not horizontally scroll.
5. `src/components/device-directory.tsx` retains server-owned `DirectoryState`; on unauthorized/disconnected transitions its **local filter terms are cleared**. Search/group filters operate **only on the caller-supplied authorized page**. The label reads `匹配 x / n` in the current page, never a claimed organization-wide count. The clear-filters button is interactive only for an authorized page with active filters.
6. `src/components/device-directory.test.tsx` checks current-page-only counts, semantic table/caption, denied/failed/throttled states, absence of retained rows and no fake global totals.
7. `scripts/verify-preview-chromium.py` checks the compact asset header, disabled search/group/add-device controls, a canonical computed theme token, desktop asset list placement and 320/390/768/1024/1440px viewport fit.

## Automatic acceptance

| Gate | Result |
| --- | --- |
| [Next.js source safety, Vitest, strict TypeScript, production SSR checks #42379234795](https://github.com/kunlong-luo/lanveriq/actions/runs/42379234795) | **SUCCESS** |
| [Real Chromium keyboard, Base UI dialog/tabs, 320–1440px layouts and zero external business requests #42379234786](https://github.com/kunlong-luo/lanveriq/actions/runs/42379234786) | **SUCCESS** |
| [Disconnected static Next.js Pages deploy #42379234856](https://github.com/kunlong-luo/lanveriq/actions/runs/42379234856) | **SUCCESS** |
| [Synthetic-only Caddy/Spring/Tomcat/PostgreSQL 18/Chromium edge/RBAC #42379148828](https://github.com/kunlong-luo/lanveriq/actions/runs/42379148828) | **SUCCESS**, source revision includes all UI/runtime changes |

The public `/overview/`, `/devices/`, and inert `/login/` URLs also responded HTTP 200 with unconnected-data notices. Public preview: https://kunlong-luo.github.io/lanveriq/ . None of the above gates prove production-network readiness.

## Next improvements

- Consolidate the **remaining duplicate presentation selectors** among `globals.css`, `craftwork-theme.css`, and `lanveriq-bc.css` without silently changing behavior or visual hierarchy. The single-source token guard must continue to pass.
- Review actual desktop/mobile screenshots and assisted-tech navigation with humans; browser DOM and overflow checks are not complete subjective visual acceptance.
- When a separately approved, authenticated backend data contract exists, introduce TanStack Table for **server-scoped** sorting and pagination; until then, never fake global totals from partial pages and do not show disconnected synthetic records.
- Keep production login, agent data, privileged device controls, credential capture and public Pages API calls **disabled by default**.
