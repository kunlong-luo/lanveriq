# L2B-22 Polkit action review — NOT INSTALLED

**HISTORIC/REVIEW SOURCE ONLY · v0.1 NOT RELEASE READY.**

This directory is **not** a root helper, installed policy, PolicyKit JavaScript
rule, D-Bus endpoint or deployable package. The checked-in
`org.lanveriq.agent.package.policy` is an intentionally inert XML **review
fixture**, with three independent action IDs and `no` for every
`allow_any`, `allow_inactive` and `allow_active`. **Never** install or
copy this file into `/usr/share/polkit-1/actions/` or enable a command in
the Tauri GUI based on it. Local polkit rules can override XML defaults.

The three reviewed actions are **package install**, **package upgrade**, and
**package remove**. None is a service start, enable-at-boot, collection,
network, recording, remote-control, certificate-issuer or raw shell action.
They do not name a package URL, executable path or user-provided arguments.

`python3 apps/desktop/tools/verify_desktop_polkit_review.py` performs exact
XML/source policy review; associated unit tests inject malicious authorization,
cached approval, implied actions and arbitrary helpers to prove rejection.
The existing Tauri process still exposes only the original two read-only
inspection commands.

**Before any legitimate privileged helper**: independently approve publisher
identity and Signed-By root, use a fixed trusted package identity and source;
bind unprivileged caller to a per-operation active OS identity, re-check all
claims root-side, use a separately audited fixed-action broker and OS-native
APT/PackageKit with no generic shell, ensure cancellation/replay protection,
and independently authorize start/boot, networking and collection. Prove this
on disposable Ubuntu24 L2 and then consented Pop!_OS24 COSMIC L3. Until then:
**G-05/G-09 OPEN; v0.1 NOT RELEASE READY**.

[Polkit security limits](../../../docs/development/desktop-tauri-l2b22-polkit-inert-action-review-2026-11-24.md)
· [existing installer threat model](../../../docs/development/desktop-tauri-l2b17-agent-install-intent-polkit-threat-contract-2026-11-24.md).
