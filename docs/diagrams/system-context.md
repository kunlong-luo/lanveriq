# D-01 — System context (planned)

~~~mermaid
flowchart LR
  User["Authorized IT / audit administrator"]
  Staff["Notified device user"]
  Browser["Web browser (React/Vite)"]
  Lan["Lanveriq LAN server"]
  Windows["Windows 11 Rust Agent"]
  Linux["Linux Rust Agent"]
  Data[("PostgreSQL 18")]
  Media[("Local disk / NAS — v0.3")]

  User --> Browser
  Browser <-->|"HTTPS REST + WSS"| Lan
  Windows <-->|"Authenticated HTTPS REST"| Lan
  Linux <-->|"Authenticated HTTPS REST"| Lan
  Lan --> Data
  Lan --> Media
  Staff -.->|"Visible policy + authorized UI"| Windows
  Staff -.->|"Visible policy + authorized UI"| Linux
~~~

The system operates inside a private network without external telemetry, SaaS or AI. **WSS is dashboard-only in v0.1**. Video is not part of v0.1. See [overall architecture](../architecture/system-overview.md).
