# D0 — documentation-before-code gate

**Status: WRITTEN / AWAITING MAINTAINER REVIEW.** Documentation completeness is distinct from implementation readiness and real-world deployment authorization.

## Review checklist
- [x] Product scope, non-goals and user personas written
- [x] Functional requirements and milestone acceptance written
- [x] System architecture, trust zones and module boundaries documented
- [x] Server PostgreSQL and endpoint SQLite/WAL architecture decisions recorded
- [x] Versioned device asset inventory fields (CPU/RAM/OS/volumes/NIC/IP/MAC), sampling, privacy/retention, API and ER diagrams defined
- [x] API/event envelope, all-or-nothing acknowledgment, identity re-enrollment and idempotency behavior documented
- [x] LAN bootstrap/trusted TLS and dashboard v0.1 REST + authenticated WSS design documented
- [x] Typed policy lifecycle, health/gap semantics and role/action matrix documented
- [x] v0.1 implementation order and hands-on test cases documented
- [x] Windows/Linux feature and limitations matrix written
- [x] Capture pipeline and authorization restrictions documented
- [x] Privacy, admin identity, threat model and retention requirements written
- [x] Pagevela review and transfer decisions written
- [x] Direct-main, unit-only CI, postponed E2E decision recorded
- [x] WebSocket contract, role-scoped push security, reconnect/refetch and unit/manual acceptance written
- [x] Mermaid context/component/sequence/ER/security diagrams and index written
- [x] Test strategy, runbooks, roadmap and issue-sized backlog written
- [ ] Maintainer explicitly reviews and accepts D0 technical baseline
- [ ] Validate the supplied Pop!_OS 24.04 LTS/KDE KWin pilot environment (`XDG_SESSION_TYPE` still unknown) and finalize certificate/bootstrap choice for v0.1
- [ ] Decide and record fixed initial event source allowlist and supported policy fields before implementing collectors
- [ ] Approve initial network-address/MAC collection scope, data retention, disclosure and Linux/Windows field-capability tests; inventory is only designed, not verified on endpoints
- [ ] Organization confirms collection scope/notice/legal requirements before any real employee data
- [x] Earlier 68-document Markdown-link check completed; current visual-gallery update checked changed Markdown/image references against repository paths (73 Markdown files + 8 SVG assets); all inspected local targets exist. All 16 Mermaid drawings remain in the repository.
- [x] Eight coordinated 16:9 SVG concept images and gallery, inventory, style guide and scope/terminology matrix added; all images carry a design-stage label and use no actual employee data.
- [ ] Manually inspect all SVGs and Mermaid diagrams in GitHub/browser at full and reduced sizes, including Chinese font fallback; automated metadata/link checks do not prove visual rendering correctness. External URLs and heading anchors have not been exhaustively checked.

## Start-code rule
**Only after maintainer approval**, begin v0.1 implementation on main, no feature branch or PR necessary. Unit-only CI may be added alongside real source and tests; avoid placeholder passing jobs. Do not begin recording before its separate consent/OS authorization gate.

## Release gate
A docs gate approval is **not** proof of a secure product. Before real-data deployment, run manual endpoint tests for auth, replay, scopes and backups; prior to v1.0, broaden automated tests and security review. Never describe unit-only CI as full certification.
