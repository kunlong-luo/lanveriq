# Lanveriq D0 technical decision package — 2026-10-30

**Status: REVIEW-READY CANDIDATES / NOT APPROVED.** Documentation-only repository. This document is a compact *decision cover sheet*, **not** a second API, PKI or privacy source of truth. Maintainer's instruction to continue review is **not** consent to deploy, authorize monitoring, or start software implementation.

## Decision authority and exact approval scope

- Product maintainer approves (or returns for revision) the V0.1 *technical* baseline. Security reviewer confirms PKI assumptions/negative tests. Organizational/legal owner separately authorizes **any real employee-data collection**, specific notice, retention and purpose.
- **D0 approval unlocks writing V0.1 application code only.** It does not certify software, authorize a real workplace pilot, permit V0.2 activity auditing, or authorize V0.3 recording.
- The root [AGENTS.md](../../AGENTS.md) continues to prohibit creating server/Agent/dashboard projects, dependencies or CI until an actual dated affirmative sign-off. Direct-to-main and unit-only early CI remain an approved workflow *after* D0.

## Four decisions to review (technical proposals, unsigned)

| Decision | Specific proposed choice | Canonical document | What a reviewer must decide |
|---|---|---|---|
| **D-07 identity/PKI** | Dedicated console + enrollment + Agent TLS hosts; mTLS mandatory only for enrolled Agent; dedicated **offline client CA and operator-mediated Smallstep CSR signing** for 1–10 endpoints; token-bound pending identity; **mTLS-only activation endpoint** followed by active-only heartbeat/ingest; Spring verifies proxy-asserted cert fingerprint/device authorization each request | [Enrollment and mTLS candidate](../architecture/enrollment-bootstrap.md), [sequence](../diagrams/enrollment-sequence.md) | Accept manual signing and verified delivery (more operator effort) **or** request an explicitly designed online CA option; approve CA custody, cert lifetime, key storage, route/identity boundary, renewal, revocation and tested proxy header strategy |
| **D-11/D-22 V0.1 protocol** | HTTP `/api/v1`; snake_case/UTC; V0.1 `agent_transport/collection_gap` only in deployed production and synthetic fixtures **test-only**; all-or-nothing event batches, inventory revision CAS, stable error codes and role-projected responses | [API/fixtures](../architecture/api-contract.md), [event delivery](../architecture/event-delivery.md), [inventory](../architecture/device-inventory.md) | Accept field-requiredness, source/event pair, null/missing rules, errors, 100-event/512-KiB and 128-KiB inventory caps, CSR enrollment DTO and retry/duplicate semantics |
| **D-17/18/20 permissions/privacy** | V0.1 baseline admin session + device group; separate `internal_operational` for raw IP/MAC/NIC; WSS metadata only; field collection conditioned on prior organization notice; proposed seven-day diagnostics only when justified | [Permission matrix and minimum-data proposal](../security/authorization-matrix.md), [privacy](../security/privacy-and-consent.md), [retention](../security/data-retention.md) | Accept/modify default network fields, health-only projection, data classification and **provisional** retention. Record separate legal/data-controller approval before real data |
| **D-02 first Linux target** | Initial reported Pop!_OS 24.04 KDE/KWin x86_64 pilot; `XDG_SESSION_TYPE` **unknown** until captured from user desktop; Windows 11 x64 independent test target; V0.1 covers health/inventory, not activity/recording | [Platform matrix/evidence](../architecture/platform-matrix.md), [test matrix](../testing/test-matrix.md) | Confirm exact first supported pilot configuration. Mark X11/Wayland unverified until real interactive-session evidence. No universal KDE/Wayland claim |

## Deliberate D0 blockers / unresolved choices

- [ ] **D-07:** approve exact console/enrollment/agent DNS names, Caddy TLS site separation and trusted fingerprint injection path (actual Caddy config/OS crypto backend are post-D0 tests), offline client-CA responsibility, operator certificate issuance/install UX, expiry and recovery.
- [ ] **D-11/D-22:** approve the V0.1 request/response fixtures and named enum/error surface; accept test-only synthetic source isolation, current inventory semantics and read-projection privacy.
- [ ] **D-17/D-18/D-20:** approve read/grant rules and the intended collection notice/minimization scope; actual employer/legal sign-off and retention authorization remain independent.
- [ ] **D-02:** identify the actual pilot Linux interactive session (or explicitly record this as **post-D0 test evidence**, with no pre-approval claim of X11/Wayland support); confirm target host and Windows test availability.
- [ ] **Maintainer D0 authorization:** record a dated, explicit **APPROVE** decision for the reviewed Git SHA and any exceptions in [D0 gate](documentation-gate.md) and [open decisions](open-decisions.md). If any above blocking technical choice remains open, D0 stays pending.

## Negative test acceptance (planned, NOT TESTED)

| ID | Required rejection/invariant | Evidence stage |
|---|---|---|
| D0-SEC-01 | browser login and initial enroll succeed only on their own verified TLS sites; Agent host rejects no/wrong certificate and **pending cert cannot heartbeat before dedicated activation** | real Caddy + Agent LAN test after D0 |
| D0-SEC-02 | forwarded fingerprint injected directly from LAN / unauthorized Host header cannot make Spring accept a device | real proxy isolation test after D0 |
| D0-SEC-03 | revoked/rotated/expired Agent certificate or wrong-device ID is denied, even over an already-open connection | real TLS + Spring integration after D0 |
| D0-PROTO-01 | mutated event sequence or event_id rejects *whole* batch with 409; timeout/retry is idempotent and ACK only after commit | Java/PostgreSQL + Rust/SQLite verification after D0 |
| D0-PROTO-02 | older inventory_revision cannot overwrite current NIC/IP/volume children; old response cannot delete new pending snapshot | PG/SQLite transactional verification after D0 |
| D0-PROTO-03 | production V0.1 rejects process/audit test data and malformed/unsupported protocol versions | server validation tests after D0 |
| D0-PRIV-01 | wrong-group and health-only admin cannot infer MAC/IP, asset-tag, hidden device counts via REST/WSS/cursor/errors | auth unit + scoped manual test after D0 |
| D0-OS-01 | actual session protocol and Win/Linux NIC/CPU/RAM/OS capability statuses recorded without guessing; denied/unsupported means not collected | manual platform test after D0 |

Passing this *documentation review* means the assertions are written, **not** that these tests passed. See [manual acceptance](../testing/manual-acceptance.md), [D0 gate](documentation-gate.md), [implementation order](../planning/v0.1-implementation-sequence.md).

## Approval record — intentionally blank

| Field | Entry |
|---|---|
| Reviewed Git main commit SHA | **NOT RECORDED** (must be captured at decision time) |
| Maintainer decision | **PENDING — APPROVE / REQUEST REVISION / REJECT** |
| Maintainer identity and decision date | **NOT RECORDED** |
| D-07 reviewer and signed selection | **PENDING** |
| D-11/D-22 contract reviewer and version | **PENDING** |
| D-17/18/20 privacy and role reviewer | **PENDING** |
| D-02 Linux pilot decision/evidence | **PENDING** |
| Approved exceptions and acceptance ownership | **NONE APPROVED** |
| Separate organization/legal authorization for employee data | **NOT GRANTED** |

**Never fill approval fields automatically**, based on a repository commit, AI analysis or a generic “continue.” The approved snapshot is the reviewed Git commit plus explicit accountable human sign-off; later source edits require a new baseline review.
