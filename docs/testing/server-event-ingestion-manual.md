# SRV-03 — PostgreSQL 18 event ingestion acceptance

**Status: Java 25 unit/AOP tests plus a separately compiled Testcontainers PostgreSQL 18.6 integration suite. Actual container execution, real Flyway V6, row locks, JSONB conflicts, verified client-mTLS and HTTP batch ACK are NOT YET EXECUTED/ACCEPTED.** No real employee monitoring authorized.

## Implementation and security boundary

- [SRV-03 internal transactional service](../../apps/server/src/main/java/com/lanveriq/server/events/DeviceEventIngestionService.java) is deliberately **not an HTTP controller**; default Spring Security still denies business routes. `VerifiedDeviceCertificate` is an internal datum, not evidence of a TLS handshake on its own. A future reviewed Caddy/Spring bridge must extract the client cert identity only from a trusted verified peer and recheck the fingerprint in PostgreSQL; no request-provided fingerprint, Host or forwarded certificate header is trusted.
- [Flyway V6](../../apps/server/src/main/resources/db/migration/V6__endpoint_events_atomic_ingestion.sql) creates immutable `endpoint_events` with native UUIDv7 ID, server-set `received_at`, two independent unique keys `(device_id, device_sequence)` and `(device_id, event_id)`, and a PostgreSQL UPDATE/DELETE rejection trigger. A future retention path requires a separately approved controlled database maintenance process.
- The whole batch is checked before SQL (1–100 ordered positive sequences, unique IDs, <=512 KiB JSON, permitted operational gap payloads). Only `agent_transport/collection_gap` is accepted by production service. `synthetic_test/protocol_fixture` is test-policy-only.
- PostgreSQL locks the active device, validates a matching current, unexpired, unrevoked client credential fingerprint, expands validated JSONB using JPA/Hibernate native SQL, inserts with `ON CONFLICT DO NOTHING` and re-reads **every** identifier and canonical SHA-256 digest. Any conflict rejects and rolls back the entire transaction. A complete ACK leaves the Spring AOP boundary only after commit.

**Credential concurrency hardening (2026-10-31):** All V0.1 device-write repositories now reuse `JpaActiveDeviceCredentialGuard` inside their existing Spring transactions. The guard locks the active device row **first** and the *exact* unrevoked, issued, unexpired fingerprint row **second**, each via Hibernate/JPA PostgreSQL `FOR UPDATE`; validation after lock uses `clock_timestamp()`, not a stale transaction-start timestamp. Credential status changes serialize with the write. A manually dispatched PG18 integration test adds revoked, expired, future and concurrent revocation denials with zero event/heartbeat side effects. **These new PostgreSQL cases are not executed by unit CI.** No HTTP route or new agent collection is enabled.

## Real PostgreSQL 18 execution — opt-in only

The [dedicated JUnit Testcontainers suite](../../apps/server/src/integrationTest/java/com/lanveriq/server/events/PostgreSqlEventIngestionIT.java) uses an **actual `postgres:18.6` container** with Spring Boot 4.1's `@ServiceConnection` and managed Flyway/JPA. It includes **exact PostgreSQL 18.6** and V1–V6 migration checks, native UUIDv7 for both device and accepted event IDs, JSONB and database-set receive time, duplicate replay, both unique keys and split-identity collisions, rollback after sequence/event-ID conflicts, forged/expired/revoked credential denial, UPDATE/DELETE mutation-trigger denial and concurrent identical/conflicting batches. No developer-provided private keys, accounts or employee events are used.

A workstation with a permitted Docker daemon and JDK 25 can run it explicitly:

```sh
cd apps/server
./gradlew integrationTest
```

Or in the GitHub repository, open **Actions → PostgreSQL 18 integration (manual, synthetic-only) → Run workflow → main**. This runs `./gradlew integrationTest` on an isolated hosted runner and uploads short-lived synthetic-only JUnit XML. The workflow has **no push, pull_request or schedule trigger**. The normal `server-unit` workflow only compiles the integration sources; that successful compilation is not evidence that Docker/PostgreSQL or transactional behavior passed.

For an execution to close the *database-only* acceptance gate, record the Git SHA, run URL, exact PostgreSQL server version (the suite now asserts `server_version_num = 180006`), Flyway history, successful JUnit cases and sanitized logs. The manual workflow also logs the tested Git SHA and inspects the pulled `postgres:18.6` image's ID/repository digests in an always-run provenance step; **confirm a nonempty image digest in the recorded output**, rather than treating a tag as immutable. An image digest is build provenance, not proof that SQL passed. The suite still has no observed real execution. A green DB suite **still does not validate Caddy TLS, certificate custody, cross-protocol ACK or actual endpoint/OS security**.

## Manual test matrix — all real database/HTTP cases still open

| Case | Expected behavior | Evidence |
|---|---|---|
| SRV-EVENT-01 | PG18 Flyway V1–V6 applies, UUIDv7 and two unique keys plus immutable trigger exist; no event seed | Manual script written, **NOT RUN** |
| SRV-EVENT-02 | Same device/sequence/event ID/content replay returns identical accepted sequences without a second row | Unit comparison; real PG NOT RUN |
| SRV-EVENT-03 | Changed content on reused sequence, reused event ID on different sequence or split collision across two rows rejects **entire batch**, no partial insert | Unit + AOP rollback; real PG NOT RUN |
| SRV-EVENT-04 | Wrong path/body/cert ID, unregistered/pending/revoked/expired cert or revoked device rejected before write | Source/unit; real TLS and DB NOT RUN |
| SRV-EVENT-05 | 101 events, 512 KiB overflow, invalid policy/source/schema, duplicate IDs, disordered sequences, forbidden operational payload fields | Unit/source; real HTTP rejection NOT RUN |
| SRV-EVENT-06 | Concurrent same-device duplicate batches serialize under `FOR UPDATE OF d`; no false positive ACK after lost response | Real PG concurrent test NOT RUN |
| SRV-EVENT-07 | Caddy h1/h2 strict SNI/Host, unspoofable client cert identity, valid offline-issued clientAuth-only leaf and revocation | NOT IMPLEMENTED/TESTED |
| SRV-EVENT-08 | Rust AGT-03 sends over real mTLS and prunes only after complete post-commit ACK | NOT IMPLEMENTED/TESTED |
| SRV-EVENT-09 | SQL read-only, corruption, invalid payload, retention/backup and trigger tamper testing | NOT TESTED |

The disposable PostgreSQL 18.6 [manual smoke script](server-postgres18-manual.md) may be run with **synthetic-only** data in an approved local environment:

```sh
bash apps/server/scripts/verify-postgres18.sh
```

This script checks schema, constraints, no seeds and HTTP deny-all, **not** actual ingestion. For true acceptance, use an independently provisioned loopback-only PostgreSQL 18 test database plus a separately approved mTLS Caddy setup; verify real transaction/rollback/concurrency and capture sanitized evidence with commit SHA and environment details. Never weaken certificate verification, expose raw fingerprints from untrusted headers or use real employee data to make a test pass.
