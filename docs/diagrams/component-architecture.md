# D-02 — Component architecture (planned)

~~~mermaid
flowchart TB
  subgraph Endpoint["Windows 11 / Linux managed endpoint"]
    Source["Allowlisted OS / activity sources"]
    Agent["Rust agent: service, policy, health"]
    Store[("SQLite WAL: ordered outbox")]
    Transport["HTTPS REST uploader"]
    Companion["Visible Tauri companion — optional v0.3"]
    Capture["OS-authorized session capture — v0.3"]
    Source --> Agent
    Agent --> Store
    Store --> Transport
    Companion --> Capture
  end

  subgraph LAN["Single private LAN server"]
    Caddy["Caddy HTTPS + WSS"]
    API["Spring Boot modular monolith"]
    Identity["Identity / device enrollment"]
    Devices["Device / heartbeat health"]
    Ingest["Atomic event ingestion"]
    Query["Scoped event query"]
    WS["WS hub: in-process notifications"]
    Policy["Policy / RBAC"]
    Recording["Recording index / media — v0.3"]
    PG[("PostgreSQL 18")]
    Disk[("Local / NAS media")]
    Caddy --> API
    API --> Identity
    API --> Devices
    API --> Ingest
    API --> Query
    API --> WS
    API --> Policy
    API --> Recording
    Identity --> PG
    Devices --> PG
    Ingest --> PG
    Query --> PG
    Policy --> PG
    Recording --> PG
    Recording --> Disk
  end

  subgraph UI["Authorized dashboard browser"]
    React["React + Vite"]
    QueryCache["TanStack Query: REST truth"]
    React --> QueryCache
  end

  Transport -->|"HTTPS: heartbeat + event batch"| Caddy
  Capture -.->|"HTTPS: closed segments, approved"| Caddy
  QueryCache <-->|"HTTPS REST"| Caddy
  WS -->|"Post-commit, scoped status push"| Caddy
  Caddy -->|"WSS ephemeral JSON"| React
~~~

Transport is split deliberately: endpoint event delivery is persisted/retried; dashboard push is best-effort and REST-resynchronized. See [components](../architecture/components.md) and [WS contract](../architecture/websocket-contract.md).
