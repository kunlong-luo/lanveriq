# DESKTOP-L2B-4 — native Debian payload file inventory + partial Syft SPDX

**SPEC / EXACT-SHA L1 + NATIVE L2 PASS · 2026-11-23 · UNSIGNED UNTRUSTED · v0.1 NOT RELEASE READY**

## Why this stage is needed

L2B-1/2 used pinned Syft v1.54.0 to generate an SPDX 2.3 dependency catalog from the desktop SOURCE repository, with 608 discovered packages in the accepted L2B-2 native run. This does not establish what the built Debian software package physically includes. L2B-3 established offline verified-attestation intake DENIAL tests but no trusted signer or evidence. This stage makes the source/payload distinction explicit.

Anchore [Syft filesystem scanning](https://oss.anchore.com/docs/guides/sbom/getting-started/) and [binary identification](https://oss.anchore.com/docs/capabilities/binary/) can discover packages within a directory or binary. Syft scans do not guarantee complete runtime dependency disclosure, especially for system libraries not physically included in the packaged files.

The new [payload evidence validator](../../apps/desktop/tools/verify_desktop_payload_evidence.py) first re-runs the existing strict Debian archive audit and re-verifies the L2B-1 untrusted receipt against the exact original Debian .deb, source SPDX and committed Cargo.lock/pnpm-lock/Tauri config, SHA/run/attempt. It separately reads the actual Debian data tar and hashes EVERY delivered regular file, then compares sizes, modes, symlinks, directory allowlists and the SHA-256 hashes against **an independent, uninstalled temporary extraction root**. It rejects same-length ELF substitution (not just length changes), added/missing files, symlinked payload, altered permissions, receipt tampering and forged install/signature claims.

The [native CI workflow](https://github.com/kunlong-luo/lanveriq/blob/d04d3e01ca97afd0c4a2ae31d5df19f5791c5e09/.github/workflows/desktop-deb-candidate.yml) scans only the pre-audited GUI-only Debian archive, extracting to runner.temp but **never installing or executing it**. It creates an additional payload-only SPDX 2.3 JSON by pinned Anchore Syft action v0.24.3 / Syft v1.54.0. Uploads, dependency-snapshot submission and release assets are all disabled. The local unsigned JSON payload evidence includes: exact candidate .deb SHA256, original source receipt hash, source SPDX hash, partial payload SPDX hash, each delivered regular file path/mode/size/SHA256, source commit SHA and workflow run/attempt. The create/verify actions recheck all fields; a passing tool never changes install_authorized=false, release_approved=false or signature=UNSIGNED_UNTRUSTED.

A standalone [fast synthetic CI](../../.github/workflows/desktop-payload-inventory.yml) runs the attack-oriented Python fixtures separately from the slow native Tauri build. The test fixture uses dpkg-deb build and extract with synthetic ELF/launcher/PNG, and imports the existing Debian archive tests as well as the new L2B-4 tests.

## Limits and non-proof

A delivered Debian file inventory is **not a complete binary/runtime SBOM**. A stripped Rust executable may yield few or even zero detected Syft packages. GTK/WebKitGTK/GLib/JSC, OpenSSL and other shared libraries dynamically resolved by Pop!_OS are not all shipped inside this GUI-only Debian payload. Approved Debian Depends fields are only declared requirements, not verified runtime installation. This stage does not make software reproducible, attest a trusted builder, verify official signing identity, collect license clearance or audit runtime shared-library versions on a genuine Pop!_OS 24.04 machine.

The native artifact is still GUI-only, unsigned, neither downloadable nor installed, without Agent, systemd unit, polkit install helper or production network/data/remote-control authorization. No Syft or receipt success changes release readiness.

## Acceptance

- L2B-4 synthetic fast L1: **PASS** at exact feature SHA [e107a45b](https://github.com/kunlong-luo/lanveriq/commit/e107a45b7e697b055fbc3a916cae46a1afcf1832), [Ubuntu24 CI #45166773714 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45166773714). 42 unittest-discovered tests: 26 imported existing Debian archive negative tests + **16 newly implemented payload evidence tests**. The suite runs actual dpkg-deb synthetic build/extraction and rejects mismatched byte hashes, sizes, modes, symlinks, extras and forged authority. This is L1 only; payload Syft scanner and native Tauri not run in this fast job.
- L2B-4 native L2: **PASS** [functional SHA e107a45b, real CI #45166773406 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45166773406), Ubuntu 24.04 real Tauri release Debian, strict archive, source SPDX/receipt, extracted payload Syft SPDX, exact byte-to-byte file hashes, payload receipt create + reverify, experimental Ed25519 lab (prior stage). All need the same functional source SHA and actual SUCCESS.
- Next unresolved prerequisites: independent approved release signer and root custody/verified recipient build provenance (L2B-3), dependency/license closure including external runtime libraries and actual binary SBOM limitations, signed and version-revoked release, separately consented fixed-scope polkit operations, real Pop!_OS 24.04/COSMIC device installation and uninstall L3.

**G-05/G-07/G-08/G-09 OPEN; v0.1 NOT RELEASE READY.**

[Current status](../CURRENT_STATUS.md) · [Release gaps](../planning/release-gap-register.md) · [Desktop hardware acceptance](../testing/linux-desktop-installer-acceptance.md).

## Native evidence accepted from GitHub job logs

- Exact functional source: [e107a45b](https://github.com/kunlong-luo/lanveriq/commit/e107a45b7e697b055fbc3a916cae46a1afcf1832); [actual native CI #45166773406 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45166773406). Source read-only 12 tests, Debian archive 26, L2B-1 receipt discovery 38 (including 26 imported archive tests), L2B-2 Ed25519 lab 16, L2B-4 payload discovery 42 (including 26 imported archive tests) all PASS. These suite counts overlap; the 16 L2B-4 cases are new, not an additional 42 distinct tests.
- Actual native GUI-only Debian candidate SHA256: 1c1358ff2bbc92ffc426ca857ed41fa9bd4e67d00f4e936929fe7c86cbcf9928. The **three delivered files** are usr/bin/lanveriq-desktop (11,287,016 bytes; SHA256 79ba6c6a046e7df6370636353aa689e3d6b70474b27bff3f8b13940e3fed12ae), usr/share/applications/lanveriq-desktop.desktop (225 bytes; SHA256 4e24322a36b20cfb591aefe6dbc631c9569ebfafb6c3a7ef28e35af1a1a88302), and usr/share/icons/hicolor/128x128/apps/lanveriq-desktop.png (629 bytes; SHA256 8b90893025acc3060ff51d4c0c57f2b9b10e33ba8681a1925a9f6a682e7276bc). Direct Debian tar SHA256 matched the independently unpacked file SHA256.
- Native Syft v1.54.0 partial payload SPDX SHA256: 51c4ba1b5012fa619a0a0620bbf5a0db45602c2d3691b05b936a6373d5c94506; **only 1 discovered package** in the temporary extracted files. Source-only SPDX SHA256: a407c333a9feef9985f317b00842ecb00d4292679f75e3909fb34ec96e3dcf90. Those separate scans cannot claim complete dynamic/external runtime dependencies. The temp payload evidence revalidation and original source receipt both PASS.
- No files were uploaded or published, no runtime dynamic dependencies approved, no trusted publisher signature, no installation/startup, no genuine Pop!_OS 24.04 acceptance. These are ephemeral values from a disposable Ubuntu CI run, NOT remotely retrievable release artifacts.

**Only delivered-file integrity + partial scanned payload was accepted. Complete runtime SBOM, independent producer provenance, official signing/revocation and all production release gates remain OPEN.**
