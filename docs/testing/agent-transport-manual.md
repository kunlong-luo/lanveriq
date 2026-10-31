# AGT-03 — mTLS transport and whole-batch ACK acceptance

**Status: implementation scaffold and synthetic unit tests only. NO live HTTPS, Caddy, certificate custody, server ingestion or deployment acceptance has been executed.** The Agent service binary does not create or call the HTTP client, so no endpoint connection or employee monitoring is enabled.

## Trust design and implementation

- [Reqwest 0.13.5](https://docs.rs/reqwest/0.13.5/reqwest/) with **Rustls only** handles TLS and client-certificate authentication. `AgentTlsTransport::new` is *explicit*: exactly `https://agent.lanveriq.internal` port 443, reviewed PEM server root CA, client certificate plus corresponding private key in PEM; `tls_certs_only` excludes publicly trusted/OS roots, no proxies, no redirects, HTTPS-only, TLS >=1.2, connection timeout 5 seconds, request timeout 12 seconds. No disabling hostname validation, invalid certificate trust or substituting an HTTP fallback.
- **Certificate custody is not implemented**. A future approved secure OS adapter must pass identity material from restricted service custody; credentials are never placed in TOML, URLs, logs or SQLite. Signing CA private key never belongs on Agent or Spring. An uploaded `device_id` alone is **not** authentication: Caddy must require real client certificates and Spring must revalidate registered fingerprint, active/non-revoked credential, exact path/body/device group in PostgreSQL on **every request**.
- Canonical JSON for synthetic fixtures only: `schema_version=1`, `batch_id`, `device_id`, and event fields `event_id`, `device_sequence`, `schema_version`, `source=synthetic_test`, `event_type=protocol_fixture`, `policy_version`, `occurred_at`, `payload`. The production `agent_transport/collection_gap` pair remains **not implemented**. Source records are read from AGT-02 SQLite; client rechecks SHA-256 against their persisted compact JSON before sending.
- SQLx V2 migration `upload_batches` persists one outstanding `batch_id`, last sequence and event-set fingerprint in one transaction. New events during an ambiguous timeout **do not change** the in-flight batch; retry after restart keeps the same UUID and membership. Only verified ACK removes the planned sequences. 100-event/512 KiB outbound limit and 4 KiB response limit.
- **Fail-closed ACK:** only HTTP 200 plus `schema_version=1`, valid server metadata, and exactly the full set of expected positive sequence numbers (no duplicate, missing or invented values) permits `OutboxStore::acknowledge`. Any other HTTP status or incomplete JSON leaves the local outbox intact. 401/403 pauses for identity repair, 409/400/422/quota errors quarantine or require operator/schema correction, 429/5xx permit bounded retry with server-supplied numeric `Retry-After` clamped to 1–300 s (30 s fallback). **No automatic retry scheduler or jittered backoff loop is implemented yet**: the API returns a disposition for a future approved scheduler. No request retries occur from the AGT-01 executable.
- Heartbeat structure is typed and preserves `not_collected` for unsupported/unimplemented sources. It is only a synthetic fixture: no real queue/health measurement or server heartbeat has occurred.
- The Rust CI tests **protocol classification, serialization, local SQLite state and startup input rejections**. It does not use live mTLS or mock out production Caddy/PG gates.

## Negative and manual acceptance matrix (all live cases unexecuted)

| Case | Expected | Current evidence |
|---|---|---|
| AGT-TRAN-01 | Only approved agent HTTPS host, server CA and PEM client identity permitted | Pure unit input rejection; no genuine certificate handshake |
| AGT-TRAN-02 | Wrong CA/hostname, expired leaf, wrong EKU, revoked cert and forged client-cert headers rejected by Caddy/Spring | NOT TESTED, server route and Caddy not implemented |
| AGT-TRAN-03 | Valid synthetic heartbeat reports status and absent capabilities without inventing metrics | JSON structure unit; no HTTP transfer |
| AGT-TRAN-04 | Stable batch UUID, event IDs/digest/sequence through lost response, restart and new queue arrivals | File-backed SQLite synthetic unit |
| AGT-TRAN-05 | Full ACK removes only explicit records, no partial/duplicate/invented sequence deletes | Local classification plus file-backed unit; no real server response |
| AGT-TRAN-06 | 401/403 suspends; 409 quarantines; 429/5xx retries with bounded delay | Pure classification unit; actual scheduler still missing |
| AGT-TRAN-07 | Wrong-device path/body/certificate, reused fingerprint and revoked credential rejected by PostgreSQL | NOT IMPLEMENTED/TESTED; SRV-02/SRV-03 + Caddy |
| AGT-TRAN-08 | Server whole-batch transaction, identical replay returns same ACK, conflict rolls back entire batch | NOT IMPLEMENTED/TESTED; SRV-03 |
| AGT-TRAN-09 | Authenticated real heartbeat after mTLS activation, server observed timestamp/online status | NOT IMPLEMENTED/TESTED |
| AGT-TRAN-10 | Windows 11 + Pop!_OS actual trust store, key custody, CA and service integration | NOT TESTED |

Before runtime verification, provision **synthetic** signed device identity and isolated PostgreSQL 18/Caddy with trusted offline CA out-of-band. Record Git SHA, browser/Agent OS, TLS versions, CA and public leaf fingerprints, sanitized HTTP codes and session state. Never commit a private key or disable certificate validation. A unit-green source tree is not permission to collect real data or enable the service.
