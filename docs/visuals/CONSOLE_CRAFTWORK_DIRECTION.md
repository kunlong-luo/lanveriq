# NXT-21 — Craftwork-inspired Lanveriq Console

**Date:** 2026-11-21. **Scope:** disconnected `apps/web-next` visual showroom. **CI:** new source revision pending acceptance.

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

## Safety and verification

The public Pages preview is disconnected and static: no business API, Spring login, credentials, cookies, PostgreSQL, Redis, Agent, remote access, collection, automatic recovery or certificate issuance. All production gates remain OFF; v0.1 NOT RELEASE READY, Issue #5 open.

For this exact functional SHA, require frozen pnpm/source/route tests, TypeScript and Next build, real Chromium 11-route and 320–1440px checks, 15 isolated visual screenshots and manifest, Pages static-only export, plus RD-2 fail-closed. Earlier green CI does not attest to this UI change.
