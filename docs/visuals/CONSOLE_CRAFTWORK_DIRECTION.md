# NXT-21 — Craftwork-inspired Lanveriq Console

**Date:** 2026-11-21. **Scope:** disconnected `apps/web-next` visual showroom. **CI:** accepted at functional SHA `e43886a3be9524f82961a09f409df3edb7af9b0c` (four independent same-SHA successes).

## Reference and originality

Use https://craftwork.design/ only as a directional reference: whitespace, editorial typography, crisp hairline borders, modular cards and restrained accents. Keep Lanveriq's own layout, routes, components, copy and brand. No copied Craftwork screenshots, images, font files, paid UI kits or unlicensed assets.

The canonical front end is already Next.js 16 App Router / React 19 / Tailwind 4 / Base UI Dialog; Vite is legacy only.

## Visual contract

- Keep verified canvas `#F5F7FA`, white surfaces, ink `#172235`, indigo token `#4D5FC9`, and semantic mint only as *decorative* accent, never proof of online/healthy status.
- Use a light editorial hero, real disconnected-capability labels, a CSS-only geometric motif, restrained rounded surfaces and high-contrast navigation selection.
- Preserve all 11 routes and existing localized headings, device unknown counts (em dash, not invented zeros), no data charts, and disabled actions.
- Responsive: 320px single metric column, 390px double, 768px sidebar at least 220px, 1440px usable directory area. No horizontal overflow.
- Keep skip link, `aria-current`, visible keyboard focus, Base UI mobile focus-managed Dialog and reduced-motion behavior.
- Native right-edge scrollbar is visually hidden, but wheel, touch, keyboard and programmatic scrolling stay enabled.

## NXT-22 — Local workspace command palette (CI PENDING)

- Header search is an enabled **page-only** quick navigation control (desktop and mobile >=44px), with `Ctrl/⌘ + K`, Escape and Base UI Dialog focus handling. It filters only the nine checked-in public `consoleRoutes`; direct device search, login, network calls and session state are deliberately outside the implementation.
- No new dependency, API handler, telemetry or persistent client storage. Device search and all business actions remain disabled.
- Source/route Vitest, visual source gate, full Next.js build, real Chromium keyboard/mobile interactions, disconnected Pages export and RD-2 default-OFF must pass on the **exact functionality SHA** before acceptance. Historical NXT-21 results remain distinct.

## Safety and verification

The public Pages preview is disconnected and static: no business API, Spring login, credentials, cookies, PostgreSQL, Redis, Agent, remote access, collection, automatic recovery or certificate issuance. All production gates remain OFF; v0.1 NOT RELEASE READY, Issue #5 open.

**Verified at the exact functional SHA:** [Next fast #44873138900](https://github.com/kunlong-luo/lanveriq/actions/runs/44873138900) (frozen pnpm, source/route tests, TypeScript/build), [real Chromium #44873138797](https://github.com/kunlong-luo/lanveriq/actions/runs/44873138797) (11 routes, 320–1440px, keyboard/mobile and 15 isolated screenshot artifacts), [static-only Pages #44873138865](https://github.com/kunlong-luo/lanveriq/actions/runs/44873138865), and [RD-2 OFF #44873138989](https://github.com/kunlong-luo/lanveriq/actions/runs/44873138989). Earlier SHAs and later docs-only commits do not replace this acceptance. This does not approve physical-device/production L3.
