# Lanveriq Desktop — Tauri 2 read-only offline preview

**DEVELOPER SOURCE ONLY · NOT AN INSTALLER · v0.1 NOT RELEASE READY**

This is the first implementation of the planned Pop!_OS 24.04 GUI install-and-config companion. The source uses Tauri 2 / Rust 1.100, React 19 / Vite 8 / Tailwind 4 and the Lanveriq light design. Tauri is separate from apps/web (the sole Next.js fleet-management console) and from apps/agent (the independent systemd Agent).

Currently implemented:
- Four-stage Chinese welcome → local status → privacy notice → **disabled** install UX, plus local status/security views.
- One **read-only** Rust IPC command (inspect_host) accessing public /etc/os-release and exact dpkg-query/systemctl show properties only. Browser frontend fallback is explicitly marked unverified; no fake device data.
- No native install/start/enable commands, no root, shell, polkit, package updater, network plugin, tray, desktop autostart or signed installer. Closing the GUI has no influence on the independent Agent service. **Never enable Agent from this preview.**
- Source mutation negatives, pure Rust unit tests and GitHub CI on disposable Ubuntu 24.04; none of this is physical Pop!_OS L3.

## Developing this source (maintainers only)

On a Linux development environment with Node 24.22.3, pnpm 12, Rust 1.100 and the Tauri v2 WebKitGTK 4.1 Linux build prerequisites installed, run the following from apps/desktop:

    pnpm install --frozen-lockfile
    pnpm test
    pnpm build
    pnpm tauri dev

The first source submission bootstraps real lockfiles in CI before switching to frozen/locked installation. Until both generated Cargo.lock and pnpm-lock.yaml are committed and verified, this is NOT a reproducible release pipeline. Browser-only local preview runs pnpm dev but **cannot inspect the host**; do not treat browser fallback as actual installation.

End users **must not** be asked to run these commands. Their future approved product receives a signed, double-clickable installation experience. There is currently **no end-user installer**. Do not run a real install on personal or production devices.

[Source/CI milestone](../../docs/development/desktop-tauri-first-source-2026-11-23.md) · [ADR-0013](../../docs/architecture/adrs/0013-tauri-desktop-installer-idle-agent.md) · [L3 plan](../../docs/testing/linux-desktop-installer-acceptance.md).
