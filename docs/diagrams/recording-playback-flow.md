# D-18 — Authorized recording and playback closure (V0.3)

~~~mermaid
flowchart TB
  Policy["Published policy + prior notice"]
  Grant{"OS user-session capture permission?"}
  No["No recording; mark gap/denial"]
  Capture["Visible capture + finalized segments"]
  Upload["HTTPS hash-verified upload"]
  Index[("PostgreSQL recording index + protected media")]
  Catalog["Restricted recording catalog"]
  Request["Review case: device + reason + UTC range"]
  Approve{"Authorized independent approval?"}
  Deny["Deny and audit"]
  Playback["Authenticated, range-scoped playback"]
  Audit["Playback/export access audit"]
  Retain["Retention expiration and deletion"]
  Policy --> Grant
  Grant -->|"No"| No
  Grant -->|"Yes"| Capture
  Capture --> Upload --> Index --> Catalog
  Catalog --> Request --> Approve
  Approve -->|"No"| Deny
  Approve -->|"Yes"| Playback
  Index --> Playback
  Playback --> Audit --> Retain
  Index --> Retain
~~~

Stored playback is **not realtime screen streaming**; separate permission/design is required for live screen previews. Neither hidden recording nor bypassing OS grants is permitted. [Center UI and API](../development/recording-playback-center.md).
