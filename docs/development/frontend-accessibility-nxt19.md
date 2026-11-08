# NXT-19 — Accessible asset directory and authorized-page interaction

**Date:** 2026-11-08  
**Implementation:** `apps/web-next` Next.js 16.4.5 / React 19 / Base UI 1.9.0 / shadcn-style source-owned controls.  
**Release boundary:** The public static Next.js preview remains disconnected. No real login, session, business API, Agent operations, database access or user data is enabled.

## Scope

Following NXT-18 CSS/visual acceptance, NXT-19 focuses on **keyboard, screen-reader table semantics, mobile touch targets and task efficiency** rather than adding decorative UI or speculative operational capabilities.

### Table semantics and keyboard navigation

- In `DeviceDirectory`, a device's name/OS cell is now a native `<th scope="row">`, linked semantically to the rest of the row; column headers continue using `<th scope="col">`.
- `Table` gains two opt-in props, `scrollRegionLabel` and `scrollRegionDescriptionId`. **Only** tables requiring horizontal scroll use the added `role="region"`, label and `tabIndex={0}` around the semantic table. Ordinary card tables remain unchanged.
- In authorized-list state, a visually presented phone-width hint explains that the seven-column table scrolls horizontally. A keyboard user can focus the region and navigate it with arrow keys. This hint and the table itself are both **absent** when logged out, forbidden or disconnected.
- The global skip link points to the `main#main` landmark with `tabIndex={-1}`, making its destination programmatically focusable.

### Authorized-page-only ordering

The `DirectorySort` model offers **server order (default), device name ascending/descending, and group ascending**, backed by `sortAuthorizedDirectoryPage()`.

- Every operation sorts a **copy** of the *single existing server-authorized page* after local filters; it never queries other pages, edits source data, changes permissions or infers total devices.
- `Intl.Collator('zh-CN', {numeric:true, sensitivity:'base'})` provides natural, case-insensitive mixed Chinese/numeric display ordering. Equal values retain their original page sequence.
- Sorting is a labeled native `select`, **disabled unless `state.kind === 'ready'`** and reset to server order on revocation/disconnection. New pages still depend on explicit upstream authorized data, not client-side fake global pagination.
- Search and group selectors are wrapped in neutral layout containers rather than nesting a non-phrasing `div` inside `label`; their native selects carry explicit accessible names.
- On mobile, search, group, sort and clear-filter controls have minimum **44px touch-target heights**.

### Automated acceptance

Unit tests cover stable local sorting, numeric natural order, input immutability, row/column table header associations, role/label/keyboard focus region, and absence of rows or scroller without authorization.

The **disposable NXT-17 synthetic-directory Chromium route**, generated only in GitHub Actions and never present in public Pages, extends its test coverage for:

1. Local sort descending and restoration to server order on a two-row synthetic page.
2. Authorized `<th scope="row">` and keyboard-focusable horizontally scrollable region; actual ArrowRight scroll at 390px.
3. 44px phone-width search/group/sort controls.
4. Existing search/group/no-results/clear focus, new-page group normalization and revocation/re-authorization.
5. Sort disabled/reset when authorization is withdrawn.
6. Strict loopback-only requests, no cookies, no real devices and no real API.

The public production-preview Chromium test also checks disabled sort without data, the skip-to-main focus target and the Base UI mobile modal's 44px close button. Existing visual screenshot capture continues to run against the **ordinary, disconnected routes before any temporary fixture is mounted**.

### Acceptance status and security-test contract

- [NXT-19 Next.js build, TypeScript and unit tests #42444319842](https://github.com/kunlong-luo/lanveriq/actions/runs/42444319842): **SUCCESS**.
- [NXT-19 real Chromium, 11 disconnected routes, 15 screenshots and authorized-directory fixture #42444319741](https://github.com/kunlong-luo/lanveriq/actions/runs/42444319741): **SUCCESS**. Log includes `NXT-19 SYNTHETIC DIRECTORY CHROMIUM PASS` (sorting/row headings/horizontal keyboard scrolling/revocation).
- [Disconnected Pages #42444319706](https://github.com/kunlong-luo/lanveriq/actions/runs/42444319706): **SUCCESS**, with no synthetic device fixture published.
- The first [Caddy/Spring/PG18 synthetic gate #42444298037](https://github.com/kunlong-luo/lanveriq/actions/runs/42444298037) failed at the existing **exact-scoped-membership** assertion. Analysis found that this test still selected `td:first-child strong`, while the canonical device identity is now correctly rendered as `th[scope='row'] strong`. It did not establish a data leakage, although it could not prove the membership until fixed. `apps/server/scripts/verify-synthetic-console-browser.py` now targets that semantic row header and **retains every existing A/B exact-membership/count/uniqueness assertion**. [Full repeated Caddy/Spring/Tomcat/PostgreSQL18/multi-admin Chromium #42444502902](https://github.com/kunlong-luo/lanveriq/actions/runs/42444502902): **SUCCESS**. The log explicitly confirms exact A/B scoped membership, separate administrator browser cookies, HTML/RSC authorization at the mTLS edge, session revocation, data cache purge, live permission removal and account disable. This success verifies the synthetic-only gate; it does not enable any production service.

### Non-goals and future work

This does not add authenticated server-side sorting or pagination, global device totals, remote device actions, real-time monitoring, production login or database integration. Once the separate server-side pagination/RBAC contract has been explicitly accepted, a mature TanStack Table model may help connect these local presentation primitives to server-scoped paging.

Browser DOM and keyboard assertions improve accessibility but **do not equal an independent screen-reader certification or formal WCAG audit**. A future product release needs assisted-technology review and data-contract approval.
