# AGT-02 SQLite WAL / durable outbox acceptance

**Status: SQLx file-backed synthetic tests in unit CI; real power-loss, Windows 11/Pop!_OS disk failures and installed-service runtime NOT TESTED.** No live endpoint monitoring is active. Use only synthetic fixtures; never store real employee data, passwords, keys or tokens in the test DB.

## Implemented code boundary

- `OutboxStore::open` requires an absolute path in an existing local directory. One SQLx connection, WAL, foreign keys ON, busy timeout 5 seconds and synchronous FULL; SQLx embedded `migrate!` owns schema. `libsqlite3-sys =0.37.0` enables the bundled C SQLite library (documented upstream 3.51.3 with WAL-reset fix), and runtime verifies `sqlite_version()` before migrations. Confirm *actual* SQLite version in each test binary before production certification.
- `append_synthetic` is for synthetic fixture objects ONLY, with a stable caller-generated UUIDv7. Event+cursor+monotonic sequence are committed as one transaction; failed cursor write rolls back all three. Canonical compact JSON bytes are hashed with SHA-256 and stored with immutable payload. Full envelope-fingerprint interoperability is still an AGT-03/SRV-03 contract item.
- Outbox has 2048-event / 16 MiB budget, 16 KiB per-event cap, 100-event/512 KiB pending batch cap and 256 gap-record cap. A full queue writes a bounded **synthetic** collection gap when possible without advancing cursor; this is not permission to drop real audit events silently.
- ACK deletes **only explicit positive sequence numbers**. Ambiguous timeout, 5xx or missing server response must never call ACK. Server trust/authenticated ACK path is not yet implemented.
- **INV-01-C/D V3 (staged):** single latest `inventory_pending` is expanded from 64 KiB to **128 KiB** without discarding preexisting synthetic pending rows, and `inventory_latest_state` records enrolled device ID, last UUID, revision and semantic content SHA-256 (excluding volatile `observed_at`, UUID, revision). `stage_inventory_snapshot` requires typed normalized `RawInventory`; identical content returns the same pending UUID/JSON or `Unchanged` after ACK, without revision churn. Changed content atomically supersedes the latest pending snapshot while incrementing the independent revision. Transaction failure restores previous body/digest/revision; stale ACK must match **device ID, snapshot UUID and revision**. Old synthetic staging cannot cross an enrolled device identity boundary. No automatic collection or transport is connected.
- `OutboxStore` is a module for controlled tests. It is not wired into the AGT-01 startup service, and it collects or uploads nothing.

## Acceptance matrix

| Case | Expected result | Status |
|---|---|---|
| AGT-DB-01 | SQLx migration applies; WAL, FULL, foreign keys; patched linked SQLite version | Synthetic Rust tests; manual OS validation open |
| AGT-DB-02 | Event, device sequence and cursor commit together | Synthetic Rust tests; manual sudden-power-loss validation open |
| AGT-DB-03 | Forced cursor failure rolls back event and sequence | Synthetic Rust test |
| AGT-DB-04 | Restart retains immutable UUID, content digest, sequence and unacknowledged event | Synthetic Rust test |
| AGT-DB-05 | Noncontiguous ACK removes only acknowledged sequence | Synthetic Rust test; authenticated server ACK pending |
| AGT-DB-06 | Duplicate event UUID rejected without advancing cursor | Synthetic Rust test |
| AGT-DB-07 | Queue full records bounded synthetic gap, leaves cursor and sequence unchanged | Synthetic Rust test; real disk-full not run |
| AGT-DB-08 | Inventory revision advances independently, stale ACK cannot wipe fresh snapshot | Synthetic Rust test |
| AGT-DB-08A | Content-normalized IPv4/IPv6 inventory is staged once; same content across timestamp/restart/ACK consumes no extra revision | Synthetic file-backed Rust tests; real endpoint pending |
| AGT-DB-08B | Device/snapshot/revision-bound ACK, old ACK cannot delete new pending; identity change denied | Synthetic file-backed Rust tests; real server ACK pending |
| AGT-DB-08C | Injected SQL abort rolls back pending, semantic digest and SQLite counter together | Synthetic file-backed Rust tests; sudden power loss pending |
| AGT-DB-08D | Stored JSON, wire digest, semantic digest, canonical fields and device binding revalidated before eligible delivery and ACK; tamper blocks deletion | Synthetic file-backed Rust tests; OS adversarial ACLs pending |
| AGT-DB-09 | Forced power-off, disk full/read-only/corrupt DB, live checkpoints and SQLite-aware backup/restore | NOT TESTED |
| AGT-DB-10 | Pinned `Cargo.lock`, Windows and Linux compile, unit and Clippy | CI evidence linked in backlog |
| AGT-DB-11 | Exact event-envelope digest, device credentials, retry/backoff, server dedup and TLS | NOT IMPLEMENTED; AGT-03/SRV-03 |

## Manual commands (on controlled test machine)

```sh
cargo test --workspace --locked
cargo clippy --workspace --all-targets --locked -- -D warnings
cargo tree --locked -i libsqlite3-sys
```

For real outage/durability acceptance, use a disposable test VM and a synthetic-only DB in a locally mounted, tightly permissioned directory. Record OS/fs, SQLite runtime `sqlite_version()`, SQLx/libsqlite3-sys versions, WAL/synchronous settings, tested binary hash and sanitized test outcomes. Do not copy an active SQLite main file as a backup without its WAL/shm or the SQLite backup API. Do not simulate a real power failure by merely dropping an in-memory pool. Keep this gate open until OS-specific restart, quota/disk pressure, integrity check and checkpoint behavior have evidence.
