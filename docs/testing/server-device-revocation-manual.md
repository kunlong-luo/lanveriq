# SRV-02-REV — internal group-scoped device/certificate revocation

**Status (2026-10-31):** default-OFF internal Spring service, Hibernate/JPA repository, Java 25 unit tests and opt-in PostgreSQL 18.6 Testcontainers source staged. **No real PostgreSQL execution observed, no browser/revoke API, no actual Agent/Tomcat/Caddy mTLS revocation request, and no OS trust-store or offline CA revocation.** No actual employee/device data is approved.

## Scope and security semantics

- `DeviceRevocationService` is only constructed if `lanveriq.device-revocation.enabled=true`. The default remains OFF. It is **not** an HTTP controller or production console feature, and does not enable Agent routes. Only an authenticated, enabled `AdminPrincipal` from Spring Security's server-side context can invoke it; it accepts a canonical device UUID, **never** a caller-supplied administrator UUID, role, group ID or asserted credential.
- Inside a single `@Transactional` JPA call, `JpaDeviceRevocationRepository` locks the device row **first**. The lock query **rechecks** the current enabled DB user, assigned `owner_security_admin` role and device's exact database group. Unknown devices, wrong-group users, disabled administrators and retired identities fail without revealing which condition occurred.
- Only `pending_certificate` and `active` devices can first transition to `revoked`; all registered pending/active credentials become `revoked` with a database timestamp. The device row is set to `revoked` with a database timestamp. Because heartbeats/events/inventory/activation also lock the device before authorization, new writes after the revocation commit cannot pass the active-device check. Revocation **does not erase immutable events or existing inventories**.
- A single `admin_access_log` row with `action='device.revoke'` and `outcome='allowed'` must commit with both updates. Any audit insert failure must roll back **all** state changes. An authorized retry on an already-revoked device is idempotent and creates no second audit. A retired device is not implicitly reactivated/revoked. The same old device ID and certificate must never be reused to bypass an explicit re-enrollment.
- This is **DB-state revocation**, not CA CRL/OCSP implementation, TLS session invalidation, or a complete browser-admin/CSRF/API review. Those remain separate explicit gates.

## Acceptance, synthetic-only

The [manual PostgreSQL 18 integration workflow](../../.github/workflows/server-postgres18-integration.yml) runs `cd apps/server && ./gradlew integrationTest` with Java 25 and Docker. Do not add it to automatic push/PR CI. `PostgreSqlDeviceRevocationIT` opts into the service with test-only configuration and creates invented UUIDv7 devices, independent groups, users and certificates in a disposable PostgreSQL 18.6 database. It injects a test-only Spring Security principal; it **does not prove administrator password/session authentication**.

| Check | Expected result | Current evidence |
|---|---|---|
| REV-DB-01 | Authorized owner in matching device group revokes both device and credential, writes exactly one allowed audit, then subsequent heartbeat fails | Test source staged; DB execution pending |
| REV-DB-02 | Repeated authorized revoke returns idempotent outcome without changing audit count | Test source staged; DB execution pending |
| REV-DB-03 | Same role but wrong group, or disabled administrator with stale context, cannot change device/credential or audit | Test source staged; DB execution pending |
| REV-DB-04 | Synthetic exception on audit insert triggers complete rollback of both updates; removing test trigger permits authorized retry | Test source staged; DB execution pending |
| REV-DB-05 | Pending device and registered pending certificate are both revoked; no activation shortcut | Test source staged; DB execution pending |
| REV-JAVA-01 | No/disabled/wrong principal or nil device ID denied before repository; query checks group+role, device-lock first, terminal states, audit after writes | Java unit tests in normal server CI |
| REV-TLS-01 | Existing mTLS session using the now-revoked client leaf cannot continue Agent calls; real Host/SNI, OS key custody and revocation propagation | **NOT implemented/executed; blocking** |
| REV-WEB-01 | Scoped browser admin login, CSRF, audit, no device enumeration, secure endpoint behavior | **NOT implemented/executed; blocking** |

Record the exact successful run SHA, postgres image digest, test case results and negative/fault-injection evidence before closing a **database-only** subgate. A compiled integration test is not evidence of PostgreSQL behavior. A synthetic success is not authorization to collect or export real employee data.
