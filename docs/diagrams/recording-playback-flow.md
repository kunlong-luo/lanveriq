# D-18 — Authorized recording and playback closure (V0.3)

~~~mermaid
flowchart TB
  Policy["Published policy + prior notice"]
  Grant{"OS user-session capture permission?"}
  No["No recording; mark gap/denial"]
  Capture["Visible capture + finalized segments"]
  Upload["Authenticated HTTPS checksum-verified upload"]
  Index[("Restricted PostgreSQL index + local media")]
  Catalog["Permission-filtered recording catalog"]
  Request["Review case: device, purpose, UTC interval"]
  Approve{"Independent approver grants scoped access?"}
  Deny["Deny and audit"]
  Gate["Spring checks session + group + case + UTC interval"]
  Clip{"Requested media wholly within approved time window?"}
  Bounded["Verified bounded rendition or fully contained segment"]
  Playback["Server-mediated playback of verified segment range"]
  Audit["Playback/export access audit"]
  Retain["Retention expiry + verifiable deletion"]
  Policy --> Grant
  Grant -->|"No"| No
  Grant -->|"Yes"| Capture
  Capture --> Upload --> Index
  Index -->|"Metadata only"| Catalog
  Catalog --> Request --> Approve
  Approve -->|"No"| Deny
  Approve -->|"Yes"| Gate
  Gate -->|"Grant valid"| Clip
  Clip -->|"Yes"| Bounded
  Clip -->|"No / clipping unavailable"| Deny
  Bounded --> Playback
  Gate -.->|"Read verified bytes after authorization"| Index
  Playback --> Audit
  Index --> Retain
  Audit --> Retain
~~~

**No direct `Index → Playback` authorization bypass:** content is loaded through the authenticated Spring media handler only **after** checking session, role, group, approved case, permitted time window and each HTTP Range request. **HTTP Range byte offsets are not equivalent to UTC timestamps**; a segment crossing case boundaries must be verified clipped or denied. Missing/corrupt/unavailable segments show gaps. Stored playback is **not** live screen streaming. [Recording & Playback Center](../development/recording-playback-center.md).
