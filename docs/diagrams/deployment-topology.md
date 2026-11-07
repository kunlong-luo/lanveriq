# D-03 — Private LAN deployment topology

~~~mermaid
flowchart TB
  subgraph Private["Private LAN — authenticated hosts only"]
    subgraph Server["One Linux server host (Docker Compose)"]
      Proxy["Caddy: HTTPS 443 / WSS"]
      App["Spring Boot API + in-memory WS hub"]
      Front["Private Next.js App Router runtime — proposed, not activated"]
      PG[("PostgreSQL: private container network")]
      Media[("Restricted video volume — v0.3")]
      Proxy -->|"Protected HTML/RSC only after Spring session check; public static assets"| Front
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

**Target topology under NXT-11 review, not a deployable configuration.** Only Caddy TCP 443 may be reachable from approved LAN clients. The Next.js SSR/RSC runtime must be private to Caddy, Spring upstream HTTPS/mTLS must be private, and PostgreSQL must never be exposed on the LAN. In the accepted disposable CI only, Next.js binds `127.0.0.1:3100`; the production namespace/firewall design and Container/Host reachability have not been reviewed or deployed. Install/verify the private CA through an authorized provisioning channel. The recording mount is optional later. See [deployment](../architecture/deployment.md).
