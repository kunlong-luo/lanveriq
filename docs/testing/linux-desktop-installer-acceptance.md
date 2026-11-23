# G-05 / G-07 / G-08 / G-09 — Pop!_OS 24.04 desktop installer acceptance

**TEST PLAN · 2026-11-23 · partial read-only desktop SOURCE L1 2/2 CI PASS · actual GUI/polkit/install L2/L3 NOT RUN · v0.1 NOT RELEASE READY**

> **2026-11-23 DESKTOP-L2A candidate (CI PENDING):** A milestone CI now attempts a real **GUI-only** Tauri Debian archive and strict temporary-root payload audit; 19 source-controlled hostile synthetic packaging tests accompany it. **Neither a signed one-click Agent installer nor real dpkg install/upgrades exist**; new bundle evidence cannot check off G-05 L3 or the real GUI/Polkit acceptance below. [Source and exact non-proof](../development/desktop-tauri-offline-deb-candidate-2026-11-23.md).

Source design: [ADR-0013](../architecture/adrs/0013-tauri-desktop-installer-idle-agent.md), [desktop product scope](../product/linux-desktop-installer-v01.md). This plan covers a future Tauri 2 application and scoped OS authorization bridge. Neither is implemented. LNX1–3 Ubuntu synthetic unit/package checks do not satisfy the tests below.

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
