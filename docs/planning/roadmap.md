# Documentation-first development roadmap

**Status:** Draft plan; none of the software milestones have started. **Workflow:** commit directly to main, keep commits atomic, GitHub CI runs only unit tests during pre-release MVP, E2E explicitly deferred.

## Gate D0 — document baseline (CURRENT)
Deliver/review PRD, user roles, data fields, platform capabilities, component boundaries, API/event contracts, PG and SQLite models, retention, threat model, manual test plan and development rules. Confirm operating jurisdiction/monitoring notice scope. **No application code until D0 review is acknowledged**; incomplete sign-offs remain open decisions.

## v0.1 — end-to-end skeleton (priority P0)
1. Server bootstrap: Java 25/Spring Boot 4, PostgreSQL/Flyway migrations, Caddy HTTPS, React/Vite shell.
2. Rust agent skeleton, embedded SQLite store with migrations and single writer.
3. Device identity, short-lived pairing, revocation, heartbeat and capability reporting.
4. Durable event outbox, bounded batch ingestion, unique sequence and retry acknowledgments.
5. Basic dashboard device list, device detail, last-seen and queue/error status.
**Exit:** actual Windows and Linux devices enroll, show heartbeat, survive server outage, replay once. Run focused manual smoke checks; unit CI remains minimal.

## v0.2 — activity and audit
Allowlisted Windows Event Log and Linux auditd adapters, supported application activity metadata, gap semantics, server timeline search and RBAC, event retention, administrator activity audit. Backend tests cover authorization, dedup, retention and schema constraints. **Exit:** a recorded event is searchable with correct source/device/time and unauthorized access is blocked in manual checks.

## v0.3 — authorized video (separate safety gate)
Tauri companion, policy display/capture status, OS permission adaptation, chunked segments/sha256, local quota and upload retry, approved playback, retention and recording-gaps. **Exit:** recording is observable and can be stopped, denied OS permission cannot be bypassed, permissions and playback require authorization, actual Windows/Linux target manually verified. No promise of full 24/7 coverage.

## v0.4 — usability and maintainability
Simple installers Windows MSI and Linux DEB (RPM after verified demand), log diagnostics, backup/restore, local offline upgrade procedure, secure defaults, multilingual entry docs and failure UX. Keep dependencies stable and code modular.

## v1.0 — production readiness (explicit future scope)
Reevaluate CI, add integration and E2E/platform tests, security audit and penetration/permission review, full installer verification, signed binaries, restore drills, issue triage, legal/privacy sign-off. Do **not** claim production readiness while CI is unit-only and cross-platform recording remains untested.

## Proposed milestone order
D0 → v0.1 → v0.2 → v0.3 → v0.4 → v1.0. Milestones are outcomes, not fixed time estimates. Each item links to an Issue when development starts. No AI or public cloud in roadmap.
