# ADR-0004: Rapid main development, unit-only CI, deferred E2E

**Status:** Accepted for pre-release MVP only | **Date:** 2026-10-30

## Context
Single maintainer prioritizes getting the MVP working. Pagevela currently uses extensive lint/type/build, browser E2E and release gates, but duplicating that full CI would slow early Rust/Java/desktop development. A public repo still needs reasonable security discipline.

## Decision
1. Work **directly on main**. No feature branches or mandatory PR reviews while the repository is in pre-release development. Make small, descriptive atomic commits; push regularly. Do not rewrite or force-push main.
2. GitHub Actions CI runs **only unit test jobs**: Rust `cargo test --workspace`, Java `mvn test`, web `pnpm test --run` (or equivalent Vitest command), when the associated components are created. Before implementation exists, do **not** create fake/passing CI jobs.
3. Do **not** create or execute Playwright/E2E suites in the early stages. No browser matrix, release automation, CodeQL pipeline or containerized integration matrix in CI at this stage.
4. Local developer checks for touched modules: formatter/linter, typecheck and build when appropriate; **not** part of CI gates. Track their results honestly.
5. High-risk paths (auth, enrollment, DB migrations, outbox replay, video permissions, access control) require meaningful unit tests **and targeted manual review/smoke checks**, even though E2E is deferred.
6. Publish no production-ready release until a dedicated hardening stage adds integration, platform/device and security acceptance.

## Risks/mitigations
Direct-main commits may temporarily break compilation; limit diff size and revert promptly. Unit-only CI cannot catch packaging, cross-process auth failures, Wayland behavior or real video capture; label these **unverified**. If multiple maintainers join or main instability rises, adopt PR review and broader CI via a superseding ADR.

## Future review trigger
Before first usable customer/employee deployment and before v1.0: dedicated CI strategy review, integration tests for ingest/replay/access control, Windows/Linux installer verification, recording authorization evidence and actual LAN runtime validation. The development convenience exception never relaxes privacy/security boundaries.
