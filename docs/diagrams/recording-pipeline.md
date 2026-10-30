# D-07 — Authorized recording pipeline (v0.3, not v0.1)

~~~mermaid
flowchart LR
  Policy["Published organization policy"]
  Session["Visible user-session companion"]
  Grant{"OS screen permission granted?"}
  Capture["OS-authorized capture"]
  Segment["Closed video segment"]
  Hash["Manifest: UTC range + bytes + SHA-256"]
  Upload["Authenticated HTTPS upload"]
  API["Spring recording service"]
  PG[("PostgreSQL metadata")]
  Video[("Restricted local/NAS storage")]
  Notify["Restricted WS status notification"]
  Denied["Permission denied / unsupported"]
  Gap["Explicit capture_gap"]
  Case["Approved review case + RBAC"]
  Playback["Audited browser playback"]
  Policy --> Session
  Session --> Grant
  Grant -->|Yes| Capture
  Grant -->|No| Denied
  Denied --> Gap
  Capture --> Segment --> Hash --> Upload --> API
  API --> PG
  API --> Video
  API --> Notify
  Video -->|Server-mediated only| Playback
  Case --> Playback
~~~

Use only fully finalized segments and server-generated object paths. Stop recording when policy or OS grant is withdrawn. No hidden capture, permission bypass, keylogging or audio by default. See [recording spec](../architecture/recording.md).
