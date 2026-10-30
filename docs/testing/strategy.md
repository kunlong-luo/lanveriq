# Test strategy — fast feedback, unit-only GitHub CI

**Explicit product owner preference:** develop directly on main; no mandatory branches/PRs; **CI only runs unit tests**; do not implement E2E initially. This is a *temporary* pre-release policy under [ADR-0004](../architecture/adrs/0004-rapid-main-development.md).

## CI jobs once code exists
| Component | Unit runner | Target scope |
|---|---|---|
| Rust crates/Agent | `cargo test --workspace` (native runner, platform-gated code) | outbox, migrations, retry, state transitions, redaction |
| Java API | `./mvnw test` or pinned Maven `mvn test` | auth, token expiry, event validation/dedup, RBAC logic, retention |
| React dashboard | `pnpm test` (Vitest run mode) | UI state, hooks, formatting, error states and auth-related rendering |

Run only when relevant project exists; unit jobs should fail on real tests, not artificially pass with "echo OK". Keep GitHub Actions permissions contents:read, runner/timeouts bounded and no external deployment. Local format, lint, typecheck and build are recommended but **not** part of CI.

## Unit test priorities
1. Credential/token expiry, replay and authorization deny decisions.
2. SQLite transaction atomicity and single ordered writer.
3. Event sequence conflicts and server deduplication.
4. Backoff, queue quota and explicit collection_gap behavior.
5. Retention predicate and policy scope.
6. Recording permission state machine and segment manifest validation (v0.3).
7. UI view models, handling missing unsupported capabilities and stale/offline states.

## Targeted manual smoke checks (not automated E2E)
After v0.1 locally: deploy one server and enroll one Windows + one Linux test endpoint; confirm TLS, last-seen, reboot, network disconnect and replay. For v0.2 manually verify cross-role API denial, limited audit sources and retention on test data. For v0.3 verify OS permission dialogs, capture status, denial fail-closed, logout/lock gaps, storage quota and approved playback. Log date/OS/version/operator/outcome in acceptance notes; **manual checks are not CI jobs**.

## Security constraints
Do not conflate unit CI green with end-to-end, platform or security certification. Before production deployment add contract/integration tests, cross-platform install tests, actual capture UX checks, vulnerability checks and eventually E2E; do not silently weaken requirements to maintain speed.

## Performance
During development collect ad hoc measurements for agent memory/CPU, event latency and video bitrate. No automated performance budgets in CI until justified.
