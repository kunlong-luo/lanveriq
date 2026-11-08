# Testing strategy — L1 fast checks, L2 batch integration, L3 milestone acceptance

**Approved 2026-11-08:** develop the complete v0.1 P0 business loop in coherent batches. Do **not** rerun the entire Caddy/Tomcat/PG18/Chromium stack on each CSS/service method/DAO commit. Equally, do **not** defer the first security or database test until every feature is built. The previous unit-only CI policy was an earlier preimplementation snapshot and is superseded by the [approved batched delivery plan](../planning/v0.1-batched-delivery-plan-2026-11-08.md).

| Level | When | Scope | Blocks |
| --- | --- | --- | --- |
| **L1 rapid / every relevant push or PR** | Each source change, only matching paths | Rust `cargo test --locked`/Clippy, Java Gradle JUnit + build/type checks, Next.js Vitest/typecheck/route isolation. For affected auth/credential/permission/PKI/DB migrations/ACK, write and run **targeted fail-closed negative unit tests immediately** | Merge of broken source / invalid contract |
| **L2 batch integration** | At a cohesive business-batch boundary, via explicit synthetic `workflow_dispatch` | Real disposable PostgreSQL 18 Testcontainers, Agent SQLite and HTTPS handshake, Caddy/Tomcat mTLS, scoped multi-admin authorization, selected integration cases. Iterate with targeted retry before one clean batch full rerun | Completion of A/B/C/D business batch |
| **L3 milestone safety/release** | On an intentionally created `acceptance/v0.1-*` tag or explicit dispatch | 11-route Chromium/keyboard/15 responsive screenshot suite, complete Caddy/Spring/PG18/RBAC/HTML/RSC, Windows/Linux real-device checks (separate authorized lab), failover, restore drill, privacy/license/operations review | Milestone signoff and pilot/production approval |

**Important:** passing a synthetic network/DB test in GitHub Actions is **not** permission to enable default-OFF paths, real employee telemetry, production Agent, real admin login, or capture. Actual managed-device tests and privacy approval require separate authorization and evidence. The full PostgreSQL manual workflow, Agent PKI/mTLS isolated tests and browser workflows remain available; nothing in this plan weakens authentication/authorization or failure-mode expectations.

## CI selection and reporting

- The path-filtered `server-unit.yml`, `agent-unit.yml`, `web-next-unit.yml` continue on source changes. Keep unit evidence real (not echo-only), Java 25, Gradle, pinned Rust dependencies, frozen pnpm.
- Move `console-https-synthetic.yml` and `nextjs-browser-preview-synthetic.yml` away from automatic main push. Trigger large suites **only on milestone tags or explicit dispatch**; tag runs use the full set of synthetic probes (not the old dispatch defaults that silently skipped the browser).
- `pages-static-preview.yml` publishes the **disconnected showcase only** on explicit dispatch or milestone; ordinary backend and UI commits should not redeploy the public site repeatedly.
- For changes to certificates, session/RBAC, PKI, schema or digest/ACK logic, always run their **focused L1 negative suite** immediately. If the change crosses a trust boundary, complete the appropriate **L2 targeted** test before the batch is accepted. Never remove negative checks solely to speed up execution.
- Report results by **SHA, command, actual run ID, and scope**. Clearly separate compiled tests from executed database tests, isolated synthetic results from real-device results, and tested source from authorized production.

## Unit test priorities
1. Credential/token expiry, replay and authorization deny decisions.
2. SQLite transaction atomicity and single ordered writer.
3. Entire-batch transaction + sequence/event-ID conflicts, deduplication, explicit per-sequence acknowledgment and new-enrollment identity.
4. Backoff, queue quota and explicit collection_gap behavior.
5. Retention predicate and policy scope.
6. Recording permission state machine and segment manifest validation (v0.3).
7. UI view models, handling missing unsupported capabilities and stale/offline states.
8. WebSocket handshake/Origin/session authorization, group scope, notification-after-commit, bounded queue, client reconnect + REST resync. See [test matrix](test-matrix.md).

## Real-device/manual smoke checks (separate from CI)
Run manual WSS scenarios MAN-WS-01 through MAN-WS-09 from [test matrix](test-matrix.md) on actual LAN browsers; these are **not automated browser E2E** and are not GitHub CI jobs.
After v0.1 locally: deploy one server and enroll one Windows + one Linux test endpoint; confirm TLS, last-seen, reboot, network disconnect and replay. Record cases MAN-001 through MAN-013 from [manual acceptance](manual-acceptance.md) and mark unexecuted tests clearly. For v0.2 manually verify cross-role API denial, limited audit sources and retention on test data. For v0.3 verify OS permission dialogs, capture status, denial fail-closed, logout/lock gaps, storage quota and approved playback. Log date/OS/version/operator/outcome in acceptance notes; **manual checks are not CI jobs**.

## Security constraints
Do not conflate unit CI green with end-to-end, platform or security certification. Before production deployment add contract/integration tests, cross-platform install tests, actual capture UX checks, vulnerability checks and eventually E2E; do not silently weaken requirements to maintain speed.

## Performance
During development collect ad hoc measurements for agent memory/CPU, event latency and video bitrate. No automated performance budgets in CI until justified.
