# Issue-ready prioritized backlog

**Draft. No code work authorized until D0 documentation gate.** Priority P0 = first usable skeleton, P1 = pre-release capability, P2 = optional later.

| ID | Title | Priority | Depends on | Acceptance evidence |
|---|---|---|---|---|
| DOC-01 | Approve PRD, collection policy and Linux matrix | P0 | none | documented owner review |
| ARCH-01 | Validate canonical event_type schema, atomic batch, re-enrollment and pairing | P0 | DOC-01 | [delivery contract](../architecture/event-delivery.md) and negative cases accepted |
| SRV-01 | Bootstrap **Gradle 9.8.2** Groovy + **Spring Boot 4.1.2/Java 25** + JPA/Hibernate/PostgreSQL 18 + Flyway | P0 | D0 | official Gradle Wrapper, local dependency resolution/migration, real JUnit 6 unit test |
| SRV-02 | Device enrollment and revocation | P0 | SRV-01 | unit tests token replay/expiry |
| ID-01 | PostgreSQL uuidv7() device key and bound credential; safe re-enrollment | P0 | SRV-02 | identity collision/wrong-key/clone/lost identity unit tests |
| AUTH-01 | Baseline secure admin session, role/group-scoped REST + WSS access | P0 | SRV-01 | role/group/Origin/CSRF and device-credential negative tests |
| AGT-01 | Rust service lifecycle and config | P0 | D0 | Windows/Linux service smoke |
| AGT-02 | SQLite WAL single-writer outbox | P0 | AGT-01 | crash/retry/quota unit tests |
| AGT-03 | HTTPS authenticated heartbeat and event batch | P0 | AGT-02, SRV-02 | unit test retries/dedup |
| SRV-03 | Whole-batch PostgreSQL event ingestion + idempotent ack | P0 | SRV-02 | exact duplicate/conflict/rollback unit tests |
| INV-01 | Rust platform inventory: CPU/RAM/OS/volumes/multiple NICs with IP/MAC | P0 | AGT-01 | INV-01 through INV-07 unit cases + manual Windows/Pop!_OS |
| INV-02 | PG inventory current+NIC/IP/volume schema and idempotent snapshot API | P0 | SRV-02, INV-01 | INV-08–12 unit tests; late snapshot no overwrite |
| INV-03 | Bounded CPU/RAM/disk sampling, freshness and device detail UI | P0 | INV-02, WEB-01 | INV-13–16, permission checks and freshness display |
| WEB-01 | Dashboard shell and device list (authoritative REST snapshot) | P0 | SRV-02 | UI unit tests |
| MON-01 | Dedicated Monitoring Center: fleet/source health, backlog, limited hardware summary | P0 | WEB-01, INV-03, WS-01 | real-device heartbeat, group scope and no unsupported-as-zero tests |
| WS-01 | Authenticated Spring WSS upgrade, scoped topics, after-commit notifier | P0 | SRV-02, SRV-03, AUTH-01 | WS-001 through WS-009 unit tests; manual MAN-WS-01 through 08 |
| WEB-WS-01 | React WSS client with resubscribe/reconnect and REST refetch | P0 | WEB-01, WS-01 | duplicate/slow/reconnect unit tests and manual validation |
| OPS-00 | Secure bootstrap, credential and manual LAN acceptance | P0 | SRV-01, AGT-03 | [MAN-001–013](../testing/manual-acceptance.md) evidence on synthetic endpoints |
| AUD-01 | Windows scoped Event Log source | P1 | AGT-03 | mock unit + manual Windows test |
| AUD-02 | Linux auditd/journal adapter | P1 | AGT-03 | mock unit + manual Linux test |
| ACT-01 | OS/window activity metadata collector | P1 | AGT-03 | supported/unsupported feature matrix |
| SRV-04 | Timeline search and RBAC | P1 | AUD-01/02 | negative role/device tests |
| OPS-01 | Retention, quota and backup/restore | P1 | SRV-03 | cleanup unit + restore exercise |
| CAP-01 | Visible recording companion and OS grant | P1 | privacy gate | manual deny/allow tests |
| CAP-02 | Segmented upload and manifest | P1 | CAP-01 | checksums/retry tests |
| AUTH-02 | V0.3 approved recording review cases, per-request/Range access checks | P1 | AUTH-01, CAP-02 | independent approval, expiry/revoke, cross-group, byte-range denial and access audit tests |
| WEB-02 | Recording & Playback Center: catalog, approved case and scoped playback | P1 | CAP-02, AUTH-02 | case approval/expiry, partial range, missing segment and access audit tests |
| REC-03 | Recording retention, integrity and verified deletion closure | P1 | WEB-02, OPS-01 | failed/expired segment clean-up and backup-restore audit |
| PKG-01 | Windows/Linux installers | P1 | v0.2 | clean-device installation |
| SEC-01 | Production security review and integration suite | P0 before v1.0 | v0.4 | full evidence, not unit CI only |

## Maintenance rules
Break tasks so one change is easy to review and revert. Source code may go directly to main during early development, and each task should leave documentation and meaningful unit coverage. No mandatory PR process yet; do not create misleading empty CI jobs or passing placeholder tests.
