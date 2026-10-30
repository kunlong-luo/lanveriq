# Issue-ready prioritized backlog

**V0.1 development is underway after D0 approval on 2026-10-30.** SRV-01 source and Java 25 unit + bootJar CI have passed; SRV-02 internal token/CSR transaction and AUTH-01 JPA-backed auth/group scope, opt-in offline first-admin and a feature-flagged default-OFF console session skeleton are partly implemented; no real deployment login or device endpoints are open. PostgreSQL migrations, database issuance guards and mTLS runtime remain UNTESTED. Migration/runtime evidence must be verified before marking SRV-01 fully accepted. Priority P0 = first usable skeleton, P1 = pre-release capability, P2 = optional later. No real employee data without separate approval.

| ID | Title | Priority | Depends on | Acceptance evidence |
|---|---|---|---|---|
| DOC-01 | **D0 baseline decision recorded** (V0.1 technical scope and first Linux target; no real-data notice) | P0 **D0 approved** | none | [technical sign-off](../governance/d0-decision-package-2026-10-30.md); organizational/legal data policy remains open |
| ARCH-01 | **D0 protocol design accepted** for event_type, atomic batch, re-enrollment and CSR-based pairing | P0 **D0 approved** | DOC-01 | [API design contract](../architecture/api-contract.md) selected; generated schema and integration negative tests post-D0 |
| SRV-01 | Bootstrap **Gradle 9.8.2** Groovy + **Spring Boot 4.1.2/Java 25** + JPA/Hibernate/PostgreSQL 18 + Flyway | P0 **unit + bootJar PASS; PG startup pending** | D0 | [server code](../../apps/server/README.md) and [CI passing run](https://github.com/kunlong-luo/lanveriq/actions/runs/41061058441) establish Java 25 compilation, JUnit and Boot JAR packaging; [manual PG18 runbook](../testing/server-postgres18-manual.md) is scripted **but NOT EXECUTED** |
| SRV-02 | Device enrollment and revocation | P0 **token + CSR transaction unit PASS; external protocol pending** | SRV-01, AUTH-01 | JDK25 unit tests validate opaque SHA-256 token, Bouncy Castle PKCS#10/P-256 signature and atomic JPA orchestration; [CI run](https://github.com/kunlong-luo/lanveriq/actions/runs/41065309965). V3 SPKI unique migration and actual PG locking/rollback **NOT TESTED**; no exposed pairing/Admin/activation/issuer or revocation APIs |
| ID-01 | PostgreSQL uuidv7() device key and bound credential; safe re-enrollment | P0 | SRV-02 | identity collision/wrong-key/clone/lost identity unit tests |
| AUTH-01 | Baseline secure admin session, role/group-scoped REST + WSS access | P0 **V5 rate-limit/session/audit unit PASS; live HTTP/WSS pending** | SRV-01, SRV-02 | [Java 25 unit CI](https://github.com/kunlong-luo/lanveriq/actions/runs/41066726762): Argon2id / Spring Security DAO auth, disabled user and wrong password denial, no global admin authority, fresh role+group check, separate `internal_operational` V4 grant predicate, server-bound administrator UUID; opt-in local interactive first owner + group + audit transaction with role-row locking covered by unit tests ([run 41067769799](https://github.com/kunlong-luo/lanveriq/actions/runs/41067769799)); feature-flagged Spring Security login/logout/CSRF, exact Host/Origin/fetch-site, session fixation/cookie config now in source; Bucket4j/Caffeine rate-limit, JPA V5 session-epoch revocation and generic login/logout audit implemented in source with unit tests; **live** browser login/rate limiting/disabled-session revocation, Caddy ingress, WSS scope and PG18 still **NOT TESTED**. [console acceptance](../testing/console-session-manual.md) · [V5 internal security implementation](../../apps/server/README.md) |
| AGT-01 | Rust service lifecycle and config | P0 **Tokio/TOML + systemd/Windows SCM source and unit CI; OS smoke pending** | D0 | [Agent source](../../apps/agent/README.md) and [manual AGT-01 lifecycle checks](../testing/agent-lifecycle-manual.md); strict config / stop lifecycle tests, Cargo.lock + Windows SCM compile check; real Pop!_OS systemd and Windows 11 SCM install/start/stop **NOT TESTED** |
| AGT-02 | **SQLx 0.9 SQLite** single-write-connection WAL outbox + migrations | P0 | AGT-01 | crash/retry/quota, linked SQLite patch and migration unit tests |
| AGT-03 | HTTPS authenticated heartbeat and event batch | P0 | AGT-02, SRV-02 | unit test retries/dedup |
| SRV-03 | Whole-batch PostgreSQL event ingestion + idempotent ack | P0 | SRV-02 | exact duplicate/conflict/rollback unit tests |
| INV-01 | Rust platform inventory: CPU/RAM/OS/volumes/multiple NICs with IP/MAC | P0 | AGT-01 | INV-01 through INV-07 unit cases + manual Windows/Pop!_OS |
| INV-02 | PG inventory current+NIC/IP/volume schema and idempotent snapshot API | P0 | SRV-02, INV-01 | INV-08–12 unit tests; late snapshot no overwrite |
| INV-03 | Bounded CPU/RAM/disk sampling, freshness and device detail UI | P0 | INV-02, WEB-01 | INV-13–16, permission checks and freshness display |
| WEB-01 | Dashboard shell + **Orval-generated OpenAPI TanStack Query client**, authoritative REST device list | P0 | SRV-02 | generated schema/API diff and UI unit tests |
| MON-01 | Dedicated Monitoring Center: fleet/source health, backlog, limited hardware summary | P0 | WEB-01, INV-03, WS-01 | real-device heartbeat, group scope and no unsupported-as-zero tests |
| WS-01 | Authenticated Spring WSS upgrade, scoped topics, after-commit notifier | P0 | SRV-02, SRV-03, AUTH-01 | WS-001 through WS-010 unit tests; manual MAN-WS-01 through 09 |
| WEB-WS-01 | React WSS client with resubscribe/reconnect and REST refetch | P0 | WEB-01, WS-01 | duplicate/slow/reconnect unit tests and manual validation |
| OPS-00 | Secure bootstrap, credential and manual LAN acceptance | P0 | SRV-01, AGT-03 | [MAN-001–013](../testing/manual-acceptance.md) evidence on synthetic endpoints |
| AUD-01 | Windows scoped Event Log source | P1 | AGT-03 | mock unit + manual Windows test |
| AUD-02 | Linux auditd/journal adapter | P1 | AGT-03 | mock unit + manual Linux test |
| ACT-01 | OS/window activity metadata collector | P1 | AGT-03 | supported/unsupported feature matrix |
| SRV-04 | Timeline search and RBAC | P1 | AUD-01/02 | negative role/device tests |
| OPS-01 | Retention, quota and backup/restore | P1 | SRV-03 | cleanup unit + restore exercise |
| CAP-01 | Visible recording companion and OS grant | P1 | privacy gate | manual deny/allow tests |
| CAP-02 | Segmented upload and manifest | P1 | CAP-01 | checksums/retry tests |
| AUTH-02 | V0.3 approved recording review cases, **time-window-to-media mapping** and per-request/Range authorization | P1 | AUTH-01, CAP-02 | independent approval, expiry/revoke, cross-group, **no out-of-window frames**, Range denial and access audit tests |
| WEB-02 | Recording & Playback Center: catalog, approved case and scoped playback | P1 | CAP-02, AUTH-02 | case approval/expiry, partial range, missing segment and access audit tests |
| REC-03 | Recording retention, integrity and verified deletion closure | P1 | WEB-02, OPS-01 | failed/expired segment clean-up and backup-restore audit |
| PKG-01 | Windows/Linux installers | P1 | v0.2 | clean-device installation |
| SEC-01 | Production security review and integration suite | P0 before v1.0 | v0.4 | full evidence, not unit CI only |

## Maintenance rules
Break tasks so one change is easy to review and revert. Source code may go directly to main during early development, and each task should leave documentation and meaningful unit coverage. No mandatory PR process yet; do not create misleading empty CI jobs or passing placeholder tests.
