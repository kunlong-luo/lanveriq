# SRV-02-ACT — internal pending certificate activation acceptance

**Status (2026-11-09):** internal Java 25/Spring JPA activation and PostgreSQL 18.6 integration sources exist. The new POST activation handler and stateless security filter chain are **source-only and default-OFF**, requiring both `lanveriq.agent.ingress.assertions-enabled=true` and independently `lanveriq.agent.activation.http-enabled=true`; normal configuration sets both to false. The handler obtains identity only from the pinned upstream mTLS policy and checks the reviewed pending credential in JPA. **No verified end-to-end Agent→Caddy→Tomcat/PG18 activation, real operator-issued CA/leaf or managed OS key custody; no production activation, Agent telemetry or remote desktop is authorized.**

## Scope and required trust chain

- First, a scoped authorized console administrator must review the stored pending CSR. Certificate registration must independently verify a real clientAuth-only P-256 signed leaf/issuer chain against a dedicated client CA, stored CSR SPKI, authoritative device-ID URI SAN, leaf validity and fingerprint; it must never store an online CA signing key. **Actual operator-approved signed-leaf issuance and distribution are not deployed or end-to-end tested.**
- Later, an actual verified Caddy mTLS Agent request must provide a server-proven peer identity over an isolated trusted upstream. An attacker-supplied JSON field, `X-Forwarded-Client-Cert` or arbitrary fingerprint header must never become `VerifiedDeviceCertificate`. **The source-level Caddy assertion parser and default-OFF HTTP route exist, but their live two-hop trust boundary has not been verified for activation.**
- The staged `DeviceActivationService` validates `schema_version=1`, exact device ID in path, body and internally verified peer, a bounded Agent version, and a required non-nil per-attempt `request_id` before any SQL. The successful receipt echoes this exact ID after the repository operation; it never invents a distinct correlation ID. It never accepts a pairing token or raw certificate bytes in activation JSON.
- The JPA repository locks the canonical device row and checks an approved/unexpired CSR, matching SPKI, a bound unexpired and unrevoked pending credential and its exact certificate fingerprint. It sets the credential and device to `active` in one Spring-managed transaction, populating `devices.enrolled_at`. Same already-active certificate retries perform no writes. Pending/expired/revoked/forged credentials cannot authorize heartbeat or event ingestion.
- **Important limit:** the synthetic Testcontainers fixture directly inserts a pending CSR and credential with fictitious fingerprint. It verifies conditional database transitions and locking only, **not** certificate chain/provenance, administrator review authorization or a real TLS handshake.

**2026-10-31 fixture correctness repair (SOURCE ONLY):** `PostgreSqlDeviceActivationIT` previously reused the exact same `csr_spki_digest` (the 64-hex `b…b` fixture) for each `@BeforeEach` synthetic enrollment, in conflict with Flyway V3's `device_enrollment_csrs_unique_spki` index when the isolated PostgreSQL container is reused between test methods. Each fixture now generates a fresh 256-bit-shaped hexadecimal SPKI digest and binds that same digest to its CSR and pending client credential. This prevents false failures from duplicate invented keys without weakening the unique-index rule. Actual PostgreSQL 18 execution remains **NOT OBSERVED**.

## Synthetic-only Testcontainers acceptance matrix

Run `cd apps/server && ./gradlew integrationTest` with Java 25 and a permitted Docker daemon, or explicitly dispatch `server-postgres18-integration.yml` on `main` in GitHub Actions. Automatic CI must remain unit-only (plus integration-source compilation).

| Check | Expected behavior | Evidence status |
|---|---|---|
| ACT-DB-01 | Reviewed pending CSR and pending matching SPKI/fingerprint activate device and credential in one transaction | Written, PostgreSQL not run |
| ACT-DB-02 | Same active fingerprint retry succeeds without duplicate credentials or state changes | Written, PostgreSQL not run |
| ACT-DB-03 | Pending credential cannot post a heartbeat until activation commits | Written, PostgreSQL not run |
| ACT-DB-04 | Wrong fingerprint, absent review, expired pending enrollment or revoked cert is rejected without state transition | Written, PostgreSQL not run |
| ACT-DB-05 | Concurrent same-device activation requests serialize and converge on one active identity | Written, PostgreSQL not run |
| ACT-DB-06 | Forced partial UPDATE failure rolls back credential and device state; real database fault injection | Still needs independent PostgreSQL negative test |
| ACT-TLS-01 | Offline signed client-only leaf, CSR-SPKI/issuer/SAN checks, host/SNI restrictions and verified forwarded identity | Not implemented or tested |
| ACT-OS-01 | Real Windows/Linux private-key custody and Agent activation over mTLS with browser pairing host unaffected | Not implemented or tested |

A green synthetic database suite closes *only its demonstrated transaction checks*. Required before **enabling** activation HTTP outside separately controlled disposable acceptance: real CA/leaf registration, group-scoped approval/audit/CSRF, revocation, trusted Caddy mTLS identity bridge, strict host/SNI negatives, direct-backend isolation and platform key custody. Do not publish a device activation endpoint or accept real-device telemetry until independent evidence exists.

## A2 source-only HTTP slice (2026-11-09; main branch)

- The activation route is **POST /api/v1/devices/{deviceId}/activate** only, JSON only, exclusively the Agent host. The dedicated HTTP security chain matches that exact path and POST method; it is stateless and does not accept browser sessions. Normal Spring Boot startup does not mount either route or security chain without **both** the pinned-ingress and the separate activation feature flag. The flag alone is not approval to deploy or activate real devices.
- Before JPA, `AgentIngressAssertionPolicy.resolve` requires the pinned Caddy→Tomcat TLS client certificate, canonical Agent Host, loopback backend, exact route, single verified Agent leaf fingerprint and no untrusted forwarding assertions. The JPA transaction locks the reviewed pending CSR, approved unexpired leaf credential and exact fingerprint; revoked/wrong-device/expired identities are denied. An HTTP error returns `Cache-Control: no-store` with an empty body, never a device lookup oracle.
- The activation request's UUID `request_id` is required, non-nil and **correlation only**. The service returns that exact ID on success, so a later native Agent client can reject stale/mismatched responses. Existing source does NOT yet implement the Rust Agent enrollment/activation client or OS private-key custody.
- Java L1 Mockito/MockMvc and fixed Spring property annotation checks do not prove actual mTLS or complete application context mount; real Caddy→Tomcat/PG18 negative/positive tests and review of CA, cookies/CSRF separation, rate limits, TLS1.3, OS custody and device consent are still required.
