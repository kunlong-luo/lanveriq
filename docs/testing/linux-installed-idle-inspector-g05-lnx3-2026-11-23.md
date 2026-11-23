# G-05-LNX3 — Installed Agent idle-state read-only audit (Pop!_OS acceptance aid)

**2026-11-23 · ACTIVE SOURCE / L1 synthetic-first · physical Pop!_OS 24.04 L3 NOT RUN · NO INSTALLATION APPROVAL · v0.1 NOT RELEASE READY**

## Why this exists

G-05-LNX1 checks the committed systemd source; LNX2 builds and validates a native Linux .deb entirely within disposable Ubuntu 24.04 CI. Neither demonstrates that a **real installed unit** on an authorized Pop!_OS host is **disabled, inactive and still blocked from network access after drop-ins and service-manager caching**. The [systemctl reference](https://www.freedesktop.org/software/systemd/man/latest/systemctl.html) distinguishes on-disk unit text and the manager's loaded/effective properties. This checker deliberately rejects that ambiguity instead of assuming the source template equals the installed service.

## Implementation — safe to run without privileges

[verify-installed-agent-idle.py](../../apps/agent/tools/linux/verify-installed-agent-idle.py) is **read only**: exact commands restricted to dpkg-query -W, dpkg-query -S and systemctl show; also reads *only* the public binary's ELF magic and committed systemd unit bytes. It does NOT inspect any user profile, login, private state, PKI key, /etc/lanveriq-agent/agent.toml or OS inventory. No agent execution, systemctl enable/start/stop/reload, dpkg install/remove, network, remote control, collection or state mutation. A host without an installed candidate must print **NOT_APPROVED** with a nonzero exit code.

The inspector only accepts:
- A real installed **lanveriq-agent-offline-preview 0.1.0~dev1**, uniquely owning /usr/bin/lanveriq-agent and /usr/lib/systemd/system/lanveriq-agent.service.
- Both package files regular, root-owned, with exact 0755 executable/0644 unit permissions, binary ELF header and byte-for-byte equality to the same checked-out source unit.
- systemctl show --all with exactly parsed expected properties: Id, LoadState=loaded, ActiveState=inactive, UnitFileState=disabled, NeedDaemonReload=no and canonical FragmentPath.
- **Zero DropInPaths**, no effective IPv4/IPv6 allowance, PrivateNetwork=yes, IPAddressDeny=any, SocketBindDeny=any, RestrictAddressFamilies=AF_UNIX, DynamicUser=yes, StateDirectory=lanveriq-agent, StateDirectoryMode=0700, no capabilities, and key confinement settings.
- A single canonical systemctl ExecStart record with **exact** path and --config argument; shell wrappers/unknown args fail. A service that has been started, is enabled, has failed, has unreviewed overrides, has stale manager state or has nonstandard systemd property serialization is **NOT_APPROVED**, never silently normalized.

**Important limitations:** The comparison is strict and may produce a false negative across different installed systemd versions/property formats; capture that failure and independently inspect it rather than relaxing security checks on speculation. It does not prove kernel sandbox enforcement, key custody, organizational approval, OS identity, actual Pop!_OS COSMIC session security, live service restart behavior, package signature or legal consent. It is also not tamper-proof against an already root-compromised host. A tool PASS means **only** its narrow observed read-only checks pass; it does not approve enabling the service, installing the preview on a personal workstation or closing G-05.

## Running the checker

On a machine with a clean checkout, the following is **non-mutating** and expected to return NOT_APPROVED unless the exact preview package was installed in a separately authorized, disposable lab environment:

    cd lanveriq
    python3 apps/agent/tools/linux/verify-installed-agent-idle.py

For a positive check in an **already authorized isolated laboratory**, inspect the package, service and actual run approval separately. This document **does not grant installation/start authorization**. No --unit or --snapshot arguments exist: CI synthetic fixtures cannot force a physical PASS.

## Negative tests and CI

[test_verify_installed_agent_idle.py](../../apps/agent/tools/linux/test_verify_installed_agent_idle.py) tests the **pure parser** with synthetic systemctl property messages and corrupted state, without calling systemctl, apt, dpkg installation or touching the host. [Ubuntu24 synthetic custody workflow](../../.github/workflows/agent-linux-popos24-custody.yml) runs these negatives plus a read-only check that a disposable runner without a Lanveriq package cannot announce a physical PASS; it does not create an installed system service. Existing LNX1 and LNX2 tests remain active.

The exact functional SHA, real workflow logs and PASS/FAIL must be written only after CI completes. Unrun physical acceptance cannot be upgraded based on this script. [Issue #7](https://github.com/kunlong-luo/lanveriq/issues/7) remains open for formal Pop!_OS 24.04 actual dpkg/service/boot/COSMIC/key-custody and consent evidence. [Issue #6](https://github.com/kunlong-luo/lanveriq/issues/6) and G-02 WORM are separate. **G-05/G-02/G-08 OPEN, v0.1 NOT RELEASE READY; all production networking, collection, auto-recovery, remote control and signing OFF.**

## 2026-11-23 exact functional source SHA CI evidence

**Functional [a4787fab4fad22aa37a63eba0ecafff2e77999d7](https://github.com/kunlong-luo/lanveriq/commit/a4787fab4fad22aa37a63eba0ecafff2e77999d7) — all 5/5 actually triggered GitHub workflow runs SUCCESS.**

- [Ubuntu24 Linux custody/package #45132153568](https://github.com/kunlong-luo/lanveriq/actions/runs/45132153568): actual logs Ran 16 systemd source-policy tests, Ran 16 native .deb packaging mutation tests, Ran **14 new installed-effective-property negative tests**. Clean uninstalled disposable Ubuntu24 host correctly returned NOT_APPROVED. Actual locked Rust release compilation, native offline .deb staging/extraction, Linux CSR separate-UID permission denial and transient systemd DynamicUser two-invocation custody checks all PASS.
- [Rust fmt/full Cargo unit/Clippy #45132153583](https://github.com/kunlong-luo/lanveriq/actions/runs/45132153583) SUCCESS; [Next.js fast #45132153679](https://github.com/kunlong-luo/lanveriq/actions/runs/45132153679) SUCCESS; [documentation authority #45132153608](https://github.com/kunlong-luo/lanveriq/actions/runs/45132153608) SUCCESS; [RD-2 fail-closed #45132153592](https://github.com/kunlong-luo/lanveriq/actions/runs/45132153592) SUCCESS.

**Precise non-proof:** No genuine installed preview package was checked on Pop!_OS24.04 or any other physical test workstation; the 14 property checks used only synthetic systemctl fixtures. In CI a missing package correctly failed; there was no successful real installed-systemd check. No host private keys, employee devices or data, CA or independent WORM. Package signature/custody and supply chain authentication NOT VERIFIED, full dpkg install/removal and systemd drop-in effective-state L3 NOT RUN. No G-05 closure and no approval to install the unsigned candidate on personal/production hardware. **G-02/G-05/G-08 OPEN, v0.1 NOT RELEASE READY.**
