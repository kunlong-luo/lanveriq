# NXT-24 — Canonical Next.js directory rename to apps/web

**Date:** 2026-11-21. **Development branch:** `main` only. **Acceptance:** same-source-SHA CI pending. **Production release:** v0.1 NOT RELEASE READY.

## Contract

The previously accepted Next.js 16.4.5 / React 19 / Tailwind 4 / Base UI application has moved as a **single complete Git tree** from the temporary migration path `apps/web-next` to **`apps/web`**. The old Vite application that formerly occupied `apps/web` was deleted in NXT-23; it has **not** been restored. All prior Next.js App Router pages, components, styles, frozen pnpm lockfile, source-only safety checkers and browser test scripts are retained.

The old Vite code remains recoverable in historical commits, but there is only one runnable frontend in the current tree. The package identity is `@lanveriq/web`. No additional frontend, alias/symlink, redirect or compatibility launcher is retained.

## Paths that must move together

- Fast Next.js workflow is now `.github/workflows/web-unit.yml`, no longer `web-next-unit.yml`. All pnpm working directories are `apps/web`.
- `nextjs-browser-preview-synthetic.yml` and `pages-static-preview.yml` only react to canonical `apps/web` UI source changes on `main`; their isolated runtime and export paths follow the move.
- Springdoc / Orval synthetic contract milestone, Caddy upstream HTTPS and disposable multi-admin browser workflows use `apps/web`. They do not enable credentials, business routes or a live frontend.
- RD-2 source guards and negative fixtures examine the new Next.js route location, retaining default-OFF remote access and safety checks.
- NXT-17 synthetic directory, NXT-18 Chromium and synthetic auth fixture scripts still require their exact CI flags; the directory basename check changes from `web-next` to `web` without weakening it.
- The fast Next.js and Pages CI jobs execute the revised `scripts/verify-single-ui.mjs` and its negative tests, refusing Vite resurrection, the obsolete `apps/web-next` path, a stale Vite workflow or conflicting launch documentation.
- Update canonical documentation and path links to `apps/web`. Earlier milestone commit SHAs and historical evidence are retained. Old source snapshots in Git history still use their original paths.

## Pop!_OS 24.04 local launch

```bash
# From repo root; inspect/commit any local changes before updating.
git status --short
git switch main
git pull --ff-only origin main
cd apps/web
corepack enable
corepack pnpm install --frozen-lockfile
corepack pnpm dev
```

Open `http://127.0.0.1:3100/overview`. Do not run `vite`, `npm --prefix apps/web-next` or the legacy 5173 server. A local process from an older checkout may continue listening until stopped; repository renaming does not kill processes, delete untracked files, modify Docker volumes or touch actual devices. If a pre-rename local checkout contains untracked Vite residue in `apps/web`, inspect it before pulling.

## Security limitations

GitHub Pages is a public **disconnected static showroom** and the ordinary local Next.js preview remains disconnected; both are forbidden from querying real devices or collecting credentials. Any future real administrator login, FIDO MDS/WebAuthn trust, production network connectivity, Agent collection, automatic recovery, RustDesk remote session or CA issuance needs separate authorization and acceptance. No production switch is changed in this refactor.

## Exact-sha CI evidence

Pending Next.js fast unit/typecheck/build/HTTP, real Chromium keyboard/mobile/screenshots, disconnected Pages build+deploy, and RD-2 default-OFF verification on the single rename source commit. Do not cite NXT-23's previous success as NXT-24 proof.
