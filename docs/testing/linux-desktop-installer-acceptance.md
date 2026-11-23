# G-05 / G-07 / G-08 / G-09 — Pop!_OS 24.04 desktop installer acceptance

**TEST PLAN · 2026-11-23 · Tauri native Debian L2A archive PASS + L2B-1 unsigned source SPDX/receipt PASS + L2B-2 ephemeral Ed25519 receipt-signature lab PASS · trusted release signer, polkit, real Pop!_OS24 L3 NOT RUN · v0.1 NOT RELEASE READY**

> **2026-11-23 DESKTOP-L2A/L2B-1 completed subchecks; this does NOT approve deployment:** A real **GUI-only** Tauri Debian archive passed the strict temporary-root payload audit at [SHA d6583756, CI #45157687749](https://github.com/kunlong-luo/lanveriq/actions/runs/45157687749); a later native Tauri + pinned Anchore Syft source SPDX + unsigned local digest receipt passed at [SHA 4051276a, CI #45160206981](https://github.com/kunlong-luo/lanveriq/actions/runs/45160206981), including 12 source, 26 archive, and 38 discovered receipt suite test cases. This verifies only GUI candidate shape and local integrity, NOT a signed origin or complete binary SBOM. **Neither a signed one-click Agent installer nor actual dpkg install/upgrades exist**; new bundle evidence cannot check off G-05 L3 or the real GUI/Polkit acceptance below. [Source and exact non-proof](../development/desktop-tauri-offline-deb-candidate-2026-11-23.md).

> **DESKTOP-L2B-2 2026-11-23 独立合成 L1 和原生回执实验 L2 PASS；正式发行仍未签名：** [临时 Ed25519 签名验真实验规范](../development/desktop-tauri-l2b2-offline-signature-lab-2026-11-23.md)[功能 SHA ae0775a5 · 离线验真 #45163358720 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45163358720) 在 Ubuntu24 实际执行 16 项 OpenSSL 3 临时密钥签名验真与拒绝篡改/伪授权测试，公私钥仅由 disposable CI 产生；[功能 SHA ce1a9c26 · 原生 Tauri + Syft + 实验回执验签 #45161801144 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45161801144) 同样已验证，但**未批准正式发行密钥和独立身份**；不关闭下方真实签名安装、polkit 和 Pop!_OS 实机 L2/L3 检查。

> **DESKTOP-L2B-3 接收端离线来源预检 2026-11-23 L1 负测 PASS：** [功能 SHA 7a722680 / Ubuntu24 CI #45165058590 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45165058590) 发现执行 44 项（26 复用 Debian + 18 新增接收端），真实 GitHub CLI 拒绝伪造 bundle/root；“成功验签响应”仅测试中模拟，**没有独立批准的根或发行签名，也不能安装/分发**。[L2B-3 规范](../development/desktop-tauri-l2b3-offline-recipient-provenance-2026-11-23.md)。

> **L2B-4 2026-11-23 合成 L1 #45166773714 PASS / 原生 L2 #45166773406 PASS（仅交付文件+局部 SBOM）：** [交付文件完整性 + 局部 Syft payload SPDX 规范](../development/desktop-tauri-l2b4-payload-sbom-evidence-2026-11-23.md)要求同一原生 Debian 精确 SHA/源码回执、解包树所有文件哈希及权限匹配、额外 Syft 目录扫描、实际 create/verify PASS，合成负测拒绝修改 ELF/多余文件/软链/伪造发布。**外部运行时库不在交付包中，不能将扫描宣称为完整 SBOM**；物理 Pop!_OS L3/真实安装仍 NOT RUN。

Source design: [ADR-0013](../architecture/adrs/0013-tauri-desktop-installer-idle-agent.md), [desktop product scope](../product/linux-desktop-installer-v01.md). This plan covers the existing read-only Tauri 2 preview plus a future scoped OS authorization bridge. The read-only GUI source exists; the privileged bridge, signed integrated installer and installation authority do NOT exist. LNX1–3 Ubuntu synthetic unit/package checks do not satisfy the tests below.

> **2026-11-23 L1 已通过范围：** apps/desktop 只读 status、React 预览、9 项 Python 安全负测、5 项 React 测试、4 项 Rust 单测、原生 Tauri 编译/Clippy，在 [源码功能 SHA 86c3 Desktop #45148201067](https://github.com/kunlong-luo/lanveriq/actions/runs/45148201067) 的 **2/2 Ubuntu24 CI SUCCESS**。**下列 GUI 可访问性、真正安装/签名/polkit/L2/L3 验收清单并没有因此自动通过**；全部真实安装与授权测试 NOT RUN。参见 [源码里程碑](../development/desktop-tauri-first-source-2026-11-23.md)。

## L1 — source and negative tests (NOT RUN)

- [ ] Desktop UI can compile offline from a locked toolchain and render welcome, consent, preflight, install, progress, paused/disabled, active, failed, update and uninstall states; no false healthy/network status.
- [ ] Consent required and unticked by default; cancellation or denied PolicyKit/polkit authorization cannot install, configure, start or enable the service.
- [ ] Fixed paths/arguments in native bridge; deny arbitrary binary, shell, filesystem path, symlink, package version, unit override and privileged action not allowlisted.
- [ ] Reject invalid signature, package identity, hash, OS/architecture, downgrade, expired/revoked release, unsigned config, unaudited drop-in and legacy manually installed /usr/local binary conflict.
- [ ] A non-root GUI cannot access protected keys or use the Agent's privileged identity. Diagnostics redact secrets and never display private SQLite state or user identifiers by default.
- [ ] GUI closure does not trigger service control. Desktop application has no tray/login-autostart resident component.
- [ ] Existing Agent production network/collection/remote/auto-recovery/CA flags remain default-OFF; RD-2 negative gate included.

## L2 — disposable environment (NOT RUN)

- [ ] Build genuine Tauri Linux artifact with bundled WebKitGTK prerequisites tested, signed package verification, cryptographic provenance and exact reviewed version.
- [ ] With isolated disposable Ubuntu24 VM and snapshot: install GUI and inspect package; it does not implicitly install or start an unapproved Agent or access employee data.
- [ ] Using a **separately authorized** lab test policy: approved helper grants only requested package/config/service operations. Test denied/revoked/unprivileged users.
- [ ] Install Agent package, inspect dpkg ownership, systemctl cat/show effective properties, ensure no unknown drop-in, prove no network permissions or collection.
- [ ] Simulate safe service start/stop where separately approved; GUI exit and logout do not kill independent service. No tray/Dock icon appears while the system unit remains discoverable.
- [ ] Upgrade/reinstall interrupted and rolled back; dry-run removal retains operator config and local persistent state; purge gated separately.
- [ ] Reboot after explicit approval; confirm boot-autostart state matches separately recorded choice (never inferred from installation).
- [ ] Save exact source SHA, actual CI run IDs, artifacts, test logs, negative outcomes and rollback steps; skipped jobs remain NOT RUN.

## L3 — authorized real Pop!_OS 24.04 / COSMIC (NOT RUN)

- [ ] Host OS/architecture/kernel/COSMIC and hardware inventory captured without employee identifiers; owner/operator written consent to install/inspect/reboot.
- [ ] First-launch GUI with system fonts, display scaling, keyboard/screen-reader, no native surprise scrollbars, permission prompts and admin denial verified on the actual target.
- [ ] Signed installer authenticity and repeatable package install/upgrade/rollback tested with real polkit and dpkg; no unrelated files or existing custom user state overwritten.
- [ ] Real effective unit/daemon state, non-root DynamicUser, owner-managed config, private key directory protection, OS and Caddy connectivity OFF and fail-closed drop-ins validated by independent operator.
- [ ] Explicit start/boot opt-in separately approved where permitted; GUI closes; logged-in COSMIC session ends; service can remain running independently without persistent tray/taskbar/Dock icon. App remains discoverable by user and administrator; normal uninstall/stop controls present.
- [ ] Actual stop/disable/uninstall and retention/revocation completed with organizational record; no covert installation, no forced persistence, no anti-uninstall.
- [ ] Security/privacy/legal, installation/OS maintenance, accessible UX and SRE reviewers sign-off; G-02 independent WORM and G-01/G-03/G-04 admin/PKI gates remain separate.

**Acceptance tiers:** source design = no execution; L1 pass ≠ L2; L2 Ubuntu synthetic pass ≠ actual Pop!_OS L3; GUI installation pass ≠ production Agent fleet authorization. Any test failure is recorded before remediation. G-05/G-07/G-08/G-09 remain OPEN, and **v0.1 NOT RELEASE READY**.
