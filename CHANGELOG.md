# Changelog

All notable project changes will be documented here. No product binaries or running services exist yet.

## [Unreleased] — documentation planning baseline, 2026-10-30

### Added
- English README and Chinese project introduction.
- Product requirements, user journeys, nonfunctional targets and acceptance criteria.
- LAN system architecture, component boundaries, PostgreSQL schema and SQLite/WAL agent outbox design.
- Device enrollment and event API contracts, platform compatibility matrix, later recording design.
- Security/privacy, device authorization, threat model, retention and operational runbooks.
- Development roadmap, issue-sized backlog, unit test strategy and docs-first gate.
- Architecture decisions for monorepo, databases, disclosed recording and rapid main-first development.
- Pagevela architecture reference and selective transfer matrix.
- Detailed atomic event delivery and reinstall identity rules, organizational policy lifecycle, permission matrix, health/gap semantics, v0.1 manual acceptance and implementation sequence.
- v0.1 HTTPS/polling simplification, LAN certificate/enrollment plan, and dashboard UX design.

### Policy
Direct main commits during pre-release; CI only runs unit tests once code exists; no E2E until a later quality stage. **No functional implementation has begun.**
