# ADR-0013 — Pop!_OS GUI installer + independent headless Agent

**Status: ACCEPTED FOR DEVELOPMENT (design only; GUI choice now under ADR-0014 candidate review) · Date: 2026-11-23 · v0.1 Linux-first · NOT APPROVED FOR REAL DEPLOYMENT**

> **2026-11-27:** [ADR-0014](0014-gpui-desktop-candidate.md) authorizes isolated GPUI replacement evaluation, not cutover. Freeze Tauri feature expansion; all installer, consent, custody and independent service decisions below remain binding. Prior failures and evidence are retained.

> **2026-11-24 L2B-20 真实 APT `Signed-By` LAB：** [Ubuntu24 #45334019405](https://github.com/kunlong-luo/lanveriq/actions/runs/45334019405) **9/9 实际 apt-get update 系统验签通过**，证明隔离的合成 deb822 仓库接受正确 InRelease/Packages 并拒绝错误签名者/keyring、全局信任绕过、无签名、索引篡改、过期及未来日期。测试没有真实 `.deb` 或 apt-get install、没有向机器添加发行者根。**目标产品方案仍需独立批准的签名 APT 仓库/专用 keyring，受审受限 Polkit broker，以及合法授权 Pop!_OS COSMIC L3**。不要将 CI 临时 key 当成真实发行源，也不需要自研取代 APT 的包验证。[L2B-20 证据](../../development/desktop-tauri-l2b20-real-apt-signed-by-offline-ci-2026-11-24.md)。

> **2026-11-24 L2B-19 接收端根导入前的 LAB 评估：** [Ubuntu24 #45328874536](https://github.com/kunlong-luo/lanveriq/actions/runs/45328874536) 34/34 根 pin、双签、撤销、过期和版本模拟回滚负测已成功；**所有 pin/时间/高水位仍由同一测试 CI 输入，不是真实独立预置可信根或设备防回滚保障**。后续应复用成熟 TUF 元数据客户端、按需审查 Sigstore 离线 bundle 与独立身份/密钥托管，不能把 LAB 文件或临时公钥当成正式 Root。Agent 签名安装器、Polkit 授权和 Pop!_OS 实机 L3 均未实施。[L2B-19 安全验证交接](../../development/desktop-tauri-l2b19-receiver-root-preprovision-lab-2026-11-24.md)。

> **2026-11-24 L2B-18 发行供应链验证进度：** [LAB #45326441957](https://github.com/kunlong-luo/lanveriq/actions/runs/45326441957) 30/30 真正 OpenSSL 3 双角色签名与攻击测试 SUCCESS。该实验证明互操作性，**不证明独立管理域、发行者公钥独立预置、可信时间/撤销或法律双审**，也不创建签名安装器/Polkit。未来真正可用图形安装器仍须经独立批准的发布身份/可信根/受限授权及 Pop!_OS 物理 L3。[L2B-18](../../development/desktop-tauri-l2b18-dual-signature-custody-lab-2026-11-24.md)。

> **2026-11-24 L2B-17 仅设计 L1 证据：** [Ubuntu24 offline #45324617901](https://github.com/kunlong-luo/lanveriq/actions/runs/45324617901) 已完成 39/39 安装意图拒绝测试，实际 Tauri 两个只读 IPC、禁用 bundle/installer 和 L2B-16 发行权限门禁均 PASS。此阶段并未引入真正的 Polkit action、privileged broker、Agent 包签名或安装/启停，不能批准生产部署。后续逐次授权不能依赖 Polkit 保留授权缓存或客户端自报 command_line；必须由独立 broker 对发行者/工件和实际授权主体做验证。[L2B-17 规范](../../development/desktop-tauri-l2b17-agent-install-intent-polkit-threat-contract-2026-11-24.md)。

> **Implementation note 2026-11-23（源码限定 L1 2/2 CI 已验收）：** apps/desktop Tauri 2 / React 只读离线 UI 已增加，**不包括实际安装、安装桥接、签名软件包或系统启动**；候选编译和测试需独立 CI 核验。本文较早的“仓库没有 Tauri desktop”陈述属于决议时点的历史快照。见 [首个源码阶段](../../development/desktop-tauri-first-source-2026-11-23.md)。

## 2026-11-24 L2B-19 后续发行来源选型（仍处于审核阶段）

依照 [L2B-19 实际接收端根 LAB / 34 项测试](../../development/desktop-tauri-l2b19-receiver-root-preprovision-lab-2026-11-24.md)，真实 Debian/Pop!_OS Agent 安装应**优先审查复用系统已有的 apt-secure/InRelease/Packages 校验与 `Signed-By` 仓库专用 keyring**，不允许让任意 GUI 输入把公钥注册为全局 APT 信任根。APT 仓库认证并非单包独立签名，须保留对外部审查的发布者身份、签名轮换、可信时间、撤销、回滚与安装策略要求；若需要安全的离线多版本交付或外部工件来源证明，再分别评估成熟 TUF 和 Sigstore 离线 bundle 客户端。现在**没有配置任何产品仓库、发行密钥、Polkit 或受限安装 broker**；Tauri 仍仅允许两个只读 IPC，发行和实机门禁全部 OPEN。

## Context and user outcome

Requiring an end user to install Rust, compile Cargo, edit TOML, run dpkg manually and use systemctl is an unacceptable normal installation experience. Product goal: one reviewed graphical installer package on Pop!_OS 24.04; clear administrator consent and permissions; subsequent background operation without a permanent window, dock/taskbar icon or tray icon. A user may open a management UI again intentionally to inspect or reconfigure the installation.

**At this ADR's decision time (2026-11-23)** the repository only had an idle Rust Agent, a source-controlled systemd unit and unsigned/nonactivating offline Debian candidate. The previously recorded statement that no Tauri app existed is **HISTORICAL**, not a present-tense inventory: [L2B-21 Tauri read-only GUI review](../../development/desktop-tauri-l2b21-install-gate-review-ui-2026-11-24.md) now exists and [same-SHA native UI CI #45368842676](https://github.com/kunlong-luo/lanveriq/actions/runs/45368842676) passed. **Still absent** are a trusted signed Agent installer, privileged fixed-action Polkit broker, formally approved Agent service installation and consented Pop!_OS COSMIC physical L3. The idle Agent service defaults to no production network/collection/remote control. This ADR authorizes design/isolated tests only.

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
