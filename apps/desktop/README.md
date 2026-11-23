# Lanveriq Desktop — Tauri 2 read-only offline preview

**DEVELOPER SOURCE ONLY · NOT AN INSTALLER · v0.1 NOT RELEASE READY**

This is the first implementation of the planned Pop!_OS 24.04 GUI install-and-config companion. The source uses Tauri 2 / Rust 1.100, React 19 / Vite 8 / Tailwind 4 and the Lanveriq light design. Tauri is separate from apps/web (the sole Next.js fleet-management console) and from apps/agent (the independent systemd Agent).

Currently implemented:
- Four-stage Chinese welcome → local status → privacy notice → **disabled** install UX, plus local status/security views.
- One **read-only** Rust IPC command (inspect_host) accessing public /etc/os-release, exact dpkg-query/systemctl show properties, and metadata-only existence of the historical /usr/local/bin/lanveriq-agent path (never reading/executing it). React warns about legacy files, active/enabled services, unreviewed drop-ins and stale systemd cache. Browser frontend fallback is explicitly marked unverified; no fake device data.
- No native install/start/enable commands, no root, shell, polkit, package updater, network plugin, tray, desktop autostart or signed installer. Closing the GUI has no influence on the independent Agent service. **Never enable Agent from this preview.**
- Source mutation negatives, pure Rust unit tests and GitHub CI on disposable Ubuntu 24.04; none of this is physical Pop!_OS L3.

## Planned native .deb audit milestone (not an end-user installer)

A separate, **milestone-only** Ubuntu 24.04 workflow now attempts to use the official Tauri CLI to build an **unsigned, GUI-only** native .deb inside disposable CI. The committed default bundle remains OFF. A strict package inspector at tools/verify-desktop-deb.py and 19 hostile synthetic archive tests reject package hooks, bundled Agent/service/configuration, session autostart, hidden/shell launchers, unknown payload and malformed metadata. The source candidate is **CI PENDING**; even if validated, this is not signed, distributed, installed or approved for end users. [L2A documentation](../../docs/development/desktop-tauri-offline-deb-candidate-2026-11-23.md).

## Developing this source (maintainers only)

On a Linux development environment with Node 24.22.3, pnpm 12, Rust 1.100 and the Tauri v2 WebKitGTK 4.1 Linux build prerequisites installed, run the following from apps/desktop:

    pnpm install --frozen-lockfile
    pnpm test
    pnpm build
    pnpm tauri dev

Both real generated pnpm-lock.yaml and Cargo.lock have now been committed and the workflow uses frozen/locked dependency installation. This remains a NON-RELEASE preview until native compile checks, release signatures, privilege mediation, and actual Pop!_OS L3 have separately passed. Browser-only local preview runs pnpm dev but **cannot inspect the host**; do not treat browser fallback as actual installation.

End users **must not** be asked to run these commands. Their future approved product receives a signed, double-clickable installation experience. There is currently **no end-user installer**. Do not run a real install on personal or production devices.

[Source/CI milestone](../../docs/development/desktop-tauri-first-source-2026-11-23.md) · [ADR-0013](../../docs/architecture/adrs/0013-tauri-desktop-installer-idle-agent.md) · [L3 plan](../../docs/testing/linux-desktop-installer-acceptance.md).
