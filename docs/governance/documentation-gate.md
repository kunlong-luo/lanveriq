# D0 — documentation-before-code gate

**Status: REVIEW PACKAGE PREPARED / D0 APPROVAL STILL PENDING.** Documentation completeness is distinct from implementation readiness and real-world deployment authorization.

**Review this compact [D0 decision package](d0-decision-package-2026-10-30.md)** and its linked D-07 PKI, D-11/D-22 wire fixtures, D-17/18/20 privacy/RBAC and D-02 pilot proposal. All are **candidates, not a signed technical freeze**.

## Review checklist
- [x] Product scope, non-goals and user personas written
- [x] Functional requirements and milestone acceptance written
- [x] Dedicated Monitoring Center (V0.1) and Recording & Playback Center (V0.3) UI/API loop definitions exist; **not yet implemented**
- [x] System architecture, trust zones and module boundaries documented
- [x] Maintainer explicitly chose **Gradle**, not Maven: [stable Java backend stack](../development/backend-stack.md) and [Groovy DSL reference](../development/backend-gradle-reference.md) (Gradle 9.8.2/Boot 4.1.2/Java 25/JaCoCo 0.8.16); **not installed or compiled**
- [x] Server PostgreSQL and endpoint SQLite/WAL architecture decisions recorded
- [x] Root `AGENTS.md` strengthened with source-of-truth precedence, operational workflow, security/durability invariants, verification/evidence standards and concise navigation; detailed policy remains in [reuse-first](../development/reuse-first-policy.md).
- [x] Maintainer's **repository-wide reuse-first policy** codified in [AGENTS.md](../../AGENTS.md), [reuse-first policy](../development/reuse-first-policy.md), contributing guide and ADR-0007: mature built-ins/SDKs/libs before custom utility code; UUID examples use native PostgreSQL/Rust uuid/browser Web Crypto
- [x] Ecosystem-first ADR and selected libraries documented ([ADR-0007](../architecture/adrs/0007-mature-ecosystem-first.md)); **SQLx/Orval have not been installed or tested**
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
- [x] Split **pre-code design decisions** from **post-D0 implementation evidence**: SQLx/SQLite bundled WAL patch, actual single-connection transactions, generated Orval client compatibility and dependency redistribution/license verification cannot be claimed tested in this docs-only repository. See the post-D0 verification list below.
- [ ] **D-07 BLOCKER:** sign off the [three-ingress + operator-mediated client-CA proposal](../architecture/enrollment-bootstrap.md) or record a reviewed alternative; choose actual hostnames, cert issuance/delivery, trusted proxy-to-Spring identity mapping, key custody, rotation and revocation. A proposed workflow is not proof of working PKI.
- [ ] **D-11/D-22 BLOCKER:** maintainer approves the [candidate V0.1 DTO/source/error/role projection fixtures](../architecture/api-contract.md), event/gap semantics and inventory CAS, or requests changes. Springdoc OpenAPI/Orval artifacts follow implementation and are not falsely reported tested.
- [ ] Maintainer explicitly reviews and accepts **the full D0 technical baseline**; choosing the Java build tool alone does not satisfy D0
- [ ] Confirm the initial Pop!_OS 24.04 LTS/KDE KWin **pilot target and actual user-session `XDG_SESSION_TYPE`** (still unknown), plus the proposed certificate/bootstrap boundary before D0; real Agent/OS integration and capability tests occur after code is permitted.
- [ ] Freeze **V0.1 synthetic protocol-fixture source names and policy fields** before V0.1 code. Real Windows/Linux application and audit collector source allowlists remain V0.2 approval requirements, not silently enabled in V0.1.
- [ ] Approve V0.1 baseline administrator role/group permission checks and bind uploaded device inventory to its credential before coding
- [ ] Approve initial network-address/MAC collection scope, data-retention/notice assumptions and the **planned** Linux/Windows field-capability acceptance matrix; actual collection/API tests are post-D0 and inventory is not yet verified on endpoints.
- [ ] Organization confirms collection scope/notice/legal requirements before any real employee data
- [x] Historical review inspected **83 Markdown files** at that time. **Final 2026-10-30 pre-report pass inspected 90 existing Markdown files / 446 local path links (none missing); 20 Mermaid pages and 8 SVGs passed structural-only checks.** See [final closure report](final-closure-review-2026-10-30.md). Folder links such as `assets/` require directory-aware checking. **20 Mermaid pages and 8 SVG concept assets exist**. This verifies path existence, **not** browser/diagram rendering, all anchors or actual system operation.
- [x] Eight coordinated 16:9 SVG concept images and gallery, inventory, style guide and scope/terminology matrix added; all images carry a design-stage label and use no actual employee data.
- [ ] Manually inspect all SVGs and Mermaid diagrams in GitHub/browser at full and reduced sizes, including Chinese font fallback; automated metadata/link checks do not prove visual rendering correctness. External URLs and heading anchors have not been exhaustively checked.

## Post-D0 implementation verification (NOT preconditions requiring pre-D0 application code)
- [ ] Build the approved Java/Gradle/JPA backend against a real PostgreSQL 18 instance; verify `uuidv7()`/JPA mapping, Flyway migrations, event-batch atomic ACK and inventory compare-and-set. No H2 substitute for PG-specific claims.
- [ ] Build Rust/SQLx for each target OS; record the **actual linked SQLite version** with patched WAL and test single-writer transactions, persistence/recovery, quotas and idempotent retries.
- [ ] Generate Springdoc OpenAPI from the real server, run Orval against the accepted local schema, and check TS client/server error, auth and privacy-scoped DTO contracts; do not require nonexistent generated code before D0.
- [ ] Review pinned dependency transitive licenses, security advisories and redistribution constraints before incorporating or releasing binaries, and document any alternative needed.
- [ ] Run platform/auth/CA/revocation, WSS browser and backup/restore manual acceptance on representative Windows/Linux machines before any real-data pilot. Keep results NOT TESTED until evidence exists.

## Explicit D0 sign-off (intentionally unsigned)
The maintainer must record **their approval decision, date, reviewed commit SHA, D-07 and D-11/D-22 decisions, V0.1 privacy/role boundaries and any approved exceptions** here, in the [unsigned decision package](d0-decision-package-2026-10-30.md), and in [open decisions](open-decisions.md). Current decision: **PENDING — implementation code is NOT authorized.** A commit, a document review or a selected library cannot auto-approve this gate.

## Start-code rule
**Only after maintainer approval**, begin v0.1 implementation on main, no feature branch or PR necessary. Unit-only CI may be added alongside real source and tests; avoid placeholder passing jobs. Do not begin recording before its separate consent/OS authorization gate.

## Release gate
A docs gate approval is **not** proof of a secure product. Before real-data deployment, run manual endpoint tests for auth, replay, scopes and backups; prior to v1.0, broaden automated tests and security review. Never describe unit-only CI as full certification.
