# STACK-RUST1.100 — Rust stable toolchain upgrade, no Agent feature activation

**2026-11-23 · SOURCE CANDIDATE · EXACT-SHA CI PENDING · Linux-first Pop!_OS24.04 · v0.1 NOT RELEASE READY**

## Motivation and exact baseline

Upstream Rust stable 1.100.0 (2026-11-12): https://blog.rust-lang.org/2026/11/12/Rust-1.100.0/ . Existing Lanveriq Agent Rust edition 2024, SQLx 0.9.0, Tokio, reqwest 0.13.5 / Rustls, ring, SQLite and strict mTLS/ACK semantics are retained. This batch changes **the developer default toolchain and explicit CI invocations** from Rust 1.94.0 to 1.100.0; the Cargo package's **rust-version=1.94 MSRV remains unchanged**, since adopting a newer compiler alone does not justify raising the published minimum.

## Code changes

- apps/agent/rust-toolchain.toml default channel 1.100.0, with rustfmt and Clippy.
- Agent Linux unit and Linux Pop!_OS custody / real Caddy→Spring→PG18.7 synthetic workflow Rust installer + all cargo +1.100.0 invocations.
- Windows targeted Rust unit and CNG/CSR synthetic workflow installer pins, and Linux CSR issued-leaf interop workflow pins.
- No Cargo dependency or lockfile regeneration, no TLS/version policy change, no cryptographic primitive or protocol replacement, no secret, no license change.
- No change to actual OS service startup, production networking, collection, automatic recovery, CA signing or remote control flags.

## Required independent acceptance

1. Same functional SHA Agent Linux cargo fmt --check, full unit suite and Clippy -D warnings with real Rust 1.100.0; if rustfmt changes source formatting, fix with new reviewed source commit, never disable the check.
2. Same SHA Linux Ubuntu24 disposable custody, CSR and native offline .deb with locked Cargo; Linux Batch A real isolated Caddy→Tomcat→PG18.7, exact TLS/replay/ACK negatives.
3. Windows target/CSR workflows must be checked where their dispatch conditions run; absent/queued/skipped Windows checks are NOT PASS. Ensure no cross-platform regression before claiming full compatibility. No physical Pop!_OS L3 from Ubuntu CI.
4. The MSRV remains 1.94 as a declaration, **not re-tested here unless a real 1.94 build is triggered**, so no new compatibility guarantee is implied.
5. Record exact SHA, run URLs, observed PASS/FAIL/PENDING; revert or fix by separate main commit if new toolchain fails. Preserve older successful 1.94 run IDs as historical 1.94 evidence only.

**G-02/G-05/G-08 OPEN. v0.1 NOT RELEASE READY. This does not enable production agent networking, device capture, remote, automatic recovery, real FIDO/CA or external WORM.**

## Exact source-SHA CI acceptance (2026-11-23)

**Functional SHA [1905ef5a08377df0853a2de45cb2d3c772d9a7bf](https://github.com/kunlong-luo/lanveriq/commit/1905ef5a08377df0853a2de45cb2d3c772d9a7bf): 9/9 actual GitHub workflow runs completed SUCCESS.**

- [Linux Agent full Rustfmt, cargo workspace unit and Clippy -D warnings #45138974357](https://github.com/kunlong-luo/lanveriq/actions/runs/45138974357): success using pinned 1.100.0.
- [Ubuntu 24.04 Linux offline CSR, Unix UID, real transient systemd DynamicUser and real compiled release Debian candidate #45138974180](https://github.com/kunlong-luo/lanveriq/actions/runs/45138974180): success, no real Agent installed/started.
- [Linux Batch A real isolated Rust→Caddy TLS1.3→Spring/Tomcat→PostgreSQL18.7 #45138974359](https://github.com/kunlong-luo/lanveriq/actions/runs/45138974359): success on both independent CI jobs.
- [Linux Rust-generated P-256 CSR/issued client-auth leaf → Java validation #45138974342](https://github.com/kunlong-luo/lanveriq/actions/runs/45138974342): success.
- [Windows targeted Rust Agent native unit, fmt/Clippy #45138974364](https://github.com/kunlong-luo/lanveriq/actions/runs/45138974364): success.
- [Windows synthetic CNG non-exportable P-256 CSR, rustls mutual TLS and Java BouncyCastle interop #45138974343](https://github.com/kunlong-luo/lanveriq/actions/runs/45138974343): success.
- [Next.js fast checks #45138974316](https://github.com/kunlong-luo/lanveriq/actions/runs/45138974316), [docs authority #45138974490](https://github.com/kunlong-luo/lanveriq/actions/runs/45138974490), [RD-2 fail-closed #45138974430](https://github.com/kunlong-luo/lanveriq/actions/runs/45138974430): success.

**Proof scope:** Rust 1.100 compiler build, negative tests, and synthetic network/crypto/DB flows; no authorized physical Pop!_OS24 L3, real Windows field deployment, production access, WORM custody, CA or admin MFA. Cargo MSRV remains set to 1.94 but this SHA did not trigger a separate Rust 1.94 compatibility build. v0.1 NOT RELEASE READY.
