# Rust Agent: SQLite outbox, persistence and crash recovery

**Status:** Design, no implementation. **Selected:** **SQLx 0.9 SQLite** + patched bundled SQLite WAL + one serialized write connection; supersedes the rusqlite blocking-worker proposal under [ADR-0007](adrs/0007-mature-ecosystem-first.md). Inspired by Pagevela's single ordered persistence coordinator, but backend is SQLite, not browser IndexedDB. Canonical acknowledgment semantics are specified in [Event Delivery](event-delivery.md).

## Proposed local tables
- `agent_state`: schema_version, device_id, enrollment_status, policy_version, next_sequence (audit), **next_inventory_revision** (independent and durably monotonic). Do not use a global last_ack_sequence to delete outstanding lower-numbered rows.
- `outbox_events`: event_id UUID UNIQUE, device_sequence INTEGER UNIQUE NOT NULL, **schema_version**, source, event_type, policy_version, occurred_at_utc, payload_json (allowlisted), **immutable canonical content_digest**, created_at_utc, acked_at_utc NULL, bytes. Persist every event-fingerprint field atomically; retries cannot regenerate it.
- `collector_cursors`: source PRIMARY KEY, cursor_blob, updated_at, last_event_sequence.
- `upload_batches`: id UUID, first_sequence, last_sequence, attempts, next_attempt_at, last_error_code.
- `collection_gaps`: id UUID, source, from_at, to_at, reason, reported_at.
- `inventory_pending`: one bounded latest complete snapshot with stable snapshot_id, **inventory_revision**, validated content digest, body, retry state; replace only older pending versions and never use audit-event sequence.
- `recording_manifests` (v0.3): session_id, segment_id, local_path, sha256, size, state, last_attempt_at. Paths must be confined to the configured media directory.
- Database migrations: SQLx-managed `_sqlx_migrations` metadata; no bespoke migration runner.

Never store plaintext enrollment token, device private key, password, keyboard data, clipboard data or the actual video as SQLite BLOBs. **V0.1 has no real application/process/audit collectors**: scoped inventory and heartbeat are operational, `agent_transport/collection_gap` is the only proposed production **operational gap event** when a gap can be safely persisted, and `synthetic_test/protocol_fixture` is only permitted in a controlled test profile. Real app/audit collectors require V0.2 policy review. A persistent `device_id`/private-key pair must survive an ordinary Agent restart; lost identity requires re-enrollment.

## Write and upload path
1. Adapter observes a scoped event and validates/redacts fields.
2. SQLx single-connection write pool executes an explicit **async transaction** inserting the event, incrementing sequence and advancing source cursor, then commits; never hold DB transactions across network I/O.
3. Async uploader loads up to 100 events or 512 KiB (provisional caps), sends request with device identity; persisted event ID, device_sequence, schema_version and payload/digest make retry idempotent. Persist the prepared `batch_id` for byte-identical retries where used; server dedup is **per event**, not based solely on batch ID.
4. Server validates the **whole batch**, inserts or deduplicates it atomically in PostgreSQL and returns **all** accepted sequences after commit; any invalid/conflicting item rejects the whole batch.
5. Local writer marks **only explicitly acknowledged sequences** and prunes the acknowledged rows. Ambiguous timeout → retry, do not drop. A `409 sequence_conflict` is quarantined and surfaced as degraded.
6. If outbox is full, reserve bounded emergency gap capacity and report the **affected loss interval**. If disk is fully unwriteable, heartbeat exposes integrity_degraded so server can infer a coarse gap. Never fabricate event completeness.

## Inventory offline reliability
A hardware/network inventory change persists a newer `inventory_pending` snapshot and advances `next_inventory_revision` through the SQLx single-connection transaction boundary **atomically**. Agent sends the latest snapshot over its separate HTTPS inventory endpoint. Same ID/revision/content retries are idempotent; a late older revision must not overwrite current server state. A server ack for old revision N **cannot clear** an unacknowledged newer revision N+1. Inventory is **latest-state diagnostics**, unlike the immutable audit event stream. See [Device Inventory](device-inventory.md).

## SQLite configuration proposal
`journal_mode=WAL`; `foreign_keys=ON`; `busy_timeout=5000`; `synchronous=FULL` for durable outbox pending benchmarking. Use bounded transactions, one writer, scheduled checkpoints and `PRAGMA integrity_check` in diagnostics. Use SQLx migrations and `SqlitePoolOptions::max_connections(1)` to avoid a bespoke blocking worker. Bundle SQLite including the WAL-reset fix (>=3.51.3 or officially fixed backport); **SQLite 3.54.1 is latest stable as of 2026-10-30**, but verify the actual `sqlite_version()` linked in each built Agent rather than inferring it from SQLx.

## Identity lifecycle
A clean reinstall with lost local database/device key requires fresh enrollment and a **new device_id**. Do not reset the sequence counter for an existing device_id. Old server evidence remains linked to the revoked historical record; any replacement mapping is approved and audited. See [Event Delivery](event-delivery.md).

## OS storage
- Windows: service-identity-restricted directory under `%ProgramData%/Lanveriq/`; use appropriate ACLs and optional BitLocker.
- Linux: `/var/lib/lanveriq/` owned by dedicated service account (0700). Disk encryption via host policy/LUKS as applicable.
- WAL is stored on a **local disk** only. Avoid SMB/NFS and any multi-host direct database access.
- No secrets in logs. Credentials held in protected OS key storage with supportable rotation.

## Failure scenarios
| Event | Required behavior |
|---|---|
| Restart before transaction commit | event may not have been accepted; report source cursor/gap if unrepeatable |
| Restart after local commit | replay queued event |
| Network timeout after server commit | retry; PG UNIQUE guarantees one logical event |
| Disk nearly full | warn; enforce quota; explicit gap |
| Corrupt SQLite | isolate/quarantine, stop claiming healthy, require controlled repair |
| Clock jump | keep monotonic sequence; flag drift |
| Schema upgrade | transactional migration and backup; no silent data loss |
| Host administrator tampering | cannot guarantee tamper-proof collection; server flags stale/gaps |

## SQLite WAL operational caveat
SQLite WAL supports one writer; a long-lived reader may stall checkpoints, so queries must be short and WAL size observed. Use the [SQLite WAL official guide](https://sqlite.org/wal.html) and test the actual bundled patch version. Do not place SQLite on SMB/NFS or copy an active DB file as a backup without SQLite-aware snapshot handling.

## Required unit tests
Event+cursor atomicity; retry after ambiguous ack; dedup key; sequential cursor; full-queue reporting; restart/replay; WAL checkpoint behavior; migration error; credential redaction. Actual power-loss and disk-pressure validation stay manual until broader integration testing is authorized.
