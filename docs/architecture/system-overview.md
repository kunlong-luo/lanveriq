# Architecture overview

**Status (2026-11-08):** v0.1 D0 technical design approved; **parts of Agent/Rust, Java/Spring and Next.js source plus synthetic tests exist**, but enrollment→Agent→TLS→PG18→authenticated Next.js is not complete and nothing is production enabled. See [v0.1 business batches](../planning/v0.1-batched-delivery-plan-2026-11-08.md).

[Color overview for non-engineers](../visuals/assets/01-overview.svg) · [Product infographic gallery](../visuals/README.md). These assets are **concepts**, not deployed-system screenshots.

## Deployment
The **proposed private LAN** host runs Caddy TLS, one Spring Boot 4 modular-monolith service, PostgreSQL 18 and an isolated **private Next.js App Router runtime** for authenticated HTML/RSC. The public GitHub Pages export is a **separate, deliberately disconnected static showcase**. Protected local/NAS media storage is **v0.3 only** and not a prerequisite for v0.1. The v0.1 dashboard uses **HTTPS REST for authoritative snapshots/mutations plus authenticated WSS for scoped, best-effort status notifications**, per [ADR-0005](adrs/0005-rest-plus-websocket.md) and the [WS contract](websocket-contract.md). Windows 11 and Linux terminals run a Rust service with bundled SQLite WAL. Optional Tauri 2 companion runs in the logged-in user session for device status, policy notice and permission-dependent recording.

~~~mermaid
flowchart TD
  subgraph Endpoints["Authorized Windows/Linux endpoints"]
    R["Rust Service: heartbeat, scoped audit, outbox"]
    S[("SQLite + WAL")]
    C["Optional Tauri user-session companion"]
    V["OS-permitted capture"]
    R <--> S
    C --> V
  end
  subgraph Server["LAN server"]
    P["Caddy: TLS"]
    API["Spring Boot: enrollment, ingestion, query, RBAC"]
    PG[("PostgreSQL 18")]
    NAS[("Local video disk / NAS")]
    WEB["Private Next.js App Router (future authorized runtime)"]
    WS["WebSocket notifier: in-process, after commit"]
    P --> API
    P --> WEB
    API --> PG
    API --> NAS
    API --> WS
  end
  R -->|"Authenticated HTTPS batches"| P
  V -->|"Authorized segmented uploads"| P
  Admin["Authorized browser"] <-->|"HTTPS REST + WSS"| P
~~~

**Diagrams:** [system context](../diagrams/system-context.md) · [component architecture](../diagrams/component-architecture.md) · [deployment](../diagrams/deployment-topology.md) · [trust boundaries](../diagrams/trust-boundaries.md).

## Trust boundaries
- An endpoint is not trusted merely because it is on the LAN. Server authenticates agent identity and checks every event's bounds, scope and format.
- LAN transport uses verified TLS; D-07 selects separate browser, pre-enrollment and enrolled-Agent TLS hosts (Agent mTLS only, TCP h1/h2 with strict SNI/Host), with revocable per-device identity. Never trust IP address or client-injected certificate headers.
- The dashboard may never read PostgreSQL or NAS directly. Every API, video read and export requires server-side authorization and audit.
- Screen capture is performed only in a declared user session with OS approval when required; a privileged system service cannot circumvent capture prompts.
- Endpoint evidence is best-effort, subject to manipulation by local administrators; hashes detect accidental transfer corruption, not all endpoint tampering.

## Interaction flows
**Enrollment (D-07 design with internal JPA/CSR services and a default-OFF heartbeat HTTP source only; not a full running protocol):** Admin creates expiring single-use code → Agent verifies enrollment-host TLS and prior notice, generates CSR → token is consumed atomically and UUIDv7 device enters `pending_certificate` → approved operator signs/imports leaf certificate via established PKI tooling and delivers it securely → Agent proves mTLS possession on isolated Agent host → Spring verifies active credential and device ID → heartbeat begins. No automatic credential issuing service is claimed. See [LAN bootstrap](enrollment-bootstrap.md) and [enrollment sequence](../diagrams/enrollment-sequence.md).
**Event delivery:** Source adapter filters/redacts → SQLite transaction commits immutable event + monotonic sequence + cursor → uploader sends bounded REST batch → PostgreSQL commits **all or none** with conflict checks → server confirms accepted sequences → Agent deletes **only explicit confirmed rows**. After commit, an in-process WS hub may emit an **ephemeral, scope-filtered status hint**; on reconnect the dashboard refetches REST. See [event delivery](event-delivery.md) and [WS semantics](websocket-contract.md).
**Recording:** Published policy + OS permission + visible state → session-local recorder writes closed segments → checksums/manifests → authenticated transfer → server validates, indexes and restricts playback.

## Modular backend domains
Auth/Users · Devices/Enrollment · Telemetry/Ingest · Audit/Timeline · Recording/Media · Policies/Retention · AdminAudit. Domains share an application but not each other's persistence internals; enforce boundaries in package/API design.

## Failure states
offline, stale, unsupported, permission_denied, recording_stopped, full_disk, corrupt_queue, clock_skew and collection_gap are distinct states; do not invent activities between recorded events.
