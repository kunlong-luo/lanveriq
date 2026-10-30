# Final documentation-closure review — 2026-10-30

**Verdict: PRODUCT / ARCHITECTURE DESIGN FLOW = COHERENT, D0 EXECUTION BASELINE = NOT YET SIGNED OFF.**

**Scope:** `main` branch, **90 existing Markdown files** inspected for repository-relative file paths before this report, **20 Mermaid engineering drawing pages**, **8 self-contained 1600×900 SVG concept assets**. Adding this report increases the Markdown total to **91**. Documentation-only repository; no backend, Agent, Dashboard or CI source exists; **no compilation, browser render, OS acceptance or integration tests were executed**.

## 1. What was actually checked

- Audited the **90 existing Markdown documents** for local relative file/directory references, Markdown fence parity and stale Maven/rusqlite/JDBC-only/old drawing-count terms. **446 local links** resolved to existing repository paths, with zero missing path targets under these checks. This does **not** test every heading fragment or external URL.
- Statically inspected **20 Mermaid pages**: one `~~~mermaid` fence block per drawing and known diagram type. No structural anomalies found by this lightweight scan; an actual Mermaid parser/browser rendering session **has not been performed**.
- Statically inspected **8 SVGs**: XML tag nesting, declared 1600×900 viewBox, `title`/`desc`, concept-stage labels, absence of scripts/foreign objects/external images and obvious text-coordinate escape from canvas. No failures observed. **This does not prove glyph spacing, Chinese font substitution, color contrast, overlaps or GitHub/browser rendering.**
- Compared canonical event/API/SQLite and JPA/PG rules, identity/CA and trust boundaries, monitoring RBAC and WSS state, V0.2 audit and V0.3 authorized playback.

## 2. Product and protocol loop assessment

| Loop | Document logic | Remaining proof / blocker |
|---|---|---|
| V0.1 enrollment → server UUIDv7 → heartbeat → Monitoring Center → diagnostics | **Coherent design**; group-scoped queries, separated health vs IP/MAC privilege | **D-07:** exact device certificate/mTLS route separation, PKI lifecycle and verified Caddy→Spring binding require D0 decision; not implemented |
| V0.1 approved inventory → Agent SQLite revision → HTTPS → PG atomic current snapshot → scoped UI | **Coherent design**; multi-NIC, IP/MAC source labels, revision monotonicity and idempotency | Freeze DTO/field null semantics and verify SQLite-linked patch/real OS data |
| V0.1 synthetic events → atomic Agent outbox → PostgreSQL all-or-nothing commit → individual ACK → retry | **Coherent design**; event `schema_version`, immutable content and SQLx migration state reconciled | No real audit collectors in V0.1; require actual PG transaction/SQLite crash tests before claiming reliable delivery |
| Dashboard WSS after-commit hints → authorized REST snapshot → resubscribe/reconcile | **Coherent design**; post-subscription REST eliminates initial race; visible-page periodic/visibility reconciliation limits silently missed hints | Confirm Spring handler/Origin/session behavior and exact polling budget with real browsers |
| V0.2 allowlisted audit → projection/search → scope/retention | **Conceptually coherent for future scope** | Approved OS event sources, Linux compositor capability and retention/legal purpose not yet frozen |
| V0.3 prior notice/OS session permission → segments → checksum → review case → permitted media playback → deletion | **Conceptually coherent for future scope** with a newly explicit time-vs-byte boundary | Choose and test frame-accurate time-bounded rendition **or** allow only segments fully inside approved UTC window; no unapproved overlapping frames |
| Dependency/build/operations → release | **Documented, not executed** | Gradle/JPA, SQLx and Orval dependencies not yet compiled; CA backup/restore, signature/license/dependency review and platform manual tests open |

The Agent's immutable event sequence number is independent of the inventory revision and of device UUID; there is one canonical server-generated `devices.id`. A heartbeat or live WSS connection does **not** prove collector coverage or a complete recording.

## 3. Errors corrected in this final review

1. **REST example vs canonical event schema:** included `schema_version` on each event; used a valid-looking UUIDv7 device example; marked sample `system_audit/program_started` as a synthetic protocol fixture, not a V0.1 real collector.
2. **Agent SQLite and PG schema:** persisted event-version/digest fields, corrected SQLx migration table reference and explicit inventory `inventory_revision` CAS invariant.
3. **Monitoring role conflict:** IT operator may query **health-only** collection-gap data; sensitive event detail still needs auditor permissions. Primary IP omitted from summaries without `internal_operational` permission.
4. **WSS silently dropped notices:** browser performs low-frequency visible-page and tab-resume authorized REST reconciliation even if WS remains connected; added WS-009 and WS-010 plus MAN-WS-09 cases.
5. **Recording playback authorization:** byte-range permission is **not** a UTC time-window grant. Only verified clipped/time-bounded renditions or fully contained segments may be exposed; otherwise deny, and test seeks/multi-range/expired case.
6. **Device PKI boundary:** explicitly described the preferred segregated browser/pre-enrollment vs enrolled-Agent mTLS access, trusted Caddy→Spring identity path and why this **remains unsigned under D-07**. Do not interpret architecture prose as actual certificate enrollment.
7. **Testing and governance traceability:** aligned roadmap, WSS ADR/flow/UX, backup of test examples and gate statements, and referenced this final review.

## 4. What remains before maintainer can explicitly approve D0

| Priority | Open item | Acceptable decision/evidence | Owner |
|---|---|---|---|
| **P0** | **D-07 Agent auth/PKI** | Freeze initial trusted LAN hostnames, separated browser vs pre-enrollment vs mTLS Agent ingress, enrollment issuance, credential identity mapping at proxy→Spring, rotation/revocation, host-protected keys and CA disaster recovery | Maintainer + security reviewer |
| **P0** | **D-11/D-22 schema surface** | Sign off **V0.1 canonical** request/response/event fixtures, source enum for synthetic tests, operational vs auditor projection, version and error semantics. Generated Springdoc OpenAPI and Orval verification come **after code begins**; do not demand a fictional generated artifact before D0 | Maintainer + implementer |
| **P0** | **D-20, D-17/18 access/privacy defaults** | Approve V0.1 roles, internal_operational IP/MAC restrictions, endpoint notice scope, data retention defaults and local-only deployment constraints | Maintainer / organization |
| **P0** | **D-02 OS pilot target** | Confirm actual `XDG_SESSION_TYPE` and service permissions for reported Pop!_OS 24.04/KDE/KWin setup. This does not certify Windows/Linux support; Windows remains a separate test target | Maintainer / QA |
| **P0** | **Explicit D0 owner sign-off** | Dated approval of technical baseline + documented exemptions, **not** automatically conferred by this report/commits | Maintainer |
| **Before real employee data** | Legal/notice, secure key distribution, restored backup, OS/role/manual tests | Dated evidence + accountable organization approval; do not skip because D0 was signed | Organization / security |
| **Before V0.3** | Frame/time playback authorization, exact OS capture behavior, independent reviewer, deletion | Tested denial of out-of-window media and denied OS permission, scoped audit and media retention | Product/security |

**No need to invent additional foundational frameworks, duplicate ADRs or add infrastructure services** to close these items. Mature library reuse rules remain in root [AGENTS.md](../../AGENTS.md). The minimum next step is a **decision-signoff pass**, not more general brainstorming.

## 5. Honesty and handoff boundaries

- **Product-level design flow can be called conceptually closed**. **Executable implementation, platform security, database durability and browser media behavior are not closed** without code plus tests.
- Root `AGENTS.md`, PRD, permission matrix, API and ADRs are maintained sources, not evidence of working product.
- A future tool or coding assistant must **not mark the D0 checklist as approved without explicit maintainer sign-off**. `main` only, docs-first, unit-only early CI, real manual evidence before real-data pilot.

[Documentation index](../README.md) · [D0 gate](documentation-gate.md) · [Open decisions](open-decisions.md) · [Event delivery](../architecture/event-delivery.md) · [Enrollment](../architecture/enrollment-bootstrap.md) · [WS](../architecture/websocket-contract.md) · [Playback](../development/recording-playback-center.md) · [Diagrams](../diagrams/README.md) · [SVG gallery](../visuals/README.md).
