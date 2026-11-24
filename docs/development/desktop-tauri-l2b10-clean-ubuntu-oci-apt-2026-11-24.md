# DESKTOP-L2B-10 — clean Ubuntu 24.04 OCI APT simulation and Pop!_OS L3 separation

**SPEC · 2026-11-24 · feature SHA a13ac215 · synthetic L1 PASS / native L2 PENDING · NO INSTALL · v0.1 NOT RELEASE READY**

## Security objective and existing evidence

L2B-9 [actual Ubuntu24 native Tauri Debian and ABI verification #45301534228 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45301534228) proved the real GUI-only uninstalled `.deb` declares all ten dpkg-shlibdeps direct minimum ABI groups, plus two default Tauri Depends (12 groups total, 0 direct version gaps). Its `apt-get -s` ran on a **build runner containing installed WebKitGTK/GTK development libraries**. That did not answer whether an APT solver could resolve all dependencies on a fresh host. L2B-10 adds an independently resolved fresh Ubuntu 24.04 rootfs, not a Pop!_OS image.

## Implementation (mature APT/Docker, no package installation)

The [read-only clean APT verifier](../../apps/desktop/tools/verify_desktop_clean_apt_simulation.py) takes the candidate `.deb`, the previous exact-SHA L2B-8/L2B-9 official dpkg-shlibdeps ZERO-GAP report, feature SHA, workflow run ID/attempt, and temporary output/log files. Before Docker networking, it rejects missing/tampered prior ABI receipts, unexpected source or SHA, forged license/release authority and a package with direct ABI gaps.

On a disposable GitHub Actions Ubuntu 24.04 runner, it uses maintained Docker CLI to pull the official `ubuntu:24.04` tag, inspects the **actual resolved `ubuntu@sha256:` manifest digest** and runs *that resolved digest*, not a second mutable-tag reference, with `linux/amd64`, root filesystem read-only, Linux capabilities all dropped, no-new-privileges, constrained processes/CPU/RAM, ephemeral tmpfs for APT indexes/cache and a single **read-only bind mount** of the exact candidate `.deb`. It does NOT mount the Docker socket, source repo, SSH keys, home directories or Agent. APT indexes are retrieved into **container-only tmpfs** over the Ubuntu mirror network; the target Debian is never installed.

The container verifies `/etc/os-release` says `ubuntu:24.04` and dpkg arch `amd64`, captures the initial installed package count, refuses preinstalled WebKitGTK and GTK native libraries or Lanveriq, and hashes the mounted `.deb` to match host receipt. It then executes **official `apt-get -s -o Debug::NoLocking=1 -o APT::Install-Recommends=false install /candidate.deb`**. The validator rejects missing target Inst/Conf actions, duplicate identity markers, removal/purge requests, foreign architecture/OS, unexpected packages, changed byte hashes and Agent/service names in the plan. A temporary unsigned JSON record carries OCI digest, package SHA, source/run/attempt, upstream ABI receipt SHA, captured APT simulation log SHA and planned install count; verification recomputes all evidence without executing a second simulation.

**Trust limitation:** `ubuntu:24.04` is resolved to a runtime digest and recorded, **not hard-pinned in source code to a separately preapproved publisher signature**. This is a reproducible-per-run input record, not signed supply-chain provenance. It uses publicly available Ubuntu24 package repositories at run time. The clean rootfs is a Docker *container*, not a freshly installed full desktop or virtual machine. APT success is a solver simulation, not evidence that the app starts, that the system meets ABI/Wayland/runtime plugin requirements, or that actual Pop!_OS 24.04 repositories include the same candidate versions. It cannot constitute official publisher signature or license approval.

## Evidence states

- **L1 PASS:** exact feature SHA [a13ac215](https://github.com/kunlong-luo/lanveriq/commit/a13ac2158dcb5153b2c945dece86a457e6cc4059), [synthetic OCI plan/manifest proof CI #45303283606 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45303283606): 16 new negative/positive tests, no actual Docker or native candidate. This is not an installation test.
- **Native L2 PENDING:** [same feature SHA #45303283595](https://github.com/kunlong-luo/lanveriq/actions/runs/45303283595): actual native Debian build; pinned Syft source/payload, receipt/payload/ELF/apt installed graph, dpkg-shlibdeps direct zero gap, then fresh Docker rootfs APT simulation and digest-bound read-only report, followed by ephemeral lab signature. Only if all steps finish SUCCESS may the clean-Ubuntu *simulation* be recorded as L2 PASS.

## Pop!_OS 24.04 COSMIC L3 exit criteria (NOT RUN)

An authorized Pop!_OS machine must supply independently captured OS/release and architecture, COSMIC session/version, Wayland display/session, kernel and installed package inventory; its *actual* signed/custodied configured APT repository sources and key policy must be recorded. Use a controlled host or re-creatable VM to verify `libgtk-3-0t64`, `libglib2.0-0t64`, WebKitGTK and other ABI package names/versions and official APT resolver constraints. If and only if the release/legal/installer gates are independently approved, conduct human-authorized least-privilege installation/launch and cancellation, uninstall, rollback and reboot behavior with visible status and no tray/resident icon. Test offline/no network, missing package, insufficient rights, untrusted package, refusal, timeout and partial install. No vendor signing, WORM or legal gate is closed by this OCI test.

**G-05 Pop!_OS L3, G-07 legal, G-08 authorized install/SRE and independent publisher trust remain OPEN; v0.1 NOT RELEASE READY.**

[Current status](../CURRENT_STATUS.md) · [Release gates](../planning/release-gap-register.md) · [Physical hardware acceptance](../testing/linux-desktop-installer-acceptance.md) · [L2B-9 baseline](desktop-tauri-l2b9-debian-depends-reconciliation-2026-11-24.md).
