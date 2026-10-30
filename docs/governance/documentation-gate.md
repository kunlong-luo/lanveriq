# D0 — documentation-before-code gate

**Status: D0 TECHNICAL BASELINE APPROVED 2026-10-30 — V0.1 IMPLEMENTATION MAY BEGIN.** Documentation completeness remains distinct from implemented software, passing security tests or real-world deployment authorization. Reviewed Git baseline: `977392dd73273ea2a7930651d6e3bd40da487859`; approval entered for GitHub-authenticated owner `kunlong-luo` pursuant to explicit conditional authorization in this conversation.

**Approved technical decisions and scope:** [D0 decision package](d0-decision-package-2026-10-30.md) records D-07 PKI, D-11/D-22 V0.1 protocol, D-17/18/20 technical privacy/RBAC and D-02 pilot target. No employee data, production deployment or V0.2/V0.3 feature is authorized by D0. This is a maintainer-directed documented technical approval, not an independent cryptographic signature or completed integration audit.

## Review checklist
- [x] Product scope, non-goals and user personas written
- [x] Functional requirements and milestone acceptance written
- [x] Dedicated Monitoring Center (V0.1) and Recording & Playback Center (V0.3) UI/API loop definitions exist; **not yet implemented**
- [x] System architecture, trust zones and module boundaries documented
- [x] Maintainer explicitly chose **Gradle**, not Maven: [stable Java backend stack](../development/backend-stack.md) and [Groovy DSL reference](../development/backend-gradle-reference.md) (Gradle 9.8.2/Boot 4.1.2/Java 25/JaCoCo 0.8.16); **SRV-01 source and checksum-verified Wrapper now committed, live DB/runtime verification still pending**
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
- [x] **D-07 technical baseline approved:** [three-ingress + operator-mediated client-CA selection](../architecture/enrollment-bootstrap.md), default hostnames, h1/h2-only strict SNI/Host, console-only CSR retrieval and certificate registration, P-256/clientAuth/90-day template, SPKI/device binding and DB revocation. **NOT IMPLEMENTED/TESTED**; actual DNS/CA/OS key custody and negative TLS tests required after D0.
- [x] **D-11/D-22 design baseline approved:** [V0.1 API/DTO/source/error/role projection contract](../architecture/api-contract.md), event/gap semantics and inventory CAS. **NOT COMPILED OR CONTRACT-TESTED**; Springdoc OpenAPI/Orval artifacts and real interoperability follow implementation.
- [x] **The full pre-code D0 technical baseline is approved** by the repo owner under the explicit conditional approval instruction of 2026-10-30, with reviewed Git SHA and scope in the [decision package](d0-decision-package-2026-10-30.md). Build-tool selection alone did not satisfy this; the later cross-document review did.
- [x] **D-02 pilot target approved:** reported Pop!_OS 24.04 LTS/KDE KWin x86_64 as first Linux health/inventory implementation target, Windows 11 x64 separately; actual interactive `XDG_SESSION_TYPE=UNKNOWN` and OS compatibility are required **post-D0 evidence**, with no premature X11/Wayland support claim.
- [x] **V0.1 protocol source/policy design frozen:** production operational `agent_transport/collection_gap`, controlled dev-only `synthetic_test/protocol_fixture`, bounded policy fields. **No V0.1 application or OS-audit collector**; real source allowlists remain V0.2 approval requirements.
- [x] **V0.1 baseline server authorization approved as design:** role+assigned group REST/WSS enforcement, credential-bound inventory uploads, strict CSRF/Origin and additional `internal_operational` grant for raw IP/MAC; runtime negative tests remain pending.
- [x] **Technical field schema and planned platform-capability matrix approved** for synthetic implementation, with IP/MAC minimization, restricted read permissions and *provisional* retention assumptions. **This does not approve actual employee IP/MAC collection, notice or retention**; separate organizational/legal decision and endpoint tests remain pending.
- [ ] Organization confirms collection scope/notice/legal requirements before any real employee data
- [x] Historical review inspected **83 Markdown files** at that time. **Final 2026-10-30 pre-report pass inspected 90 existing Markdown files / 446 local path links (none missing); 20 Mermaid pages and 8 SVGs passed structural-only checks.** See [final closure report](final-closure-review-2026-10-30.md). Folder links such as `assets/` require directory-aware checking. **20 Mermaid pages and 8 SVG concept assets exist**. This verifies path existence, **not** browser/diagram rendering, all anchors or actual system operation.
- [x] Eight coordinated 16:9 SVG concept images and gallery, inventory, style guide and scope/terminology matrix added; all images carry a design-stage label and use no actual employee data.
- [ ] Manually inspect all SVGs and Mermaid diagrams in GitHub/browser at full and reduced sizes, including Chinese font fallback; automated metadata/link checks do not prove visual rendering correctness. External URLs and heading anchors have not been exhaustively checked.

## Post-D0 implementation verification (NOT preconditions requiring pre-D0 application code)
- [x] **SRV-02 internal CSR+transaction orchestration compiled/unit-tested:** [GitHub Actions run 41065309965](https://github.com/kunlong-luo/lanveriq/actions/runs/41065309965) passed with Java 25 and Boot JAR packaging; Bouncy Castle 1.86 validates P-256/PKCS#10 signatures, and JPA registers a pending UUIDv7 identity and CSR atomically in source. **Real PostgreSQL V1–V3 Flyway migrations, transaction rollback/lock behavior and cert/CA/mTLS route isolation remain NOT TESTED.**
- [x] **SRV-02 internal token issuance unit-tested:** [Java 25 CI run 41060814228](https://github.com/kunlong-luo/lanveriq/actions/runs/41060814228) passed after a corrected JPA result-type compilation issue. The 256-bit token codec verifies canonical encoding and redacts secrets; the PostgreSQL native authorization, audit and V2 Flyway schema are **NOT DATABASE-TESTED**.
- [x] **Boot JAR package built:** [Java 25 CI run 41061058441](https://github.com/kunlong-luo/lanveriq/actions/runs/41061058441) passed JUnit and `./gradlew bootJar` (archive packaging only, no app startup). [PG18 manual smoke script](../testing/server-postgres18-manual.md) exists but **HAS NOT RUN**.
- [x] **SRV-01 Java 25 unit build verified:** [GitHub Actions run 41058974204](https://github.com/kunlong-luo/lanveriq/actions/runs/41058974204) passed using official Gradle 9.8.2 Wrapper, Spring Boot 4.1.2 and the maintained Node 24 GitHub Actions; `compileJava`, `compileTestJava`, JUnit `test` and `jacocoTestReport` executed. **No PostgreSQL 18 database was exercised by that CI; `bootJar` was later verified in [run 41061058441](https://github.com/kunlong-luo/lanveriq/actions/runs/41061058441).**
- [ ] Build the approved Java/Gradle/JPA backend against a real PostgreSQL 18 instance; verify `uuidv7()`/JPA mapping, Flyway migrations, event-batch atomic ACK and inventory compare-and-set. No H2 substitute for PG-specific claims.
- [ ] Build Rust/SQLx for each target OS; record the **actual linked SQLite version** with patched WAL and test single-writer transactions, persistence/recovery, quotas and idempotent retries.
- [ ] Generate Springdoc OpenAPI from the real server, run Orval against the accepted local schema, and check TS client/server error, auth and privacy-scoped DTO contracts; do not require nonexistent generated code before D0.
- [ ] Review pinned dependency transitive licenses, security advisories and redistribution constraints before incorporating or releasing binaries, and document any alternative needed.
- [ ] Run platform/auth/CA/revocation, WSS browser and backup/restore manual acceptance on representative Windows/Linux machines before any real-data pilot. Keep results NOT TESTED until evidence exists.

## Explicit D0 technical sign-off — approved for V0.1 source implementation
**Decision:** APPROVED for V0.1 software development, including real module code, dependency locks and meaningful unit-only CI. **Date:** 2026-10-30. **Reviewed technical pre-signoff Git SHA:** `977392dd73273ea2a7930651d6e3bd40da487859`. **Approver:** repository owner GitHub login `kunlong-luo`, acting through an explicit conditional authorization in the current conversation. **Record keeper:** assistant working via the authenticated GitHub connector; this is **not a cryptographically signed release/tag or independently confirmed legal identity**. See the [signed D0 technical decision package](d0-decision-package-2026-10-30.md) and [decision registry](open-decisions.md). No security test, platform test or production capability is declared passed, and employer/legal consent has not been granted.

## Start-code rule — D0 satisfied
V0.1 application development may now begin on `main`, with Gradle 9.8.2 Groovy DSL/Spring Data JPA, Rust SQLx/SQLite and React/Vite/Orval as selected. Add real unit tests alongside code; avoid empty placeholder passing CI jobs. **Do not ingest real workplace/employee data**, run a genuine monitored-device pilot or ship a production monitoring service before separate employer/legal notice/retention authorization and required actual Caddy/PKI, OS, DB, RBAC, backup and recovery tests. V0.2 audit collectors and V0.3 screen capture remain behind later independent approvals.

## Release gate
A docs gate approval is **not** proof of a secure product. Before real-data deployment, run manual endpoint tests for auth, replay, scopes and backups; prior to v1.0, broaden automated tests and security review. Never describe unit-only CI as full certification.
