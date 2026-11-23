# DESKTOP-L2B-1 — untrusted, unsigned GUI Debian candidate integrity

**SPEC · 2026-11-23 · functional SHA CI PENDING · NOT SIGNED · v0.1 NOT RELEASE READY**

## Scope and implementation

L2A real native Tauri GUI-only Debian archive passed at source SHA d6583756 and [CI run #45157687749](https://github.com/kunlong-luo/lanveriq/actions/runs/45157687749). L2B-1 adds **local integrity bookkeeping**, not verified build provenance. Use mature Anchore Syft, pinned to action full SHA 66cbf4bc1f1c0d2edc94016e65bc221b6bb0ad6c (v0.24.3) and Syft v1.54.0, to scan apps/desktop source and produce SPDX 2.3 JSON without workflow artifact, release upload, dependency snapshot submission or signing.

The [receipt checker](../../apps/desktop/tools/verify_desktop_candidate_evidence.py) re-runs the [strict archive audit](../../apps/desktop/tools/verify-desktop-deb.py) on an actual Tauri amd64 GUI .deb. It validates source SPDX, requires React and Tauri, refuses Agent dependencies and duplicate JSON fields, and binds SHA-256 of the exact .deb, SPDX, Cargo.lock, pnpm-lock.yaml and tauri.conf.json plus full source SHA, workflow run/attempt. Re-verification recomputes every field and forbids adding signed, installation or release authority. The [negative tests](../../apps/desktop/tools/test_verify_desktop_candidate_evidence.py) include byte-tamper, package injection, swapped source SHA/run, spoofed signature/release status, wrong SPDX, missing dependencies and missing/linked files.

Important distinction: Syft scanning source is a **source dependency SBOM**, not the complete binary / installed-product SBOM. The SHA and run context are untrusted claims until an independently verified signer ties them to a trusted builder; an attacker controlling all unsigned inputs can recreate matching hashes. Neither this receipt nor a passing CI build can authorize a download, signing, local installation or production rollout.

## Exit criteria and boundaries

DESKTOP-L2B-1 PASS requires **actual Ubuntu 24 native Tauri .deb generation, strict archive audit, actual Syft source SPDX, local receipt create and recompute verification, plus synthetic denial suite** on exactly the same functional SHA. A failed action, missing SPDX dependencies or source-only green CI is not PASS. CI writes evidence only to disposable runner temp; no upload, signing, distribution, polkit permission, service start or Agent packaging.

Separately **NOT RUN / NOT AUTHORIZED**: independently reviewed Sigstore/GitHub signed provenance, trusted release signer/issuer/time and revocation policy, full delivered-artifact SBOM, verification on recipient; fixed-polkit installer broker with independent installation/start/boot consent; approved Pop!_OS 24.04 COSMIC physical L3 including signed install, upgrades, rollback, uninstall and effective systemd protections. **G-05/G-07/G-08/G-09 OPEN; v0.1 NOT RELEASE READY.**

[Current status](../CURRENT_STATUS.md) · [Release gaps](../planning/release-gap-register.md) · [L2A](desktop-tauri-offline-deb-candidate-2026-11-23.md).
