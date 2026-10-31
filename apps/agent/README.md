# Lanveriq Agent — AGT-01 lifecycle, AGT-02 SQLite and staged AGT-03 transport

**D0-approved V0.1 coding slice, not a deployed or usable endpoint monitor.** The running Agent binary remains an idle loop and performs **no inventory collection, network requests, enrollment, heartbeat, audit collection, input monitoring or screen recording**. AGT-02 SQLx persistence, staged AGT-03 HTTPS/mTLS transport, and new INV-01 local OS adapter are **standalone library components only**; none is invoked by the service binary. The adapter is callable explicitly in an isolated test only; no real workplace data is authorized.

## Toolchain and dependencies

From the repository root, install the pinned Rust 1.94.0 toolchain from rustup and run:

```sh
cargo test --workspace --locked
cargo fmt --all -- --check
cargo clippy --workspace --all-targets --locked -- -D warnings
cargo build --release --locked -p lanveriq-agent
```

The committed root `Cargo.lock` pins transitive dependencies. Tokio supplies the async runtime/signal handling; Serde + TOML enforce the versioned schema; Clap parses the command line; tracing supplies diagnostics; Windows SCM events use `windows-service` 0.8.1. No custom async worker, service manager, TLS implementation or collector is introduced. AGT-02 adds SQLx 0.9.0, `uuid`, `serde_json`, `sha2` and a pinned bundled `libsqlite3-sys =0.37.0` with the WAL-reset fix.

## INV-01 on-demand local inventory (NOT WIRED TO SERVICE)

`inventory.rs` implements the typed, bounded V0.1 complete snapshot (CPU/RAM, OS, multi-NIC/IP and volumes) with **stable externally assigned** device UUID, snapshot ID and monotonic revision; it merges multiple address records per NIC, checks numeric IPv4/IPv6 prefixes and preserves unavailable fields as null. This module never probes the host, persists a revision or uploads data.

`inventory_platform.rs` adds an **explicitly invoked** local Windows/Linux source adapter using maintained and locked **`sysinfo =0.37.1`**, **`os_info =3.15.0`** and **`network-interface =2.0.5`**. Sysinfo is built only with `system` and `disk` features, omitting user/process/component/network enumeration; the adapter queries CPU/RAM, OS version, non-loopback NIC IPs/MAC and local mounted disks only. There is no automatic 30-second collection, service scheduler, network upload, serial/TPM, Wi-Fi location, username, process list, activity or screen access. An unknown metric remains null/unavailable; no fake zero or trust bypass. **A successful build is not evidence of genuine Windows 11 or Pop!_OS asset accuracy.** [INV-01 manual acceptance](../../docs/testing/agent-inventory-manual.md).

The repo `Cargo.lock` was updated by Cargo itself in a **one-time repository-scoped maintenance workflow**. That temporary write-enabled workflow has been removed; normal Linux/Windows cargo tests/clippy still run with `--locked`. New dependency versions must be reviewed when changed.

## INV-01-C durable normalized SQLite inventory (NO runtime upload)

SQLx [migration V3](migrations/20261031000300_inventory_snapshot_state.sql) upgrades the single pending inventory payload to the approved **128 KiB** envelope and adds `inventory_latest_state` (device-bound last semantic digest, UUID and revision). `OutboxStore::stage_inventory_snapshot(device_id, observed_at, raw)` accepts only the normalized typed whitelist from `inventory.rs`, computes SHA-256 of normalized **asset content excluding volatile timestamp/snapshot UUID/revision**, and atomically stages a complete JSON wire payload with a durable SQLite-generated revision and once-generated UUIDv7.

The result is `Staged`, `AlreadyPending` (exact same persisted UUID/JSON for retries/restarts), or `Unchanged` (already acknowledged; no revision increment). Changed data replaces **only the latest pending inventory** in one transaction, retaining monotonic revision independently from event `device_sequence`; a failed update rolls back the body, digest and revision together. `acknowledge_inventory_snapshot(device_id,snapshot_id,revision)` is **not wired to networking** and may only be invoked on an authenticated positive server receipt; stale/other-device ACKs cannot clear current pending data. The last semantic digest survives a successful ACK and subsequent Agent restart.

Device identity is pinned to the first enrolled UUID in the local inventory state. Changing identity requires a separately approved local identity reset/re-enrollment; a synthetic/legacy pending row cannot silently overwrite a typed inventory. SQLx remains the only database access layer, and the service binary never opens this outbox. Synthetic file-backed tests cover unchanged-after-ACK/restart, reordered NICs, stale ACK, identity mismatch, failure-triggered rollback, and legacy-state rejection. **CI proves local SQLx behavior only**: genuine power-loss, SQLite ACL/key custody, and PostgreSQL receipt semantics are still open. [AGT-02 manual acceptance](../../docs/testing/agent-sqlite-manual.md).

## INV-01-D integrity-gated inventory delivery (NOT ACTIVE)

The SQLx store now has a **strict** `verified_pending_inventory(device_id)` method for transport. Before returning wire bytes, it re-reads the on-disk SHA-256, checks exact device/snapshot/revision against `inventory_latest_state`, re-deserializes the `#[serde(deny_unknown_fields)]` V0.1 snapshot and re-normalizes the approved asset whitelist. It checks the independent semantic digest as well. An altered JSON row (even with its wire SHA-256 recomputed) fails with `InventoryConflict`, and an ACK will **not** delete a corrupted row. SHA-256 is a consistency check, **not a MAC against an attacker with SQLite write access**; restrict file custody separately.

`AgentTlsTransport::send_next_inventory` is an **explicit, one-attempt, opt-in library method**: it uses the verified on-disk JSON bytes unchanged (no JSON double encoding), a fixed device-scoped Agent HTTPS path and the existing pinned Rustls identity/CA. It accepts only the exact server V0.1 HTTP 200 receipt with matching `schema_version=1`, `device_id`, `snapshot_id`, `inventory_revision`, `server_time` and outcome `applied`/`duplicate`. `stale_ignored` is quarantined (no deletion); wrong/extra/missing fields, timeouts and invalid receipts keep the local snapshot. On valid 200 only, deletion re-checks the entire inventory inside an atomic SQLx transaction and matches the same tuple, so an in-flight old ACK cannot clear a newer snapshot. HTTP 401/403 pause; 429/5xx return bounded retry guidance. **No scheduler, retry loop, live HTTP test, production credentials or Agent runtime invocation exists.** The Spring inventory API/controller is not open, so a real upload would not be accepted. [Inventory acceptance](../../docs/testing/agent-inventory-manual.md) and [transport acceptance](../../docs/testing/agent-transport-manual.md) remain manual-gated.

## AGT-03 source: strict mTLS and batch ACK (NOT NETWORK TESTED)

`transport.rs` uses maintained `reqwest =0.13.5` with Rustls to require the exact `https://agent.lanveriq.internal` origin, a dedicated trust-only CA and a PEM client identity. HTTPS-only, no redirects/proxies, bounded timeouts and response sizes; no TLS verification bypass. `Heartbeat` is typed but synthetic and declares unavailable source capabilities as `not_collected`. Event batches are built from the AGT-02 SQLite synthetic fixtures, never arbitrary OS activity. `upload_batches` SQLx migration persists a stable batch UUID and its exact event selection across timeouts and restarts, even when new events arrive. Only a complete, validated 200 ACK deletes explicitly confirmed sequences; 401/403 pause, 409 quarantines and 429/5xx return retry instructions without any immediate retry loop.

**Not implemented:** storing client keys in approved OS service custody, server-side mTLS identity/fingerprint binding, SRV-03 ingestion, authorized device activation, endpoint network requests from the Agent binary, real heartbeats, collection, retry scheduler and real TLS/OS integration tests. [AGT-03 manual acceptance](../../docs/testing/agent-transport-manual.md). No production deployment or real employee-data collection.

## Local console-only smoke

Create an independently reviewed, strictly synthetic file at an **absolute path**. The example is `apps/agent/config/agent.example.toml`. It contains no secrets and enables no data collection. For a short foreground check:

```sh
cargo run --locked -p lanveriq-agent -- --config /absolute/path/to/agent.toml
```

The process starts an idle loop; send Ctrl+C to stop. On Linux, it also handles the SIGTERM sent by systemd. Missing/relative paths, unknown fields, unsupported schema versions, excessive file size and disallowed intervals are rejected. These are not proof of actual OS service behavior.

## OS service integration (needs real-device acceptance)

- Linux: `deploy/systemd/lanveriq-agent.service` is an **experimental** systemd unit with `DynamicUser`, `NoNewPrivileges`, private temp, restricted kernel controls and no access to home directories. It reads a root-managed, non-secret `/etc/lanveriq-agent/agent.toml`. The binary needs to be deliberately installed at `/usr/local/bin/lanveriq-agent`. Do not enable on real managed devices without acceptance.
- Windows: `--service --config <absolute-config-path>` enters an SCM dispatcher using the maintained `windows-service` crate. SCM Stop should cancel the idle loop and report a final Stopped state; bad configuration reports a service-specific failure without entering Running. SCM installation/account ACLs and service control need validation on **real Windows 11 x64**. A foreground console does not simulate Windows SCM.
- [Linux/Windows Cargo unit tests and Clippy passed](https://github.com/kunlong-luo/lanveriq/actions/runs/41146480955), but Windows/Linux compile and unit-test evidence is **not** proof of Linux systemd activation, Windows service installation, private state-directory ACLs, OS capabilities or any actual device telemetry.

## AGT-02 local SQLite WAL — synthetic-only

The optional `OutboxStore` module uses **SQLx 0.9**, embedded migrations, one write connection, `WAL`/`synchronous=FULL`/`foreign_keys=ON` and a **runtime** linked SQLite patch-version check. A local transaction persists synthetic event + UUIDv7 + monotonic sequence + source cursor atomically, and a verified ACK API deletes only explicitly named sequences. INV-01-C V3 additionally records the bound device and normalized content digest, so unchanged inventory does not churn revision even across restarts/ACKs; only a matching device+snapshot+revision can acknowledge a typed pending payload. Quota overflow reports a bounded synthetic gap; it never advances a failed event's cursor. The sqlite version must be tested on each OS/build; no environment-provided system SQLite is assumed.

Run `cargo test --workspace --locked` for real file-backed SQLite unit tests. These tests do **not** prove sudden power loss, disk-full/corrupt DB behavior, backup/restore, OS filesystem ACLs or real network dedup. [Manual AGT-02 acceptance](../../docs/testing/agent-sqlite-manual.md) lists remaining gates. Do not add private keys, plaintext enrollment tokens or employee data to test fixtures.

Follow [AGT-01 manual lifecycle checks](../../docs/testing/agent-lifecycle-manual.md). No real employee data is authorized. AGT-02 now contains the isolated SQLx SQLite synthetic persistence module; the Agent identity/TLS, actual collection, authenticated uploads and installed-service storage remain **not implemented/tested**.
