# REST and event protocol contract v0.1 (proposed)

**Base:** `/api/v1` over HTTPS only. Dashboard **WSS /ws** is documented separately in [WebSocket contract](websocket-contract.md) and is not used for endpoint batch ACKs. JSON UTF-8; server controls authorization. Version via both URL major and `schema_version` per event. This is an API **design**, not an OpenAPI implementation. The canonical delivery semantics are in [Event Delivery](event-delivery.md); the API and SQLite writer must conform to that one source of truth.

## Transport split
Rust Agent ↔ server: authenticated REST for enrollment, heartbeat and batches; browser ↔ server: REST for snapshots/mutations + authenticated WSS notifications. On every WS reconnect, the browser refetches scoped REST data. See [ADR-0006](adrs/0006-agent-rest-dashboard-ws.md).

## Device API
| Method | Path | Principal | Semantics |
|---|---|---|---|
| POST | /enrollments | authenticated admin | Create one-use short-lived device code, device group, expiry |
| POST | /devices/enroll | pairing-capable installer on enrollment host | Validate token + CSR; create pending_certificate UUIDv7, **not an active credential** |
| GET | /devices/{id}/enrollment-csr | scoped authorized security admin on console host | Return stored pending CSR + server device/group/expiry metadata for independent operator review; no private key |
| POST | /devices/{id}/client-certificates | scoped authorized security admin on console host | Validate a separately signed clientAuth leaf/chain against pending CSR/SPKI, trusted client CA and device ID; atomically register pending credential fingerprint; no certificate signing on web server |
| POST | /devices/{id}/activate | bound pending-certificate Agent on **mTLS Agent host** | Verify registered certificate fingerprint and atomically activate pending enrollment; same-cert retries idempotent |
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
Enrollment request fields for the operator-mediated mTLS candidate: `schema_version`, `token`, `agent_version`, `os`, `capabilities`, `csr_pem`, `policy_notice_version`. The server must authenticate identity beyond LAN IP; token only bootstraps identity. Enrollment tokens are not reused as ongoing API keys. On a lost local database/identity, a reinstall enrolls as a **new device_id**, leaving old evidence under a revoked historical identity. Token consumption must be atomic.

Heartbeat fields: `device_id`, `sent_at`, `agent_version`, `status`, `capabilities`, `queue_count`, `queue_bytes`, `oldest_pending_at`, `last_successful_upload_at`, `dropped_count`, `integrity_degraded`, `collection_errors`. Server stamps `received_at` and derives online/stale/offline. See [health states](health-and-gap-semantics.md).

Event batch (**V0.1 operational gap fixture**, not a user-activity or application collector; payload contains only bounded transport health):
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
      "source": "agent_transport",
      "event_type": "collection_gap",
      "policy_version": 1,
      "occurred_at": "2026-10-30T09:00:00Z",
      "payload": {"affected_source": "agent_transport", "reason": "quota_exceeded", "range_start": "2026-10-30T08:57:00Z", "range_end": "2026-10-30T09:00:00Z"}
    }
  ]
}
~~~

Response after DB commit:
~~~json
{"schema_version":1,"accepted_sequences":[101],"server_time":"2026-10-30T09:00:02Z","request_id":"a2fdba9e-fb11-42fd-ad05-3b7f19cbe0b2"}
~~~

## D-11/D-22 V0.1 wire-contract review candidates (NOT YET FROZEN)

**Decision rule:** D0 owner must explicitly approve this review surface; adding illustrative JSON does not approve or implement an API. The canonical transport split, authorization, event sequencing and inventory-CAS rules remain in [Event Delivery](event-delivery.md), [Inventory](device-inventory.md), [Enrollment](enrollment-bootstrap.md) and [RBAC](../security/authorization-matrix.md). Once accepted, generate actual OpenAPI with Springdoc and TS clients with Orval *after* code starts; fail on schema drift rather than hand-maintain parallel type definitions.

### Candidate endpoint ownership and version rules

- API major prefix `/api/v1`; JSON uses `snake_case`, `schema_version: 1` for versioned payloads, integer `policy_version`, UTC RFC 3339 timestamps with `Z`, integer byte counts, positive monotonic 64-bit `device_sequence`/`inventory_revision`, UUIDv7 **server** device IDs and persisted UUID **Agent** event/snapshot IDs.
- `console` host: administrator session REST, `/ws` WSS and restricted monitoring queries. `enroll` host: pairing request only, TLS server verification and a one-use secret; new device has no client certificate yet. `agent` host: **mTLS-only** enrolled-device routes, with a narrowly authorized pending-certificate **activation** endpoint. Hostnames are examples; D-07 signing/route controls must be approved before implementation.
- Required fields must not disappear; nullable fields are explicit `null` **with a capability/error reason** for unavailable data. An **unauthorized field is omitted entirely** from read projections (not `null` with a revealing reason). Unknown mandatory schema versions fail with `422 unsupported_schema_version`; additive, optional response properties may be added within `v1`. No automatic widening of collection rights.
- **V0.1 accepted canonical event pairs** proposed for deployed Agent: `agent_transport / collection_gap` (minimal operational coverage; allowlisted keys `affected_source`, `reason`, `range_start`, `range_end`). Development/test profile may add `synthetic_test / protocol_fixture` using invented data; production configuration must reject this pair. No process titles, URLs, window activity, system audit details, recordings or other V0.2+ sources in V0.1.
- Batch cap: 100 events and 512 KiB; inventory cap: 128 KiB, 32 NICs, 16 IPs/NIC, 64 volumes, 256 characters per bounded string, subject to the [inventory limits](device-inventory.md). Reject over-limit requests before writes. Idempotent duplicate event-batches return the same acknowledged sequences **after** commit; a conflicting event ID/sequence rejects the *entire* batch.

### Candidate one-use enrollment (manual certificate issuance pilot)

`POST /api/v1/devices/enroll` request (to enrollment host; no Agent mTLS certificate yet):
~~~json
{
  "schema_version": 1,
  "token": "REDACTED_ONE_USE_TOKEN",
  "agent_version": "0.1.0",
  "os": {"family": "linux", "arch": "x86_64"},
  "capabilities": {"hardware_inventory": "supported", "desktop_session": "unavailable"},
  "csr_pem": "PEM_CSR_PLACEHOLDER_NOT_A_REAL_KEY",
  "policy_notice_version": 1
}
~~~
Proposed `202 Accepted` after the token is **atomically consumed** and the server creates a UUIDv7 `device_id` in `pending_certificate` state:
~~~json
{
  "schema_version": 1,
  "device_id": "018f9fad-5c71-7db1-b89e-b71f8d620f44",
  "enrollment_state": "pending_certificate",
  "server_time": "2026-10-30T09:00:02Z",
  "request_id": "a2fdba9e-fb11-42fd-ad05-3b7f19cbe0b2"
}
~~~
The CSR signing and trusted out-of-band leaf delivery follow [D-07](enrollment-bootstrap.md); no private key is returned or sent. `device_public_key` is derivable from a validated CSR and must not be separately accepted as a contradictory identity. First valid enrolled mTLS **activation request** activates the credential; pending Agent credentials cannot use heartbeats/events/inventory. Reusing the pairing token fails; recovery is an administrator-audited new attempt. Neither this response nor a device ID is proof of an active authenticated device.

### Candidate security-admin CSR review and signed-certificate registration (console only)

These endpoints complete the **operator-mediated offline signing** loop; otherwise `pending_certificate` devices would have no auditable path to an active credential:

1. `GET /api/v1/devices/{id}/enrollment-csr`: only a separately authorized, group-scoped security administrator over the console's authenticated session and read audit. Returns a **pending** certificate-signing request and immutable server-derived `device_id`, group, CSR digest, policy/notice binding and pending expiry. CSR is public key material, **not** a private key; it still contains untrusted subject/extension requests that the operator must not adopt blindly.
2. Operator validates the device/group and CSR digest with the requestor, signs **offline** using the reviewed client-only CA and a maintained `step certificate sign --template ...` workflow. The issuer-controlled template **overrides** untrusted CSR subject/SAN requests with a server-authorized device identity, enforces **CA=false, digitalSignature, clientAuth EKU only**, suitable algorithm, bounded lifetime and unique issuer serial. Never use the `step` default leaf profile unchanged because it can include both serverAuth and clientAuth; no private CA signing key is deployed to Spring or its web service. Verify the output using maintained certificate tooling before registration.
3. `POST /api/v1/devices/{id}/client-certificates` (console-admin session **plus CSRF**, restricted role/group, audit) accepts **only the public PEM leaf and issuer chain**, and a reference to the pending CSR. Spring verifies issuer chain against the dedicated approved client CA, validity window, EKU/basic constraints, registered CSR **SPKI/public-key match**, authoritative device-ID identity (not CSR-supplied), unique credential fingerprint/serial, pending enrollment and absence of conflicting registrations. Perform the pending-credential registration in one PostgreSQL transaction; do **not** mark the device `active`.
4. The operator transfers the signed **public leaf/chain only** to the authorized endpoint through a separately verified channel and checks its fingerprint. No unauthenticated certificate-pickup URL or secret-bearing query string is implied. The Agent holds the corresponding private key locally and calls the separate mTLS `/activate` route.

**Suggested bounded response semantics:** 200 CSR fetch; 201 certificate registration; 409 conflict for consumed/mismatched pending identity or duplicate registered fingerprint; 410 pending enrollment expired; 422 invalid CSR/issuer/leaf/profile; 403 admin without assigned group/permission. Wrong-group calls use the API's non-enumerating 404 policy. **Missing private key, invalid CA, revoked leaf or inappropriate EKU cannot be repaired by ignoring TLS validation.**

**Still a D0 proposal:** exact DTO field/endpoint naming, issuer template, certificate fingerprints/chain handling, admin permission and error status must be signed off under D-07/D-11/D-22; don't present this as compiled or OpenAPI-generated.

### Candidate first proof and activation endpoint (Agent mTLS host only)

`POST /api/v1/devices/{id}/activate` is the **sole pending → active transition**. It requires a Caddy-verified client certificate, a **registered pending credential fingerprint**, an unexpired `pending_certificate` enrollment, exact path/body `device_id`, a non-revoked credential and an atomic PostgreSQL transition. It **does not** accept a pairing token or caller-supplied fingerprint. A different device/certificate, expired pending state or unknown CSR/leaf must fail. Retry with the **same already-active bound certificate** is idempotent (does not create or reassign device ID).

~~~json
{
  "schema_version": 1,
  "device_id": "018f9fad-5c71-7db1-b89e-b71f8d620f44",
  "agent_version": "0.1.0"
}
~~~

Proposed successful response:
~~~json
{
  "schema_version": 1,
  "device_id": "018f9fad-5c71-7db1-b89e-b71f8d620f44",
  "enrollment_state": "active",
  "server_time": "2026-10-30T09:00:30Z",
  "request_id": "a2fdba9e-fb11-42fd-ad05-3b7f19cbe0b2"
}
~~~

**All heartbeat/event/inventory routes reject a pending certificate until activation has committed.** The single signed-leaf registration route is console-admin-only (no CA private key exposed); pending-CSR fetch is likewise restricted and audited. Use exact certificate identity and database state on each subsequent request; a merely trusted client CA is not enough.

### Candidate Agent heartbeat

`POST /api/v1/devices/{id}/heartbeats` **only** on the enrolled-Agent mTLS host:
~~~json
{
  "schema_version": 1,
  "device_id": "018f9fad-5c71-7db1-b89e-b71f8d620f44",
  "sent_at": "2026-10-30T09:01:00Z",
  "agent_version": "0.1.0",
  "policy_version": 1,
  "status": "running",
  "capabilities": {"hardware_inventory": "supported", "app_activity": "not_collected"},
  "queue_count": 1,
  "queue_bytes": 320,
  "oldest_pending_at": "2026-10-30T08:57:00Z",
  "last_successful_upload_at": null,
  "dropped_count": 0,
  "integrity_degraded": false,
  "collection_errors": []
}
~~~
Server stamps `received_at` (never trusts `sent_at` for online/stale/offline) and returns `{"server_time":"2026-10-30T09:01:01Z","request_id":"..."}`. Lightweight metrics may appear as a **separately typed optional sampled block**, not a full network inventory on every heartbeat. `status: running` means Agent process state, **not** evidence of source coverage or online determination.

### Candidate inventory upload response and scope-filtered read

Use the full [inventory request example](device-inventory.md) unchanged as the candidate `POST /devices/{id}/inventory` body. Response after transactional compare-and-set:
~~~json
{
  "schema_version": 1,
  "snapshot_id": "014d9348-d2b1-44d7-a234-8e7be9838fe9",
  "inventory_revision": 17,
  "result": "accepted",
  "current_revision": 17,
  "server_time": "2026-10-30T09:01:02Z",
  "request_id": "a2fdba9e-fb11-42fd-ad05-3b7f19cbe0b2"
}
~~~
`result` is `accepted | duplicate | stale_ignored`. `accepted` and `duplicate` require identical revision/snapshot/digest. Older revisions return `stale_ignored` and report the server's larger `current_revision`; do not roll back child NIC/IP/volume tables or clear a newer local pending snapshot. Same revision with different snapshot contents is `409 inventory_conflict` and must be investigated.

`GET /api/v1/monitoring/overview` for assigned device groups returns only scoped counts, `as_of` and status classifications; no IP, MAC, hostname or out-of-scope counts. `GET /devices/{id}/inventory` for an authorized **health-only** role includes bounded non-sensitive OS/CPU/RAM/capability/freshness summaries, **omitting** interfaces, addresses, MAC, server-observed IP and asset tag. The complete permitted network details require group scope **plus** `internal_operational` permission, and read/export audit. Unknown information is not zero; for a permitted nullable field return `null` plus a reason `unsupported | permission_denied | not_collected | transient_error`. Frontend must not infer permission from a button or WSS subscription.

### Candidate stable error envelope

~~~json
{
  "code": "sequence_conflict",
  "message": "The submitted sequence conflicts with a stored event.",
  "request_id": "a2fdba9e-fb11-42fd-ad05-3b7f19cbe0b2"
}
~~~
HTTP and stable `code` pairs under review: `400 invalid_json`, `401 unauthenticated`, `403 forbidden | revoked_device`, `404 not_found` (consistent non-disclosing scope handling), `409 sequence_conflict | inventory_conflict | enrollment_conflict`, `410 pending_enrollment_expired` (authenticated/admin-scoped pending enrollment only), `413 request_too_large`, `422 invalid_schema | unsupported_schema_version | invalid_client_certificate`, `429 rate_limited`, `503 temporarily_unavailable`. A `429` or retryable `503` may include numeric `retry_after_seconds` and corresponding `Retry-After`; clamp and use exponential backoff with jitter. Never echo authentication tokens, raw sensitive payloads or hidden device existence. No non-2xx is an event ACK. `401/403` pauses Agent upload until identity is repaired; `409` quarantines conflict; `422` requires schema/policy remediation, never rapid retry.

### Pre-approval contract checklist

1. D-07 owner confirms separate ingress host boundaries, operator CSR signing and certificate delivery, reliable proxy→Spring fingerprint binding, key custody and renewal before this API is approved.
2. Approve exact required/optional/null fields, `schema_version`, event source/event_type enum, inventory `result`, error codes, size caps and role-specific projections. Freeze at least one negative fixture for wrong device credential, duplicate changed digest, stale inventory, unsupported schema and health-only IP/MAC denial.
3. Record decision ID, maintainer, timestamp and reviewed Git commit in [D0 gate](../governance/documentation-gate.md). All above are **candidate fixtures**, not live/OpenAPI-tested contracts.

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
