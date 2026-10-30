# Architecture overview

**Status:** Proposal, no implementation.

## Deployment
A private LAN host runs Caddy TLS, one Spring Boot 4 modular-monolith service, PostgreSQL 18, a static React/Vite dashboard and a protected local/NAS video volume. Windows 11 and Linux terminals run a Rust service with bundled SQLite WAL. Optional Tauri 2 companion runs in the logged-in user session for device status, policy notice and permission-dependent recording.

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
    WEB["React/Vite static SPA"]
    P --> API
    P --> WEB
    API --> PG
    API --> NAS
  end
  R -->|"Authenticated HTTPS batches"| P
  V -->|"Authorized segmented uploads"| P
  Admin["Authorized browser"] -->|"HTTPS"| P
~~~

## Trust boundaries
- An endpoint is not trusted merely because it is on the LAN. Server authenticates agent identity and checks every event's bounds, scope and format.
- LAN transport uses TLS; enrollment is one-time with identity binding and revocation. Never trust IP address alone.
- The dashboard may never read PostgreSQL or NAS directly. Every API, video read and export requires server-side authorization and audit.
- Screen capture is performed only in a declared user session with OS approval when required; a privileged system service cannot circumvent capture prompts.
- Endpoint evidence is best-effort, subject to manipulation by local administrators; hashes detect accidental transfer corruption, not all endpoint tampering.

## Interaction flows
**Enrollment:** Admin creates expiring single-use code → agent verifies server certificate → local notice presented → code exchanged for device identity → server marks code used → heartbeat begins.
**Event delivery:** Source adapter filters/redacts → SQLite transaction commits event+cursor → uploader sends bounded batch with device sequence → PostgreSQL unique constraint deduplicates → server replies only after durable commit → agent marks acknowledged.
**Recording:** Published policy + OS permission + visible state → session-local recorder writes closed segments → checksums/manifests → authenticated transfer → server validates, indexes and restricts playback.

## Modular backend domains
Auth/Users · Devices/Enrollment · Telemetry/Ingest · Audit/Timeline · Recording/Media · Policies/Retention · AdminAudit. Domains share an application but not each other's persistence internals; enforce boundaries in package/API design.

## Failure states
offline, stale, unsupported, permission_denied, recording_stopped, full_disk, corrupt_queue, clock_skew and collection_gap are distinct states; do not invent activities between recorded events.
