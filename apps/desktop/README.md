# Lanveriq Desktop — Tauri 2 read-only offline preview

**DEVELOPER SOURCE ONLY · NOT AN INSTALLER · v0.1 NOT RELEASE READY**

This is the first implementation of the planned Pop!_OS 24.04 GUI install-and-config companion. The source uses Tauri 2 / Rust 1.100, React 19 / Vite 8 / Tailwind 4 and the Lanveriq light design. Tauri is separate from apps/web (the sole Next.js fleet-management console) and from apps/agent (the independent systemd Agent).

Currently implemented:
- Four-stage Chinese welcome → local status → privacy notice → **disabled** install UX, plus local status/security views.
- Exactly two **read-only** Rust IPC commands: `inspect_host` checks `/etc/os-release`, fixed `dpkg-query`/`systemctl show` properties and historical binary existence (no execution), while `inspect_popos_preflight` runs a build-embedded, fixed `/usr/bin/python3 -I -c` metadata inspector under the current non-root user only after the operator clicks **检查兼容性（只读）**. The second command does not install, run APT updates or create evidence files. React validates an exact 10-group ABI and 3-component COSMIC contract; browser-only previews cannot claim local host verification.
- No native install/start/enable commands, no root, shell, polkit, package updater, network plugin, tray, desktop autostart or signed installer. Closing the GUI has no influence on the independent Agent service. **Never enable Agent from this preview.**
- Source mutation negatives, pure Rust unit tests and GitHub CI on disposable Ubuntu 24.04; none of this is physical Pop!_OS L3.

## Planned native .deb audit milestone (not an end-user installer)

A separate **milestone-only** Ubuntu 24.04 workflow previously built and strictly audited an **unsigned, GUI-only** native `.deb`; see [historical native L2B-10 #45303829710 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45303829710) for archived package, ABI and isolated APT-simulation evidence. The committed source still has `bundle.active=false` and no Agent payload, privileged maintainer scripts or background activation. These earlier native successes **do not** prove the newer embedded Pop!_OS GUI code compiles, and the current source has only [same-SHA desktop-ui L1 success #45314250801](https://github.com/kunlong-luo/lanveriq/actions/runs/45314250801) (23 source-guard, 22 preflight, 19 Vitest tests); Rust native test/Clippy acceptance remains pending. No signed, uploaded, installed or approved end-user installer exists. [L2A documentation](../../docs/development/desktop-tauri-offline-deb-candidate-2026-11-23.md) · [L2B-12 current contract](../../docs/development/desktop-tauri-l2b12-cosmic-readonly-gui-integration-2026-11-24.md).

## Developing this source (maintainers only)

On a Linux development environment with Node 24.22.3, pnpm 12, Rust 1.100 and the Tauri v2 WebKitGTK 4.1 Linux build prerequisites installed, run the following from apps/desktop:

    pnpm install --frozen-lockfile
    pnpm test
    pnpm build
    pnpm tauri dev

Both real generated pnpm-lock.yaml and Cargo.lock have now been committed and the workflow uses frozen/locked dependency installation. This remains a NON-RELEASE preview until native compile checks, release signatures, privilege mediation, and actual Pop!_OS L3 have separately passed. Browser-only local preview runs pnpm dev but **cannot inspect the host**; do not treat browser fallback as actual installation.

End users **must not** be asked to run these commands. Their future approved product receives a signed, double-clickable installation experience. There is currently **no end-user installer**. Do not run a real install on personal or production devices.

[Source/CI milestone](../../docs/development/desktop-tauri-first-source-2026-11-23.md) · [ADR-0013](../../docs/architecture/adrs/0013-tauri-desktop-installer-idle-agent.md) · [L3 plan](../../docs/testing/linux-desktop-installer-acceptance.md).
