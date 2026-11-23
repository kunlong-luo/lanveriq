# DESKTOP-L2B-8 — Official Debian dpkg-shlibdeps ABI minimum vs actual Tauri GUI .deb Depends

**SPEC · 2026-11-23 · feature SHA 8a7a3030 · synthetic L1 PASS / native L2 PENDING · UNSIGNED · v0.1 NOT RELEASE READY**

## Problem statement

L2B-7 succeeded on [native Ubuntu 24.04 CI #45185473034](https://github.com/kunlong-luo/lanveriq/actions/runs/45185473034): 15 ELF DT_NEEDED entries mapping to 10 Ubuntu packages, and a 277-package installed mandatory Depends/PreDepends satisfier overapproximation. However, the actual Tauri GUI-only .deb control declared **only libgtk-3-0 and libwebkit2gtk-4.1-0**; this may not include the minimum package/version metadata required by its other directly referenced shared library symbols. Those earlier successful scans do **not** prove a user could install that .deb from a clean Pop!_OS 24.04 host.

Debian Policy [Shared libraries](https://www.debian.org/doc/debian-policy/ch-sharedlibs.html) calls for using dpkg-shlibdeps for compiled ELF dependency generation. The [official dpkg-shlibdeps manual](https://manpages.debian.org/trixie/dpkg-dev/dpkg-shlibdeps.1.en.html) describes symbols/shlibs-derived minimum versions, -O stdout output, and the importance of not ignoring missing library metadata. The [Tauri 2 Debian bundler guide](https://v2.tauri.app/distribute/debian/) documents its baseline package dependencies but does not replace this Debian-level symbol audit.

## Implementation and refusal policy

The [L2B-8 checker](../../apps/desktop/tools/verify_desktop_deb_shlibdeps_audit.py) re-verifies the existing unsigned L2B-7 APT installed package graph and underlying exact source/run/ELF and Debian archive evidence. It then runs **official /usr/bin/dpkg-shlibdeps -O -e<actual GUI ELF>**, with a small disposable synthetic control file created under a temporary directory. The actual ELF is never executed. The run has no LD_LIBRARY_PATH injection and **does not** use --ignore-missing-info. Failure to resolve ABI metadata is rejected.

Instead of hand-parsing Debian dependency syntax, the checker uses OS-provided `apt_pkg.parse_depends` and `apt_pkg.version_compare` to compare groups of official computed direct shared-library package/version minimums against the actual .deb control Depends. It records: absent directly declared package groups, declarations with insufficient explicit version floors, and both complete raw expressions. Alternatives are retained and cannot be silently promoted to license or installation authority. This deliberately conservative comparison is a *direct packaging metadata review*: missing direct declarations do **not** automatically prove that APT could not install the package transitively, while a zero-gap finding would **not** prove real compatibility or legally approved distribution.

The output is an **unsigned local CI report** tied to Git source SHA, workflow run/attempt and the actual `.deb`/ELF/prior APT evidence SHA256. It must preserve `license_review=OPEN_NOT_APPROVED`, `installability_on_pop_os=NOT_TESTED`, `trusted_release_provenance=NOT_AVAILABLE`, `release_approved=false`, and `install_authorized=false`. The workflow prints counts and the precise official dependency expression, but does not modify production Tauri bundle config, expand the strict GUI-only approved dependency allowlist, execute dpkg install, upload a release artifact, install/start Agent, or grant polkit privileges.

## Evidence

- **Initial red-light:** [L1 #45188567215 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45188567215), source SHA [289afd2a](https://github.com/kunlong-luo/lanveriq/commit/289afd2a516a80c7d69cdf30ca7c9118b473e24b): 18/19 tests passed and one failed solely because the expected exception *text* did not match the already correct fail-closed rejection of short malformed output. Corrected test assertion only, with no relaxation of the validator.
- **L1 PASS:** fixed functional source SHA [8a7a3030](https://github.com/kunlong-luo/lanveriq/commit/8a7a3030381b7236d22490d200cf43f4fb7f3e21), [Ubuntu 24.04 #45188699719 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45188699719), **19 standalone hostile/positive tests**. Checks official output shape, missing/weak version constraints, alternative groups, changed ELF/source identity, forged release authority, symlink binary and Debian expression injection.
- **Native L2 PENDING:** [same feature SHA #45188699329](https://github.com/kunlong-luo/lanveriq/actions/runs/45188699329) must build a real unsigned GUI-only Tauri `.deb`, pass existing strict Debian, two Syft SPDX, ELF, 15→10 library owner and L2B-7 APT 277-package graph checks, then calculate official dpkg-shlibdeps ABI minimum from *that* exact ELF and compare with *that* exact archive. Do not claim L2 PASS until whole run succeeds.

## Actual native symbol resolution finding (first report accepted, workflow failed only at summary)

- Fixed Debian parser source [5d78b128](https://github.com/kunlong-luo/lanveriq/commit/5d78b128f1d2a8317354dfbee7b5f111a8f388b3) passed [real apt_pkg L1 #45189529981 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45189529981): 20 discovered tests including real installed Python APT parser. [Native CI #45189530069 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45189530069) built a real unsigned Tauri .deb, passed source+payload SPDX, Debian byte hashes, ELF, Ubuntu 15-to-10 package owners, read-only APT 277-package closure and **dpkg-shlibdeps report create+reverify**; final CI stdout referenced a nonexistent JSON field (`candidate_dependency_declaration` instead of `direct_package_metadata`) and FAILED. This is a workflow reporting failure, NOT a successful complete native L2 claim.
- **Official generated 10 direct shared-library requirements, exact Ubuntu24 native printout:**

```text
libc6 (>= 2.39), libcairo2 (>= 1.10.0), libdbus-1-3 (>= 1.9.14), libgcc-s1 (>= 4.2), libgdk-pixbuf-2.0-0 (>= 2.36.9), libglib2.0-0t64 (>= 2.66.0), libgtk-3-0t64 (>= 3.21.5), libjavascriptcoregtk-4.1-0, libsoup-3.0-0 (>= 3.0.3), libwebkit2gtk-4.1-0 (>= 2.41.90)
```

- **Actual archived Debian Depends:** `libwebkit2gtk-4.1-0, libgtk-3-0` (2 groups). Strict direct package/ABI-minimum comparison reports **9 missing directly declared groups plus 1 group with insufficient version floor = 10 gaps**. The WebKitGTK group has no minimum version despite the official minimum, and GTK's declared legacy package name differs from the Ubuntu24 `libgtk-3-0t64` direct ABI package name. Do not equate this with a verified failure of `apt install`; transitively satisfied dependencies may exist. Nor does it prove Pop!_OS compatibility.
- **Summary-only correction:** [SHA 1b683551](https://github.com/kunlong-luo/lanveriq/commit/1b6835518a0f47b87da28e2526cc48cf9aee4f94) changes the read-only CI report to print the existing `direct_package_metadata` key. [Fresh exact-SHA native #45190198511](https://github.com/kunlong-luo/lanveriq/actions/runs/45190198511) is pending. The gap evidence and compiler-generated ABI minimum do not authorize any production package changes.

## What still blocks installation or release

After collecting the real discrepancy, review every official computed dependency and its supported Pop!_OS 24.04 package naming/version (including Ubuntu t64 aliases). An explicitly approved dependency plan would then require a narrow Tauri bundle configuration decision plus new CI, clean-host APT/dpkg **simulation** and finally authorized **real Pop!_OS 24.04 COSMIC hardware** install/uninstall, desktop-session and runtime-loader acceptance. Independent publisher identity, verified provenance, signed release/revocation, least-privilege fixed-scope polkit helper, separate consent for Agent install/start/boot, and legal license signoff are all still OPEN. Neither this test nor a generated shlibs:Depends expression can grant approval.

**G-05/G-07/G-08/G-09 OPEN; v0.1 NOT RELEASE READY.**

[Current status](../CURRENT_STATUS.md) · [Release gap register](../planning/release-gap-register.md) · [Physical acceptance](../testing/linux-desktop-installer-acceptance.md) · [L2B-7](desktop-tauri-l2b7-apt-installed-satisfiers-2026-11-23.md).
