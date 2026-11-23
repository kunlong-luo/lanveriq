# G-05 / G-07 / G-08 / G-09 — Pop!_OS 24.04 desktop installer acceptance

**TEST PLAN · 2026-11-23 · ALL NEW DESKTOP TESTS NOT RUN · no real-host installer exists · v0.1 NOT RELEASE READY**

Source design: [ADR-0013](../architecture/adrs/0013-tauri-desktop-installer-idle-agent.md), [desktop product scope](../product/linux-desktop-installer-v01.md). This plan covers a future Tauri 2 application and scoped OS authorization bridge. Neither is implemented. LNX1–3 Ubuntu synthetic unit/package checks do not satisfy the tests below.

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
