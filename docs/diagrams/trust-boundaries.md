# D-12 — Trust boundaries and authorized flows

~~~mermaid
flowchart TB
  subgraph UserSpace["Endpoint user session — OS privacy boundary"]
    Notice["Published policy / visible state"]
    Screen["OS screen capture permission (v0.3)"]
    Notice --> Screen
  end
  subgraph Endpoint["Managed endpoint — not intrinsically trusted"]
    Collect["Scoped sources"]
    Rust["Rust Agent"]
    Queue[("SQLite on local disk")]
    Collect --> Rust --> Queue
  end
  subgraph Gateway["LAN entry point / verified TLS boundary"]
    Proxy["Caddy HTTPS/WSS"]
  end
  subgraph Application["Server authorization boundary"]
    Auth["Session/device authentication + RBAC"]
    API["Spring Boot REST + WS notification hub"]
    Auth --> API
  end
  subgraph Storage["Private storage boundary"]
    PG[("PostgreSQL")]
    NAS[("Media volume — restricted v0.3")]
  end
  Browser["Authorized admin browser"]
  Queue -->|"Mutually authenticated or proof-of-possession HTTPS"| Proxy
  Screen -.->|"Authorized finalized media only"| Proxy
  Browser <-->|"HTTPS REST + WSS"| Proxy
  Proxy --> Auth
  API --> PG
  API -.-> NAS
~~~

**Zero implicit LAN trust:** IP address or company ownership does not authorize surveillance. Browser has no direct PostgreSQL/NAS access; all sensitive reads and WSS audiences are server scoped. See [threat model](../security/threat-model.md).
