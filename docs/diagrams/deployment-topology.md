# D-03 — Private LAN deployment topology

~~~mermaid
flowchart TB
  subgraph Private["Private LAN — authenticated hosts only"]
    subgraph Server["One Linux server host (Docker Compose)"]
      Proxy["Caddy: HTTPS 443 / WSS"]
      App["Spring Boot API + in-memory WS hub"]
      Front["React/Vite static files"]
      PG[("PostgreSQL: private container network")]
      Media[("Restricted video volume — v0.3")]
      Proxy --> Front
      Proxy --> App
      App <--> PG
      App -.-> Media
    end
    subgraph W11["Managed Windows 11"]
      WinSvc["Native Rust Windows Service"]
      WinDB[("Local SQLite WAL")]
      WinSvc <--> WinDB
    end
    subgraph Linux["Managed Linux desktop"]
      LinSvc["Native Rust systemd service"]
      LinDB[("Local SQLite WAL")]
      LinSvc <--> LinDB
    end
    Admin["Authorized browser"]
    WinSvc -->|"HTTPS REST"| Proxy
    LinSvc -->|"HTTPS REST"| Proxy
    Admin <-->|"HTTPS REST / WSS"| Proxy
  end
~~~

Only the trusted HTTPS entry point is reachable by enrolled endpoints/admin workstations; PostgreSQL is **not** exposed on the LAN. Install/verify the private CA through an authorized provisioning channel. The recording mount is optional later. See [deployment](../architecture/deployment.md).
