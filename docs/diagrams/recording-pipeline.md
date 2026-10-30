# D-07 — Authorized recording pipeline (v0.3 only)

~~~mermaid
flowchart LR
  Policy["Disclosed policy / scope"]
  Session["Visible user-session companion"]
  Grant{"OS capture grant active?"}
  Denied["Denied / unsupported → record gap"]
  Capture["OS-authorized session capture"]
  Segment["Finalized video segment"]
  Hash["UTC range + SHA-256 + size manifest"]
  Upload["Authenticated HTTPS upload"]
  API["Spring recording service: validate"]
  PG[("PostgreSQL metadata")]
  Disk[("Restricted local/NAS media")]
  Notify["Scope-filtered WSS status hint (no footage)"]
  Reviewer["Case-approved authenticated reviewer"]
  Guard["Recheck group, case, interval, Range"]
  Playback["Audited server-mediated playback"]
  Policy --> Session --> Grant
  Grant -->|"No"| Denied
  Grant -->|"Yes"| Capture
  Capture --> Segment --> Hash --> Upload --> API
  API --> PG
  API --> Disk
  API --> Notify
  Reviewer --> Guard
  Guard -->|"Approved interval only"| Playback
  Guard -.->|"Read verified finalized media"| Disk
~~~

Video upload and storage do **not** grant viewing permission. No direct NAS links; every playback range requires an approved review case and role/device scope. Stop capture when policy or OS grant is withdrawn. No hidden recording, keylogging, microphone or permission bypass. See [recording spec](../architecture/recording.md) and [playback center](../development/recording-playback-center.md).
