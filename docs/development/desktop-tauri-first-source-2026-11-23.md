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
