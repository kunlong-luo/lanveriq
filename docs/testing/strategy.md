# Test strategy — fast feedback, unit-only GitHub CI

**Explicit product owner preference:** develop directly on main; no mandatory branches/PRs; **CI only runs unit tests**; do not implement E2E initially. Use the [manual acceptance plan](manual-acceptance.md) to document real-device checks outside CI. This is a *temporary* pre-release policy under [ADR-0004](../architecture/adrs/0004-rapid-main-development.md).

## CI jobs once code exists
| Component | Unit runner | Target scope |
|---|---|---|
| Rust crates/Agent | `cargo test --workspace` (native runner, platform-gated code) | outbox, migrations, retry, state transitions, redaction |
| Java API | `./gradlew test` from `apps/server` (**Gradle 9.8.2**, Boot **4.1.2**, JUnit **6**) | auth, token expiry, event validation/dedup, RBAC logic, retention; JaCoCo reports |
| React dashboard | `pnpm test` (Vitest run mode) | UI state, hooks, formatting, error states and auth-related rendering |

Run only when relevant project exists; unit jobs should fail on real tests, not artificially pass with "echo OK". Keep GitHub Actions permissions contents:read, runner/timeouts bounded and no external deployment. Local format, lint, typecheck and build are recommended but **not** part of CI.

**Java backend test baseline:** Boot-managed `spring-boot-starter-test` + `org.springframework.security:spring-security-test`, Gradle's `jacoco` 0.8.16. **83% line coverage is a later possible threshold**, not a green-check substitute or an unconditional gate before real tests. Do not run Docker-dependent Testcontainers, integration E2E or Kafka jobs in the early unit-only CI. See [backend stack](../development/backend-stack.md).

## Unit test priorities
1. Credential/token expiry, replay and authorization deny decisions.
2. SQLite transaction atomicity and single ordered writer.
3. Entire-batch transaction + sequence/event-ID conflicts, deduplication, explicit per-sequence acknowledgment and new-enrollment identity.
4. Backoff, queue quota and explicit collection_gap behavior.
5. Retention predicate and policy scope.
6. Recording permission state machine and segment manifest validation (v0.3).
7. UI view models, handling missing unsupported capabilities and stale/offline states.
8. WebSocket handshake/Origin/session authorization, group scope, notification-after-commit, bounded queue, client reconnect + REST resync. See [test matrix](test-matrix.md).

## Targeted manual smoke checks (not automated E2E)
Run manual WSS scenarios MAN-WS-01 through MAN-WS-09 from [test matrix](test-matrix.md) on actual LAN browsers; these are **not automated browser E2E** and are not GitHub CI jobs.
After v0.1 locally: deploy one server and enroll one Windows + one Linux test endpoint; confirm TLS, last-seen, reboot, network disconnect and replay. Record cases MAN-001 through MAN-013 from [manual acceptance](manual-acceptance.md) and mark unexecuted tests clearly. For v0.2 manually verify cross-role API denial, limited audit sources and retention on test data. For v0.3 verify OS permission dialogs, capture status, denial fail-closed, logout/lock gaps, storage quota and approved playback. Log date/OS/version/operator/outcome in acceptance notes; **manual checks are not CI jobs**.

## Security constraints
Do not conflate unit CI green with end-to-end, platform or security certification. Before production deployment add contract/integration tests, cross-platform install tests, actual capture UX checks, vulnerability checks and eventually E2E; do not silently weaken requirements to maintain speed.

## Performance
During development collect ad hoc measurements for agent memory/CPU, event latency and video bitrate. No automated performance budgets in CI until justified.
