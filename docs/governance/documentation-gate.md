# D0 — documentation-before-code gate

**Status: WRITTEN / AWAITING MAINTAINER REVIEW.** Documentation completeness is distinct from implementation readiness and real-world deployment authorization.

## Review checklist
- [x] Product scope, non-goals and user personas written
- [x] Functional requirements and milestone acceptance written
- [x] Dedicated Monitoring Center (V0.1) and Recording & Playback Center (V0.3) UI/API loop definitions exist; **not yet implemented**
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
- [x] [Cross-document review of V0.1 scope, RBAC, REST, recording case flow and all drawings](documentation-review-2026-10-30.md) written; no runtime claims
- [x] Baseline V0.1 access controls and separate later recording review-case authorization reconciled in PRD, API, tasks and drawings
- [ ] Maintainer explicitly reviews and accepts D0 technical baseline
- [ ] Validate the supplied Pop!_OS 24.04 LTS/KDE KWin pilot environment (`XDG_SESSION_TYPE` still unknown) and finalize certificate/bootstrap choice for v0.1
- [ ] Decide and record fixed initial event source allowlist and supported policy fields before implementing collectors
- [ ] Approve V0.1 baseline administrator role/group permission checks and bind uploaded device inventory to its credential before coding
- [ ] Approve initial network-address/MAC collection scope, data retention, disclosure and Linux/Windows field-capability tests; inventory is only designed, not verified on endpoints
- [ ] Organization confirms collection scope/notice/legal requirements before any real employee data
- [x] Re-audited all **83 Markdown files** for file-relative links against the current repository tree on 2026-10-30; no missing file targets were found. Folder links such as `assets/` require directory-aware checking. **20 Mermaid pages and 8 SVG concept assets exist**. This verifies path existence, **not** browser/diagram rendering, all anchors or actual system operation.
- [x] Eight coordinated 16:9 SVG concept images and gallery, inventory, style guide and scope/terminology matrix added; all images carry a design-stage label and use no actual employee data.
- [ ] Manually inspect all SVGs and Mermaid diagrams in GitHub/browser at full and reduced sizes, including Chinese font fallback; automated metadata/link checks do not prove visual rendering correctness. External URLs and heading anchors have not been exhaustively checked.

## Start-code rule
**Only after maintainer approval**, begin v0.1 implementation on main, no feature branch or PR necessary. Unit-only CI may be added alongside real source and tests; avoid placeholder passing jobs. Do not begin recording before its separate consent/OS authorization gate.

## Release gate
A docs gate approval is **not** proof of a secure product. Before real-data deployment, run manual endpoint tests for auth, replay, scopes and backups; prior to v1.0, broaden automated tests and security review. Never describe unit-only CI as full certification.
