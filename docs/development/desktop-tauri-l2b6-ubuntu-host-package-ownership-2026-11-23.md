# DESKTOP-L2B-6 — Ubuntu 24.04 direct ELF library ownership, versions and copyright material

**SPEC / functional SHA f69f4aca / Ubuntu24 synthetic L1 PASS / real native L2 PENDING · 2026-11-23 · NO RELEASE / NO INSTALL · v0.1 NOT RELEASE READY**

## Scope and architectural choice

The preceding [L2B-5](desktop-tauri-l2b5-elf-runtime-dependencies-2026-11-23.md) directly inspected 15 GNU readelf DT_NEEDED entries in a temporary GUI-only Debian candidate. The native audit successfully recorded ELF64/x86-64/PIE, GNU_STACK RW, interpreter, and Debian Depends of libgtk-3-0 and libwebkit2gtk-4.1-0. These two package declarations and the 15 direct SONAMEs are different kinds of evidence; neither is a complete host package or license inventory.

[Ubuntu package metadata collector](../../apps/desktop/tools/verify_desktop_ubuntu_runtime_packages.py) adds a separate read-only map. It takes the previously unsigned L2B-5 runtime report and the exact extracted GUI ELF; compares SHA, Git commit, run/attempt, 15 allowed names and closed authority flags; checks the CI host actually declares Ubuntu 24.04 amd64. It uses **GNU ldconfig -p** to list current host SONAME cache, **dpkg-query --search** on actual library files to get the file owner, and **dpkg-query --show** to read installed package version/status and the raw immediate Depends expression. The glibc loader SONAME is associated only with the already reviewed absolute PT_INTERP file, not with an arbitrary path. Ambiguous/missing locations/ownership, unknown package versions, unexpected symlinks or missing copyright material are rejected rather than guessed.

For each unique owning installed package it reads `/usr/share/doc/<package>/copyright`, bounded to 2 MiB, and stores path and SHA256. This is an input for organization counsel, **not** a validated SPDX license value or a legal approval. Multiple SONAMEs may belong to one Debian package, and one installed package may be referred to by many SONAMEs. The report carries DIRECT_ONLY_NOT_RECURSIVELY_RESOLVED, transitive_runtime_closure=NOT_VERIFIED, pop_os_24_runtime=NOT_TESTED, license_review=OPEN_NOT_APPROVED, signed_provenance=NOT_AVAILABLE, release_approved=false and install_authorized=false. In particular, collecting the owning package's *immediate* Depends string is not the same as resolving recursive dependencies, optional dlopen modules, ABI-specific symbols or all system library versions.

This deliberately relies on mature Debian utilities rather than custom binary/package parsers: [dpkg-query manual](https://man7.org/linux/man-pages/man1/dpkg-query.1.html), [ldconfig manual](https://man7.org/linux/man-pages/man8/ldconfig.8.html), [Ubuntu copyright packaging policy](https://ubuntu.com/project/docs/how-ubuntu-is-made/concepts/debian-directory/). There is no `ldd`, `dpkg --install`, `apt install` for candidate artifacts, host service execution, root GUI, network collection or privileged installer. Only the pre-existing CI setup installs build prerequisites.

## Verification evidence and strict distinction

- **Synthetic L1 PASS, exact functional SHA [f69f4aca](https://github.com/kunlong-luo/lanveriq/commit/f69f4acac88cdecb037af388c4b216f3b07fb583)**: [Ubuntu 24.04 CI #45175109029 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45175109029), 16 new unit tests. These cover cache filtering, library ambiguity, path safety, invalid package IDs/versions, source SHA/run replay, forged approval and partial SONAMEs. L1 uses synthetic cache/metadata, not a real Tauri artifact.
- **Full native L2 PENDING**: [same functional SHA #45175109149](https://github.com/kunlong-luo/lanveriq/actions/runs/45175109149), requiring the actual Tauri `.deb`/source and payload SPDX, source receipt, every payload SHA, L2B-5 runtime evidence create/verify, then a full 15 SONAME-to-installed-package map and copyright SHA create/verify. Do not claim acceptance before the entire step and run complete.
- **Physical Pop!_OS L3 and legal review NOT RUN**: independent Pop!_OS 24.04 COSMIC host must repeat host ownership and version mapping, ABI/symbol resolution, transitive dependencies, optional plugin loads, licenses, update/rollback, actual limited-permission installer and service lifecycle after independent authorization. Ubuntu CI versions are specific to the disposable Ubuntu runner and must not be copied as Pop!_OS versions.

## Gate decision

**G-05 Desktop and G-07 legal licenses stay OPEN**. G-02 WORM/G-08 SRE/G-09 UI and all other production trust gates remain unchanged. There is no authorized production signer, Sigstore proof, binary release upload, polkit installer or one-click Agent `.deb`. **v0.1 NOT RELEASE READY**.

[Current status](../CURRENT_STATUS.md) · [Release gaps](../planning/release-gap-register.md) · [Pop!_OS hardware acceptance](../testing/linux-desktop-installer-acceptance.md).
