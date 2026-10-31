# SRV-02-ACT — internal pending certificate activation acceptance

**Status:** internal Java 25/Spring JPA implementation and JUnit/AOP tests written, opt-in PostgreSQL 18.6 integration suite added. **No PostgreSQL container run observed. No Caddy mTLS, CA signing, actual leaf validation/registration or Agent HTTP activation implemented. No real device monitoring authorized.**

## Scope and required trust chain

- First, a scoped authorized console administrator must review the stored pending CSR. Certificate registration must independently verify a real clientAuth-only P-256 signed leaf/issuer chain against a dedicated client CA, stored CSR SPKI, authoritative device-ID URI SAN, leaf validity and fingerprint; it must never store an online CA signing key. **That registration workflow is not implemented.**
- Later, an actual verified Caddy mTLS Agent request must provide a server-proven peer identity over an isolated trusted upstream. An attacker-supplied JSON field, `X-Forwarded-Client-Cert` or arbitrary fingerprint header must never become `VerifiedDeviceCertificate`. **That ingress bridge is not implemented.**
- The staged internal `DeviceActivationService` validates `schema_version=1`, exact device ID in path, body and internally verified peer, plus a bounded Agent version before any SQL. It never accepts a pairing token or raw certificate bytes in activation JSON.
- The JPA repository locks the canonical device row and checks an approved/unexpired CSR, matching SPKI, a bound unexpired and unrevoked pending credential and its exact certificate fingerprint. It sets the credential and device to `active` in one Spring-managed transaction, populating `devices.enrolled_at`. Same already-active certificate retries perform no writes. Pending/expired/revoked/forged credentials cannot authorize heartbeat or event ingestion.
- **Important limit:** the synthetic Testcontainers fixture directly inserts a pending CSR and credential with fictitious fingerprint. It verifies conditional database transitions and locking only, **not** certificate chain/provenance, administrator review authorization or a real TLS handshake.

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

A green synthetic database suite closes *only its demonstrated transaction checks*. Required before activation HTTP: real CA/leaf registration, group-scoped approval/audit/CSRF, revocation, trusted Caddy mTLS identity bridge, strict host/SNI negatives, direct-backend isolation and platform key custody. Do not publish a device activation endpoint or accept real-device telemetry until independent evidence exists.
