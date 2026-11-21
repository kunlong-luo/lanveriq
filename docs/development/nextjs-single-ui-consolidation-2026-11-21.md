# NXT-23 — Single canonical Next.js UI, legacy Vite removal

**Date:** 2026-11-21. **Target:** `main` only. **Acceptance:** pending exact source SHA L1, Chromium, Pages and RD-2 CI. **Release:** v0.1 NOT RELEASE READY.

## Decision

Lanveriq has only **one runnable product frontend**: `apps/web-next`, Next.js 16.4.5 App Router / React 19 / Tailwind 4 / Base UI, on `127.0.0.1:3100`. The pre-migration Vite `apps/web` subtree and its dedicated `web-unit.yml` workflow were deleted from the active Git tree. Their complete earlier source, tests and commit history remain recoverable using Git history, but they are no longer supported launch targets or release candidates. No branch or PR is created for the cleanup.

This resolves the contradictory `3100` vs `5173` launch recipes. The public GitHub Pages site remains a **read-only disconnected design export**, not an operational management endpoint.

## Cross-project feature review before deletion

| Former Vite responsibility | Canonical Next.js counterpart / disposition |
| --- | --- |
| App shell, device directory, monitoring, enrollment, settings and disabled login | App Router pages in `apps/web-next/src/app`; `console-shell.tsx`, `device-directory.tsx`, `login-preview.tsx`; visual and unit tests |
| HTTPS-only browser session and CSRF request adapter, not mounted in Vite | `src/lib/staged-console-auth.ts` + isolated negative tests; **not imported from UI routes** |
| Restricted read-only device queries | `src/lib/staged-console-read.ts` + tests; no API proxy, session or live hook |
| Strict server-authorized device projection, unavailable/forbidden states | `src/lib/console-device-projection.ts` and `device-directory.tsx` + tests; connection/status is not inferred |
| Generated Springdoc → Orval TypeScript contract | `apps/server/contract-client-check` and `apps/web-next/contract-check`; milestone-only type gate |
| Vite's anonymous health proxy and fictional demo device records | **Intentionally retired**. Neither was a verified operational device service. Do not reproduce dummy online counters or connect the public UI to Spring. |
| Vite-specific pnpm lockfile, Vite executable, `web-unit.yml` | **Deleted**; Next.js retains its independently locked package, L1 tests, real Chromium gate and Pages export |

These are corresponding security boundaries and tests, **not** a statement that every old test is byte-for-byte identical. The retained Next.js negative regression suite is the authority for subsequent UI changes. No production login, collection, upload, remote control, certificate issuance, auto-recovery, WSS or device access is enabled by this consolidation.

## Automated guard

`apps/web-next/scripts/verify-single-ui.mjs` rejects:

1. Reintroduction of `apps/web` as a second executable frontend.
2. Reintroduction of `.github/workflows/web-unit.yml`.
3. An unexpected canonical `apps/web-next` package, development command or build command.
4. README launch commands pointing back to `cd apps/web`, `http://127.0.0.1:5173`, or deleted `apps/web/README.md`.

Negative fixture tests are in `apps/web-next/scripts/test_verify_single_ui.mjs`. The guard runs in both the rapid Next.js workflow and the public static Pages build. All existing Next.js source-level runtime isolation / no-API routes and RD-2 fail-closed checks remain mandatory.

## Pop!_OS 24.04 local launch

From your local repository root, ensure you do not have uncommitted work before pulling:

```bash
git switch main
git pull --ff-only origin main
cd apps/web-next
corepack enable
corepack pnpm install --frozen-lockfile
corepack pnpm dev
```

Open `http://127.0.0.1:3100/overview`. The 5173 port is no longer a repository-backed application. A locally running older Vite process or ignored/untracked old files will not be stopped or removed by a Git commit; check the old terminal and stop it manually. Do **not** delete shared Docker databases, state volumes, private configuration, or device secrets as part of UI cleanup.

## CI evidence

The initial change requires fresh same-source-SHA results from Next.js L1, Chromium (11 routes and 320–1440px screenshot and keyboard checks), public static Pages and RD-2. Historical success from NXT-22 does not transfer to this deletion.
