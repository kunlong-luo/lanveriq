# NXT-28 — Compact-height navigation and unified mobile drawer

**Date:** 2026-11-21 · **Branch:** `main` · **Status:** four exact-SHA synthetic UI/guard CI checks passed; this is not a production release.

## Rationale

The single canonical `apps/web` Next.js UI already hides the document's native right-edge scrollbar and uses a Base UI Dialog for mobile navigation. But its desktop sticky sidebar did not explicitly scroll within a short browser window, and the mobile sheet still inherited the older lavender visual treatment. At a 520px-high viewport, the final navigation items or safety notice could fall below the visible area without a clear scroll contract.

## Implementation

- Scope vertical `overflow-y: auto` and `overscroll-behavior-y: contain` to the **desktop sidebar**; hide only its native scrollbar. Do not set `overflow: hidden` on `html` or `body`.
- Keep the mobile Base UI Dialog and its existing `overflow-y: auto`, focus trap, Escape dismissal and trigger-focus restoration. Hide only the sheet scrollbar while preserving wheel, touch and keyboard navigation.
- Replace mobile-only lavender backgrounds/selected-row treatments with the same white `#FFFFFF`, ink `#172235` and neutral borders as the desktop sidebar. No new component library, custom focus implementation or business API.
- Leave `/login`, business data, identity and security state unchanged. The ordinary preview remains disconnected.

## Automated acceptance

`scripts/test_console_visual_source.mjs` checks the CSS and that the compact Chromium gate remains called. `scripts/verify-preview-chromium.py` now exercises **1024 × 520** desktop and **390 × 520** mobile pages: scrollable navigation, last-route reachability, hidden native scrollbars, mobile selected token, Escape focus restoration, blocked external/API/WebSocket requests and absent cookies. Existing 320–1440 responsive and 11-route checks remain.

**NXT-28 exact-source acceptance: FOUR SUCCESS.** [functional SHA `e76cd345`](https://github.com/kunlong-luo/lanveriq/commit/e76cd34505dd8e49833ab9c0b61421288c43c353) passed FOUR same-SHA checks: [Next.js L1 #44882686781](https://github.com/kunlong-luo/lanveriq/actions/runs/44882686781), [real Chromium #44882686713](https://github.com/kunlong-luo/lanveriq/actions/runs/44882686713), [disconnected Pages build/deploy #44882686808](https://github.com/kunlong-luo/lanveriq/actions/runs/44882686808), and [RD-2 default-OFF #44882686775](https://github.com/kunlong-luo/lanveriq/actions/runs/44882686775). Chromium screenshot artifact: `nxt18-visual-qa-e76cd34505dd8e49833ab9c0b61421288c43c353` (ID `14698677524`). The browser run includes 11 disconnected routes, responsive checks, and the new 520px-high desktop/mobile scroll and focus tests. The initial 8b31ae9d and diagnostic aec487f4 attempts were superseded after the existing wheel-scroll test was corrected to target the main content instead of the newly independently scrollable sidebar. Only `e76cd345` is the accepted functional SHA. None of these checks authorizes live login, devices, remote operations or certificate issuance.

## Local Pop!_OS 24.04 verification

```bash
git status --short
git switch main
git pull --ff-only origin main
cd apps/web
corepack enable
corepack pnpm install --frozen-lockfile
corepack pnpm dev
```

Open `http://127.0.0.1:3100/overview` and resize the window below 600px in height. At desktop widths, scroll the white left sidebar to the last link. Below 750px width, open the mobile menu, scroll within it and press Esc; focus should return to the menu control. If an old Vite/5173 server still runs on the local machine, stop only that old process after inspecting its owner, without deleting local data.

**Release boundary:** official FIDO MDS provenance, approved trust roots/time/revocation, real administrator WebAuthn MFA and physical Pop!_OS 24.04 acceptance are open. Production networking, inventory collection, auto-recovery, remote sessions and CA signing remain OFF. **v0.1 NOT RELEASE READY.**
