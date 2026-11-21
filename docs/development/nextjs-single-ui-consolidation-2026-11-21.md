# NXT-23 / NXT-24 — One canonical Next.js UI (historical migration audit)

**Date:** 2026-11-21 · **Branch:** `main` only · **Current source:** `apps/web` · **Release:** **NOT RELEASE READY**.

> This document distinguishes two *separate* commits. NXT-23 **removed the old Vite application** that originally occupied `apps/web` while Next.js still lived at `apps/web-next`. NXT-24 **renamed the accepted Next.js tree to `apps/web`**. Consequently `apps/web` and `.github/workflows/web-unit.yml` now **exist again**, but both contain **Next.js**, not Vite. Treating their presence as an error would be an obsolete NXT-23-only rule.

## Current source of truth

| Concern | Active location / contract |
| --- | --- |
| Frontend | `apps/web`, package `@lanveriq/web`, Next.js **16.4.5**, React **19**, Tailwind **4**, Base UI |
| Local developer command | `cd apps/web && corepack pnpm install --frozen-lockfile && corepack pnpm dev` |
| Browser | `http://127.0.0.1:3100/overview`; loopback only |
| Fast UI CI | `.github/workflows/web-unit.yml` — **Next.js** frozen install, guards, Vitest, typecheck, build, HTTP |
| Real synthetic browser QA | `.github/workflows/nextjs-browser-preview-synthetic.yml`, Playwright/Chromium; disconnected preview |
| Public static showcase | `.github/workflows/pages-static-preview.yml`, export from `apps/web/out`, GitHub Pages |
| Contract checking | `apps/server/contract-client-check` + `apps/web/contract-check`, milestone-only generated Orval types |
| Staged restricted runtime | `apps/web/src/lib/staged-console-auth.ts`, `staged-console-read.ts`, `console-device-projection.ts`; **unmounted** |
| Source safety guard | `apps/web/scripts/verify-single-ui.mjs` with `test_verify_single_ui.mjs` |

The active single-UI guard rejects **reintroduced `apps/web-next`**, `web-next-unit.yml`, Vite configs/dependencies or Vite dev commands inside `apps/web`, and stale readme instructions that launch `apps/web-next` or port 5173. It **requires** the Next.js `apps/web` manifest and **requires** the Next.js `web-unit.yml` workflow. This is the precise current behavior of the checked-in script, not the outdated negative checks in the first NXT-23 audit.

## Source migration and evidence

- **NXT-23:** [functional SHA `e54af66a`](https://github.com/kunlong-luo/lanveriq/commit/e54af66aa7803b43c2b85a6bff7efd419b44a428). Removed **32 tracked legacy Vite files** and the **old Vite** `web-unit.yml` workflow; Next.js was still at `apps/web-next`. Same-SHA [Next L1 #44874669194](https://github.com/kunlong-luo/lanveriq/actions/runs/44874669194), [Chromium #44874669221](https://github.com/kunlong-luo/lanveriq/actions/runs/44874669221), [Pages #44874669171](https://github.com/kunlong-luo/lanveriq/actions/runs/44874669171) and [RD-2 #44874669193](https://github.com/kunlong-luo/lanveriq/actions/runs/44874669193) passed. These links refer to the **historical pre-rename source layout**.
- **NXT-24:** [functional SHA `6ed47c47`](https://github.com/kunlong-luo/lanveriq/commit/6ed47c472b86b6385aff9f6e0c71ab70f0ef8b6c). Moved entire Next.js application to `apps/web`; recreated `web-unit.yml` for **Next.js**. Same-SHA [Next L1 #44876019214](https://github.com/kunlong-luo/lanveriq/actions/runs/44876019214), [real Chromium #44876019297](https://github.com/kunlong-luo/lanveriq/actions/runs/44876019297), [disconnected Pages #44876019222](https://github.com/kunlong-luo/lanveriq/actions/runs/44876019222), [RD-2 #44876019189](https://github.com/kunlong-luo/lanveriq/actions/runs/44876019189) and [Java server unit #44876019234](https://github.com/kunlong-luo/lanveriq/actions/runs/44876019234) passed. NXT-24 Chromium screenshots: `nxt18-visual-qa-6ed47c472b86b6385aff9f6e0c71ab70f0ef8b6c` (artifact ID `14695613816`).
- Historical Vite source and tests remain available through Git history only; **do not** reintroduce its anonymous health proxy, fabricated device activity or inactive credentials into the running Next.js UI.

## Pop!_OS 24.04 local development

From the repository root, protect local changes before updating:

```bash
git status --short
git switch main
git pull --ff-only origin main
cd apps/web
corepack enable
corepack pnpm install --frozen-lockfile
corepack pnpm dev
```

Visit `http://127.0.0.1:3100/overview`. The former Vite **5173** server is not part of this repository; older local processes must be stopped manually. Do not remove unrelated files, credentials, shared Docker volumes or databases when updating the UI. See the [NXT-24 migration contract](nextjs-path-rename-nxt24-2026-11-21.md) and the [canonical developer README](../../apps/web/README.md).

## Safety and release boundaries

GitHub Pages remains a **public, disconnected static UI design showcase**; the ordinary Next.js local preview is also disconnected. The command palette navigates only allowlisted local pages; device search and all business operations remain unavailable. Real administrator WebAuthn MFA, official FIDO MDS provenance, trusted time/revocation, production Caddy topology, authenticated device reads, Agent collection, auto-recovery, RustDesk sessions, CA issuance and **physical Pop!_OS 24.04 acceptance** require separate validation and authorization. A successful static UI/Chromium CI run does not grant production permission.
