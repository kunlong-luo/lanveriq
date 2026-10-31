# HB-01 — internal device heartbeat and latest-state acceptance

**State:** V0.1 Java JPA/Flyway V7 implementation committed, Java unit and transactional-proxy coverage; opt-in real PostgreSQL integration assertions staged. **No PostgreSQL 18 execution observed, no HTTP heartbeat route or trusted Caddy mTLS bridge exists. No real endpoint monitoring authorized.**

## Scope and privacy boundary

- `DeviceHeartbeatService` is internal. It must be invoked only after an independently verified Agent mTLS identity is mapped to the enrolled device. An arbitrary body `device_id` or forwarded fingerprint header does **not** authenticate the caller.
- The service validates `schema_version=1`, device/path/credential ID equality, version, status, bounded machine-code capability/error states, nonnegative queue counters, and UTC timestamps **before SQL**. Raw free-text error messages, IP/MAC, file paths, process/window activity, employee names or recording status are not accepted.
- Inside **one Spring-managed transaction**, the JPA repository locks a currently active device row and checks the current unexpired, unrevoked bound client certificate fingerprint in PostgreSQL; it then updates `devices.last_seen_at` and upserts one `device_heartbeat_latest` row. `received_at` is database-owned. The Agent's clock must not determine online/stale/offline.
- Capabilities `not_collected`, `unsupported`, `permission_denied` and `degraded` retain their distinct meanings. Successful heartbeats do **not** establish collector coverage or authorize future device telemetry.

## Opt-in synthetic PostgreSQL test

Open [manual PostgreSQL 18 integration workflow](../../.github/workflows/server-postgres18-integration.yml) on `main` and dispatch it explicitly; it uses Testcontainers 2.x, PostgreSQL 18.6, JDK 25 and `./gradlew integrationTest`. It is not scheduled, pushed, or part of unit CI.

| Check | Expected |
|---|---|
| HB-DB-01 | Flyway V1–V7 applies, `device_heartbeat_latest` exists empty on first startup |
| HB-DB-02 | Accepted synthetic active-device heartbeat stores one row, with DB receipt, `devices.last_seen_at`, typed queue/capabilities JSONB |
| HB-DB-03 | A later heartbeat **updates the single latest row**, retaining `unsupported` vs `not_collected` without fabricated coverage |
| HB-DB-04 | Unknown, forged, expired or revoked fingerprint cannot change `last_seen_at` or add heartbeat rows |
| HB-DB-05 | Missing, malformed, oversized, untrusted-device, free-form error or unsupported schema input is rejected before database writes |
| HB-DB-06 | A failed write rolls back the complete latest-state transaction, with no successful response before commit |
| HB-HTTP-01 | Real Caddy verified mTLS, Host/SNI and independent Agent trust bridge reject header-spoofed/direct-backend heartbeats — **NOT IMPLEMENTED** |
| HB-OS-01 | Installed Windows/Pop!_OS Agent schedules authentic heartbeats and UI derives online/stale/offline from DB server receipts — **NOT IMPLEMENTED** |

**Acceptance boundary:** A successful synthetic PostgreSQL run may establish HB-DB evidence only. It does not close Agent transport, certificate issuance, session login, REST/WSS permissions, OS service tests, organizational privacy approval or real-data authorization. Record tested Git SHA, run URL, actual DB version, sanitized JUnit artifacts and image digest; never upload real device data, certificates or passwords.
