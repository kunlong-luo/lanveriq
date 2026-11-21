# NXT-28 — Compact-height navigation and unified mobile drawer

**Date:** 2026-11-21 · **Branch:** `main` · **Status:** source implementation submitted for CI; this is not a production acceptance or release.

## Rationale

The single canonical `apps/web` Next.js UI already hides the document's native right-edge scrollbar and uses a Base UI Dialog for mobile navigation. But its desktop sticky sidebar did not explicitly scroll within a short browser window, and the mobile sheet still inherited the older lavender visual treatment. At a 520px-high viewport, the final navigation items or safety notice could fall below the visible area without a clear scroll contract.

## Implementation

- Scope vertical `overflow-y: auto` and `overscroll-behavior-y: contain` to the **desktop sidebar**; hide only its native scrollbar. Do not set `overflow: hidden` on `html` or `body`.
- Keep the mobile Base UI Dialog and its existing `overflow-y: auto`, focus trap, Escape dismissal and trigger-focus restoration. Hide only the sheet scrollbar while preserving wheel, touch and keyboard navigation.
- Replace mobile-only lavender backgrounds/selected-row treatments with the same white `#FFFFFF`, ink `#172235` and neutral borders as the desktop sidebar. No new component library, custom focus implementation or business API.
- Leave `/login`, business data, identity and security state unchanged. The ordinary preview remains disconnected.

## Automated acceptance

`scripts/test_console_visual_source.mjs` checks the CSS and that the compact Chromium gate remains called. `scripts/verify-preview-chromium.py` now exercises **1024 × 520** desktop and **390 × 520** mobile pages: scrollable navigation, last-route reachability, hidden native scrollbars, mobile selected token, Escape focus restoration, blocked external/API/WebSocket requests and absent cookies. Existing 320–1440 responsive and 11-route checks remain.

Required exact-SHA evidence after the code lands: Next.js frozen pnpm/Vitest/source/typecheck/build/HTTP, real Chromium, isolated static Pages and RD-2 fail-closed. Do not claim a gate passed before its GitHub run completes; prior UI checks do not transfer to a new commit.

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
