# AGT-01 Rust Agent Windows/Linux lifecycle acceptance

**Status: NOT EXECUTED on real Windows or Pop!_OS/KWin.** This procedure covers only the **idle** AGT-01 service. Do not enable real telemetry, enrollment, screen capture, privileged audit or a monitoring deployment. Unit test compilation (including Windows runner) does not replace Windows SCM and systemd hands-on evidence.

## Shared safety and evidence

Use a dedicated synthetic test machine or disposable VM with permission to install a service. Record the tested Git SHA, Rust version, binary architecture, Windows build or Linux distro/systemd version, service account, configuration file ACLs, start/stop outcomes and scrubbed unit/service statuses. No passwords, private keys, real employee activity, local IP/MAC inventory or sensitive paths in public logs. Review the root [D0 gate](../governance/documentation-gate.md) and [runtime choices](../development/rust-agent-stack.md).

| Case | Procedure | Expected result |
|---|---|---|
| AGT-LIFE-01 | Build using `cargo build --release --locked -p lanveriq-agent`; test `--help` | Explicit `--config`, no default collection or network calls |
| AGT-LIFE-02 | Pass missing file, relative path, unsupported schema, extra TOML key or oversized file | Start fails closed with no OS service Running state |
| AGT-LIFE-03 | Run with the local synthetic example then Ctrl+C | Idle process starts and exits cleanly |
| AGT-LIFE-04 | Inspect network connections, created files and source classification | No heartbeat, telemetry, credential, SQLite store or upload activity in AGT-01 |
| AGT-LIFE-05 | Restart repeatedly and interrupt startup/stop | No lingering child processes, unbounded memory growth or false healthy telemetry |

## Linux: selected Pop!_OS 24.04 / KDE KWin x86_64

Use a deliberately installed **reviewed** binary at `/usr/local/bin/lanveriq-agent` and a root-managed `/etc/lanveriq-agent/agent.toml` containing only the minimal synthetic example. Review `apps/agent/deploy/systemd/lanveriq-agent.service` before copying it into systemd. The unit intentionally uses `DynamicUser=yes`, `StateDirectory=lanveriq-agent` and a read-only system view.

Verify: `systemd-analyze verify`, manual service start/status, `systemctl stop` sending SIGTERM, repeat start, missing-config startup and `journalctl -u lanveriq-agent` for synthetic status only. Check the transient service identity, write boundary, private temp, absence of outbound network calls and that a failed config cannot be treated as healthy. Record actual OS/kernel and **interactive** X11/Wayland session only if directly observed; do not infer from KWin. The Agent service has no screen or desktop-session dependency.

## Windows 11 x64: SCM service lifecycle

Build the `lanveriq-agent.exe` binary with the pinned toolchain and install a test-only SCM service **after reviewing** the absolute binary path, the `--service` flag, the `--config` absolute path, file permissions and a least-privilege service account. Use Windows' own SCM installation/management tools, not custom SCM bindings. Check StartPending → Running → Stop → Stopped, missing-file service-specific failure, restart and no logged secrets. A normal `--config` foreground invocation is not SCM integration proof. Review Windows Event Log statuses for launch/stop failure; record sanitized evidence. Uninstall the test service afterwards.

## Exit gate

AGT-01 may be marked fully accepted only after **both OS manual smoke checks pass** and the source compiles with tests for the supported Windows/Linux targets. This gate remains open while either real OS result is missing. AGT-02 SQLx WAL/persistence, AGT-03 TLS identity and inventory permission tests have separate acceptance gates.
