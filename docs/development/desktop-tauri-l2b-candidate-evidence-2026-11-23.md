# DESKTOP-L2B-1 — untrusted, unsigned GUI Debian candidate integrity

**SPEC / EXACT-SHA L2B-1 PASS · 2026-11-23 · UNSIGNED / UNTRUSTED · v0.1 NOT RELEASE READY**

## Scope and implementation

L2A real native Tauri GUI-only Debian archive passed at source SHA d6583756 and [CI run #45157687749](https://github.com/kunlong-luo/lanveriq/actions/runs/45157687749). L2B-1 adds **local integrity bookkeeping**, not verified build provenance. Use mature Anchore Syft, pinned to action full SHA 66cbf4bc1f1c0d2edc94016e65bc221b6bb0ad6c (v0.24.3) and Syft v1.54.0, to scan apps/desktop source and produce SPDX 2.3 JSON without workflow artifact, release upload, dependency snapshot submission or signing.

The [receipt checker](../../apps/desktop/tools/verify_desktop_candidate_evidence.py) re-runs the [strict archive audit](../../apps/desktop/tools/verify-desktop-deb.py) on an actual Tauri amd64 GUI .deb. It validates source SPDX, requires React and Tauri, refuses Agent dependencies and duplicate JSON fields, and binds SHA-256 of the exact .deb, SPDX, Cargo.lock, pnpm-lock.yaml and tauri.conf.json plus full source SHA, workflow run/attempt. Re-verification recomputes every field and forbids adding signed, installation or release authority. The [negative tests](../../apps/desktop/tools/test_verify_desktop_candidate_evidence.py) include byte-tamper, package injection, swapped source SHA/run, spoofed signature/release status, wrong SPDX, missing dependencies and missing/linked files.

Important distinction: Syft scanning source is a **source dependency SBOM**, not the complete binary / installed-product SBOM. The SHA and run context are untrusted claims until an independently verified signer ties them to a trusted builder; an attacker controlling all unsigned inputs can recreate matching hashes. Neither this receipt nor a passing CI build can authorize a download, signing, local installation or production rollout.

## Exit criteria and boundaries

DESKTOP-L2B-1 PASS requires **actual Ubuntu 24 native Tauri .deb generation, strict archive audit, actual Syft source SPDX, local receipt create and recompute verification, plus synthetic denial suite** on exactly the same functional SHA. A failed action, missing SPDX dependencies or source-only green CI is not PASS. CI writes evidence only to disposable runner temp; no upload, signing, distribution, polkit permission, service start or Agent packaging.

Separately **NOT RUN / NOT AUTHORIZED**: independently reviewed Sigstore/GitHub signed provenance, trusted release signer/issuer/time and revocation policy, full delivered-artifact SBOM, verification on recipient; fixed-polkit installer broker with independent installation/start/boot consent; approved Pop!_OS 24.04 COSMIC physical L3 including signed install, upgrades, rollback, uninstall and effective systemd protections. **G-05/G-07/G-08/G-09 OPEN; v0.1 NOT RELEASE READY.**

[Current status](../CURRENT_STATUS.md) · [Release gaps](../planning/release-gap-register.md) · [L2A](desktop-tauri-offline-deb-candidate-2026-11-23.md).

## Actual acceptance, exact source and CI

- Function commit: [4051276a3932d4a3709f3345b4ad30dcc91dd3bb](https://github.com/kunlong-luo/lanveriq/commit/4051276a3932d4a3709f3345b4ad30dcc91dd3bb) on main. Actual Ubuntu 24.04 [Desktop GUI .deb + source SPDX + receipt run #45160206981 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45160206981).
- Executed: 12 Desktop read-only source tests, 26 hostile/positive Debian package archive tests, 38 L2B candidate-receipt test cases, actual locked Tauri release .deb build and full archive verifier PASS, Syft v1.54.0 source SPDX 2.3 inventory **608 packages**, unsigned receipt created then recomputed and verified PASS. The 38 tests include 26 previously imported packaging tests via unittest discovery, plus 12 L2B-specific receipt tests; count does **not** mean 38 distinct new tests.
- Real unsigned GUI-only candidate: lanveriq-desktop_0.1.0_amd64.deb SHA-256 **ebc7a03258878df81160b8bccf42a90046d43eb557fc17558dc6b4570d64be88**. Local SPDX SHA-256 **1c34c35cf464b141d9f9750afc0f5e579433673ac36d147875ef22be725722b1**. Both hashes are ephemeral CI measurements; **not uploaded, not signed, not an independently retrievable or verifiable artifact**. Do not treat them as a reproducibility claim; original L2A .deb SHA differs across source/build executions.
- Historical red test preserved: function SHA [ca1595ac](https://github.com/kunlong-luo/lanveriq/commit/ca1595ac760444e8e70b494e41260daf0bd6b94a) [#45160121568 FAILED](https://github.com/kunlong-luo/lanveriq/actions/runs/45160121568) before native build, because synthetic .deb fixture used generic candidate.deb while final exact identity guard required canonical filename. Fixed the fixture only; never relaxed filename or archive allowlists.

**Accepted L2B-1 is restricted to local unsigned candidate file-integrity/source dependency recording. Trusted provenance, release signing, a complete binary SBOM, independent verify-on-recipient, authorized polkit install and physical Pop!_OS 24.04 remain NOT RUN/OPEN. G-05/G-07/G-08/G-09 OPEN; v0.1 NOT RELEASE READY.**
