# REST and event protocol contract v0.1 (proposed)

**Base:** `/api/v1` over HTTPS only. Dashboard **WSS /ws** is documented separately in [WebSocket contract](websocket-contract.md) and is not used for endpoint batch ACKs. JSON UTF-8; server controls authorization. Version via both URL major and `schema_version` per event. This is an API **design**, not an OpenAPI implementation. The canonical delivery semantics are in [Event Delivery](event-delivery.md); the API and SQLite writer must conform to that one source of truth.

## Transport split
Rust Agent ↔ server: authenticated REST for enrollment, heartbeat and batches; browser ↔ server: REST for snapshots/mutations + authenticated WSS notifications. On every WS reconnect, the browser refetches scoped REST data. See [ADR-0006](adrs/0006-agent-rest-dashboard-ws.md).

## Device API
| Method | Path | Principal | Semantics |
|---|---|---|---|
| POST | /enrollments | authenticated admin | Create one-use short-lived device code, device group, expiry |
| POST | /devices/enroll | pairing-capable installer | One-time token exchange after server certificate validation |
| POST | /devices/{id}/heartbeats | bound device credential | 30s status, version, queue and *latest sampled* CPU/RAM/volume health; **not** complete hardware info |
| POST | /devices/{id}/event-batches | bound device credential | Validate, insert-once, durable ack |
| GET | /devices | authorized operator | Scope-filtered paginated inventory |
| GET | /monitoring/overview | scoped IT operator | Server-derived online/stale/offline counts with `as_of`; never bypass group scope |
| GET | /monitoring/devices | scoped IT operator | Bounded fleet health/queue and permitted hardware summary; optional primary IP only if unambiguous/authorized |
| GET | /devices/{id} | authorized operator | Device detail, capability and health |
| POST | /devices/{id}/inventory | bound device credential | Idempotent bounded OS/CPU/RAM/NIC/IP/MAC/volume snapshot, versioned; **not** an event-batch ACK |
| GET | /devices/{id}/inventory | scoped IT/security operator | Restricted current inventory incl. multiple NICs/IPs and volume capacities |
| GET | /devices/{id}/metrics | scoped IT/security operator | Bounded host CPU/RAM/disk samples, time-range/cursor filters |
| POST | /devices/{id}/revoke | privileged admin | Disable identity and future uploads |
| GET | /devices/{id}/activity | scoped auditor | Time-range and kind-filtered activity |
| GET | /devices/{id}/audit-events | scoped auditor | Time-range and event filters |
| GET | /devices/{id}/collection-gaps | scoped IT operator (health-only), scoped auditor (approved detail) | Role-specific projections; never expose sensitive event fields to a health-only operator |
| GET | /admin/access-log | privileged auditor | Immutable viewing/action trace |

## V0.3 restricted recording APIs (design only)
The **Agent upload** plane has separate authenticated endpoints: `POST /recording-sessions`, `POST /recording-segments/init`, `PUT /recording-segments/{id}/content`, `POST /recording-segments/{id}/complete`, each requiring the enrolled device's credential, allowed recording policy and verified upload constraints.

The **reviewer plane** uses `GET /recordings`, `GET /recordings/{sessionId}`, `GET /recordings/{sessionId}/segments`, `POST /review-cases`, `POST /review-cases/{caseId}/approve` and `GET /review-cases/{caseId}/recordings/{sessionId}/stream`. Playback requires logged-in identity, device-group scope, approved case and a bounded authorized time interval on **every** Range request. Never expose a case-less `GET /recordings/{id}/play` or direct NAS link. A case identifier is not a bearer secret; an expired/revoked grant fails closed. See [Recording & Playback Center](../development/recording-playback-center.md).

## Identity and scope invariants
The canonical Agent ID is **server-created `devices.id` as PostgreSQL UUIDv7**. Public payloads call it `device_id`, and authenticated credentials must bind the same device ID; MAC, IP, hostname and serial are never device auth. **V0.1 already needs minimal admin role + device-group authorization** for inventory, monitoring queries and WSS subscriptions. V0.2 expands sensitive audit/search/export roles; never defer V0.1 isolation to V0.2. See [Device Identity](device-identity.md) and [RBAC](../security/authorization-matrix.md).

## Asset inventory contract
See [Device Inventory](device-inventory.md) for the typed snapshot body, sampling (static at startup/change/24h, dynamic around 60–120s), MAC/IP source labels, full-snapshot transaction and policy limitations. The server stamps `received_at` and trusted `server_observed_remote_ip`. Hardware identity is **not** derived from MAC, IP, hostname, disk or CPU. Service authenticates `device_id` by bound credential even for inventory uploads. The only canonical identifier is server-assigned `devices.id UUIDv7`.

## Request envelopes
Enrollment request fields: `token`, `agent_version`, `os`, `capabilities`, `device_public_key`, `policy_notice_version`. The server must authenticate identity beyond LAN IP; token only bootstraps identity. Enrollment tokens are not reused as ongoing API keys. On a lost local database/identity, a reinstall enrolls as a **new device_id**, leaving old evidence under a revoked historical identity. Token consumption must be atomic.

Heartbeat fields: `device_id`, `sent_at`, `agent_version`, `status`, `capabilities`, `queue_count`, `queue_bytes`, `oldest_pending_at`, `last_successful_upload_at`, `dropped_count`, `integrity_degraded`, `collection_errors`. Server stamps `received_at` and derives online/stale/offline. See [health states](health-and-gap-semantics.md).

Event batch (**synthetic protocol fixture only**; `system_audit/program_started` is not a real V0.1 collector and requires a later V0.2 approved source allowlist):
~~~json
{
  "schema_version": 1,
  "batch_id": "7ba33d0d-9340-47ad-b4c7-70a5080ad239",
  "device_id": "018f9fad-5c71-7db1-b89e-b71f8d620f44",
  "events": [
    {
      "event_id": "6782a074-e42c-4198-adfa-5f6ab57d7d43",
      "device_sequence": 101,
      "schema_version": 1,
      "source": "system_audit",
      "event_type": "program_started",
      "policy_version": 1,
      "occurred_at": "2026-10-30T09:00:00Z",
      "payload": {"executable": "example-app"}
    }
  ]
}
~~~

Response after DB commit:
~~~json
{"schema_version":1,"accepted_sequences":[101],"server_time":"2026-10-30T09:00:02Z","request_id":"a2fdba9e-fb11-42fd-ad05-3b7f19cbe0b2"}
~~~

## All-or-nothing transaction (v0.1)
A batch is limited to **100 events and 512 KiB**, with no partial acknowledgments. Server validates device identity, schema version, policy/source allowlist and all events before it commits. A fully valid batch inserts or recognizes identical duplicates in **one PostgreSQL transaction**. A failed batch commits nothing. Only confirmed `accepted_sequences` may be marked acknowledged; never delete everything below a single high-watermark. Same `device_id + device_sequence` with different event content (or same `event_id` mapped to a different sequence) is `409 sequence_conflict` and requires a halted/quarantined batch, not overwrite. See [Event Delivery](event-delivery.md).

## Error and retry
- 400 invalid syntax; 401 no identity; 403 revoked/out-of-scope; 409 identity or sequence payload conflict; 413 oversized; 422 field/schema validation; 429 backoff; 5xx retry. Network timeout, 429 and 5xx retain the local batch and use bounded retry, not repeated immediate retries.
- Stable `code`, `message`, `request_id`, optional `retry_after_seconds`. Freeze these error semantics and role-scoped response projections in OpenAPI **before** Orval generation; no non-2xx response counts as an ACK. Never echo credentials or sensitive payload.
- A duplicate sequence with identical immutable canonical event content is idempotent; a conflict in sequence or event ID with different content is an audit-worthy **conflict**, not an overwrite. Canonical event fields are `event_id`, `device_sequence`, `schema_version`, `source`, `event_type`, `occurred_at`, `payload`, `policy_version`. The database adds server-received `received_at`.
- Limit pagination and time range. Validate `device_id` against credential, not the body alone. Query ordering uses stable `received_at, event_id` cursor keys; source timestamps are untrusted and should not silently reorder evidence. Admin device/time/data-category scopes are enforced before database rows are returned; see [permission matrix](../security/authorization-matrix.md).

## Authentication
Initial certificate trust and device pairing are specified in [LAN bootstrap](enrollment-bootstrap.md). 

Admin: secure session with MFA support and role/device-group scopes; Agent: revocable unique credential bound to device. **mTLS is the preferred technical candidate, not a fully frozen contract**: CA/certificate issuance, enrollment, proxy identity binding, rotation and revocation remain unresolved in [D-07](../governance/open-decisions.md). Browser and pre-enrollment requests must not inherit mandatory post-enrollment Agent certificate checks. No insecure plaintext HTTP exception on a trusted LAN.
