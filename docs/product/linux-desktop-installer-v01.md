# Lanveriq Desktop — Pop!_OS 24.04 installer and local manager (v0.1)

**PRODUCT DESIGN BASELINE · 2026-11-27 · offline source preview / real device deployment NOT APPROVED**

**2026-11-28 技术调整：** [ADR-0014](../architecture/adrs/0014-gpui-desktop-candidate.md) GPUI 原生只读功能及 KDE/X11 12/12 所有人验收已通过；源码切换至唯一 apps/desktop，迁移后 CI PASS，本机 MVP-08 DONE。旧 Tauri 描述仅为历史；安装权限不变。

**MVP-08 Tauri 历史尝试（前端 L1 PASS，原生 APT 审计 FAIL，本机未验）：** 客户端优先，七入口工作区（概览、安装向导、服务器与配置、设备注册、Agent 服务、安全与诊断、关于），复用原只读检查、草稿、安装审核与诊断；无真实连接、凭据导入、注册、安装、启停。未核验显示未知，敏感动作禁用，紧凑窗保留可见导航。现有 [任务账本](../planning/local-mvp-tasks.md) 管理 MVP-08～16、前置条件、CI/本机退出验收。

> **2026-11-23 源码进度（L1 构建已通过，安装功能仍不存在）：** 已在 apps/desktop 新增 React/Tauri 2 预览向导和只读本机检查，但图形化真实安装、签名包、polkit broker、更新/卸载仍没有实现。不要把源码预览等同可下载的桌面客户端；详见 [首个功能阶段](../development/desktop-tauri-first-source-2026-11-23.md)。

## Goal

Ordinary users receive a **single verified desktop installation experience** with no requirement to install Rust, Node, Cargo or Gradle, edit TOML, execute manual shell commands, or understand systemd. Installation and configuration happen through a user-facing GPUI native desktop application; the compiled Rust Agent runs separately as a **systemd system service** when and only when authorized. Closing the desktop GUI leaves an already authorized Agent service running. There is **no permanent GUI, Dock/taskbar icon, tray icon or desktop-session background process** after the installer closes; the app remains discoverable in the applications menu or an administrative system-management entry and can be reopened deliberately. No secret/silent installation, concealed system process or anti-uninstall behavior.

Current state: a GPUI desktop **noninstalling preview** exists; a verified installer bundle, privileged service-install bridge and production signing do **NOT** exist in this repository. The existing unsigned, nonactivating offline Agent .deb is **not** a product installer and must not be distributed or installed as a released application.

## Deliverable boundaries

| Part | Intended responsibility | Must not do |
| --- | --- | --- |
| GPUI desktop manager (planned) | First-run welcome, signed package identity, privacy notice, host compatibility, administrator consent, installation/configuration progress, status, diagnostics, update/uninstall screens | Root GUI, systemd daemon, persistent tray process, unattended service start, collection, remote desktop |
| Audited OS authorization bridge (planned) | Narrow fixed PolicyKit/polkit-mediated package/config/systemd operations after explicit consent, verify fixed paths/versions and signature | Arbitrary shell/argv, generic root IPC, hidden privilege elevation, executable packages from arbitrary URLs |
| Rust Agent (existing idle-only code) | Independent system-level systemd service; offline safe idle lifecycle, logs, OS-protected local state | Depend on the GUI being open, auto-enroll, auto-upload, collect, issue certificates, control remote sessions |
| Next.js web console (existing disconnected preview) | Future central administrator management, access control and device fleet reporting after separate approvals | Replace the local privileged installer or be bundled as desktop privileged service |

Implementation layout **proposal only**: apps/desktop for the GPUI native Rust GUI; apps/agent remains independent; a narrowly scoped install broker should be packaged and audited separately only if genuinely necessary. Do not create a competing apps/web-next or reuse obsolete web Vite runtime.

## User journeys and screen states

1. **Obtain:** authorized distribution presents signed version/hash, supported amd64 Pop!_OS 24.04 and local package source. If signature/version/OS/architecture is unrecognized, block with an actionable error; no silent download or insecure fallback.
2. **Welcome and permissions:** explain what the Agent will do **now** (idle/offline only) and what is explicitly not enabled (real enrollment, capture, networking, remote control). Link notice, data-retention options, administrator authorization and support information. Consent checkbox is off by default, and denial ends without install/start.
3. **Preflight:** read-only checks of OS/architecture, conflicts with old /usr/local/manual install, existing package/service/drop-ins, disk space, already-held configuration, and relevant systemd/PolicyKit support. If unexpected state, stop and explain rather than overwrite or remove.
4. **Install and configure:** after explicit authorized action, a limited broker validates the exact signed package and approved package owner, installs in package-managed paths, atomically writes only non-secret whitelisted settings, and respects retained state. No plain-text private key import into GUI; OS custody separate.
5. **Service choice:** show **not started** as the safe default while gates are open. In a **future separately approved build**, present distinct opt-ins for initial service start and startup on boot. Neither action can be inferred from general installation consent. The systemd service remains independent of the desktop GUI.
6. **Finish:** close the window. No tray/taskbar/Dock residency or login autostart. Manual reopen shows package version, installed/stopped/running/failed/permission-required state, last local check, clearly disconnected/not-approved features, and approved actions. No invented successful connection or active device count.
7. **Maintain:** provide on-demand diagnostics with redacted journals, explicitly authorized update/rollback, and uninstall with stop/disable, audit, revocation and separate retention choices. Default uninstall retains sensitive local data for operator/legal review; destructive purge requires its own recorded authorization.

## Failure and adversarial states

- Cancel consent, reject admin auth, missing polkit, unsupported host, invalid signature/hash, modified .deb, wrong package owner, path/symlink/drop-in substitution, untrusted source, stale service manager, existing /usr/local legacy binary or version downgrade: **no enable/start and no partial trusted state claim**.
- Service failed/inactive/disabled must be accurately displayed. Do not equate package presence, systemctl "loaded", and running healthy Agent; no fictitious connection to server.
- Installation interruption/failed update must return to inspectable state; never delete SQLite WAL, local certificates or operator-owned configuration by cleanup scripts. Recovery paths need explicit authorization and tests.
- Restricted privilege operations must have allowlisted actions/paths, deny arbitrary arguments, have scope/time-bound polkit approval and record audit metadata without secrets. A normal user must not acquire root operations via a GUI IPC layer.
- Notifications and accessibility: user can review effective permissions and uninstall at any time; keyboard navigation, screen-reader labels, COSMIC focus behavior, 100–200% scaling, 1366×768 and mobile-size/window overflow acceptance. No hidden-only settings.

## Packaging and lifecycle design

- Candidate shipping outcome: **one double-clickable, signed package** installs the desktop manager and delivers/references the audited Agent package. Exact subpackage dependency, PackageKit/polkit integration and final update format are pending prototype + target-OS acceptance; do not claim the current .deb already implements this.
- Use native dpkg/systemd rather than writing a daemon manager. The Agent executable uses package-managed /usr/bin; unit uses /usr/lib/systemd/system. Installation alone must not enable or start it before release gates. No maintainer hooks that silently auto-enable.
- Long-running Agent should be resilient to COSMIC logout and GUI closure, but systemd restart/automatic recovery policies are separately gated; existing idle unit's Restart=on-failure is a controlled template, **not** authorization for production self-recovery.
- Configuration is schema-versioned, safe-by-default, bounded, nonsecret; privileged state belongs to OS-protected paths and existing custody strategy.

## Definition of done and status

**Planned implementation, not current capability.** The v0.1 desktop milestone requires: source app + reviewed native package; reproducible signed release proof; synthetic negative tests for privilege/input/installer failures; disposable Ubuntu24 package lifecycle; authorized Pop!_OS24 actual GUI/polkit/dpkg/systemd reinstall/upgrade/reboot/rollback/uninstall/COSMIC tests; privacy/accessibility sign-off; G-05/G-07/G-08/G-09 approvals. Detailed matrix: [L3 acceptance](../testing/linux-desktop-installer-acceptance.md). No completed milestone is implied by this design document.

Unrelated capture, screen recording, stealth behavior, user activity analysis and RustDesk remote control are **not v0.1 installer scope**. v0.1 is still **NOT RELEASE READY**, and existing production-disabled guards remain enforced.

[Decision](../architecture/adrs/0013-tauri-desktop-installer-idle-agent.md) · [Installation entry](../operations/installation.md) · [Linux Agent L3](../testing/linux-popos24-agent-acceptance.md).
