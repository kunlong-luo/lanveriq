# Lanveriq Desktop — Tauri 2 read-only offline preview

**EXACT SOURCE GUI-only Debian L2 PASS, native Tauri preview PASS · NOT A SIGNED AGENT INSTALLER · v0.1 NOT RELEASE READY**

[完整 GUI-only .deb 审计 #45322290248](https://github.com/kunlong-luo/lanveriq/actions/runs/45322290248) 在指定源码 SHA c4f04e44 实际生成并完整审计 unsigned lanveriq-desktop_0.1.0_amd64.deb（SHA-256 83ab54d1331213335e3ec77e383c6b8c384690f37826e42413ffc35d2efe504a）：Rust/Clippy、恶意归档、局部 Syft SPDX、15 ELF SONAME、官方 dpkg-shlibdeps 缺口 0、干净 Ubuntu OCI APT 模拟及 LAB 临时 Ed25519 PASS；**没有安装、上传或分发该包**。[原生只读预览 CI #45322718592](https://github.com/kunlong-luo/lanveriq/actions/runs/45322718592) 也已实际 **overall SUCCESS**（React 和原生 Rust 两个 job 都 PASS），不等于真 Pop!_OS24/COSMIC L3。安全/发布交接见 [L2B-15](../../docs/development/desktop-tauri-l2b15-current-source-deb-l2-success-handoff-2026-11-24.md)。

This is the first implementation of the planned Pop!_OS 24.04 GUI install-and-config companion. The source uses Tauri 2 / Rust 1.100, React 19 / Vite 8 / Tailwind 4 and the Lanveriq light design. Tauri is separate from apps/web (the sole Next.js fleet-management console) and from apps/agent (the independent systemd Agent).

Currently implemented:
- Four-stage Chinese welcome → local status → privacy notice → **disabled** install UX, plus local status/security views.
- Exactly two **read-only** Rust IPC commands: `inspect_host` checks `/etc/os-release`, fixed `dpkg-query`/`systemctl show` properties and historical binary existence (no execution), while `inspect_popos_preflight` runs a build-embedded, fixed `/usr/bin/python3 -I -c` metadata inspector under the current non-root user only after the operator clicks **检查兼容性（只读）**. The second command does not install, run APT updates or create evidence files. React validates an exact 10-group ABI and 3-component COSMIC contract; browser-only previews cannot claim local host verification.
- No native install/start/enable commands, no root, shell, polkit, package updater, network plugin, tray, desktop autostart or signed installer. Closing the GUI has no influence on the independent Agent service. **Never enable Agent from this preview.**
- Source mutation negatives, pure Rust unit tests and GitHub CI on disposable Ubuntu 24.04; none of this is physical Pop!_OS L3.

## Independent Ubuntu APT archive cache security lab (not native package build)

**Native validation progress (2026-11-24):** [c4f04e44 source #45322290248](https://github.com/kunlong-luo/lanveriq/actions/runs/45322290248) passed fresh Ubuntu signed-index verification of the reviewed 175-file cache, offline installation of disposable Ubuntu build prerequisites, actual Rust workspace tests, and strict Clippy. It is compiling the real unsigned GUI-only Debian candidate; remaining archive, Syft, ELF, ABI and OCI audits have NOT passed yet. [ea62c63c](https://github.com/kunlong-luo/lanveriq/commit/ea62c63cb9523c1ac34ac64c7f7134bbe58fbe3a) aligns the independent preview native job to the same strict cache verification and no-download prerequisite installation; [#45322718592](https://github.com/kunlong-luo/lanveriq/actions/runs/45322718592) remains unverified. No end-user GUI or Agent installation is authorized.

**Accepted engineering subgate:** [two-runner #45321096944](https://github.com/kunlong-luo/lanveriq/actions/runs/45321096944) passed saved/cache restored **175 exact Ubuntu .deb archives** with fresh signed-index per-package hashes/sizes/versions/architecture, identical SHA-256 manifest and exact APT install closure. These are **public Ubuntu build dependencies only**. The [current full candidate #45322290248](https://github.com/kunlong-luo/lanveriq/actions/runs/45322290248), code c4f04e44, now restores the exact reviewed cache and independently checks signed-index metadata again before `apt-get --no-download` installs disposable CI headers. It retains real Rust test/Clippy, unsigned GUI-only archive/Syft/ELF/dpkg-shlibdeps/OCI gates; its own native/package result is **not yet accepted**. No end-user installer, Agent activation, production signing or Pop!_OS L3 is authorized.

The [L2B-14 specification](../../docs/development/desktop-tauri-l2b14-ubuntu-apt-prefetch-cache-lab-2026-11-24.md) tracks experimental read-only Ubuntu24 WebKitGTK development dependency cache prewarming. The [standalone offline GitHub workflow](../../.github/workflows/desktop-apt-cache-contract.yml) at [#45321096899](https://github.com/kunlong-luo/lanveriq/actions/runs/45321096899) passed 20/20 synthetic tests (18 hostile negatives, two valid inputs); the separate [real Ubuntu24 no-install APT simulation #45321177263](https://github.com/kunlong-luo/lanveriq/actions/runs/45321177263) confirmed its exact dependency-closure parser on 171 selected packages. The fresh APT signed-index verifier checks every restored cache file against package/version/architecture/size/SHA256 metadata in a different CI runner; actual end-to-end cache save/recovery remains unverified. This cache has no permissions to install, activate, bundle, sign, upload or distribute the Agent. GUI-native CI now triggers only for its actual code and embedded read-only inspectors; strict Rust/Clippy tests are not weakened. All release gates remain OPEN.

## Planned native .deb audit milestone (not an end-user installer)

A separate **milestone-only** Ubuntu 24.04 workflow previously built and strictly audited an **unsigned, GUI-only** native `.deb`; see [historical native L2B-10 #45303829710 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45303829710) for archived package, ABI and isolated APT-simulation evidence. The committed source still has `bundle.active=false` and no Agent payload, privileged maintainer scripts or background activation. These earlier native successes **do not** prove the newer embedded Pop!_OS GUI code compiles, and the current source has only [same-SHA desktop-ui L1 success #45314250801](https://github.com/kunlong-luo/lanveriq/actions/runs/45314250801) (23 source-guard, 22 preflight, 19 Vitest tests); Rust native test/Clippy acceptance remains pending. The [L2B-13 same-current-source GUI-only .deb re-audit](../../docs/development/desktop-tauri-l2b13-current-source-gui-deb-reaudit-2026-11-24.md) has started as [candidate CI #45315837623](https://github.com/kunlong-luo/lanveriq/actions/runs/45315837623). Its source safety/React/Cargo-lock/Rustfmt gates ran successfully, while native package/ELF/dpkg-shlibdeps/clean Ubuntu OCI/strict Clippy evidence remains unverified. No signed, uploaded, installed or approved end-user installer exists. [L2A documentation](../../docs/development/desktop-tauri-offline-deb-candidate-2026-11-23.md) · [L2B-12 current contract](../../docs/development/desktop-tauri-l2b12-cosmic-readonly-gui-integration-2026-11-24.md).

## Developing this source (maintainers only)

On a Linux development environment with Node 24.22.3, pnpm 12, Rust 1.100 and the Tauri v2 WebKitGTK 4.1 Linux build prerequisites installed, run the following from apps/desktop:

    pnpm install --frozen-lockfile
    pnpm test
    pnpm build
    pnpm tauri dev

Both real generated pnpm-lock.yaml and Cargo.lock have now been committed and the workflow uses frozen/locked dependency installation. This remains a NON-RELEASE preview until native compile checks, release signatures, privilege mediation, and actual Pop!_OS L3 have separately passed. Browser-only local preview runs pnpm dev but **cannot inspect the host**; do not treat browser fallback as actual installation.

End users **must not** be asked to run these commands. Their future approved product receives a signed, double-clickable installation experience. There is currently **no end-user installer**. Do not run a real install on personal or production devices.

[Source/CI milestone](../../docs/development/desktop-tauri-first-source-2026-11-23.md) · [ADR-0013](../../docs/architecture/adrs/0013-tauri-desktop-installer-idle-agent.md) · [L3 plan](../../docs/testing/linux-desktop-installer-acceptance.md).
