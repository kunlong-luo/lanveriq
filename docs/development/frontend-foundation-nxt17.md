# NXT-17 — Consolidated B+C theme and isolated authorized-directory interaction acceptance

**Date:** 2026-11-08  
**Scope:** Canonical `apps/web-next` Next.js 16.4.5 + React 19 + TypeScript 7 + Tailwind 4 + Base UI 1.9.0.  
**Release boundary:** Public GitHub Pages continues to be a disconnected, noncredentialed design preview. No real PostgreSQL/Redis/Spring/Agent reads and no device operations.

## Design and CSS decision

The approved B+C design remains unchanged. The previous three ordered imports in `src/app/layout.tsx` (`globals.css`, `craftwork-theme.css`, `lanveriq-bc.css`) have been reduced to **two**:

1. `globals.css`: Tailwind 4 import, global reset and old foundational layout styles.
2. `lanveriq-bc.css`: historical NXT-13 presentation rules **first**, followed by the current B+C rules **in their original order**. The legacy `craftwork-theme.css` file was removed after its styles were migrated. Canonical semantic tokens remain declared only in B+C's own `:root`.

Moving the historical rules **without reordering** preserves the intended CSS cascade and avoids a second independent theme import. The exact legacy artistic geometries (`.hero-art-grid`, `.hero-art-orbit`, `.hero-art-core`, `.hero-art-marker`, `.hero-art-note`, `.hero-kicker-icon`, `.hero-preview-tag`) were removed because the current overview uses separate `bc-illustration-*` markup. This eliminates ~1.9 KB of obsolete CSS. The source-only route contract now rejects reintroducing `craftwork-theme.css` or its import, a missing consolidated theme marker, missing semantic tokens, and regression to the oversized promotional device hero.

**Technical debt:** This is **file/import consolidation and selective dead-rule cleanup**, not comprehensive selector-level deduplication. Many foundational selector rules in the early style blocks remain intentionally in place to avoid subtle specificity and breakpoint regressions; review their removal in future small, screenshot-verified increments.

## Device directory behavior

`DeviceDirectory` remains a local view over a **single server-authorized page**. Its `filterDirectoryRows` never requests additional records, and no total beyond the current page may be inferred from its row count.

- If an operator selects a device group and a subsequently authorized page no longer includes it, `normalizeAuthorizedGroupSelection` makes the group empty immediately; a React effect clears the stale selector.
- A filter reset clears search and group selections, then restores keyboard focus to the search input, because the reset button disappears after the action.
- Any transition to `unconnected`, `signed_out`, `forbidden` or another nonready state hides authorized rows, disables filtering and clears filter terms.
- No change adds credentials, network calls, polling, fake device counts or Agent operations.

## Real-browser interaction gate without public fake data

New **unmounted** `src/synthetic-only-directory-probe.tsx` contains short, explicit **CI-only** synthetic fixtures. It is intentionally outside the App Router; no public Next.js route imports it.

The `scripts/install-synthetic-directory-fixture.mjs` installer will create a disposable `src/app/synthetic-directory-probe/page.tsx` only when **all** are true:

- The runner is GitHub Actions (`GITHUB_ACTIONS=true`).
- `LANVERIQ_NXT17_BROWSER_FIXTURE=1` is explicitly set for the synthetic-only step.
- The working directory is `apps/web-next`, the static preview routes already exist, and the test route has not been mounted.
- The ordinary preview has first passed frozen dependency installation, source isolation, unit tests, Next.js production build and the **real Chromium disconnected UI** checks.

The browser workflow then runs a **second temporary build** and starts the generated route only on `127.0.0.1:3100`. Real Chromium checks:

1. Authorized-only search, group filter, matching and no-results messages.
2. Reset button restores focus to the search field; rows return.
3. Switching to a new authorized page with different groups clears the obsolete selection and never renders previous-page rows.
4. Revocation removes rows immediately and disables search and group selection.
5. Reauthorization restores only the new authorized page, without stale filters.
6. 390px and 1440px no document overflow, no cookies and **no network requests outside local isolated Next.js**.

A shell cleanup trap removes the generated `src/app/synthetic-directory-probe` directory and terminates the ephemeral Next.js process. The published GitHub Pages job always checks out the **unchanged repository** and builds without this test route.

The ordinary `scripts/verify-route-contract.mjs` explicitly **rejects** a committed `src/app/synthetic-directory-probe` folder or any runtime source importing the unmounted fixture.

## Verified automated acceptance (2026-11-08)

All relevant CI workflows completed successfully **after the NXT-17 source changes**:

| Checked surface | Evidence |
| --- | --- |
| [Next.js isolated preview checks #42437031698](https://github.com/kunlong-luo/lanveriq/actions/runs/42437031698) | **SUCCESS** — frozen pnpm 12 lock, source/route safety, Vitest, TypeScript 7, real production build and 11-page HTTP checks |
| [Next.js real Chromium + disposable directory #42437192574](https://github.com/kunlong-luo/lanveriq/actions/runs/42437192574) | **SUCCESS** — ordinary 11 pages, mobile keyboard/focus and no network/cookies; **NXT-17 SYNTHETIC DIRECTORY CHROMIUM PASS** for both 390/1440px, authorized page search/group/clear, scope replacement, revoke and restore |
| [Public GitHub Pages deployment #42437031767](https://github.com/kunlong-luo/lanveriq/actions/runs/42437031767) | **SUCCESS** — published disconnected Next.js only |
| [Caddy/Tomcat/Spring/PG18/multi-admin browser #42437080815](https://github.com/kunlong-luo/lanveriq/actions/runs/42437080815) | **SUCCESS** — synthetic origin isolation, authenticated HTML/RSC, no-store, cookie/session/CSRF, edge static identity and two-admin group RBAC |

A live check returned HTTP 200 for the disconnected `/devices/` page and **HTTP 404** for `/synthetic-directory-probe/`, confirming that the fixture never reached the publicly deployed Pages site.

The first fixture run failed **only** because the previous browser step killed the pnpm launcher without killing its remaining Next.js child process, leaving port 3100 occupied. The browser workflow was corrected to launch both Next servers in dedicated `setsid` process groups and terminate the entire group at step exit. The resulting [re-run #42437192574](https://github.com/kunlong-luo/lanveriq/actions/runs/42437192574) fully passed. This was a CI cleanup issue, not a service authorization failure.

## Acceptance and remaining work

- Normal GitHub Actions: `verify:routes`, Vitest, strict typecheck, production Next, 11-page HTTP smoke and Pages static export.
- Browser GitHub Actions: production Next, 11-page/320–1440px/keyboard/Base UI checks, then disposable fixture and its independent browser test.
- Caddy/Spring/Tomcat/PostgreSQL18: synthetic-only TLS, session, CSRF/RBAC and cross-admin edge isolation (not production approval).
- Subsequent follow-up: further remove redundant CSS declarations by measured visual comparison, add authorized server-side paging only after independent API/RBAC approval, and conduct assistive-technology and human visual QA.

**Do not infer production readiness or live device status from these synthetic tests.**
