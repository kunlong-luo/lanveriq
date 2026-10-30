# Rust Agent — ecosystem-first, maintainable library baseline

**Status: design selection 2026-10-30, not compiled or verified on Windows/Pop!_OS.** This replaces the original `rusqlite`-plus-handwritten-blocking-worker implementation recommendation with **SQLx 0.9** for async SQLite. It does **not** change SQLite/WAL, transaction/inventory/ACK semantics, device identity or the privacy scope. See [ADR-0007](../architecture/adrs/0007-mature-ecosystem-first.md).

## Reuse tested crates instead of inventing infrastructure

| Area | Choice | Stage | Avoid reinventing |
|---|---|---|---|
| Runtime, bounded concurrency | `tokio` 1.x, `tokio::sync` | V0.1 | async scheduler, cancel, queues, timers |
| REST + client mTLS | `reqwest` 0.13.x with `rustls` | V0.1 | custom HTTP/TLS, retry transport parsing |
| Wire format | `serde`, `serde_json` | V0.1 | custom serialization |
| Embedded DB / migrations | **`sqlx` 0.9.x SQLite**, `migrate!`, single-connection pool | V0.1 | manual SQLite FFI, custom DB async worker, custom migration engine |
| IDs, time | `uuid` 1.x, `time` or `chrono` (pick one with SQLx compatibility) | V0.1 | custom UUID, clock parsing |
| Device inventory | `sysinfo`, `os_info`, `network-interface` where supported | V0.1 | handcrafted CPU/RAM/NIC OS parsers for common fields |
| Diagnostics | `tracing`, `tracing-subscriber`; Linux `tracing-journald` if justified | V0.1 | custom logging format/tracing engine |
| Errors/config | `thiserror`, `serde` + `toml` / vetted config loader | V0.1 | stringly typed error and bespoke config parser |
| Sensitive memory | `secrecy` or `zeroize` as required | V0.1 | custom secret-memory cleanup |
| Windows service | `windows-service` 0.8.x | V0.1 | custom Windows SCM bindings |
| Linux daemon | installed systemd unit + optional `sd-notify` 0.5.x | V0.1 | daemonize/fork/watchdog framework |
| Tauri user companion | **Tauri 2 official plugins** for store/autostart/updater/Stronghold as needed | later | handmade desktop auth/preferences/updater |
| OS-authorized recording | approved OS session capture interfaces and PipeWire/portal integration via maintained components | V0.3 | custom Wayland permission bypass or screen grabber |
| Video codec | maintained FFmpeg integration at V0.3, audited licensing | V0.3 | custom H.264 encoder/container/segmenter |

**Only install what a implemented feature needs.** One stable crate version and its transitive dependencies are pinned in `Cargo.lock` for the final Agent binary. Avoid adding `reqwest` and another HTTP client, multiple logging frameworks, or parallel ORMs.

## SQLx SQLite selection and safety constraints

**Why SQLx rather than rusqlite or SeaORM here?** SQLx provides Tokio-compatible SQLite pools, transactions and versioned migrations without manually wrapping a synchronous driver in a blocking worker. SeaORM adds entity/ActiveModel abstraction that has limited value for a small *append-only sequence/outbox state machine* and can obscure exact transactional guarantees; `rusqlite` remains a reputable fallback if SQLx compatibility/durability testing fails. This is not "never write SQL": SQLx query macros and parameter binding are **mature abstraction over SQL drivers**; the required domain transitions still need exact, tested transactions.

- Use `sqlx = { version = "0.9", default-features = false, features = ["runtime-tokio", "sqlite", "migrate", "macros", "uuid"] }` as a **documented reference**, not yet an installed dependency. Use `sqlx::migrate!` / SQLx migrations, not a custom migration runner.
- Use **one database connection in the write pool** (`SqlitePoolOptions::max_connections(1)`) and one logical persistence boundary; other producers submit bounded commands, never mutate `next_sequence` directly. Preserve atomic `event + cursor + next_sequence`, inventory revision CAS, per-sequence ACK deletion. Short transactions only.
- Enable `journal_mode=WAL`, `foreign_keys=ON`, `busy_timeout`, bounded disk quotas, scheduled checkpoints and SQLite-aware backups. For audit durability keep `synchronous=FULL` initially and benchmark actual failure modes.
- SQLx uses bundled SQLite via `libsqlite3-sys` when its `sqlite` feature is enabled. **Do not infer the bundled SQLite version from SQLx's version**; verify `SELECT sqlite_version()` at startup and in tests. As of 2026-10-30, SQLite **3.54.1** is the latest official stable release; at minimum ship a version with the WAL-reset fix (3.51.3+ or officially fixed backport), prefer newest verified patched release. Resolve `libsqlite3-sys` carefully; `cargo update` and inspect dependency tree and binary runtime value.
- For compile-time checked SQL, use `query!`/`query_as!` with a controlled development SQLite schema or committed SQLx offline metadata; normalize migration SQL to LF for reproducible checks. No fake success from tests using an unrelated in-memory schema.
- Validate crash recovery, power loss, full disk, duplicate batches, older inventory revision, cross-platform DB location and ACLs before claiming durability.
- SQLx pool does **not** replace event/ack business protocol, state-machine tests, encryption-at-rest policy or device-identity auth.

## Device identity and privileged OS separation

The Rust system service owns durable Agent state, approved hardware/health collection and HTTPS; it should not hold a hidden unrestricted desktop screenshot capability. Windows runs as an SCM-managed Service using `windows-service`. Linux runs as a systemd service (use official unit sandboxing, not custom daemonization). Later **Tauri user-session** handles visible consent and OS-sanctioned capture grants. Portal-mediated Wayland capture cannot be performed by simply granting root to the system daemon.

Use `reqwest` built-in TLS identity/certificate support and **verified** LAN CA roots; no hand-built TLS stack, no disabled certificate validation. Before using Tauri Stronghold, decide whether secrets belong in the user-session companion or the privileged system service; **do not assume a desktop password store is available to a headless system service**.

## Use an existing endpoint collector only if it earns its footprint

[osquery](https://osquery.readthedocs.io/en/stable/) is a mature Windows/Linux OS inventory and event instrumentation tool; [Wazuh Syscollector](https://documentation.wazuh.com/current/user-manual/capabilities/system-inventory/index.html) is also mature. **Do not embed either by default** in V0.1: they add a second installed component/daemon, increase permissions, distributions, upgrade and notice scope, and provide much broader collection than Lanveriq's 1–10 authorized endpoints require. Reassess for approved V0.2 source allowlists; do not expose arbitrary SQL or remote command execution to administrators.

## References (verified official docs)
- [SQLx SQLite options](https://docs.rs/sqlx/latest/sqlx/sqlite/struct.SqliteConnectOptions.html) / [SQLx pool](https://docs.rs/sqlx/latest/sqlx/sqlite/type.SqlitePoolOptions.html) / [SQLx migrations](https://docs.rs/sqlx/latest/sqlx/macro.migrate.html) / [SQLx crate features](https://docs.rs/crate/sqlx/0.9.0/features).
- [SQLite release chronology](https://www.sqlite.org/chronology.html) / [WAL corruption fix](https://sqlite.org/wal.html).
- [sysinfo](https://docs.rs/sysinfo/latest/sysinfo/) / [os_info](https://docs.rs/os_info/latest/os_info/) / [network-interface](https://docs.rs/network-interface/latest/network_interface/).
- [Windows service crate](https://docs.rs/windows-service/latest/windows_service/) / [systemd notifications](https://docs.rs/sd-notify/latest/sd_notify/).
- [Reqwest TLS identity](https://docs.rs/reqwest/latest/reqwest/tls/struct.Identity.html) / [Tauri Stronghold](https://v2.tauri.app/plugin/stronghold/).
