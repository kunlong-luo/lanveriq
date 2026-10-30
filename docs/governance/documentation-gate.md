# D0 — documentation-before-code gate

**Status: WRITTEN / AWAITING MAINTAINER REVIEW.** Documentation completeness is distinct from implementation readiness and real-world deployment authorization.

## Review checklist
- [x] Product scope, non-goals and user personas written
- [x] Functional requirements and milestone acceptance written
- [x] System architecture, trust zones and module boundaries documented
- [x] Server PostgreSQL and endpoint SQLite/WAL architecture decisions recorded
- [x] API/event envelope, all-or-nothing acknowledgment, identity re-enrollment and idempotency behavior documented
- [x] LAN bootstrap/trusted TLS and dashboard v0.1 REST-polling design documented
- [x] Typed policy lifecycle, health/gap semantics and role/action matrix documented
- [x] v0.1 implementation order and hands-on test cases documented
- [x] Windows/Linux feature and limitations matrix written
- [x] Capture pipeline and authorization restrictions documented
- [x] Privacy, admin identity, threat model and retention requirements written
- [x] Pagevela review and transfer decisions written
- [x] Direct-main, unit-only CI, postponed E2E decision recorded
- [x] Test strategy, runbooks, roadmap and issue-sized backlog written
- [ ] Maintainer explicitly reviews and accepts D0 technical baseline
- [ ] Finalize first target Linux environment and certificate/bootstrap choice for v0.1
- [ ] Decide and record fixed initial event source allowlist and supported policy fields before implementing collectors
- [ ] Organization confirms collection scope/notice/legal requirements before any real employee data
- [ ] Recheck all new and changed Markdown links; the previous baseline was verified

## Start-code rule
**Only after maintainer approval**, begin v0.1 implementation on main, no feature branch or PR necessary. Unit-only CI may be added alongside real source and tests; avoid placeholder passing jobs. Do not begin recording before its separate consent/OS authorization gate.

## Release gate
A docs gate approval is **not** proof of a secure product. Before real-data deployment, run manual endpoint tests for auth, replay, scopes and backups; prior to v1.0, broaden automated tests and security review. Never describe unit-only CI as full certification.
