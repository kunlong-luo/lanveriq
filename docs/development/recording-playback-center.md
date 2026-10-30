# Recording & Playback Center — V0.3 screen specification

**Routes:** `/recordings`, `/recordings/:sessionId`, `/review-cases`. **Status:** later-stage feature, **not implemented**. This center covers **authorized stored recordings**, not realtime hidden surveillance.

## Required preconditions
Approved, prior organization notice; eligible device group and business purpose; OS/session-specific permission; visible recording status; restricted retention; approved reviewer case. A managed-device owner cannot silently bypass user-session capture permissions.

## Pages
1. **Recording catalog:** authorized device/group/time/status filters; session rows show start/end, permission source, segment count/size, completion status, last verified upload, retention expiry and **recording gaps**. No automatic thumbnail of potentially sensitive employee content.
2. **Session detail:** bounded timeline of uploaded, missing/corrupted, paused and policy-disallowed intervals; UTC and viewer local time; integrity/source metadata.
3. **Review case:** reason, requesting user, approver **must be a different authorized person** from the requester (self-approval denied), eligible device IDs, explicit allowed UTC interval, expiration, requested scopes, approval/denial and access audit.
4. **Playback:** server-mediated authenticated stream of finalized/verified segments with HTTP Range support, seek, gap indication, playback error state and a prominent case reference. **HTTP byte Range is not a recording time interval.** Map the approved UTC interval to only permitted frames/media and re-check role/group/case/time for requests and byte ranges; never expose raw filesystem paths or a public NAS object URL.
5. **Retention and deletion:** show expiry policy and removal status; deleted or missing media is unavailable, never silently pretended complete.

## Workflow
`User session + visible permission → media segment finalized → hash and manifest → authenticated upload → PostgreSQL index → restricted catalog → request access → approve purpose/time range → authenticated playback → access audit → retention deletion`.

## API proposal (v0.3, exact OpenAPI to be frozen before code)
| Method | Resource | Permission |
|---|---|---|
| GET | `/api/v1/recordings?device_id=&from=&to=&cursor=` | authorized restricted catalog metadata |
| GET | `/api/v1/recordings/{sessionId}` | scoped recording metadata and gap summary |
| GET | `/api/v1/recordings/{sessionId}/segments` | reviewer scoped metadata; no filesystem paths |
| POST | `/api/v1/review-cases` | authorized request, purpose and desired time scope |
| POST | `/api/v1/review-cases/{caseId}/approve` | independently authorized approver **different from requester**, with role, group and time checks; deny self-approval |
| GET | `/api/v1/review-cases/{caseId}/recordings/{sessionId}/stream` | **case-bound media**; Range requests permitted only within approved interval |

Recording agent-upload endpoints remain specified separately in [REST API](../architecture/api-contract.md). No case/credential tokens in URL; case IDs are opaque resource IDs, not capabilities. Every request re-checks the authenticated session and approved grant.

## Time-window-to-media authorization (V0.3 gate)
An approval for **10:00–10:05** does not authorize a complete recording segment spanning **09:58–10:08** even if an HTTP `Range` header is valid. Before serving media, map source timestamps to allowed frames and either (a) use a **tested, server-mediated, time-bounded rendition** created with maintained media tooling or (b) serve **only entire verified segments fully inside** the approved window. If neither works, deny access and show the unavailable boundary; **never expand approval to an overlapping full segment**. Apply checks to seeks, multipart/byte ranges, downloads, caches, expired/revoked cases and changing group membership, with `Cache-Control: no-store` for sensitive streams. Test cross-boundary frames as well as byte-range negatives. The technique is **not yet implemented or selected**; owner approval and media testing are required before V0.3.

## Fail-closed and operational issues
- No policy/OS grant? no capture. Case denied/expired/revoked? no playback. Session unavailable due to lock/logout? show a gap.
- Segment missing, corrupt, incomplete, moved or expired? show unavailable and record incident, do not serve unverified bytes.
- Restrict concurrent streams, access logs, client caching and exports; redact secrets and honor retention.
- No unrestricted live screen view, webcam/microphone, covert install or bypass. Live session streaming requires a **separate later design and permission gate**.

## Closure and test expectations
Manual recording permission denial/withdrawal; segment finalization/retry/sha256; case scope/approval/expiry/revocation and rejected self-approval; metadata-only list visibility; time-window-to-frame verification and 206 Partial Content/Range boundary tests including overlapping segments; playback reads and export audit; retention deletion and restore tests. Unit-only CI remains the early automation scope.

**Diagram:** [Playback center closure flow](../diagrams/recording-playback-flow.md). [Recording architecture](../architecture/recording.md). [Functional closure](../product/functional-closure.md).
