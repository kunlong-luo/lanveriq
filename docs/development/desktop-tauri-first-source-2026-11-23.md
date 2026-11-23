# Lanveriq Desktop v0.1 — first Tauri 2 source implementation

**2026-11-23 · SOURCE CANDIDATE · CI PENDING · noninstalling offline preview only · Pop!_OS physical L3 NOT RUN · v0.1 NOT RELEASE READY**

## Implemented this source milestone

- New apps/desktop: independent **Tauri 2 Rust** and **React 19, Tailwind 4, Base UI available, Lucide**. It is separate from the canonical Next.js apps/web; the desktop-local Vite bundler is not a revival of the removed web-next/Vite console.
- Chinese 4-step wizard: welcome, **read-only** target system inspection, notice/permissions explanation, disabled install. Separate status and safety tabs. No invented device data, active service, certificate or connection status. No persistent tray or autostart.
- Exactly one Tauri IPC: **inspect_host**. On Linux, only public /etc/os-release and fixed read-only **/usr/bin/dpkg-query -W** and **/usr/bin/systemctl show** are used; no read of keys, private configuration, user inventory or journals. Non-Linux and browser-only previews display unverified/unsupported.
- Config: bundle.active=false, no signing/installer, no elevated broker, no network/autostart/shell/updater plugins. Install/service start/boot/collection/network/remote/CA release constants explicitly FALSE. Consent explanation is not actual installation or start authorization.
- Synthetic source-negative tests deny a modified release lock, extra invoke, unsafe shell/commands, privilege scope changes, tray and bundle activation. Pure Rust tests check OS exact version, package mismatches, failed/running service and unreviewed overrides.

## CI and next steps

The new [disposable Ubuntu24 CI](../../.github/workflows/desktop-tauri-preview.yml) runs frontend tests, TypeScript/Vite build, Python negative source tests, real Rust/Tauri source compile and Clippy with WebKitGTK 4.1; it **never runs or installs Agent**. Generated lockfiles must be reviewed and committed in a follow-up, followed by strict --frozen-lockfile/--locked CI. Any CI failure remains recorded. CI must finish at exact functional SHA before PASS is claimed.

Next steps: real generated Cargo.lock/pnpm-lock.yaml; actual Chromium keyboard/small-screen GUI review; separately reviewed limited polkit broker and signed package design; actual Pop!_OS24 GUI/service/install/rollback/L3 only with approval. These are NOT IMPLEMENTED/NOT RUN. No authorized signed desktop .deb exists.

[ADR-0013](../architecture/adrs/0013-tauri-desktop-installer-idle-agent.md) · [product requirements](../product/linux-desktop-installer-v01.md) · [L3 acceptance](../testing/linux-desktop-installer-acceptance.md) · [Issue #7](https://github.com/kunlong-luo/lanveriq/issues/7).

**G-02/G-05/G-07/G-08/G-09 OPEN, v0.1 NOT RELEASE READY.** Production network, capture, auto-recovery, remote, CA and real FIDO/WORM remain OFF.

## Locked dependency build candidate (2026-11-23; CI pending)

GitHub's first desktop frontend job built and tested React/TypeScript successfully; it exported an actual pnpm12 generated lockfile. The extra transient pnpm 12 self-bootstrap YAML document was separated from the project importer document; only the actual desktop dependency graph is committed to apps/desktop/pnpm-lock.yaml. [Independent non-installing Cargo.lock bootstrap #45146503121](https://github.com/kunlong-luo/lanveriq/actions/runs/45146503121) SUCCESS; its generated Cargo version-4 lock is committed without inventing dependency hashes. The one-use bootstrap workflow has been removed from main.

Historical failures preserved: [First desktop L1 #45145378646](https://github.com/kunlong-luo/lanveriq/actions/runs/45145378646) FAIL due to a negative-test fixture escaping bug; [Second desktop L1 #45145579280](https://github.com/kunlong-luo/lanveriq/actions/runs/45145579280) frontend tests PASS, native job FAIL on **rustfmt only** after Ubuntu WebKit prerequisites; 6 formatting differences were applied in a reviewed source commit. Those runs do **not** prove native Tauri compile. New source requires exact frozen pnpm and Cargo --locked builds; pending runs must not be called successful. No signed .deb, broker, real Pop!_OS L3 or activation.

## Native Tauri compile finding and minimal icon fix (2026-11-23)

[Locked-source native #45146915232](https://github.com/kunlong-luo/lanveriq/actions/runs/45146915232): frozen-pnpm frontend and native Cargo.lock/Rustfmt PASSED, but Tauri native compile FAILED in tauri::generate_context! because the required icons/icon.png did not exist. No security gate was bypassed. The next source commit adds a **static launcher/window icon** (not a tray or resident login autostart icon), verifies the real PNG signature by a new negative test, and moves Rustfmt + Cargo lock verification **before** slow Ubuntu WebKitGTK installation. The failure is preserved, and a new exact-SHA CI is mandatory before native PASS can be claimed.

## Follow-up: headless install preflight refinement (source pending CI, 2026-11-23)

The read-only Tauri inspector now also checks whether a historical manual Agent path exists at /usr/local/bin/lanveriq-agent by **symlink_metadata without following a link, executing the file or reading its contents**. The snapshot only includes a three-state presence indicator (present_unreviewed / not_detected / unverified). The React safety assessment prioritizes old-path conflicts, running/enabled services, unexpected drop-ins and stale systemd manager cache over any candidate-package label. Each yields a visible operator warning and cannot enable installation. Pure-state tests cover these conflicts. The new path-presence indicator **does not authenticate package signature, ownership, identity or security**, and inability to read the path is not treated as absence. No install/start/stop/enable/polkit or Agent activation has been added. **L1 CI not yet verified at this source stage; real Pop!_OS L3 NOT RUN.**

## Exact source SHA accepted after native PNG fix — 2026-11-23

**Functional [86c3c407e45e4d73a4b43c73a6cfa4203923d107](https://github.com/kunlong-luo/lanveriq/commit/86c3c407e45e4d73a4b43c73a6cfa4203923d107): [Desktop Tauri 2 offline preview #45148201067](https://github.com/kunlong-luo/lanveriq/actions/runs/45148201067) — 2/2 actual jobs SUCCESS.** Frontend: Python static no-shell/no-broker/no-autostart gate + **9 mutation tests**, **5 Vitest pure-state tests**, Node24.22.3, pnpm12 **--frozen-lockfile**, TypeScript and Vite build. Native: Ubuntu24 WebKitGTK4.1 prerequisites; Rust1.100; Cargo.lock **--locked**, Rustfmt, actual Tauri codegen/native Rust compile; **4 Rust unit tests** (OS/version, package version, unsafe/unverified systemd, always-disallowed install/start); Clippy -D warnings all PASS. [Docs authority #45148201149](https://github.com/kunlong-luo/lanveriq/actions/runs/45148201149) and [RD-2 fail-closed #45148201078](https://github.com/kunlong-luo/lanveriq/actions/runs/45148201078) also SUCCESS on exact feature SHA. No Next.js test triggered for these paths; do not claim it.

Earlier actual failures preserved: first [#45145378646](https://github.com/kunlong-luo/lanveriq/actions/runs/45145378646) Python negative-fixture escaping; second [#45145579280](https://github.com/kunlong-luo/lanveriq/actions/runs/45145579280) Rustfmt formatting; third [#45146915232](https://github.com/kunlong-luo/lanveriq/actions/runs/45146915232) Tauri generated-context missing icons/icon.png. All repaired in later source commits; CI remains green only for SHA 86c3.

**Scope and limits:** Native code compilation != real desktop window/COSMIC integration; static icon is for an opened app only; no actual .deb signed/build, no polkit privilege mediator, no package installation, no systemd start or boot enable, no actual Pop!_OS24 L3, no organizational consent or user deployment. This is a **read-only design+source L1** milestone, not release approval. G-05/G-07/G-08/G-09 OPEN, v0.1 NOT RELEASE READY.

## L1.1 first red test and targeted repair (2026-11-23)

Initial SHA [c352e412](https://github.com/kunlong-luo/lanveriq/commit/c352e4127844669772bc941f8af9ab1431144ce2) [desktop CI #45149478116](https://github.com/kunlong-luo/lanveriq/actions/runs/45149478116) **FAILED**: one old Vitest assertion expected the text “未批准” after the new higher-priority active-service warning correctly returned “未获授权”; 8/9 frontend tests passed, security-source negative suite passed, native Rust build **SKIPPED** due to the preceding failure. Next commit updates only the expected warning and removes a redundant Rust metadata no-op; it must pass frozen pnpm, native Rust Cargo and all gates at a new exact SHA before the feature is accepted. This preserves the actual failure history. No installer privilege enabled and Pop!_OS L3 still NOT RUN.
