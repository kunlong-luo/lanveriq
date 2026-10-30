# Lanveriq D0 technical decision package — 2026-10-30

**Status: D0 TECHNICAL BASELINE APPROVED FOR V0.1 IMPLEMENTATION — 2026-10-30.** The repository remains documentation-only at sign-off: **no running software or passed operational security tests**. This is an auditable technical decision cover sheet, not a substitute API/PKI/privacy source of truth. **Real employee-data processing, live endpoint deployment, V0.2 audit and V0.3 recording remain NOT AUTHORIZED.**

## Decision authority and exact approval scope

- The authenticated GitHub repository owner `kunlong-luo` issued an explicit **conditional authorization in this chat** (approve if the final technical review finds no blockers). The reviewed D0 technical baseline meets that pre-code condition; the approval is recorded below against a fixed pre-signoff Git commit. This record is entered by the assistant on the maintainer's instruction, **not an independent cryptographic signature or independent security audit**. A separate security/code review and observed negative tests are required before any real-data pilot. Organizational/legal owner separately authorizes **all real employee-data collection**, notice, retention and purpose.
- **D0 approval unlocks writing V0.1 application code only.** It does not certify software, authorize a real workplace pilot, permit V0.2 activity auditing, or authorize V0.3 recording.
- With the dated D0 authorization recorded here, V0.1 implementation modules, dependencies and meaningful unit-only CI **may now be created** under [AGENTS.md](../../AGENTS.md) and the direct-to-main workflow. No production or real employee-data processing is permitted merely because code can be written.

## Four selected D0 technical decisions (implementation evidence pending)

| Decision | Approved V0.1 technical choice | Canonical document | Approved boundary / outstanding post-D0 evidence |
|---|---|---|---|
| **D-07 identity/PKI** | Dedicated console + enrollment + Agent TLS hosts; mTLS mandatory only for enrolled Agent; dedicated **offline client CA and operator-mediated Smallstep CSR signing** for 1–10 endpoints; token-bound pending identity; **mTLS-only activation endpoint** followed by active-only heartbeat/ingest; Spring verifies proxy-asserted cert fingerprint/device authorization each request | [Enrollment and mTLS candidate](../architecture/enrollment-bootstrap.md), [sequence](../diagrams/enrollment-sequence.md) | **Selected:** admin-only CSR review + signed public-leaf registration against stored CSR SPKI; offline client CA via Smallstep, custom clientAuth-only P-256 leaf with `urn:uuid:<device_id>` URI SAN and 90-day validity; three default hostnames and h1/h2-only strict SNI/Host. The operator protects CA signing key and approved key material. Actual Caddy/OS key-store, renewal, proxy header and revocation tests are mandatory after code. |
| **D-11/D-22 V0.1 protocol** | HTTP `/api/v1`; snake_case/UTC; V0.1 `agent_transport/collection_gap` only in deployed production and synthetic fixtures **test-only**; all-or-nothing event batches, inventory revision CAS, stable error codes and role-projected responses | [API/fixtures](../architecture/api-contract.md), [event delivery](../architecture/event-delivery.md), [inventory](../architecture/device-inventory.md) | **Selected:** V0.1 field-requiredness and missing/null/redaction, source/event pair, named errors, atomic 100-event/512-KiB ACKs, 128-KiB inventory CAS, pending CSR/activation flow and role-specific projection semantics. Real Springdoc OpenAPI/Orval generated contract tests follow implementation; breaking changes require review. |
| **D-17/18/20 permissions/privacy** | V0.1 baseline admin session + device group; separate `internal_operational` for raw IP/MAC/NIC; WSS metadata only; field collection conditioned on prior organization notice; proposed seven-day diagnostics only when justified | [Permission matrix and minimum-data proposal](../security/authorization-matrix.md), [privacy](../security/privacy-and-consent.md), [retention](../security/data-retention.md) | **Selected:** role + group baseline; `internal_operational` grant is required even for owners to read raw IP/MAC/NIC; metadata-only WSS, network collection controlled by approved notice, and *provisional* 7-day network diagnostics only if justified. **No employer/legal notice or retention approval is inferred; do not collect real employee data yet.** |
| **D-02 first Linux target** | Initial reported Pop!_OS 24.04 KDE/KWin x86_64 pilot; `XDG_SESSION_TYPE` **unknown** until captured from user desktop; Windows 11 x64 independent test target; V0.1 covers health/inventory, not activity/recording | [Platform matrix/evidence](../architecture/platform-matrix.md), [test matrix](../testing/test-matrix.md) | **Selected:** reported Pop!_OS 24.04 x86_64 KDE/KWin machine as first Linux V0.1 health/inventory test target, plus independent Windows 11 x64 validation. Interactive `XDG_SESSION_TYPE` remains UNKNOWN and cannot substantiate X11/Wayland or screen capture support without observed evidence. |

## Design decisions resolved for D0; remaining execution/organizational gates

- [x] **D-07 selected technical design:** `console.lanveriq.internal`, `enroll.lanveriq.internal`, `agent.lanveriq.internal` are approved pilot *default* names, not yet provisioned DNS. Caddy h1/h2 only + `strict_sni_host on`; isolated Agent mTLS, dedicated offline client CA, operator-reviewed CSR/public cert registration, clientAuth-only P-256/90d leaf with `urn:uuid:<device_id>` URI SAN, SHA-256 DER fingerprint, renewal/revocation. All Caddy/PKI/key-store/runtime behavior **NOT TESTED**.
- [x] **D-11/D-22 technical contract:** V0.1 DTO envelopes and error semantics, production `agent_transport/collection_gap` only, `synthetic_test/protocol_fixture` in controlled test profile only, duplicate-safe atomic event ACK, monotonic inventory and privacy-scoped projections are the implementation baseline. Real generated OpenAPI and OS/DB fixtures **NOT TESTED**.
- [x] **D-17/D-18/D-20 technical scope:** baseline role+group auth, separate `internal_operational` permission and privacy-minimized V0.1 inventory contract selected. **Employer notice, actual IP/MAC collection authorization and legal/retention policy NOT GRANTED**; V0.1 may use only synthetic data until separately authorized.
- [x] **D-02 target chosen:** reported Pop!_OS/KWin x86_64 first, Windows 11 x64 second; actual interactive `XDG_SESSION_TYPE = UNKNOWN` is explicitly deferred to post-D0 device evidence, and no compositor capability is claimed.
- [x] **Maintainer D0 technical authorization:** the repo owner explicitly instructed in this chat to approve if this final review found no blockers. The technical review has no remaining pre-code D0 design blocker; reviewed pre-signoff Git SHA and decision date recorded below. **Real-data pilot/production and V0.2/V0.3 approvals are independent and still closed.**

## Negative test acceptance (planned, NOT TESTED)

| ID | Required rejection/invariant | Evidence stage |
|---|---|---|
| D0-SEC-01 | browser login and initial enroll succeed only on their own verified TLS sites; Agent host rejects no/wrong certificate and **pending cert cannot heartbeat before dedicated activation** | real Caddy + Agent LAN test after D0 |
| D0-SEC-02 | forwarded fingerprint injected directly from LAN / unauthorized Host header cannot make Spring accept a device | real proxy isolation test after D0 |
| D0-SEC-03 | revoked/rotated/expired Agent certificate or wrong-device ID is denied, even over an already-open connection | real TLS + Spring integration after D0 |
| D0-SEC-04 | console refuses certificate with wrong CSR public key, issuer, clientAuth EKU, CA flag, device ID, expiration or duplicate fingerprint; only authorized admin can register | security admin/PKI integration after D0 |
| D0-SEC-05 | TLS SNI/HTTP Host disagreement, HTTP/2 cross-host reuse and client-injected/duplicate fingerprint headers cannot reach Agent identity as authenticated | real Caddy proxy + Spring negative test after D0 |
| D0-PROTO-01 | mutated event sequence or event_id rejects *whole* batch with 409; timeout/retry is idempotent and ACK only after commit | Java/PostgreSQL + Rust/SQLite verification after D0 |
| D0-PROTO-02 | older inventory_revision cannot overwrite current NIC/IP/volume children; old response cannot delete new pending snapshot | PG/SQLite transactional verification after D0 |
| D0-PROTO-03 | production V0.1 rejects process/audit test data and malformed/unsupported protocol versions | server validation tests after D0 |
| D0-PRIV-01 | wrong-group and health-only admin cannot infer MAC/IP, asset-tag, hidden device counts via REST/WSS/cursor/errors | auth unit + scoped manual test after D0 |
| D0-OS-01 | actual session protocol and Win/Linux NIC/CPU/RAM/OS capability statuses recorded without guessing; denied/unsupported means not collected | manual platform test after D0 |

Passing this *documentation review* means the assertions are written, **not** that these tests passed. See [manual acceptance](../testing/manual-acceptance.md), [D0 gate](documentation-gate.md), [implementation order](../planning/v0.1-implementation-sequence.md).

## Approval record — maintainer-directed technical sign-off

| Field | Entry |
|---|---|
| Reviewed pre-signoff Git main commit SHA | `977392dd73273ea2a7930651d6e3bd40da487859` (technical baseline after security/contract fixes; sign-off-only documentation commits follow) |
| Maintainer decision | **APPROVED — D0 for V0.1 source implementation ONLY** |
| Approver and decision date | GitHub-authenticated repository owner `kunlong-luo`; explicit conditional instruction in this conversation; **2026-10-30**. This is not an independently verified handwritten/digital signature |
| D-07 technical selection | Three separated TLS hosts, Agent-only mTLS, offline client CA/operator CSR review, P-256/90-day leaf and per-request active fingerprint verification; security integration tests pending |
| D-11/D-22 contract baseline | V0.1 `/api/v1`, schema_version=1, DTO/event/error/inventory/scope semantics selected; generated contract/integration evidence pending |
| D-17/18/20 technical baseline | Assigned group + role, extra `internal_operational` grant for IP/MAC, no activity/recording in V0.1; employer/legal permission separate |
| D-02 target and evidence | Pop!_OS 24.04 x86_64 KDE/KWin pilot selected; X11/Wayland unobserved, Windows 11 real test pending |
| Approved exceptions / acceptance owner | No bypass of mTLS, device/group authorization or privacy gates. Implementer owns post-D0 unit/integration/manual evidence; independent security review before real data |
| Separate organization/legal authorization for employee data | **NOT GRANTED — not included in D0 approval** |

**Approval provenance:** the explicit conditional user instruction (“最后一次检查，如果没有问题就批准”), issued while authenticated through the repo owner's GitHub connection, is the authorizing maintainer action recorded here **after a no-blocker technical review**. This is not invented approval from a generic “continue,” and does not substitute for independent organizational/legal security sign-off. Future incompatible changes require a new documented D0 decision/ADR; implementation must demonstrate the documented security properties before processing real employee data.
