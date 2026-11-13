> **2026-11-13 当前状态优先说明：** [Linux-first 上线差距与验收账本](../planning/release-readiness-2026-11-13.md) 是最新证据入口。`main` `758f8d9a` 的 Rust→Caddy→Tomcat TLS1.3→PG18 严格 ACK 已通过 [隔离 L2 #43356545857](https://github.com/kunlong-luo/lanveriq/actions/runs/43356545857)，不应再按旧记录认定“完全未运行”。但整批 A/B/C/D/E、正式 Pop!_OS 设备运行、Next.js/WSS/生产部署仍未完成；首发范围收敛到 **Pop!_OS 24 Linux-only**，Windows 11 另行 L3 验收，远控 RD-2 继续 OFF。本文原有时间线保留为历史，任何生产或真实数据启用须单独获批。

# Lightweight pre-release and migration plan

**Status 2026-11-08:** **no production release approval**. Following owner authorization, v0.1 is being developed in **cohesive business batches**; L1 relevant fast checks continue on push, expensive synthetic Caddy/Spring/PG18/Chromium and browser visual checks run on explicitly intended milestone tags or workflow dispatch. Public Pages is a disconnected design preview and no longer auto-deploys for each source edit. See [approved plan](../planning/v0.1-batched-delivery-plan-2026-11-08.md) and [three-tier tests](../testing/strategy.md). Previously successful CI runs provide historical evidence, not permission for real accounts/endpoints/Agent data.

## Milestone gates — development versus production

| State | Who/what can approve it | Required evidence |
| --- | --- | --- |
| Source change | Automated L1 on matching paths | Gradle/Cargo/pnpm source checks and focused safety negatives |
| Business batch A/B/C/D complete | Synthetic-only L2 workflow dispatch | Genuine disposable PG18, Caddy/TLS/Agent test path, negative RBAC and error/recovery proof; log run ID/SHA |
| v0.1 release candidate | L3 `acceptance/v0.1-*` milestone tag or explicit dispatch | Full Chromium, origin/session/RBAC, integration suites, packaging, restore and platform checklist |
| Authorized pilot | Organizational/legal/data-owner approval plus operator | Actual authorized Pop!_OS 24.04 device for Linux-only v0.1 (Windows requires its own later native L3), notice/data minimization/retention, CA/key custody, enrollment/revoke/stop, restore |
| Production release | Explicit separate release sign-off | Real deployment topology/security assessment, upgrade/rollback/restore, patch/installer/signed artifact, privacy and observability |

No tag or synthetic green check alone authorizes real deployment; the default-OFF feature flags must remain closed until independent approval.

## Historical 2026-11-08 device-protocol preflight

> The historical TLS1.2-minimum/never-tested claim below was superseded by actual isolated TLS1.3/PG18 L2 [#43356545857](https://github.com/kunlong-luo/lanveriq/actions/runs/43356545857). The remaining real blockers are Agent systemd/PKI/custody/recovery, authentic Next.js/WSS, approved real OS and recovery/production sign-off.

## Original preflight snapshot

The Batch A heartbeat HTTP controller exists only behind **two default-OFF flags**, and the running Rust Agent still has no live enrollment/transport. **DO NOT enable live device ingestion** before: (1) decide/implement/assert the Agent→Caddy minimum TLS version (current Rust `tls_version_min(TLS_1_2)` is **not** proof of TLS1.3-only); (2) validate both Caddy client-identity CA and independent Caddy→Tomcat upstream client certificate, exact SNI/Host and forged-header rejection; (3) run Rust/Java JSON golden fixtures, strict heartbeat/inventory/batch ACK/PKI revocation negative cases; (4) run actual disposable Agent→Caddy→Tomcat→PG18 Batch A L2 and authorized Windows/Linux + key custody acceptance L3. These are separate from previous synthetic Console HTML/RSC/RBAC successes. Canonical checklist: [device transport stack](../architecture/device-communication-stack.md), [document audit](../governance/documentation-audit-2026-11-08.md), [Batch A](../development/batch-a-first-heartbeat-implementation.md).


- Changes are small, atomically committed and documented; never force-push main.
- Run relevant local Rust formatting/compiler, Java compilation, TS typecheck/build and actual unit tests where code exists.
- Updated schema/API? Update contracts + migration and test compatibility. Authorization/credential changes require targeted manual validation.
- Only tag a milestone as an experimental preview after reproducing startup, TLS, enroll/heartbeat and stop/restart on synthetic endpoints.

## Proposed versioning
`v0.1.x`: enrollment, heartbeat, outbox, dashboard with REST + WSS.
`v0.2.x`: app/security activity, scoped search, retention.
`v0.3.x`: approved recording and media flow.
`v0.4.x`: installers, recovery, diagnostics.
`v1.0`: after security, platform and integration acceptance.

The repository now contains working source and passing synthetic integration, but **no approved production installer/Compose bundle or formal GitHub Release**; examples below are still release targets.

## Upgrade/rollback design
- Version Java/Flyway PostgreSQL migrations and Rust SQLite migrations explicitly. Always back up and test restore before destructive schema changes. Avoid rolling back by replacing a live SQLite/WAL database file.
- Support overlapping API/event schema versions for previously enrolled agents. Agent must refuse unsupported server policy expansions.
- Preserve device_id and monotonically increasing sequences across compatible upgrades; lost keys/DB trigger fresh enrollment and historical linkage.
- Record installer architecture, checksums and upgrade path for Windows 11 x64 and first validated Linux configuration.

## Release readiness boundary
Passing synthetic integration/Chromium CI is **necessary but insufficient** for production rollout. NXT-10 demonstrated disposable direct-origin isolation and protected HTML/RSC; NXT-11 reviews publicly cacheable static assets, explicit Next.js network namespace/firewall topology, and real login usability separately. The production Caddy Console remains disabled and Next.js authorized routes are unmounted. Formal v1.0 still requires real deployment integration/negative tests, independent UI accessibility review, real-device tests, signed distribution review, organization/privacy sign-off and exercised backup/restore.
