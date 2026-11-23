# DESKTOP-L2B-7 — read-only APT installed mandatory dependency satisfiers

**SPEC · 2026-11-23 · exact-SHA L1 PASS / native L2 PENDING · LICENSE OPEN · v0.1 NOT RELEASE READY**

## Why this follows L2B-6

[L2B-6](desktop-tauri-l2b6-ubuntu-host-package-ownership-2026-11-23.md) tied the GUI-only Tauri ELF's 15 direct DT_NEEDED libraries to 10 actual installed Ubuntu 24.04 amd64 Debian packages and hashed each package's copyright material. This does not resolve their mandatory dependencies. Debian syntax supports complex version constraints, OR alternatives and virtual packages; writing our own parser is not acceptable.

Instead L2B-7 reuses the Ubuntu-maintained **python3-apt** API ([upstream documentation](https://apt-team.pages.debian.net/python-apt/library/apt.package.html)): apt.Cache(), the installed package version's get_dependencies('PreDepends', 'Depends'), and each alternative's installed_target_versions. This does **not** execute the GUI/Agent or install, update, mark, or remove packages. The [verifier](../../apps/desktop/tools/verify_desktop_apt_installed_closure.py) first independently revalidates the L2B-6 owner report against its L2B-5 runtime report, GUI ELF, source SHA and CI attempt. All mandatory dependency groups must have at least one installed satisfier; otherwise reject. If more than one satisfying alternative is installed, retain **every** installed satisfier and count the ambiguous group rather than guessing which alternative is chosen.

The bounded graph traverses at most 1,024 installed package nodes and 8,192 mandatory dependency OR groups; every node is versioned. A copyright SHA256 is captured for each available Debian notice, and missing notice count remains open for legal review. The graph, digest, source SHA and previous evidence digest are recorded in an unsigned JSON receipt and recomputed during verification. The complete graph is **an overapproximation** of installed APT-mandatory satisfiers, not a definitive APT transaction, actual dlopen/module list, installed Pop!_OS package closure, or proof of license compliance.

The [independent L1](../../.github/workflows/desktop-apt-closure.yml) executes real system python3-apt read-only cache access and 14 synthetic acceptance/rejection cases. The [native workflow](../../.github/workflows/desktop-deb-candidate.yml) runs the graph only AFTER building and auditing the real unsigned GUI-only Debian candidate, source and payload Syft SPDX, original source receipts, ELF direct-ABI checks and L2B-6 installed package ownership. Results are deliberately kept local to the disposable CI runner, never uploaded or distributed.

## Exact-SHA acceptance

- **L1 PASS:** functional [1c080cd8](https://github.com/kunlong-luo/lanveriq/commit/1c080cd8e5f6c3fc0e77e357f5e92fda6038b0af), [Ubuntu 24.04 #45185473078 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45185473078). Real /usr/bin/python3 imports APT's installed cache; 14 unit cases pass: no satisfier, multiple alternatives, wrong installed version, source/run manipulation, invalid IDs and absent copyright material.
- **Native L2 PENDING:** same functional SHA [#45185473034](https://github.com/kunlong-luo/lanveriq/actions/runs/45185473034) must finish whole native .deb/Syft/readelf/owner graph chain and report actual package, OR-group, ambiguous-alternative and copyright-missing counts. Do NOT infer native PASS from isolated L1.
- **L3 / legal NOT RUN:** Pop!_OS 24.04 COSMIC physical session, true runtime dlopen/plugins and symbol/ABI resolution, rolling updates and downgrade behavior, copyright/license obligations and independent approvals, signed publisher identity and independent recipient provenance remain open. A working Ubuntu CI cache is not a Pop!_OS machine.

**G-05/G-07/G-08/G-09 OPEN; v0.1 NOT RELEASE READY.**

[Current status](../CURRENT_STATUS.md) · [Release gaps](../planning/release-gap-register.md) · [Linux physical acceptance](../testing/linux-desktop-installer-acceptance.md).
