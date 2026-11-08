# NXT-18 — B+C visual-system refinements and responsive screenshot evidence

**Date:** 2026-11-08  
**Project:** `apps/web-next` (Next.js 16.4.5 / React 19 / TypeScript 7 / Base UI 1.9.0).  
**Scope:** The disconnected preview, source-only CSS governance and responsive visual quality. **This does not activate authentication, business data, PostgreSQL/Redis, real Agent access or device operations.**

## Theme refactor: exactly scoped CSS edits

The NXT-17 consolidation reduced app-level CSS imports to `globals.css` plus `lanveriq-bc.css`. NXT-18 evaluated all blocks in the latter, distinguishing exact selector duplicates **within the same media-query scope** from legitimate same-selector variants across different responsive conditions.

The initial audit found **10 duplicate same-scope selector groups**. NXT-18 resolved them:

- Removed NXT-13 `body` and `::selection` declarations superseded by the active B+C theme.
- Combined the `Card` / `CardTitle` / `CardDescription` semantic slot declarations in one authoritative B+C rule each, retaining the required border, background, typography line-height and letter spacing.
- Removed unused striped 22px connectivity legend-bar styling and the previous two-column status legend, now superseded by the neutral, data-honest B+C connection ring.
- Joined duplicate device table heading and cell appearance declarations in the existing compact table style blocks.
- Consolidated duplicated `@media (max-width:950px)` hero layout rules without changing their values.

The resulting single-theme stylesheet has **zero exact duplicate rule keys in the same responsive context**. The new dependency-free `scripts/verify-css-structure.mjs` walks CSS blocks and detects any regression. `scripts/verify-route-contract.mjs` runs that check in the existing safe source-only CI gate.

**Important limitation:** This does not mean no declaration of a property appears in different selectors, component classes, or different media breakpoints. Those are often necessary for the CSS cascade. Avoid a blind regex-based deduplication of semantically distinct selectors.

## Browser and screenshot process

The new `scripts/capture-visual-qa-chromium.py` executes only on GitHub Actions, against **127.0.0.1:3100**, immediately after the existing 11-route disconnected real-Chromium acceptance and **before** any synthetic directory fixture route is mounted.

It visits these five primary screens:

- `/overview` (dashboard entry point)
- `/devices` (compact asset workspace)
- `/monitoring` (read-only status)
- `/design-system` (brand components/tokens)
- `/login` (disabled credential preview)

At **390×844 mobile, 768×1024 tablet, 1440×900 desktop**, it records exactly **15 full-page PNGs** plus a `manifest.json` with page geometry and computed brand colors. GitHub Actions `actions/upload-artifact@v4` retains the screenshots for **7 days** and provides them in the run's artifact section.

**Hard assertions:** each page returns HTTP 200, contains visible H1, stays inside the viewport without document-level horizontal overflow, has canonical B+C background and violet tokens, shows the disconnected-only warning, and never makes requests outside isolated Next.js. The login capture must keep both credential inputs disabled and contain no form. Desktop/tablet sidebar and mobile Dialog trigger are checked separately. Device list must remain above the fold at desktop. No session cookies or external requests are accepted.

**What this does and does not prove:** This combines **automated layout/token regression checks** with **visual review screenshots**. It is not a golden-image pixel-diff system. The screenshots should be inspected during design review; only after a stable, approved baseline should a meaningful pixel threshold be introduced. Do not claim subjective color harmony, screen-reader quality or pixel-for-pixel parity is fully automated.

## Data isolation

- Captures use only the ordinary public disconnected preview and have no account, device or synthetic-fixture records.
- `/synthetic-directory-probe` remains generated only during a **separate, later CI step** behind the NXT-17 temporary-route gate; it is never included in visual artifacts or GitHub Pages.
- No backend, network proxy, agent or telemetry source is activated by screenshot capture.
- Artifact uploads contain **only** preview screenshots, computed layout geometry, a manifest and explanatory text; they never include headers, cookies, credentials or full browser traces.

## Verified NXT-18 final acceptance — 2026-11-08

The audited CSS and follow-up mobile adjustments have now passed **all four final gates**:

| Gate | Final result |
| --- | --- |
| [Next.js isolated source/lock/build/type/unit/11-page HTTP #42443164528](https://github.com/kunlong-luo/lanveriq/actions/runs/42443164528) | **SUCCESS** |
| [Real Chromium routes, 320–1440px, NXT-17 directory fixture, NXT-18 screenshots #42443164574](https://github.com/kunlong-luo/lanveriq/actions/runs/42443164574) | **SUCCESS** — emitted `NXT-18 VISUAL QA PASS: 15 screenshot captures` and `NXT-17 SYNTHETIC DIRECTORY CHROMIUM PASS` |
| [Static Pages publish, disconnected preview only #42443164765](https://github.com/kunlong-luo/lanveriq/actions/runs/42443164765) | **SUCCESS** |
| [Caddy / Spring / PostgreSQL 18 / Chromium multi-admin synthetic HTTPS #42443133166](https://github.com/kunlong-luo/lanveriq/actions/runs/42443133166) | **SUCCESS** — edge HTML/RSC, mTLS, identity, cache purge and RBAC |

The [visual artifacts from Chromium run #42443164574](https://github.com/kunlong-luo/lanveriq/actions/runs/42443164574) contain 15 full-page screenshots plus `manifest.json`; the artifact is retained seven days (through November 15, 2026). These snapshots contain no real business data and are **not** an approved golden baseline.

### Follow-up fixes found by reviewing real screenshots

The initial 15-image audit revealed two practical mobile layout defects, so NXT-18 did not stop at simply generating screenshots:

1. At **390px** the four status cards were unnecessarily tall as four single-column rows. A new `@media (min-width:360px) and (max-width:430px)` rule restores a compact **two-column** KPI grid (two rows). At **320px** the existing single-column mode is preserved for readability. The full-page mobile overview height reduced from approximately **3318px to 2891px** in the same Chromium capture environment.
2. The device-group `NativeSelect` inherited a late `max-width:245px` override, resulting in a narrower control than the search field. A properly scoped mobile rule now makes the select and its wrapper **100% width**.
3. The mobile menu trigger increased from **39×39px to 44×44px** and the navigation close target is also at least 44px.

`verify-preview-chromium.py` now asserts the KPI column count at 320/390px, the device group/select width against the search box at phone widths, and the mobile menu target geometry in addition to the existing Escape/focus checks. The final screenshots were captured **after** these adjustments and reviewed for content flow.

The ZIP provides a disconnected-only offline gallery (separately packaged for design review), with a simple index allowing users to switch between three widths and five screens. This user-facing gallery does not modify the runtime app or replace the original design prototype.

## Follow-on tasks

1. Compare the 15 screenshots with the independently approved B+C prototype and record specific discrepancies.
2. Correct spacing, density and mobile interaction defects without turning on backend functions.
3. Introduce a reviewed golden-image comparison strategy (platform-specific font stability and controlled baseline updates) only after the visual design has been approved.
4. Continue improving screen-reader behavior and keyboard accessibility in addition to screenshot checks.
