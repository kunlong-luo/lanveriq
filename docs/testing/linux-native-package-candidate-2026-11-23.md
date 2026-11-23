# G-05-LNX2 — Native offline Debian package candidate and removal rehearsal

**ACTIVE SOURCE PROTOTYPE · 2026-11-23 · Ubuntu24 isolated CI ONLY · NOT APPROVED for installation · REAL Pop!_OS L3 NOT RUN · v0.1 NOT RELEASE READY**

## Problem and scope

The existing Linux-first systemd source template used an ExecStart path under /usr/local/bin. That is appropriate for a **manual local administrator build** but **not** for a native .deb: Debian policy reserves /usr/local for the system administrator, and packages must place their executables in package-managed locations. This phase aligns the *candidate native package* with /usr/bin/lanveriq-agent and keeps a single source-controlled systemd idle-only service template. It introduces **no real host installer or service start**.

Primary Debian Policy: https://www.debian.org/doc/debian-policy/ch-opersys.html. systemd StateDirectory/DynamicUser reference: https://www.freedesktop.org/software/systemd/man/latest/systemd.exec.html.

## Implementation and exact boundary

- [Builder](../../apps/agent/tools/linux/build-offline-agent-deb.sh): uses mature dpkg-deb --build --root-owner-group on an existing **locked Cargo release build**, and only if the input is the repository's actual target/release/lanveriq-agent ELF path. Creates an unapproved **lanveriq-agent-offline-preview 0.1.0~dev1 amd64** candidate within a caller-provided existing output directory; refuses symlink inputs/outputs, non-ELF, wrong architecture and output overwrite. No network, no root required, no service control.
- Payload is exactly **usr/bin/lanveriq-agent** (0755, root:root) and **usr/lib/systemd/system/lanveriq-agent.service** (0644, root:root), plus only directory entries. The systemd source template retains PrivateNetwork=yes, IPAddressDeny=any, AF_UNIX only, DynamicUser=yes, zero Linux capabilities and a ConditionPathExists for an operator-owned /etc/lanveriq-agent/agent.toml.
- The .deb must contain **no** configuration/credentials, conffiles, maintainer postinst/prerm/preinst/postrm scripts, triggers, environment hooks or automatic enable/start calls. The package does not create /etc/lanveriq-agent or provision runtime identity, meaning normal service startup remains condition-blocked even if another actor tries to activate the unit without approved config.
- [Archive validator/rehearsal](../../apps/agent/tools/linux/verify-offline-agent-deb.py) uses dpkg-deb metadata, control.tar and data.tar, checks an exact file/dir allowlist, root ownership, modes and original source unit; refuses special file types/symlinks, malformed/missing data, extra hooks, bad architecture and non-ELF payload. It only calls dpkg-deb --extract into a TemporaryDirectory and checks operator **synthetic** config and mock persistent state remain after second extraction and staged removal of the two package-owned files.
- [Mutation negatives](../../apps/agent/tools/linux/test_verify_offline_agent_deb.py) exercise disposable fake ELF package-shape fixtures (not real Agent binaries), malicious postinst/config/symlink/file/permissive modes/other unit/old identity/unsafe /usr/local, and ensure the builder cannot accept an arbitrary executable. The **real package pipeline** must separately build the locked **Rust Agent release** and validate the generated artifact.

## Explicit limits

**This is not an installer, not an approved Debian release package and not a real dpkg upgrade or purge.** The rehearsal does not call apt, dpkg --install, systemctl, service manager, TUF/cosign signature, repository metadata or any real host path. Since no Debian maintainer scripts exist, no automated service enable/start is provided, but actual dependency triggers/administrator policy/systemd presets and previously installed units remain outside this experiment. Do not publish the unapproved package as a release artifact. No real employee/endpoint data, FIDO, CA, WORM or network functionality.

The path change can break a **historical manually installed** /usr/local/bin configuration. There is **no automatic migration**; a future signed and authorized package installer must check and explicitly resolve old locally managed binaries, real dpkg maintainer policies and host-specific drop-ins instead of replacing/removing files owned by administrators. Do not delete /var/lib/private/lanveriq-agent state or /etc/lanveriq-agent config on upgrade/removal without a separately approved retention/consent procedure. systemd DynamicUser may apply id-mapped mounts and protect /var/lib/private; a simulated directory is not real host custody.

## CI and L3 gate

[Ubuntu24 isolated Linux custody CI](../../.github/workflows/agent-linux-popos24-custody.yml) must compile the actual locked Cargo release, call builder and archive validator, and run the synthetic negative suite; existing transient systemd and CSR/DAC checks remain. CI must record *exact functional SHA and run ID* only after completion. Other workflows (Rust fmt/test/Clippy, RD-2 default deny, docs, Next.js) are separate evidence.

Actual Pop!_OS 24.04 L3 still requires written permission, physical/VM OS identity and COSMIC, real dpkg install/remove/upgrade/rollback/boot on authorized isolated device, systemctl cat and effective systemctl show/drop-ins, root vs DynamicUser private state/keys and opt-in consent. [Issue #7](https://github.com/kunlong-luo/lanveriq/issues/7) remains OPEN. Independent WORM [Issue #6](https://github.com/kunlong-luo/lanveriq/issues/6) remains OPEN; G-02/G-05/G-08 and all other production gates are NOT RELEASE READY. No production networking, collection, auto-recovery, remote, CA or live FIDO authorization.

## Exact-SHA software acceptance evidence (2026-11-23)

**Functional SHA [ccf56b3078f0dfe34de04730fbd874780d668feb](https://github.com/kunlong-luo/lanveriq/commit/ccf56b3078f0dfe34de04730fbd874780d668feb): 5/5 actually triggered GitHub workflows SUCCESS.** [Ubuntu24 Linux systemd/CSR/DPKG synthetic L2 #45116447766](https://github.com/kunlong-luo/lanveriq/actions/runs/45116447766) actually reported Ran 16 tests for systemd, Ran 16 tests for Debian package mutations, compiled actual locked Rust release, built the offline-preview native .deb and validated its exact payload and absence of activation hooks, then successfully completed the disposable root extraction/upgrade/payload-removal rehearsal. Earlier real transient systemd DynamicUser custody and distinct Unix UID key denial still PASS. Candidate archive was stored only temporarily and not published.

Other same source SHA PASS: [Rust fmt/unit/Clippy #45116447809](https://github.com/kunlong-luo/lanveriq/actions/runs/45116447809), [Next.js #45116447646](https://github.com/kunlong-luo/lanveriq/actions/runs/45116447646), [docs #45116447722](https://github.com/kunlong-luo/lanveriq/actions/runs/45116447722), [RD-2 default OFF #45116447683](https://github.com/kunlong-luo/lanveriq/actions/runs/45116447683).

**Historical failures preserved:** initial SHA [2d9649c8](https://github.com/kunlong-luo/lanveriq/commit/2d9649c8503e91776601c298948862ab48f50842) run [#45116207862 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45116207862) with 8 failing Debian package tests; first repair SHA [fe3e4952](https://github.com/kunlong-luo/lanveriq/commit/fe3e495228db6316899cd55b6af2ce1868cc95ad) run [#45116371922 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45116371922) with 9 failures due to the legal tar root-dot member. Final current SHA has actual successful tests and does not retroactively change those red results.

**Limitation:** this is Ubuntu24 compilation, package creation/extraction and temporary-root rehearsal, not true dpkg install/purge or authorized Pop!_OS24 COSMIC L3. G-02/G-05/G-08 OPEN; v0.1 NOT RELEASE READY.
