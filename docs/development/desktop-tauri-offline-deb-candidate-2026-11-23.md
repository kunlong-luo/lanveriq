# DESKTOP-L2A — native Tauri GUI-only Debian package candidate (uninstalled)

**2026-11-23 · SOURCE CANDIDATE / exact-SHA CI PENDING · no signed or released package · real Pop!_OS 24.04 L3 NOT RUN · v0.1 NOT RELEASE READY**

## What is being built and why

The desktop L1 [Tauri 2 preview](desktop-tauri-first-source-2026-11-23.md) builds with a real Rust compiler and pinned frontend dependencies but is developer-only. The next step towards a future double-click installation is to demonstrate that the **official Tauri 2 Debian bundler** produces a genuine Linux .deb **of the graphical read-only Desktop app only**, then inspect every archive entry without installing it on the runner or any user machine.

Official Tauri [CLI build --bundles deb --config](https://v2.tauri.app/reference/cli/) and [configuration merge](https://v2.tauri.app/develop/configuration-files/) define the bundling mechanism. The committed apps/desktop/src-tauri/tauri.conf.json **continues to set bundle.active=false, targets=[]**. A narrowly scoped **isolated CI-only command-line JSON override** temporarily sets bundle.active=true and targets=["deb"] in the disposable runner. This is not a release configuration or installation authorization.

## Implementation

- [GUI-only package audit tool](../../apps/desktop/tools/verify-desktop-deb.py) opens the actual .deb with read-only dpkg-deb --field, --ctrl-tarfile and --fsys-tarfile; inspects metadata, regular-file types, root-owned modes, executable ELF signature, PNG icon header and .desktop launcher; then extracts into a **temporary directory** for content checks. Exact GUI identity/version/amd64 are checked. ELF/PNG signatures alone never imply origin trust.
- Rejection-first package manifest: exactly one graphical binary under usr/bin, exactly one desktop launcher, approved hicolor PNG icons and their parent directories. No Agent executable, systemd unit, operator configuration, state, external scripts, new shell helpers, user autostart, hidden launcher or arbitrary assets. Debian control may include only control/md5sums: **no preinst, postinst, prerm, postrm, triggers or conffiles**. If the official bundler adds other entries, CI must fail and require separate review, not an automatic allowlist expansion.
- [19 negative/positive synthetic archive tests](../../apps/desktop/tools/test_verify_desktop_deb.py) attempt hostile Agent executables, systemd service, config/autostart payloads, maintainer scripts, incorrect identity/version/architecture, shell/hidden launcher, symlink, bad executable/PNG and extra launcher.
- New [milestone-sized CI](../../.github/workflows/desktop-deb-candidate.yml) uses Ubuntu24, Node24.22.3/pnpm12/Rust1.100, frozen locks, existing fail-closed Desktop source checks, React tests, native WebKitGTK4.1 prerequisites and actual Tauri CLI Debian build. **No installation of this .deb, systemctl activation, Agent build/package, artifact upload, release draft, production data or distribution**. The job prints the candidate SHA-256 in ephemeral logs; this is a measurement, not a trusted signature.
- Existing [DESKTOP-L1](../../.github/workflows/desktop-tauri-preview.yml) compile CI remains unchanged; expensive release-profile package CI runs only for its own audit/workflow paths or manual dispatch. A failed bundle does not disable any security check.

## Acceptance and explicit non-proofs

1. Same functional SHA: source validator and negative tests PASS and **real** Tauri bundler produces exactly one GUI-only amd64 Debian candidate accepted by the archive verifier on disposable Ubuntu24. Synthetic fixtures alone are insufficient.
2. Immutable default bundle-off remains true in source Tauri JSON; no Agent service, privilege broker, install/start/boot enable or tray functionality added.
3. Any CI FAIL or unexpected Tauri bundle entry/version drift must be diagnosed and preserved. Repairs require new source SHA and tests.
4. CI proves **package shape only**, not vendor signing, distribution attestations/SBOM, actual Pop!_OS24 COSMIC GTK/Wayland focus/accessibility, installation/upgrade/removal, authorized polkit, Agent custody, start consent or incident response. **G-05/G-07/G-08/G-09 OPEN.**

**This is not the promised one-click Agent installer:** the native .deb contains only the read-only GUI and is intentionally unsigned, uninstalled and unpublished. The separate unsigned Agent preview .deb is still unapproved. Final integrated signed distribution and privilege-scoped install bridge need separate approval. No production networking, collection, auto-recovery, remote, FIDO MFA, CA or independent WORM enabled. **v0.1 NOT RELEASE READY.**
