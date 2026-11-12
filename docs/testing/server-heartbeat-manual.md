# HB-01 — internal device heartbeat and latest-state acceptance

**2026-11-12 replay guard (source; pending exact-SHA CI):** Flyway V9 adds a
per-device persistent accepted-request UUID ledger. After device and certificate
locks succeed, the same transaction inserts the validated UUID before updating
`last_seen_at` or `device_heartbeat_latest`. Repeated IDs produce HTTP 409,
no-store, empty body, and no receipt. The synthetic PostgreSQL 18 regression
accepts two distinct IDs and then replays the first, asserting latest state and
server receive time remain unchanged. No Agent runtime/production ingress is
enabled. The ledger retains only device/request UUID pairs, not heartbeat
payloads, and has no purge: define bounded lifetime/epoch and replay-window
policy before production scale without silently weakening security.

**CURRENT UPDATE 2026-11-08 (supersedes the 2026-10-31 test-stage statements below):** V0.1 Java JPA/Flyway latest-heartbeat implementation and synthetic PostgreSQL tests exist; Caddy/Tomcat/PG18 have *historical* combined TLS/RBAC synthetic success. **Batch A1** introduced a `POST /api/v1/devices/{deviceId}/heartbeats` HTTP controller and its exact stateless Spring Security chain behind **two default-OFF flags**; [Java MockMvc/negative L1 #42448164511](https://github.com/kunlong-luo/lanveriq/actions/runs/42448164511) passed. However **this new endpoint has NOT been exercised through a real Rust Agent→Caddy→Tomcat→PG18 L2 chain**. The Agent binary remains idle. Do not treat earlier successful synthetic Console TLS tests as new heartbeat route acceptance; no real employee/device monitoring is authorized. [Batch A record](../development/batch-a-first-heartbeat-implementation.md) · [full protocol/security gate](../architecture/device-communication-stack.md).

**Credential revocation race test (2026-10-31, opt-in source only):** The shared `JpaActiveDeviceCredentialGuard` now requires the same ordered device→credential locks for heartbeats, event batches and inventory. A staged synthetic PostgreSQL 18.6 suite checks revoked, expired and future client credentials cannot change `device_heartbeat_latest`, `devices.last_seen_at` or canonical events. This does **not** prove actual mTLS/Tomcat certificate attributes or real DB execution until the manual workflow succeeds.

**Cross-device and replay checks (2026-10-31, source only):** The staged manual PG18 suite includes two same-group active devices with distinct registered certificates. A forged first-device fingerprint on the second-device heartbeat must not update either device's `last_seen_at` or heartbeat state. After a genuine heartbeat and event have been committed, revoking the certificate must prevent further heartbeat changes **and deny an otherwise-identical event replay ACK**. A `pending` certificate must also fail despite an active device. These are Testcontainers source tests, **not observed PostgreSQL execution or actual Caddy/Tomcat identity proof**.

## Scope and privacy boundary

- `DeviceHeartbeatService` retains an internal, transactional contract, now called by a **default-OFF, trust-gated HTTP source** only when both Agent ingress and heartbeat flags are enabled; the normal runtime has neither production Agent host nor registered device keys. It must be invoked only after an independently verified Agent mTLS identity is mapped to the enrolled device. An arbitrary body `device_id` or forwarded fingerprint header does **not** authenticate the caller.
- The service validates `schema_version=1`, device/path/credential ID equality, version, status, bounded machine-code capability/error states, nonnegative queue counters, and UTC timestamps **before SQL**. Raw free-text error messages, IP/MAC, file paths, process/window activity, employee names or recording status are not accepted.
- Inside **one Spring-managed transaction**, the JPA repository first locks the currently active device row and then the exact registered credential row with `FOR UPDATE`, verifying its fingerprint, validity window and nonrevoked state after the lock (using `clock_timestamp()`); it then updates `devices.last_seen_at` and upserts one `device_heartbeat_latest` row. `received_at` is database-owned. The Agent's clock must not determine online/stale/offline.
- Capabilities `not_collected`, `unsupported`, `permission_denied` and `degraded` retain their distinct meanings. Successful heartbeats do **not** establish collector coverage or authorize future device telemetry.

## Opt-in synthetic PostgreSQL test

Open [manual PostgreSQL 18 integration workflow](../../.github/workflows/server-postgres18-integration.yml) on `main` and dispatch it explicitly; it uses Testcontainers 2.x, PostgreSQL 18.6, JDK 25 and `./gradlew integrationTest`. It is not scheduled, pushed, or part of unit CI.

| Check | Expected |
|---|---|
| HB-DB-01 | Flyway V1–V9 applies, `device_heartbeat_latest` exists empty on first startup |
| HB-DB-02 | Accepted synthetic active-device heartbeat stores one row, with DB receipt, `devices.last_seen_at`, typed queue/capabilities JSONB |
| HB-DB-03 | A later heartbeat **updates the single latest row**, retaining `unsupported` vs `not_collected` without fabricated coverage |
| HB-DB-04 | Unknown, forged, expired or revoked fingerprint cannot change `last_seen_at` or add heartbeat rows |
| HB-DB-05 | Missing, malformed, oversized, untrusted-device, free-form error or unsupported schema input is rejected before database writes |
| HB-DB-06 | A failed write rolls back the complete latest-state transaction, with no successful response before commit |
| HB-HTTP-01 | Default-OFF heartbeat POST + exact-route chain **SOURCE IMPLEMENTED/L1 PASS**. Actual Rust mTLS→Caddy→Tomcat→PG18 and Host/SNI/header-spoofing/expired/revoked/wrong-ID checks **NOT EXECUTED on the new route**; L2 batch A acceptance required |
| HB-OS-01 | Installed Windows/Pop!_OS Agent schedules authentic heartbeats and UI derives online/stale/offline from DB server receipts — **NOT IMPLEMENTED** |

**Acceptance boundary:** A successful synthetic PostgreSQL run may establish HB-DB evidence only. It does not close Agent transport, certificate issuance, session login, REST/WSS permissions, OS service tests, organizational privacy approval or real-data authorization. Record tested Git SHA, run URL, actual DB version, sanitized JUnit artifacts and image digest; never upload real device data, certificates or passwords.
