# DESKTOP-L2B-10 — clean Ubuntu 24.04 OCI APT simulation and Pop!_OS L3 separation

**SPEC · 2026-11-24 · feature SHA 11dd7762 · synthetic L1 + full native Ubuntu OCI L2 PASS · NO INSTALL · v0.1 NOT RELEASE READY**

## Security objective and existing evidence

L2B-9 [actual Ubuntu24 native Tauri Debian and ABI verification #45301534228 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45301534228) proved the real GUI-only uninstalled `.deb` declares all ten dpkg-shlibdeps direct minimum ABI groups, plus two default Tauri Depends (12 groups total, 0 direct version gaps). Its `apt-get -s` ran on a **build runner containing installed WebKitGTK/GTK development libraries**. That did not answer whether an APT solver could resolve all dependencies on a fresh host. L2B-10 adds an independently resolved fresh Ubuntu 24.04 rootfs, not a Pop!_OS image.

## Implementation (mature APT/Docker, no package installation)

The [read-only clean APT verifier](../../apps/desktop/tools/verify_desktop_clean_apt_simulation.py) takes the candidate `.deb`, the previous exact-SHA L2B-8/L2B-9 official dpkg-shlibdeps ZERO-GAP report, feature SHA, workflow run ID/attempt, and temporary output/log files. Before Docker networking, it rejects missing/tampered prior ABI receipts, unexpected source or SHA, forged license/release authority and a package with direct ABI gaps.

On a disposable GitHub Actions Ubuntu 24.04 runner, it uses maintained Docker CLI to pull the official `ubuntu:24.04` tag, inspects the **actual resolved `ubuntu@sha256:` manifest digest** and runs *that resolved digest*, not a second mutable-tag reference, with `linux/amd64`, root filesystem read-only, Linux capabilities all dropped, no-new-privileges, constrained processes/CPU/RAM, ephemeral tmpfs for APT indexes/cache and a single **read-only bind mount** of the exact candidate `.deb`. It does NOT mount the Docker socket, source repo, SSH keys, home directories or Agent. APT indexes are retrieved into **container-only tmpfs** over the Ubuntu mirror network; the target Debian is never installed.

The container verifies `/etc/os-release` says `ubuntu:24.04` and dpkg arch `amd64`, captures the initial installed package count, refuses preinstalled WebKitGTK and GTK native libraries or Lanveriq, and hashes the mounted `.deb` to match host receipt. It then executes **official `apt-get -s -o Debug::NoLocking=1 -o APT::Install-Recommends=false install /candidate.deb`**. The validator rejects missing target Inst/Conf actions, duplicate identity markers, removal/purge requests, foreign architecture/OS, unexpected packages, changed byte hashes and Agent/service names in the plan. A temporary unsigned JSON record carries OCI digest, package SHA, source/run/attempt, upstream ABI receipt SHA, captured APT simulation log SHA and planned install count; verification recomputes all evidence without executing a second simulation.

**Trust limitation:** `ubuntu:24.04` is resolved to a runtime digest and recorded, **not hard-pinned in source code to a separately preapproved publisher signature**. This is a reproducible-per-run input record, not signed supply-chain provenance. It uses publicly available Ubuntu24 package repositories at run time. The clean rootfs is a Docker *container*, not a freshly installed full desktop or virtual machine. APT success is a solver simulation, not evidence that the app starts, that the system meets ABI/Wayland/runtime plugin requirements, or that actual Pop!_OS 24.04 repositories include the same candidate versions. It cannot constitute official publisher signature or license approval.

## Evidence states

- **L1 PASS:** exact feature SHA [a13ac215](https://github.com/kunlong-luo/lanveriq/commit/a13ac2158dcb5153b2c945dece86a457e6cc4059), [synthetic OCI plan/manifest proof CI #45303283606 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45303283606): 16 new negative/positive tests, no actual Docker or native candidate. This is not an installation test.
- **Historical native attempt FAIL:** [initial source SHA a13ac215 #45303283595](https://github.com/kunlong-luo/lanveriq/actions/runs/45303283595): full candidate Debian and direct ABI checks reached the clean Docker APT stage but Ubuntu APT `_apt` credential switching was blocked by all capabilities dropped. See correction below. **Final L2 result is accepted on corrected SHA 11dd7762, [#45303829710 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45303829710), with a clean Ubuntu simulation only.**

## First native failure and scoped CI-only correction

- [Initial native run #45303283595 FAILED](https://github.com/kunlong-luo/lanveriq/actions/runs/45303283595), source SHA [a13ac215](https://github.com/kunlong-luo/lanveriq/commit/a13ac2158dcb5153b2c945dece86a457e6cc4059). Existing native Debian build/source+payload Syft/ELF/direct-ABI 10-of-10 verification **passed**, but APT network index refresh inside an OCI container with every Linux capability dropped failed to switch credentials to its `_apt` sandbox user (`setuid`, `setgid`, `chown`: operation not permitted). This was a **container sandbox constraint**, not evidence of APT package unsatisfiability; the actual clean-root solver never completed.
- Correction [source SHA 11dd7762](https://github.com/kunlong-luo/lanveriq/commit/11dd7762dd59984ca9c45d7b7dbc50c330c90882): keep `--cap-drop=ALL`, `--security-opt=no-new-privileges`, read-only root filesystem, tmpfs APT lists/cache and read-only candidate mount. Only for downloading APT indexes within that disposable container, pass `-o APT::Sandbox::User=root` instead of enabling `CAP_SETUID`, `CAP_SETGID` and `CAP_CHOWN`. This prevents an impossible `_apt` credential transition while maintaining the no-capabilities boundary; it **does not** grant host root, GUI, installer or production networking.
- [Exact new SHA synthetic L1 #45303829608 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45303829608): 16 refusal cases including explicit all-caps-dropped/no-cap-add check. [Same SHA native candidate #45303829710](https://github.com/kunlong-luo/lanveriq/actions/runs/45303829710) is **PENDING**. Do not count previous failed native run as L2 acceptance.

## Final accepted exact-SHA native result (Ubuntu OCI simulation only)

**Functional source SHA:** [11dd7762](https://github.com/kunlong-luo/lanveriq/commit/11dd7762dd59984ca9c45d7b7dbc50c330c90882). [Real native CI #45303829710 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45303829710). Both L1 [#45303829608 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45303829608) and full L2 are bound to the same functional SHA (not the final documentation-only SHA). L2 built and strictly inspected the actual unsigned Tauri GUI-only .deb, passed source/payload Syft SPDX, exact archive/file SHA receipts, readelf direct ABI, 15 SONAME library ownership, installed APT graph, official dpkg-shlibdeps minimum version **zero-gap**, executed the new ephemeral clean Ubuntu container simulation, and finished the prior throwaway key Ed25519 lab signature.

| Recorded actual OCI/apt proof | Result |
| --- | --- |
| Resolved official Ubuntu container manifest (pulled tag resolved once, then launched by digest) | `ubuntu@sha256:562111b88caa79c71391c0c4d3c2114a7d5c35915c20561fd893cb1e07929e6b` |
| Container `/etc/os-release` / dpkg architecture | `ubuntu:24.04` / `amd64` |
| Installed package count BEFORE APT simulation | **92** |
| Preinstalled WebKitGTK/GTK native package names | **none** |
| Planned APT `Inst` actions | **221**, including exactly one Lanveriq GUI-only candidate |
| Planned candidate `Conf` actions | **exactly one** |
| Planned removals/purges | **0** |
| Official Debian directly required ABI gaps | **0** |
| Actual unsigned GUI-only `.deb` SHA256 | `9f4fb50839ab727174d61efd8f70092180fd2baec0c4bc23df6953e534e22fe1` |
| Candidate package installed, signed, uploaded, promoted | **NO** |
| Pop!_OS24 COSMIC physical GUI/session/ABI acceptance | **NOT RUN** |

The L2 validator stored the OCI manifest digest, candidate package SHA256, previous ABI receipt SHA256, source SHA/workflow attempt and a SHA256 of the original read-only APT plan. Its verify invocation compared those outputs without a second networked APT run. The test pulled Ubuntu24 fresh and did not bootstrap a full GNOME/COSMIC OS; the image tag itself was not cryptographically pinned in source against an independently approved publisher root. This is **not** an authorized distribution signature, legal license approval, or proof of real Pop!_OS24 installability.

**G-05/G-07/G-08/G-09 and all production release trust gates remain OPEN; v0.1 NOT RELEASE READY.**

## Pop!_OS 24.04 COSMIC L3 exit criteria (NOT RUN)

An authorized Pop!_OS machine must supply independently captured OS/release and architecture, COSMIC session/version, Wayland display/session, kernel and installed package inventory; its *actual* signed/custodied configured APT repository sources and key policy must be recorded. Use a controlled host or re-creatable VM to verify `libgtk-3-0t64`, `libglib2.0-0t64`, WebKitGTK and other ABI package names/versions and official APT resolver constraints. If and only if the release/legal/installer gates are independently approved, conduct human-authorized least-privilege installation/launch and cancellation, uninstall, rollback and reboot behavior with visible status and no tray/resident icon. Test offline/no network, missing package, insufficient rights, untrusted package, refusal, timeout and partial install. No vendor signing, WORM or legal gate is closed by this OCI test.

**G-05 Pop!_OS L3, G-07 legal, G-08 authorized install/SRE and independent publisher trust remain OPEN; v0.1 NOT RELEASE READY.**

[Current status](../CURRENT_STATUS.md) · [Release gates](../planning/release-gap-register.md) · [Physical hardware acceptance](../testing/linux-desktop-installer-acceptance.md) · [L2B-9 baseline](desktop-tauri-l2b9-debian-depends-reconciliation-2026-11-24.md).
