# Issue-ready prioritized backlog

**Draft. No code work authorized until D0 documentation gate.** Priority P0 = first usable skeleton, P1 = pre-release capability, P2 = optional later.

| ID | Title | Priority | Depends on | Acceptance evidence |
|---|---|---|---|---|
| DOC-01 | Approve PRD, collection policy and Linux matrix | P0 | none | documented owner review |
| ARCH-01 | Validate canonical event_type schema, atomic batch, re-enrollment and pairing | P0 | DOC-01 | [delivery contract](../architecture/event-delivery.md) and negative cases accepted |
| SRV-01 | Boot Spring Boot/PostgreSQL with Flyway | P0 | D0 | local startup / migration smoke check |
| SRV-02 | Device enrollment and revocation | P0 | SRV-01 | unit tests token replay/expiry |
| AGT-01 | Rust service lifecycle and config | P0 | D0 | Windows/Linux service smoke |
| AGT-02 | SQLite WAL single-writer outbox | P0 | AGT-01 | crash/retry/quota unit tests |
| AGT-03 | HTTPS authenticated heartbeat and event batch | P0 | AGT-02, SRV-02 | unit test retries/dedup |
| SRV-03 | Whole-batch PostgreSQL event ingestion + idempotent ack | P0 | SRV-02 | exact duplicate/conflict/rollback unit tests |
| WEB-01 | Dashboard shell and device list (authoritative REST snapshot) | P0 | SRV-02 | UI unit tests |
| WS-01 | Authenticated Spring WSS upgrade, scoped topics, after-commit notifier | P0 | SRV-02, SRV-03 | WS-001 through WS-008 unit tests; manual MAN-WS-01 through 08 |
| WEB-WS-01 | React WSS client with resubscribe/reconnect and REST refetch | P0 | WEB-01, WS-01 | duplicate/slow/reconnect unit tests and manual validation |
| OPS-00 | Secure bootstrap, credential and manual LAN acceptance | P0 | SRV-01, AGT-03 | [MAN-001–013](../testing/manual-acceptance.md) evidence on synthetic endpoints |
| AUD-01 | Windows scoped Event Log source | P1 | AGT-03 | mock unit + manual Windows test |
| AUD-02 | Linux auditd/journal adapter | P1 | AGT-03 | mock unit + manual Linux test |
| ACT-01 | OS/window activity metadata collector | P1 | AGT-03 | supported/unsupported feature matrix |
| SRV-04 | Timeline search and RBAC | P1 | AUD-01/02 | negative role/device tests |
| OPS-01 | Retention, quota and backup/restore | P1 | SRV-03 | cleanup unit + restore exercise |
| CAP-01 | Visible recording companion and OS grant | P1 | privacy gate | manual deny/allow tests |
| CAP-02 | Segmented upload and manifest | P1 | CAP-01 | checksums/retry tests |
| WEB-02 | Case-scoped playback | P1 | CAP-02, RBAC | denied access and audit checks |
| PKG-01 | Windows/Linux installers | P1 | v0.2 | clean-device installation |
| SEC-01 | Production security review and integration suite | P0 before v1.0 | v0.4 | full evidence, not unit CI only |

## Maintenance rules
Break tasks so one change is easy to review and revert. Source code may go directly to main during early development, and each task should leave documentation and meaningful unit coverage. No mandatory PR process yet; do not create misleading empty CI jobs or passing placeholder tests.
