# Lanveriq Agent — AGT-01 lifecycle foundation

**D0-approved V0.1 coding slice, not a deployed or usable endpoint monitor.** This code currently performs **no network requests, enrollment, heartbeat, database writes, inventory collection, audit collection, input monitoring or screen recording**. It merely loads explicit local configuration and runs a cancellable idle service loop.

## Toolchain and dependencies

From the repository root, install the pinned Rust 1.94.0 toolchain from rustup and run:

```sh
cargo test --workspace --locked
cargo fmt --all -- --check
cargo clippy --workspace --all-targets --locked -- -D warnings
cargo build --release --locked -p lanveriq-agent
```

The committed root `Cargo.lock` pins transitive dependencies. Tokio supplies the async runtime/signal handling; Serde + TOML enforce the versioned schema; Clap parses the command line; tracing supplies diagnostics; Windows SCM events use `windows-service` 0.8.1. No custom async worker, service manager, TLS implementation or collector is introduced.

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

Follow [AGT-01 manual lifecycle checks](../../docs/testing/agent-lifecycle-manual.md). No real employee data is authorized. AGT-02 will add the selected **SQLx 0.9 / SQLite WAL** outbox with a single writer; that database, SQLite WAL patch verification, and Agent identity/TLS are **not implemented** by AGT-01.
