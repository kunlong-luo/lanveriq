# DESKTOP-L2B-9 — Ubuntu24 Debian ABI Depends candidate repair

**SPEC · 2026-11-24 · SOURCE SHA e425616a · native L2 PASS / 10 official ABI groups covered, 0 gaps · UNSIGNED / NO INSTALL / NOT RELEASE READY**

## Why and how

DESKTOP-L2B-8 [actual Ubuntu24 native audit #45190198511 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45190198511) proved the GUI-only Tauri `.deb` contains only two Debian Depends while `/usr/bin/dpkg-shlibdeps` requires 10 direct symbol ABI package groups: nine were not directly declared and one lacked the required minimum version. The audit workflow itself passed, but the package metadata remained `GAPS_FOUND`. This is NOT proof of a Pop!_OS 24.04 install failure.

Tauri 2 supports `bundle.linux.deb.depends`; see [official Debian distribution guide](https://v2.tauri.app/distribute/debian/) and [Tauri 2 DebConfig schema](https://v2.tauri.app/reference/config/#debconfig). We use that mature API instead of a custom post-processing Debian package editor. The checked-in GUI-only config **still has `bundle.active=false`, `targets=[]` and no maintainer hooks or extra files**, so this does not create an official installable release. The Ubuntu24 CI alone temporarily enables the CLI-only `deb` target, retains the existing archive review, source/payload Syft, fixed ELF loader/15-SONAME audit, ownership and APT graph chain, and computes `dpkg-shlibdeps` again from the actual resulting ELF. It now asserts `direct_declaration_gap_count == 0` and `NO_GAP_DETECTED_UNAPPROVED` before accepting the *direct declaration* subtest.

### Pinned Ubuntu 24.04 CI baseline (not approved for all Pop!_OS systems)

```text
libc6 (>= 2.39), libcairo2 (>= 1.10.0), libdbus-1-3 (>= 1.9.14), libgcc-s1 (>= 4.2), libgdk-pixbuf-2.0-0 (>= 2.36.9), libglib2.0-0t64 (>= 2.66.0), libgtk-3-0t64 (>= 3.21.5), libjavascriptcoregtk-4.1-0, libsoup-3.0-0 (>= 3.0.3), libwebkit2gtk-4.1-0 (>= 2.41.90)
```

These are from the L2B-8 exact native audit, not guessed. `libgtk-3-0t64` and `libglib2.0-0t64` are Ubuntu24 package names, and compatibility with Pop!_OS release repositories, virtual Provides and ABI must be proved separately. The strict GUI-only archive parser allowlist has been extended *only* with the six package names known from that actual ABI baseline; it still rejects maintainer scripts, Agent, services, privilege grants and arbitrary dependencies.

The CI additionally invokes `apt-get -s -o Debug::NoLocking=1 install <absolute-path-to-unsigned-deb>` on the already provisioned disposable Ubuntu24 runner. **This is a read-only simulation in a build host with installed development libraries, not a clean-host proof, not a real installation and not a substitute for an independently authorized Pop!_OS 24.04 COSMIC physical L3.** The candidate .deb is never uploaded, installed, published or signed by a trusted release identity.

## Evidence status

- First functional SHA [a8783d1c](https://github.com/kunlong-luo/lanveriq/commit/a8783d1c1d100ce1c7a5da0ce5a8d487b177fe3a), [native CI #45301458349](https://github.com/kunlong-luo/lanveriq/actions/runs/45301458349) and [Tauri L1 #45301458456](https://github.com/kunlong-luo/lanveriq/actions/runs/45301458456): **FAIL** before native build because existing unit fixtures expected the icon's JSON syntax to stay on one line. It was a fixture-layout mismatch, not an ABI safety-bypass; no permissions were enabled.
- Exact functional correction [e425616a](https://github.com/kunlong-luo/lanveriq/commit/e425616a69775f4d165043d3ca54208719c6339e) preserves icon formatting, same dependency policy and all other release locks. [Tauri L1 #45301534178](https://github.com/kunlong-luo/lanveriq/actions/runs/45301534178) passed source reject constraints, and [native `.deb` #45301534228 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45301534228) completed exact-SHA real ABI and simulation acceptance. In particular, do not claim `GAPS_FOUND` is resolved without inspecting the generated .deb control and the recomputed official ABI relation groups.

## Remaining release gates

G-05 still requires a **clean, controlled Pop!_OS 24.04** APT simulation (not merely an already-provisioned CI runner), real COSMIC desktop/Wayland session and runtime load, independent package provenance and approved native signer; G-07 requires actual third-party license and redistribution signoff; G-08 needs authorized least-privilege polkit operations, install/uninstall/start/enable consent, rollback and SRE acceptance. G-02 WORM and other production security gates are unchanged. No production Agent deployment, data capture, networking, remote control or certificate issuance are enabled.

**v0.1 NOT RELEASE READY.**

[Current status](../CURRENT_STATUS.md) · [Release gaps](../planning/release-gap-register.md) · [Pop!_OS installer acceptance](../testing/linux-desktop-installer-acceptance.md).

## Accepted real Ubuntu 24.04 native L2 results

**Functional SHA:** [e425616a](https://github.com/kunlong-luo/lanveriq/commit/e425616a69775f4d165043d3ca54208719c6339e). [Native workflow #45301534228 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45301534228). This is an actual Tauri GUI-only unsigned Debian bundle in a disposable Ubuntu24 CI; source `bundle.active=false`, no Agent, no installed, uploaded or distributed `.deb`, no official signing key. Actual archive SHA256: `74fc9f9c2fabf6c267a4ff8b23ae9599d6221167bbdab4c7371b9e806155919e`.

| Verified native observation | Actual value |
| --- | --- |
| Official GNU/DPKG `dpkg-shlibdeps` ELF minimum requirement groups | **10** |
| Final `.deb` control Depends groups | **12** |
| Missing directly declared groups | **0** |
| Declared but insufficient minimum-version groups | **0** |
| Direct declaration gaps | **0** |
| Result schema status | `NO_GAP_DETECTED_UNAPPROVED` |
| `apt-get -s -o Debug::NoLocking=1 install <absolute .deb>` on populated Ubuntu24 builder | **PASS** (simulation only) |
| Actual Pop!_OS24 COSMIC clean-host install, upgrade, removal and launch | **NOT RUN** |

The Tauri bundler includes its legacy default `libwebkit2gtk-4.1-0` and `libgtk-3-0` entries as well as the ten explicit reviewed requirements. Their presence explains **12** declared groups instead of 10. The official direct ABI minimum/apt_pkg version comparison is zero-gap; this should **not** be confused with APT repository policy, legacy/t64 virtual package behavior, license or clean-host installation correctness. The read-only simulation ran against an already provisioned CI host where Tauri build dependencies were installed; it is not an empty-system test.

The same SHA passed actual Debian archive shape, both pinned Syft SPDX jobs, source/payload byte digests, approved ELF loader and direct SONAME audit, actual host package owner and copyright digests, read-only APT dependency metadata, and ephemeral CI-only signature lab. **This proves only an isolated candidate dependency metadata subcheck.** G-05/G-07/G-08/G-09 remain OPEN, actual Pop!_OS 24.04 and official trusted publisher/provenance remain absent.

## Next technical and organizational exit

Create a truly clean and explicit Pop!_OS24 package repository/apt solver input, verify that the pinned `-t64` package names and minimum versions resolve, then obtain an approved COSMIC/Wayland real-machine install/uninstall/user-consent session and security/legal review. Do not enable privileged Agent installation merely because Ubuntu24's `apt-get -s` returned success.
