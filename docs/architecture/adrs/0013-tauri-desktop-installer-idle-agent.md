# ADR-0013 — Pop!_OS GUI installer + independent headless Agent

**Status: ACCEPTED FOR DEVELOPMENT (design only) · Date: 2026-11-23 · v0.1 Linux-first · NOT APPROVED FOR REAL DEPLOYMENT**

> **Implementation note 2026-11-23（源码候选）：** apps/desktop Tauri 2 / React 只读离线 UI 已增加，**不包括实际安装、安装桥接、签名软件包或系统启动**；候选编译和测试需独立 CI 核验。本文较早的“仓库没有 Tauri desktop”陈述属于决议时点的历史快照。见 [首个源码阶段](../../development/desktop-tauri-first-source-2026-11-23.md)。

## Context and user outcome

Requiring an end user to install Rust, compile Cargo, edit TOML, run dpkg manually and use systemctl is an unacceptable normal installation experience. Product goal: one reviewed graphical installer package on Pop!_OS 24.04; clear administrator consent and permissions; subsequent background operation without a permanent window, dock/taskbar icon or tray icon. A user may open a management UI again intentionally to inspect or reconfigure the installation.

The current repository has only an idle Rust Agent, source-controlled systemd unit, and unsigned/nonactivating offline .deb candidate validated by Ubuntu24 synthetic CI. It does **not** contain a Tauri desktop app, signed installer, privileged install broker or actual approved real-host installation. The existing .deb contains neither configuration nor activation hooks. The existing service defaults to offline / no collection / no remote control. No user-facing desktop feature is implemented by this ADR.

## Decision

- Adopt **Tauri 2 + React + Tailwind 4 + Base UI** as the **planned** Linux desktop install/config/diagnostics application, separated from the Next.js web-admin control plane. Reuse visual tokens and reusable pure React components; do not embed the Next.js server, authentication boundary or operational API inside the desktop GUI. A desktop-only frontend bundler (if needed) is not a revival of the retired apps/web Vite frontend.
- Use system-managed signed **native Debian packages** for distribution: the Agent stays a standalone Rust binary owned by a native package, run through the **system-level systemd manager**, not a child/background process of Tauri. Final one-click bundle format, version pin, verified origin, rollback and upgrade strategy require implementation and L3 evidence. **No rustup, Cargo, Node or manual TOML needed on an ordinary recipient machine** as a shipping product criterion, not a current capability.
- Tauri executes with **ordinary user privileges**. All system-level installation, service control, protected config changes and uninstall actions are mediated by an explicitly scoped **PolicyKit/polkit authorization step**, using the distribution's reviewed package-management facilities. No unrestricted root Tauri process, generic shell/command executor, arbitrary command/path selection, dynamic scripts, ambient escalation or unauthenticated local control socket. Whether a packaged helper is required must be decided by audited implementation; the front end does not embed privileged scripts.
- Installer presents app identity, signed source, agent permissions, network/collection status, privacy notice, data retention, service-on-boot choice and uninstall route **before** authorization. A visible and attributable administrator approval must be captured; non-consent and cancellation are fail closed and leave the service unenabled.
- **Headless runtime:** after an approved start, systemd runs the Agent independently of the logged-in COSMIC session. Closing the graphical UI does not stop the Agent; logout may leave the system service running. No persistent desktop window, dock/taskbar icon or system tray icon and no hidden session agent are required. A discoverable manual app launcher/system-app entry and an accessible status/uninstall path are mandatory. This is **not covert installation** and not permission to suppress mandatory OS/legal disclosure.
- **Startup default:** while real-device deployment and legal/identity/G-05 gates remain OPEN, the actual Agent must **NOT** be installed/enabled/started on ordinary hosts automatically. In a future approved deployment, enabled-on-boot must be a **separately displayed, explicit consent choice** with auditable grant and security approval; installing the GUI or preview package alone is not start consent. No self-enrollment, network, capture, remote access, recovery or certificate issuance by default.
- Configuration separates nonsecret validated TOML from OS-protected key material. No privilege expansion through arbitrary environment, command args, user-editable unit files or drop-ins. Config edits are validated and atomically staged by a narrow approved helper; never place private keys in a GUI field, unprotected config, telemetry or diagnostics. App shows truthful disconnected/not approved/installed/stopped states and does not fabricate device identities.
- Updates and removal preserve operator-owned configuration and persistent Agent state unless a distinct, explicit retention/legal approval authorizes destruction. Stop, disable, backup/recovery, rollback and revocation are separate planned lifecycle actions; do not treat dpkg -r or UI disappearance as proof of secure erasure.

## Alternatives and tradeoffs

- CLI-only .deb installation: useful for CI and operators, **rejected as the standard end-user experience**.
- GTK4/libadwaita, Slint or Iced native GUI: valid technical options; Tauri 2 selected for existing React design-system reuse and Rust integration, subject to Pop!_OS WebKitGTK and packaging compatibility tests.
- Tauri long-running daemon/autostart tray: **rejected**. Extra user-session persistence and a UI-owned service lifecycle would increase risk and prevent headless operation.
- Self-running root GUI, shell-based installers, independent homegrown service manager and auto-enabling package postinst: **rejected** on least-privilege, audit and operational safety grounds.
- RustDesk/remote support and OS-authorized media recording are separate gated components, **not** part of the v0.1 installer.

## Scope and supersession

Refines [ADR-0008 Next.js](0008-nextjs-app-router-console.md), [ADR-0007 reuse-first](0007-mature-ecosystem-first.md) and the optional future Tauri description. **Does not supersede** Next.js as the sole web control console, nor Agent REST/mTLS/ACK, Linux identity/custody or privacy decisions. The **desktop installer/management tool moves to the v0.1 Linux release workstream**; future consent-dependent capture/recording UI remains v0.3 and independently gated.

**Approval scope:** source/design and disposable synthetic UI/install rehearsal only, no signed distribution or real service activation.

**Explicit non-authorizations:** no deployment on personal/employee endpoints, no covert install, no autostart without opt-in, no new elevated endpoint control, no production device networking/collection/remote/recovery, no real PKI/CA, no real FIDO or independent WORM sign-off. **G-02/G-05/G-07/G-08/G-09 OPEN; v0.1 NOT RELEASE READY.**

[Product behavior and acceptance](../../product/linux-desktop-installer-v01.md) · [Linux L3 installer gate](../../testing/linux-desktop-installer-acceptance.md) · [Release gaps](../../planning/release-gap-register.md).
