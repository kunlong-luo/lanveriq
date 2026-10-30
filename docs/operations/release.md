# Lightweight pre-release and migration plan

**Status:** planning only. During pre-release **all work commits to main**. No mandatory PR, E2E, release pipeline or full quality CI; GitHub Actions later runs **unit tests only** for real modules.

## Local development checkpoints
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

The repo initially contains only design docs; filenames, installer formats and command examples are **targets**, not actual release artifacts.

## Upgrade/rollback design
- Version Java/Flyway PostgreSQL migrations and Rust SQLite migrations explicitly. Always back up and test restore before destructive schema changes. Avoid rolling back by replacing a live SQLite/WAL database file.
- Support overlapping API/event schema versions for previously enrolled agents. Agent must refuse unsupported server policy expansions.
- Preserve device_id and monotonically increasing sequences across compatible upgrades; lost keys/DB trigger fresh enrollment and historical linkage.
- Record installer architecture, checksums and upgrade path for Windows 11 x64 and first validated Linux configuration.

## Release readiness boundary
Unit-only CI and no E2E are **development conveniences**. They do not justify broad production rollout. Formal v1.0 requires comprehensive integration/real-device tests, signed distribution review, privacy sign-off and exercised backup/restore.
