# DESKTOP-L2B-2 — offline detached-signature rejection lab (not a release signer)

**SPEC · 2026-11-23 · FUNCTIONAL CI PENDING · EPHEMERAL LAB TEST KEY ONLY · v0.1 NOT RELEASE READY**

## Scope and exact trust boundary

L2A native GUI-only Debian archive and L2B-1 source SPDX/local unsigned hash receipt passed at SHA 4051276a, [CI #45160206981](https://github.com/kunlong-luo/lanveriq/actions/runs/45160206981). The next limited deliverable proves that **a detached Ed25519 signature can be cryptographically verified against exact receipt bytes** using OpenSSL 3 on a disposable Ubuntu 24 runner. It does **not** authorize, create, or distribute any signed release artifact.

The workflow creates an **ephemeral, unapproved ED25519 private/public key pair only in runner.temp** with umask 077, signs the existing untrusted L2B-1 receipt, checks the public-key SHA-256 fingerprint against a separately supplied **lab** expectation and verifies the detached signature with OpenSSL 3. The [lab-only verifier](../../apps/desktop/tools/verify_desktop_lab_signature.py) also revalidates the entire unsigned receipt against the real .deb, strict archive whitelist, SPDX, Cargo/pnpm/Tauri source locks, commit SHA, GitHub run and attempt. The lab verifier never returns RELEASE_READY or production approval and has no privileged or install operations. The CI removes its test private key and signature at step exit, and no artifact upload, external signature service, release, installation, OIDC token or production key is used.

The lab key is **not** a trusted publisher, CA, HSM/KMS, verified GitHub OIDC issuer, identity certificate, timestamp, revocation log or independently approved trust anchor. A SHA-256 fingerprint taken from the same ephemeral CI session does not establish signer identity or separate administrative control. A valid lab signature cannot be used by a recipient as proof of a trusted Lanveriq release. This is not DSSE/in-toto, Sigstore transparency or a reproducible binary attestation; do not introduce a custom release signature format based on this exercise.

## Required tests and failure handling

The [negative tests](../../apps/desktop/tools/test_verify_desktop_lab_signature.py) exercise real OpenSSL signing/verification and reject corrupted signature, changed receipt bytes, swapped public key, forged public-key fingerprint, short signature, missing/symlinked key files, replay of source SHA/run, mutated .deb/SPDX/lock inputs and **re-signed forged release approval**. All tests use generated local keys and synthetic packages only. The actual exact-SHA real Tauri Debian job must independently pass archive/Syft/receipt and the final OpenSSL step on Ubuntu 24.04; failures remain recorded at their SHA and must not silently loosen any guard.


### Independent fast negative CI (not native bundle evidence)

The [dedicated offline Ed25519 lab workflow](../../.github/workflows/desktop-signature-lab.yml) verifies real OpenSSL 3 ephemeral signatures and denial paths against **synthetic** Debian fixtures on Ubuntu 24.04. It requests only contents: read and does not use id-token, GitHub secrets, public signer, uploads, installs or production actions. Its purpose is to run cryptographic L1 negative tests without waiting for WebKitGTK/Tauri. An L1 success here must not be substituted for L2B-2's native Debian package + Syft + actual lab signature check.

Verified synthetic L1: functional commit [ae0775a5](https://github.com/kunlong-luo/lanveriq/commit/ae0775a574e242e76013eb2fe060624412eec658), [offline Ed25519 test run #45163358720 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45163358720), Ubuntu 24.04 / OpenSSL 3, 16 test cases executed. This proves real OpenSSL tests on **synthetic** receipts, not a native binary or verified release issuer. The prior [native run #45161801144](https://github.com/kunlong-luo/lanveriq/actions/runs/45161801144) remains **IN PROGRESS**, not an accepted native L2B-2 PASS.

## Next gates (NOT RUN)

1. Approve and implement an **industry-standard, independently verifiable release provenance** (e.g., Sigstore/cosign or GitHub artifact attestation using protected workflow and a verified OIDC identity), with origin constraints: repository, immutable workflow, source commit, exact package digest, signer identity, revocation/time/transparency and rollback policy. Any public attestation/upload requires a separate decision. Pin actions/tool versions and enforce verification on a different trust domain. No unapproved release keys or automatic publication.
2. Generate and audit a complete **delivered-artifact and runtime dependency SBOM**; source Syft SPDX cannot replace this. Verify separately from the build runner, and approve key custody/HSM/KMS/rotation and incident response.
3. Only after trust and operator approval, implement fixed-action polkit authorization with separate install/start/enable-at-boot consent and denial/cancel tests; then conduct authorized real Pop!_OS 24.04/COSMIC L3 installation, update/uninstall, offline/privacy and no-tray background tests.

**No official signed Lanveriq GUI or Agent .deb exists; no production install, boot enable, remote or collection authority. G-05/G-07/G-08/G-09 OPEN; v0.1 NOT RELEASE READY.**

[Current status](../CURRENT_STATUS.md) · [Release gap register](../planning/release-gap-register.md) · [L2B-1](desktop-tauri-l2b-candidate-evidence-2026-11-23.md).

## Official release provenance option — decision required, NOT ENABLED

GitHub now [recommends `actions/attest`](https://github.com/actions/attest) for new build-provenance workflows rather than the older `actions/attest-build-provenance`. Its attestations are sent to GitHub's attestation service and are not local-only; enabling it would change network and evidence-retention policy and requires a separate organizational approval. See the [GitHub attestation documentation](https://docs.github.com/en/actions/concepts/security/artifact-attestations) and [recipient verification command](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations). The L2B-2 lab intentionally does not request an OIDC token, grant `id-token: write`, use this action, or upload an artifact. No platform-generated provenance is claimed.
