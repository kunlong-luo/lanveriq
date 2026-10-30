# Documentation and diagrams review — 2026-10-30

**Scope:** single GitHub `main` branch, **83 Markdown files, 20 Mermaid diagram pages, 8 self-contained SVG concept images** as observed at start of this review. **This report itself increases the Markdown count by one.** No business code or runtime tests were added. Findings describe a **design repository**, not a functioning application.

## Review methods and honest coverage
1. Checked the repository tree and internal file-relative Markdown/image links throughout the **83 existing Markdown files**. No missing **file** targets found. Directory links such as `docs/visuals/assets/` must be evaluated as directories, not files. External URLs and heading anchors were not exhaustively tested.
2. Inspected high-impact contracts (device identity, PostgreSQL/SQLite, REST, WSS, collection policy, recording), PRD, UI specifications, roles, roadmap/backlog, operations and D0 gate for contradictions; **not a formal legal, security or platform certification**.
3. Statically checked each of the **20 Mermaid pages** for one complete Mermaid fenced block and checked major arrows/authorization flows for meaning. **No actual Mermaid engine parse/render/screenshot verification**.
4. Checked **all eight 1600×900 SVG concept images** for XML tag nesting, presence of title/description and concept-stage footer, script/foreign-object/image elements and gross text overflow. No such structural issues were detected. **Browser rendering, Chinese font fallback, overlapping labels and accessibility contrast still require manual visual inspection.**

## Fixed defects in this review

| Severity | Finding before review | Correction / canonical reference |
|---|---|---|
| High | V0.1 had group-scoped REST/WSS access but product docs appeared to postpone **all** RBAC until V0.2 | Added explicit V0.1 admin session/group/Origin/CSRF baseline; V0.2 reserved for expanded audit/data-class permissions. [PRD](../product/requirements.md), [API](../architecture/api-contract.md), [permission matrix](../security/authorization-matrix.md), [backlog](../planning/backlog.md) |
| High | Recording API still advertised case-less `GET /recordings/{id}/play`, inconsistent with approved-case, per-range media reads | Split Agent upload vs reviewer planes; all media playback requires role/group/case/time and approved Range requests. [API](../architecture/api-contract.md), [playback center](../development/recording-playback-center.md) |
| High | Recording diagrams suggested media/DB could directly lead to playback, bypassing the approval decision | Explicit Spring authorization gate and media read **after** grant check. [D-07](../diagrams/recording-pipeline.md), [D-18](../diagrams/recording-playback-flow.md) |
| High | Monitoring diagram showed Agent writing directly to PostgreSQL | Added authenticated Spring REST validation and transaction boundary, post-commit WSS hint. [D-17](../diagrams/monitoring-center-flow.md) |
| High | Monitoring Center and Recording & Playback Center absent from key PRD/UI/navigation views | Added explicit milestones, route/UX/spec and task references. [PRD](../product/requirements.md), [dashboard UX](../development/dashboard-ux.md), [navigation D-15](../diagrams/dashboard-navigation.md) |
| Medium | Collection/transport gaps were incorrectly described as V0.3-only despite V0.1 outbox/health requirements | Split V0.1 operational/source gaps from V0.3 capture-specific gaps in PRD. [FR-14](../product/requirements.md) |
| Medium | V0.1 heartbeat/health were called `event source allowlist`, potentially implying early real activity capture | Clarified distinct operational endpoints and **synthetic audit test events only** in V0.1; real allowlisted audit activity deferred to V0.2. [Policy](../architecture/policy-configuration.md) |
| Medium | Full hardware/IP/MAC detail could be interpreted as unrestricted to anyone with device-health permission | Separated detailed `internal_operational` network inventory permission from ordinary health, prohibited raw MAC/IP via WSS. [RBAC](../security/authorization-matrix.md), [WSS](../architecture/websocket-contract.md) |
| Medium | Inventory sequence diagram incremented revision even if normalized snapshot had not changed | Revision now allocated/persisted **only on changed/startup/reconciliation path**; out-of-order old revisions cannot overwrite newer inventory. [D-20](../diagrams/device-inventory-flow.md) |
| Medium | 30-minute unfinalized recording segments increased crash-loss and late-upload window | Changed to provisional **1–5 minute finalized/byte-bounded** segment trial pending benchmarks, not a product guarantee. [Recording](../architecture/recording.md) |
| Medium | TLS verification in enrollment diagram looked like a server RPC; device ID generation unspecified | Made TLS CA/hostname validation an Agent handshake action and specified PostgreSQL `uuidv7()` server assignment. [D-04](../diagrams/enrollment-sequence.md) |
| Medium | V0.1 installation runbook could imply MSI/DEB and actual audit collectors already existed | Clarified manual pilot installation, V0.4 packaging, and synthetic V0.1 events. [Installation](../operations/installation.md) |
| Low | Old 16-diagram count and labels in posters/manifest; product homepage diagram lagged new centers | Aligned **20** Mermaid pages, dashboard navigation and product SVG labels, all still marked conceptual. [Index](../diagrams/README.md), [gallery](../visuals/README.md) |

## Open decisions / do not claim solved
**P0 before V0.1 code:** Maintainer's explicit D0 approval; first Linux `XDG_SESSION_TYPE` and OS capability tests (reported Pop!_OS 24.04 KDE/KWin does not prove Wayland or X11); exact internal CA/bootstrap and device credential rotation; freeze inventory and synthetic event schema (revision, error semantics, null/capability handling) and baseline permission matrix. Test with **fictional**, not employee, data.

**P0 before real employee audit data:** Local jurisdiction/legal notice, organization-approved inventory/IP/MAC/OS source allowlist, purpose, retention and deletion schedule, explicit source policy, protected credential distribution, real-device RBAC and backup/restore evidence. The public repo remains **unlicensed until the maintainer selects and commits a license**.

**Before V0.2:** Final audited OS event allowlists and application metadata definitions, supported/unsupported Wayland capabilities per actual compositor, search/query authorization and retention.

**Before V0.3:** Validate user-session capture grant behavior on real Windows and Pop!_OS; independent case approver, segment tamper detection, 206 byte-range/time-window boundary checks, upload retries, disk quotas, backup expiration and secure deletion. Live screen streaming has **no approved milestone** and requires separate disclosed authorization and design.

**Before production/V1.0:** Security review, platform validation, integration tests/E2E and signed installer/rollback review. Early GitHub CI remains **unit tests only** as requested, but a green unit check does not certify production readiness.

## Consistency invariants for future changes
- One server-generated UUIDv7 **`devices.id`** persists across normal upgrades; MAC/IP/hostname are mutable inventory, not credentials.
- Rust Agent → HTTPS REST → authenticated Spring Boot → PostgreSQL, with agent SQLite durable event outbox. Dashboard uses **REST as authority + WSS as scope-filtered hints**; reconnect requires post-subscription REST reconciliation.
- V0.1 monitors device/source health, **not** a hidden employee screen stream. V0.2 enables lawfully allowlisted audit. V0.3 includes visible OS-authorized capture **and** independently approved, logged recording playback.
- Image and diagram labels are explanatory, never evidence that code/OS capture has passed tests.

## Reference indices
[All project documentation](../README.md) · [20 technical diagrams](../diagrams/README.md) · [8 color images](../visuals/README.md) · [D0 gate](documentation-gate.md) · [Open decisions](open-decisions.md).
