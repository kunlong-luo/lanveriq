# DESKTOP-L2B-11 — Pop!_OS 24.04/COSMIC local read-only compatibility preflight

**SPEC · ACTIVE 2026-11-24 · functional SHA 0c28a741 · Ubuntu synthetic L1 PASS / actual Pop!_OS L3 NOT RUN · v0.1 NOT RELEASE READY**

## Why this milestone exists

Previously [L2B-10](desktop-tauri-l2b10-clean-ubuntu-oci-apt-2026-11-24.md) proved a clean official Ubuntu24 OCI root filesystem can *simulate* installing a real, unsigned GUI-only `.deb` with 221 APT Inst steps and zero removals; **not a Pop!_OS host, not a COSMIC session and not a real install**. System76 confirms Pop!_OS 24.04 ships its own [COSMIC desktop](https://system76.com/support/desktop-environment). The [COSMIC session starter](https://github.com/pop-os/cosmic-session/blob/master/data/start-cosmic) defaults `XDG_SESSION_DESKTOP=COSMIC` and `XDG_SESSION_TYPE=wayland`, but environment variables can be spoofed and do not prove a running compositor.

The read-only [host preflight](../../apps/desktop/tools/verify_desktop_popos_local_preflight.py) is an internal engineering capability for later **local operator-authorized GUI integration**, not a production installer, remotely callable Agent API, or a release artifact. It invokes *no shell*, network requests, APT update, APT/dpkg installation, package download, systemd action, privileged helper, screenshot or hidden monitoring. It requires a non-root process; a report is written with mode `0600` **only to an explicit, previously absent local output path**, never auto-uploaded. No user/hostnames, home directories, hardware IDs, Wayland socket paths, repository URIs, login sessions or software signing key material are copied into the report.

## Fail-closed read-only gates

1. Exact `/etc/os-release` **ID=pop / VERSION_ID=24.04** and `dpkg --print-architecture=amd64`; Ubuntu/22.04/unknown arch are rejected **before opening APT cache or writing an output file**. Detection uses local OS metadata only; it cannot establish physical origin.
2. `XDG_CURRENT_DESKTOP` contains COSMIC, `XDG_SESSION_TYPE=wayland`, and nonempty syntactically safe `WAYLAND_DISPLAY`. **Only an untrusted session *environment claim***; does not probe Wayland socket, launch the app, confirm the compositor or grant a production session.
3. Source-only Tauri `bundle.active=false`, `targets=[]`, exact L2B-9 reviewed **10** `bundle.linux.deb.depends` ABI minima. Refuse any unreviewed Debian hook, file or bundle metadata key. This binds the *source config contract* but is **not** a verification of a signed binary package.
4. Reuse maintained OS `python3-apt` `apt.Cache()` and `apt_pkg.parse_depends/version_compare` to check **cached candidate** availability and minimum version for each of the 10 direct ABI packages, including `libgtk-3-0t64` and `libglib2.0-0t64`. No private Debian version parser. Require `cosmic-session`, `cosmic-comp`, `xdg-desktop-portal-cosmic` to be actually installed; record their installed versions. Missing/stale APT metadata is rejected, not refreshed or guessed. **This is not an APT installation solver or a runtime loader test.**
5. Read bounded `sources.list`, `sources.list.d/*.list|*.sources` metadata (the OS supports deb822 sources) and record only a SHA-256 fingerprint plus count. Symlinks and oversized sources are rejected. **Fingerprints do not authenticate APT repository keys/signatures**, and empty or stale indexes are not magically valid.
6. Every successful record retains `physical_l3=NOT_APPROVED`, `gui_actual_launch=NOT_TESTED`, `popos_install_solver=NOT_RUN`, `signed_package_provenance=NOT_VERIFIED`, `license_approval=OPEN`, `release_approved=false`, `install_authorized=false`, `agent_start_authorized=false`. No change to production networking, remote control, data collection, PKI or WORM gates.

## Verified evidence: exact SHA, real environment and non-proof

- **Feature SHA:** [0c28a741](https://github.com/kunlong-luo/lanveriq/commit/0c28a7414d9c1fd82d8e53a6a35689519db75bef).
- **Actual Ubuntu24 L1 #45306555242 SUCCESS:** [CI run](https://github.com/kunlong-luo/lanveriq/actions/runs/45306555242) executed **20** synthetic positive/hostile cases for OS identity, COSMIC/Wayland claim, direct ABI candidate min versions, missing native COSMIC packages, private APT source digest, malicious symlink, privileged launch refusal, forged approval and unexpected Debian installer hooks. The same CI then ran the *actual* checker on its **Ubuntu 24.04 runner**, verified explicit `NOT_POP_OS_TARGET` exit code 2 and verified **no report file was created**. This proves a truthful negative on Ubuntu, not a physical Pop!_OS pass.
- **Prior pre-hardening SHA [bc9c8286](https://github.com/kunlong-luo/lanveriq/commit/bc9c8286135c029554429f7d825d04ea3f05cead), [L1 #45306368101 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45306368101):** 19 tests; one more strict Debian installer hook rejection was added at the final SHA. This is preserved as history, not misrepresented as the final SHA's feature evidence.

## What still needs genuine Pop!_OS 24.04/COSMIC L3

An authorized local Pop!_OS24 machine or approved physical test runner must independently verify installed OS release/package provenance, the real Wayland/COSMIC compositor session, `libwebkit2gtk-4.1-0` ABI and GStreamer/plugin dynamic-load conditions, clipboard/display permissions, GUI resize/focus/keyboard accessibility and actual launch/exit. Bind reviewed **signed** package bytes and exact approved APT repositories (not just unsigned source-config hashes), and prove clean-host APT **simulation** before any authorized real install. Then exercise least-privilege polkit approval/cancellation, user-visible non-root Agent start/stop, no-resident-tray background service, reboot/upgrade/uninstall/rollback and tampered signature refusal. These are distinct from synthetic Ubuntu CI and **NOT RUN**. Integrating preflight into the non-privileged Tauri GUI so operators need no commands is a future step, not already delivered.

**G-05/G-07/G-08/G-09 OPEN; FIDO/WORM/CA and other production gates unchanged. v0.1 NOT RELEASE READY.**

[Current status](../CURRENT_STATUS.md) · [Release gaps](../planning/release-gap-register.md) · [Pop!_OS L3 acceptance](../testing/linux-desktop-installer-acceptance.md) · [ADR-0013](../architecture/adrs/0013-tauri-desktop-installer-idle-agent.md).
