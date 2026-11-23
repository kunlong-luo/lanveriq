# DESKTOP-L2B-4 — native Debian payload file inventory + partial Syft SPDX

**SPEC / EXACT-SHA NATIVE CI PENDING · 2026-11-23 · UNSIGNED UNTRUSTED · v0.1 NOT RELEASE READY**

## Why this stage is needed

L2B-1/2 used pinned Syft v1.54.0 to generate an SPDX 2.3 dependency catalog from the desktop SOURCE repository, with 608 discovered packages in the accepted L2B-2 native run. This does not establish what the built Debian software package physically includes. L2B-3 established offline verified-attestation intake DENIAL tests but no trusted signer or evidence. This stage makes the source/payload distinction explicit.

Anchore [Syft filesystem scanning](https://oss.anchore.com/docs/guides/sbom/getting-started/) and [binary identification](https://oss.anchore.com/docs/capabilities/binary/) can discover packages within a directory or binary. Syft scans do not guarantee complete runtime dependency disclosure, especially for system libraries not physically included in the packaged files.

The new [payload evidence validator](../../apps/desktop/tools/verify_desktop_payload_evidence.py) first re-runs the existing strict Debian archive audit and re-verifies the L2B-1 untrusted receipt against the exact original Debian .deb, source SPDX and committed Cargo.lock/pnpm-lock/Tauri config, SHA/run/attempt. It separately reads the actual Debian data tar and hashes EVERY delivered regular file, then compares sizes, modes, symlinks, directory allowlists and the SHA-256 hashes against **an independent, uninstalled temporary extraction root**. It rejects same-length ELF substitution (not just length changes), added/missing files, symlinked payload, altered permissions, receipt tampering and forged install/signature claims.

The [native CI workflow](../../.github/workflows/desktop-deb-candidate.yml) scans only the pre-audited GUI-only Debian archive, extracting to runner.temp but **never installing or executing it**. It creates an additional payload-only SPDX 2.3 JSON by pinned Anchore Syft action v0.24.3 / Syft v1.54.0. Uploads, dependency-snapshot submission and release assets are all disabled. The local unsigned JSON payload evidence includes: exact candidate .deb SHA256, original source receipt hash, source SPDX hash, partial payload SPDX hash, each delivered regular file path/mode/size/SHA256, source commit SHA and workflow run/attempt. The create/verify actions recheck all fields; a passing tool never changes install_authorized=false, release_approved=false or signature=UNSIGNED_UNTRUSTED.

A standalone [fast synthetic CI](../../.github/workflows/desktop-payload-inventory.yml) runs the attack-oriented Python fixtures separately from the slow native Tauri build. The test fixture uses dpkg-deb build and extract with synthetic ELF/launcher/PNG, and imports the existing Debian archive tests as well as the new L2B-4 tests.

## Limits and non-proof

A delivered Debian file inventory is **not a complete binary/runtime SBOM**. A stripped Rust executable may yield few or even zero detected Syft packages. GTK/WebKitGTK/GLib/JSC, OpenSSL and other shared libraries dynamically resolved by Pop!_OS are not all shipped inside this GUI-only Debian payload. Approved Debian Depends fields are only declared requirements, not verified runtime installation. This stage does not make software reproducible, attest a trusted builder, verify official signing identity, collect license clearance or audit runtime shared-library versions on a genuine Pop!_OS 24.04 machine.

The native artifact is still GUI-only, unsigned, neither downloadable nor installed, without Agent, systemd unit, polkit install helper or production network/data/remote-control authorization. No Syft or receipt success changes release readiness.

## Acceptance

- L2B-4 synthetic fast L1: **PENDING** actual exact-SHA GitHub CI.
- L2B-4 native L2: **PENDING** Ubuntu 24.04 real Tauri release Debian, strict archive, source SPDX/receipt, extracted payload Syft SPDX, exact byte-to-byte file hashes, payload receipt create + reverify, experimental Ed25519 lab (prior stage). All need the same functional source SHA and actual SUCCESS.
- Next unresolved prerequisites: independent approved release signer and root custody/verified recipient build provenance (L2B-3), dependency/license closure including external runtime libraries and actual binary SBOM limitations, signed and version-revoked release, separately consented fixed-scope polkit operations, real Pop!_OS 24.04/COSMIC device installation and uninstall L3.

**G-05/G-07/G-08/G-09 OPEN; v0.1 NOT RELEASE READY.**

[Current status](../CURRENT_STATUS.md) · [Release gaps](../planning/release-gap-register.md) · [Desktop hardware acceptance](../testing/linux-desktop-installer-acceptance.md).
