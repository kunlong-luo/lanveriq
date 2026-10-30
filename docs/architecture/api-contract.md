# REST and event protocol contract v0.1 (proposed)

**Base:** `/api/v1` over HTTPS only. JSON UTF-8; server controls authorization. Version via both URL major and `schema_version` per event. This is an API **design**, not an OpenAPI implementation.

## Device API
| Method | Path | Principal | Semantics |
|---|---|---|---|
| POST | /enrollments | authenticated admin | Create one-use short-lived device code, device group, expiry |
| POST | /devices/enroll | pairing-capable installer | One-time token exchange after server certificate validation |
| POST | /devices/{id}/heartbeats | bound device credential | Status, OS, version, queue depth, capability flags |
| POST | /devices/{id}/event-batches | bound device credential | Validate, insert-once, durable ack |
| GET | /devices | authorized operator | Scope-filtered paginated inventory |
| GET | /devices/{id} | authorized operator | Device detail, capability and health |
| POST | /devices/{id}/revoke | privileged admin | Disable identity and future uploads |
| GET | /devices/{id}/activity | scoped auditor | Time-range and kind-filtered activity |
| GET | /devices/{id}/audit-events | scoped auditor | Time-range and event filters |
| GET | /devices/{id}/collection-gaps | scoped auditor | Collection discontinuity and failures |
| GET | /admin/access-log | privileged auditor | Immutable viewing/action trace |

Later recording endpoints: `POST /recording-sessions`, `POST /recording-segments/init`, `PUT /recording-segments/{id}/content`, `POST /recording-segments/{id}/complete`, `GET /devices/{id}/recordings`, `GET /recordings/{id}/play`. Each guarded by policy, OS authorization, scope, role and approval.

## Request envelopes
Enrollment request fields: `token`, `agent_version`, `os`, `capabilities`, `device_public_key`, `policy_notice_version`. The server must authenticate identity beyond LAN IP; token only bootstraps identity. Enrollment tokens are not reused as ongoing API keys.

Heartbeat fields: `device_id`, `sent_at`, `agent_version`, `status`, `capabilities`, `queue_count`, `queue_bytes`, `collection_errors`. Server stamps `received_at`.

Event batch (illustrative):
~~~json
{
  "schema_version": 1,
  "batch_id": "7ba33d0d-9340-47ad-b4c7-70a5080ad239",
  "device_id": "a0e51319-7c8c-4557-85d3-187d91ee5213",
  "events": [
    {
      "event_id": "6782a074-e42c-4198-adfa-5f6ab57d7d43",
      "sequence": 101,
      "source": "system_audit",
      "kind": "program_started",
      "occurred_at": "2026-10-30T09:00:00Z",
      "payload": {"executable": "example-app"}
    }
  ]
}
~~~

Response after DB commit:
~~~json
{"schema_version":1,"accepted_sequences":[101],"rejected":[],"server_time":"2026-10-30T09:00:02Z"}
~~~

## Error and retry
- 400 invalid schema; 401 no identity; 403 revoked/out-of-scope; 409 identity or sequence payload conflict; 413 oversized; 422 field validation; 429 backoff; 5xx retry.
- Stable `code`, `message`, `request_id`, optional `retry_after_seconds`. Never echo credentials or raw sensitive payload.
- A duplicate sequence with identical canonical event content is idempotent; same sequence with different payload is an audit-worthy **conflict**, not an overwrite.
- Limit pagination and time range. Validate `device_id` against credential, not the body alone.

## Authentication
Admin: secure session with MFA support and role/device-group scopes; Agent: unique client credential bound to device (mTLS preferred if local certificate distribution is feasible; otherwise proof-of-possession tokens). Credentials revocable/rotatable. No insecure plaintext HTTP exception on a trusted LAN.
