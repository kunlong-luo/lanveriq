# NXT-26 — Base UI dropdowns for the device directory

**Date:** 2026-11-21 · **Branch:** `main` · **Source:** `apps/web` Next.js 16.4.5 / React 19 / Tailwind 4 / Base UI React 1.9.0 · **Status:** four same-functional-SHA isolated CI successes on `0750f6b5`; production OFF. **Release:** v0.1 NOT RELEASE READY.

## Final NXT-26 isolated acceptance

**NXT-26 exact-source acceptance PASSED:** functional SHA [`0750f6b55c0bc9a69416e6014485dcf213ba1406`](https://github.com/kunlong-luo/lanveriq/commit/0750f6b55c0bc9a69416e6014485dcf213ba1406) passed all four same-SHA workflows: [Next.js frozen install, tests, typecheck, build and HTTP #44879640236](https://github.com/kunlong-luo/lanveriq/actions/runs/44879640236), [real Chromium 11 pages, 15 screenshots and CI-only popup/group/sort/keyboard/revocation acceptance #44879640279](https://github.com/kunlong-luo/lanveriq/actions/runs/44879640279), [disconnected public Pages build and deployment #44879640285](https://github.com/kunlong-luo/lanveriq/actions/runs/44879640285), and [RD-2 default OFF #44879640264](https://github.com/kunlong-luo/lanveriq/actions/runs/44879640264). Visual artifact `nxt18-visual-qa-0750f6b55c0bc9a69416e6014485dcf213ba1406` (ID `14697451711`). This is isolated UI/source acceptance, NOT live admin auth/device access, production release or physical Pop!_OS acceptance.

The first two Chromium fixture failures are historical below. The final run targeted only `.lv-select-popup[data-open]` and passed real selection, filtering, sorting, page replacement, revocation, keyboard, mobile and no-cookie/no-API checks.

## Why this change

The device directory shared by `/overview`, `/monitoring` and `/devices` still used a browser-native `<select>` for authorized group and page sort; on Pop!_OS/COSMIC and mobile browsers its operating-system popup looked inconsistent with Lanveriq's editorial control system. The project already pins `@base-ui/react@1.9.0`, so the solution **reuses a maintained accessible primitive** instead of adding another dependency, recreating keyboard navigation, or building a custom dropdown from scratch.

## Implemented boundary

- `apps/web/src/components/ui/console-select.tsx`: shared, controlled `Select.Root/Trigger/Value/Icon/Portal/Positioner/Popup/List/Item/ItemText/ItemIndicator`; disabled state controlled by the caller and checked again before changes are emitted. The trigger is named using `aria-label`. Base UI supplies focus handling, roving options, typeahead and Escape dismissal.
- `apps/web/src/components/device-directory.tsx`: replaces both `NativeSelect` usages in the existing shared directory. Authorized groups come **only** from the caller's current server-scoped page. Sort values are allowlisted (server order, label A→Z, label Z→A, group A→Z); search and filtering never issue API requests.
- `apps/web/src/app/console-polish.css`: final visual layer gives triggers and floating menus consistent white/ink/indigo appearance, clear focus/selected states, constrained viewport popup height, at least 44px mobile hit targets and reduced-motion handling. Portal popup styling remains effective outside the console layout root. Text search remains a normal accessible text input, not a dropdown.
- `apps/web/src/components/ui/input.tsx`: uses the current indigo focus token `#4D5FC9` in place of the obsolete purple.
- Retires the old unused `apps/web/src/components/ui/native-select.tsx` component. The historical native-only CSS selectors in the existing B+C stylesheet do not apply to the new Base UI triggers, which get the final `console-polish.css` styles.

## Chromium fixture timing regression — first NXT-26 run

The first same-source Chromium workflow [#44879221414](https://github.com/kunlong-luo/lanveriq/actions/runs/44879221414) **failed the CI-only synthetic fixture** at `base_ui_sort_popup_not_visible` after calling `sorting.click()`. The ordinary Next.js build, Vitest, normal disconnected Chromium route checks and 15 screenshots **passed**, as did independent [Next L1 #44879221402](https://github.com/kunlong-luo/lanveriq/actions/runs/44879221402), [Pages #44879221409](https://github.com/kunlong-luo/lanveriq/actions/runs/44879221409) and [RD-2 #44879221422](https://github.com/kunlong-luo/lanveriq/actions/runs/44879221422). This failure prevents NXT-26 from being considered accepted. The first repair replaces an immediate `is_visible()` probe with Playwright's bounded wait for the real `.lv-select-popup` portal and retains the subsequent ARIA listbox and actual option-selection checks. If it still fails, investigate runtime behavior rather than bypassing the interaction or claiming UI acceptance.

**Second run [#44879458885](https://github.com/kunlong-luo/lanveriq/actions/runs/44879458885) clarified the root cause:** the sort selection and the subsequent **group popup did work**, but Base UI kept the closed sort popup in the DOM while opening the new group popup (transition semantics). A strict `.lv-select-popup` locator matched **both**, one `data-closed` and the other `data-open`. The test now waits specifically for `.lv-select-popup[data-open]` (the active popup), preserving the real ARIA listbox, option selection and authorization-transition checks. A fresh same-SHA Chromium rerun is required to close the gate.

## Safety and acceptance checks

1. Vitest and static markup: disabled unconnected filters, authorized-only table counts, explicit `role=combobox` and accessible names; no native `<select>`; deny/signed-out states never show previous rows.
2. Source-only regression: Base UI Select is actually imported, no homegrown keyboard/portal/menu code and no fetch, WebSocket or browser storage. Existing single-UI/route/documentation safety tests remain mandatory.
3. Real disconnected Chromium: `/devices` shows two **disabled** Base UI triggers, no native select element and no operation enabled. Mobile width matches search control; no document overflow.
4. CI-only synthetic directory route in a disposable GitHub runner: open two real Base UI popups, use click/keyboard option selection, check local sorting and filtering, search and clear-focus, switch authorized page to invalidate stale group, revoke permissions to clear records and disable controls, then restore fresh authorized page with defaults. The fixture is never committed as a route or published.
5. GitHub Pages remains disconnected static-only, with no credentials, device APIs, authenticated Next.js RSC, collection, real WSS, remote control, automatic recovery, or CA issuance.

### What remains open

This is **visual consistency and accessibility behavior**, not a production device search or real group authorization implementation. Official FIDO MDS root provenance/time/revocation and administrator WebAuthn MFA, protected read endpoints, actual Pop!_OS 24.04 physical acceptance and release approval remain outstanding. The original NXT-24 five-SHA runs are historical; this code requires its own same-SHA CI evidence.

## Operator preview

```bash
git status --short
git switch main
git pull --ff-only origin main
cd apps/web
corepack enable
corepack pnpm install --frozen-lockfile
corepack pnpm dev
```

Visit `http://127.0.0.1:3100/devices`. The two dropdown triggers remain **disabled** in the default public preview, because no actual administrator/device page is authorized. Do not enable private APIs just to interact with them. Their enabled selection behavior is exercised automatically only in a quarantined CI browser fixture.
