# DESKTOP-L2B-7 — read-only APT installed mandatory dependency satisfiers

**SPEC · 2026-11-23 · exact-SHA synthetic L1 + actual native L2 PASS · LICENSE OPEN · v0.1 NOT RELEASE READY**

## Why this follows L2B-6

[L2B-6](desktop-tauri-l2b6-ubuntu-host-package-ownership-2026-11-23.md) tied the GUI-only Tauri ELF's 15 direct DT_NEEDED libraries to 10 actual installed Ubuntu 24.04 amd64 Debian packages and hashed each package's copyright material. This does not resolve their mandatory dependencies. Debian syntax supports complex version constraints, OR alternatives and virtual packages; writing our own parser is not acceptable.

Instead L2B-7 reuses the Ubuntu-maintained **python3-apt** API ([upstream documentation](https://apt-team.pages.debian.net/python-apt/library/apt.package.html)): apt.Cache(), the installed package version's get_dependencies('PreDepends', 'Depends'), and each alternative's installed_target_versions. This does **not** execute the GUI/Agent or install, update, mark, or remove packages. The [verifier](../../apps/desktop/tools/verify_desktop_apt_installed_closure.py) first independently revalidates the L2B-6 owner report against its L2B-5 runtime report, GUI ELF, source SHA and CI attempt. All mandatory dependency groups must have at least one installed satisfier; otherwise reject. If more than one satisfying alternative is installed, retain **every** installed satisfier and count the ambiguous group rather than guessing which alternative is chosen.

The bounded graph traverses at most 1,024 installed package nodes and 8,192 mandatory dependency OR groups; every node is versioned. A copyright SHA256 is captured for each available Debian notice, and missing notice count remains open for legal review. The graph, digest, source SHA and previous evidence digest are recorded in an unsigned JSON receipt and recomputed during verification. The complete graph is **an overapproximation** of installed APT-mandatory satisfiers, not a definitive APT transaction, actual dlopen/module list, installed Pop!_OS package closure, or proof of license compliance.

The [independent L1](../../.github/workflows/desktop-apt-closure.yml) executes real system python3-apt read-only cache access and 14 synthetic acceptance/rejection cases. The [native workflow](https://github.com/kunlong-luo/lanveriq/blob/d04d3e01ca97afd0c4a2ae31d5df19f5791c5e09/.github/workflows/desktop-deb-candidate.yml) runs the graph only AFTER building and auditing the real unsigned GUI-only Debian candidate, source and payload Syft SPDX, original source receipts, ELF direct-ABI checks and L2B-6 installed package ownership. Results are deliberately kept local to the disposable CI runner, never uploaded or distributed.

## Exact-SHA acceptance

- **L1 PASS:** functional [1c080cd8](https://github.com/kunlong-luo/lanveriq/commit/1c080cd8e5f6c3fc0e77e357f5e92fda6038b0af), [Ubuntu 24.04 #45185473078 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45185473078). Real /usr/bin/python3 imports APT's installed cache; 14 unit cases pass: no satisfier, multiple alternatives, wrong installed version, source/run manipulation, invalid IDs and absent copyright material.
- **Native L2 PASS:** same functional SHA [#45185473034 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45185473034) actually completed all GUI-only Debian/Syft/readelf/owner graph checks, emitted measured package/OR-group/ambiguity/copyright counts, reverified the APT report, and completed prior experimental detached Ed25519 lab-signature refusal chain. This is **only** untrusted Ubuntu24 runtime metadata.
- **L3 / legal NOT RUN:** Pop!_OS 24.04 COSMIC physical session, true runtime dlopen/plugins and symbol/ABI resolution, rolling updates and downgrade behavior, copyright/license obligations and independent approvals, signed publisher identity and independent recipient provenance remain open. A working Ubuntu CI cache is not a Pop!_OS machine.

**G-05/G-07/G-08/G-09 OPEN; v0.1 NOT RELEASE READY.**

[Current status](../CURRENT_STATUS.md) · [Release gaps](../planning/release-gap-register.md) · [Linux physical acceptance](../testing/linux-desktop-installer-acceptance.md).

## Actual native Ubuntu 24.04 source-locked results

- **Feature SHA:** [1c080cd8](https://github.com/kunlong-luo/lanveriq/commit/1c080cd8e5f6c3fc0e77e357f5e92fda6038b0af), [native run #45185473034 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45185473034). This actually built and audited GUI-only unsigned lanveriq-desktop_0.1.0_amd64.deb. SHA256: `fbaa776e06dd45d3bdf51a51031dea4615a99be5d071b36608a78bfb64337774`.
- **Collected APT mandatory installed satisfiers:** 277 distinct installed Ubuntu24 packages in the union starting from the 10 L2B-6 direct SONAME owning packages; **950** package-level Depends/PreDepends OR groups; **1** group where more than one installed alternative satisfies the requirement (not silently chosen), and **0** missing copyright notice materials. This is not the number of actually loaded ELF shared libraries and not a unique APT transaction solution.
- **Graph SHA256:** `ec67375f062c20f992391f5c177122ef30e672cf024ef8be8bb6a5796b15559b`. Native L2B-6 owner report links to previous ELF and bundle evidence; this graph creation and independent verification both PASS. No signed release artifacts, no evidence uploads, no install operations were performed.
- **Other previous native steps on same run:** strict Debian archive and payload SHA checks, pinned Syft source and payload SPDX, untrusted receipt rechecks, GNU readelf 15 direct SONAME/loader restrictions, Ubuntu actual 15-to-10 installed package ownership and copyright input checks, ephemeral laboratory Ed25519 receipt signature. Existing test suites passed on that functional SHA; imported Debian archive test cases overlap and must not be double-counted.
- **Non-proof:** The graph can overapproximate a chosen alternative package and excludes optional/late-loaded plugins, symbol resolution, sandbox permissions, dynamic libraries on a real Pop!_OS24 COSMIC install and security/license approval. 0 missing copyright files means only that files were present, **not** that redistribution obligations have been reviewed. Actual organization legal approval and G-05/G-07 remain OPEN.

## Proposed L2B-8 follow-on

Before proposing a signed or authorized install, establish a **real Pop!_OS 24.04** evidence session, package pinning and ABI/symbol compatibility, runtime loading/dlopen and accessibility behavior under COSMIC; independently review and approve license notices for the actual distribution and dependency chain. Do not pretend Ubuntu CI provides those approvals. No official one-click Agent installation is ready.

**v0.1 NOT RELEASE READY.**
