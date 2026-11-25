# v0.1.0-dev.1 — Source-only Developer Preview Candidate

**CANDIDATE · NOT PUBLISHED · SOURCE-ONLY · v0.1 NOT RELEASE READY**

This is a **draft of the release notes**, not a GitHub Release, release tag, signed source attestation, verified package, installation instruction, or independent authorization.

## What is available to review

- The existing **single Next.js Console candidate**, Spring Boot 4.2 / PostgreSQL 18, Rust Agent with SQLite durable outbox, and read-only Tauri 2 preflight **source code**.
- [Actual Ubuntu 24.04 isolated Rust → mTLS → Spring/PG18 → Next.js/Chromium business chain](../development/linux-inventory-reconnect-exact-ack-nextjs-l2-2026-11-25.md): source SHA [`db570181`](https://github.com/kunlong-luo/lanveriq/commit/db570181d603a7f069a85a23c009658e9a8bed4c), [Linux L2 #45554279736](https://github.com/kunlong-luo/lanveriq/actions/runs/45554279736) and [Agent L1 #45554279741](https://github.com/kunlong-luo/lanveriq/actions/runs/45554279741) **SUCCESS**. This proves a disposable runner, not a customer endpoint.
- Existing historical Linux signed APT/Polkit experiments are **unapproved lab evidence**, not deliverable installation steps. [Release risk](../development/desktop-tauri-l2b27-native-apt-signed-release-date-replay-oci-2026-11-24.md): APT accepted an older still-valid signed Release index; no independent publisher root, trusted time or immutable high-water is approved.

## Distribution boundary: source only

- Proposed candidate label: `v0.1.0-dev.1`. **No Git tag or GitHub Release exists for this candidate yet**.
- If a future authorized maintainer explicitly publishes a **GitHub prerelease**, the only permitted downloads are GitHub's automatically generated **source** `.zip` and `.tar.gz` archives. There must be **no .deb**, no GUI installer, no AppImage, no MSI, no executable binaries, no APT repository, no signatures claiming an approved publisher, and no extra release attachments.
- Repository scripts that can build code are for authorized isolated development only; the source archive is **not** an Agent product or install authorization.
- The [source-only preflight](../../tools/release/verify_source_preview.py) is an L1 local consistency check. Its SHA-256 inventory and fixed fields are **not signing, external custody, a WORM witness or a release approval**.
- No publish/tag operation, production secret, package build or upload is performed by this preflight; its GitHub Actions workflow holds **read-only** repository permission.

## Explicit release refusal

**G-01 through G-10 OPEN.** Production networking, employee/device collection, autonomous recovery, remote control, official issuer/CA and administrator authentication promotion remain disabled. No user-distributable Agent, approved Polkit broker, Pop!_OS 24.04 COSMIC physical L3, production signer/high-water/WORM, legal/privacy/SRE signoff exists.

For current facts use [CURRENT_STATUS](../CURRENT_STATUS.md), [release Gate register](../planning/release-gap-register.md), [release runbook](release.md), and [planned source preview contract](../planning/source-preview-v0.1.0-dev.1.json).

**SOURCE-ONLY · NOT PUBLISHED · NOT RELEASE READY.**
