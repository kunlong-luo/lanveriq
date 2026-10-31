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

`inventory_platform.rs` adds an **explicitly invoked** local Windows/Linux source adapter using maintained and locked **`sysinfo =0.37.1`**, **`os_info =3.15.0`** and **`network-interface =2.0.5`**. Sysinfo is built only with `system` and `disk` features, omitting user/process/component/network enumeration; the adapter queries CPU/RAM, OS version, non-loopback NIC IPs/MAC and local mounted disks only. There is no automatic 30-second collection, service scheduler, SQLx integration, network upload, serial/TPM, Wi-Fi location, username, process list, activity or screen access. An unknown metric remains null/unavailable; no fake zero or trust bypass. **A successful build is not evidence of genuine Windows 11 or Pop!_OS asset accuracy.** [INV-01 manual acceptance](../../docs/testing/agent-inventory-manual.md).

The repo `Cargo.lock` was updated by Cargo itself in a **one-time repository-scoped maintenance workflow**. That temporary write-enabled workflow has been removed; normal Linux/Windows cargo tests/clippy still run with `--locked`. New dependency versions must be reviewed when changed.

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

The optional `OutboxStore` module uses **SQLx 0.9**, embedded migrations, one write connection, `WAL`/`synchronous=FULL`/`foreign_keys=ON` and a **runtime** linked SQLite patch-version check. A local transaction persists synthetic event + UUIDv7 + monotonic sequence + source cursor atomically, and a verified ACK API deletes only explicitly named sequences. Separate persistent inventory revisions prevent old ACKs from clearing new snapshots. Quota overflow reports a bounded synthetic gap; it never advances a failed event's cursor. The sqlite version must be tested on each OS/build; no environment-provided system SQLite is assumed.

Run `cargo test --workspace --locked` for real file-backed SQLite unit tests. These tests do **not** prove sudden power loss, disk-full/corrupt DB behavior, backup/restore, OS filesystem ACLs or real network dedup. [Manual AGT-02 acceptance](../../docs/testing/agent-sqlite-manual.md) lists remaining gates. Do not add private keys, plaintext enrollment tokens or employee data to test fixtures.

Follow [AGT-01 manual lifecycle checks](../../docs/testing/agent-lifecycle-manual.md). No real employee data is authorized. AGT-02 now contains the isolated SQLx SQLite synthetic persistence module; the Agent identity/TLS, actual collection, authenticated uploads and installed-service storage remain **not implemented/tested**.
