# ADR-0002: PostgreSQL server + SQLite/WAL endpoint

**Status:** Accepted design direction | **Date:** 2026-10-30

## Decision
- **Server:** PostgreSQL major 18, patch version pinned when implementation starts. Java uses JDBC/Spring Data JDBC and Flyway. DB holds devices, roles, scopes, events, activity summaries, segment manifests, retention and admin audit—not video binaries.
- **Agent:** embedded SQLite using Rust **rusqlite** with a bundled, audited SQLite library and **WAL**. DB holds durable event outbox, source cursors, ack state, local policy digest, upload attempt metadata and collection-gap records—not media files or plaintext secrets.
- Use local disk only for SQLite; no network-share WAL. Agent runs one serialized writer thread/actor with short transactions; read queries may run separately. All event/cursor writes enter this gateway.

## Proposed durability parameters
- Evaluate `journal_mode=WAL`, `foreign_keys=ON`, `busy_timeout=5000`, `synchronous=FULL` (benchmark before changing), periodic WAL checkpoint, quota and corruption detection.
- Do not assume WAL offers unrestricted multi-writer concurrency: SQLite still allows one writer.
- Bundle an SQLite release with the documented 2026 WAL-reset fix (>=3.51.3 or appropriate fixed backport); inspect the **actual bundled** version at build and test. Keep dependency pins updated for security.
- Restrict access through Windows service ACLs or Linux dedicated user and 0700 directories. Host filesystem encryption (BitLocker/LUKS) is preferred initially; SQLite alone does **not** encrypt database contents.

## Delivery protocol
1. Assign immutable event ID, per-device sequence and schema version.
2. In one SQLite transaction persist event + collector cursor.
3. Submit authenticated batch; wait for server PostgreSQL transaction commit.
4. Server validates and inserts with UNIQUE(device_id, sequence), idempotently returns accepted sequence/IDs.
5. Mark acknowledged in SQLite; purge according to policy. Retry ambiguous timeouts.
6. On full disk, explicit loss/backpressure and collection_gap; never silently drop and claim completeness.

## Alternatives rejected
Postgres inside endpoints (operationally excessive); raw JSON logs (harder migration/dedup); RocksDB (unneeded footprint); server SQLite (we prefer concurrent PG query/audit workload). Optional SQLCipher only after security/licensing review.

## Verification
Crash/retry tests, abrupt restart, WAL checkpoint, full-disk quota, corrupt database handling, migration/rollback and OS ACL tests. Neither DB makes endpoint evidence tamper-proof.

## References
[SQLite WAL](https://sqlite.org/wal.html) · [rusqlite](https://github.com/rusqlite/rusqlite) · [PostgreSQL 18](https://www.postgresql.org/docs/18/).
