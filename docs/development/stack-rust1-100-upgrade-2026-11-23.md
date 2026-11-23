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
