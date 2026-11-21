# NXT-27 — Shared evidence-boundary states for monitoring and device detail

**Date:** 2026-11-21 · **Branch:** `main` only · **Stack:** `apps/web` Next.js 16.4.5, React 19, Tailwind 4 and Base UI 1.9 · **Exact-SHA acceptance:** pending on this implementation · **Release:** v0.1 NOT RELEASE READY.

## Motivation

Before NXT-27, the monitoring activity chart, inventory-freshness rows, device-detail hardware/network/health tabs and device-detail header each implemented their own disconnected notices or values. That repeated icon/strong/body markup and scattered palette overrides, and made it easy to confuse an unconnected service, unverified heartbeat evidence and restricted network fields. **No missing data may become a zero count, an online/offline claim or a guess about an actual device.**

## Current implementation

- `apps/web/src/components/ui/evidence-state.tsx` owns two small **presentational-only, server-renderable** components: `EvidenceNotice` (icon, title, message) and `EvidenceValue` (text plus contextual accessible label), with a three-way `EvidenceStatus` vocabulary:
  - **`unconnected`:** not connected to a trusted service. Display **未连接**; no count, queue length or inventory status can be inferred.
  - **`restricted`:** protected network/identity information, not displayed until each field is independently authorized. Display **权限受限**. This label is an access-boundary explanation, **not** proof that a signed-in administrator was evaluated or denied.
  - **`unverified`:** no trusted heartbeat or collection coverage evidence. Display **尚未核验**, never `在线`, `离线` or a health percentage.
- `src/components/device-detail-tabs.tsx` preserves Base UI keyboard/roving-focus tabs, but renders `EvidenceNotice` for hardware (`unconnected`), network (`restricted`) and health (`unverified`) rather than three repeated icon/content branches.
- `src/components/health-sections.tsx` shares the same notice in the **empty activity chart** and shares `EvidenceValue` across inventory snapshot, heartbeat acceptance and pending-queue rows. `/overview` and `/monitoring` both use those components.
- `src/app/(console)/devices/[id]/page.tsx` uses `EvidenceValue` for its three summary values. A canonical UUID is only a **syntax check**, never authorization or evidence that a real device exists. Refresh and certificate revocation buttons remain disabled.
- `src/app/console-polish.css` provides **one final evidence-state visual layer**: muted indigo/grey for disconnected and unverified, distinct warm neutral for protected data, consistent type, icon treatment, responsive card sizing and sufficient touch/readable layout. Obsolete `.bc-chart-message` styles were removed from `lanveriq-bc.css`.
- Normal frontend route/pages still have **no admin credentials, backend API proxy, websocket, database, synthetic devices or background polling**. The components are stateless and do not import restricted staged session/read modules.

## Acceptance gates

1. **Unit/SSR:** `evidence-state.test.tsx` checks all three statuses, message semantics, contextual accessible labels and no invented zero/health; `detail-and-monitoring.test.tsx` checks monitoring and default hardware tab. Base UI may unmount inactive panels during SSR, so **Chromium** activates network and health tabs before checking each status.
2. **Source fail-closed:** `scripts/test_console_visual_source.mjs` verifies cross-page reuse, non-network component boundaries, mapping for protected/unverified data and removal of the old chart-specific style.
3. **Real Chromium:** `scripts/verify-preview-chromium.py` checks the real disconnected monitoring trend and inventory values, an unconnected UUID-only device summary, restricted **network** and unverified **health** panel after keyboard/tab navigation, no cookies, no business-API requests and unchanged disabled device operations.
4. **Previous required gates remain:** Next.js frozen install/Vitest/TypeScript/HTTP, NXT-26 Base UI synthetic authorized-directory fixture, responsive screenshots/static Pages and RD-2 default OFF. The temporary synthetic fixture is never committed as a public route.

## Explicit exclusions and release blockers

This is a display-system consolidation, **not** real device monitoring or a trust-evidence backend. Live data, authenticated Console RBAC, official FIDO MDS root-source/signature/revocation/trusted-time checks, actual administrator WebAuthn MFA and physical Pop!_OS 24.04 Agent/device acceptance are still open. All production networking, collection, auto-recovery, remote sessions and CA signing remain OFF.

## Local source preview

```bash
git status --short
git switch main
git pull --ff-only origin main
cd apps/web
corepack enable
corepack pnpm install --frozen-lockfile
corepack pnpm dev
```

Open `http://127.0.0.1:3100/monitoring` or `http://127.0.0.1:3100/devices`. To view a UUID-format detail placeholder, only use an explicitly synthetic valid UUID such as `/devices/00000000-0000-7000-8000-000000000001`; it does **not** identify a known device. All normal previews remain intentionally disconnected.
