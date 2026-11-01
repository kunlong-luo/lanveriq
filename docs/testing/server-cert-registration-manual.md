# SRV-02-REG — signed client certificate registration (synthetic-only)

**Status (2026-10-31):** Java-only, default-OFF internal implementation, Java 25 unit tests and opt-in PostgreSQL 18.6 integration source. **No PostgreSQL container execution, real offline CA signing exercise, Caddy verified mTLS, browser enrollment/admin registration HTTP endpoint, or deployed device acceptance. No real workplace collection permitted.**

## Scope and controls

1. Operator-mediated signing stays **offline** via a reviewed issuer-owned clientAuth-only profile, not a homegrown Java CA. The Spring server never loads a CA private key.
2. An independently provisioned, dedicated **public** client-identity CA root must be pinned via an absolute local certificate file. The registration capability is disabled by default; it is only enabled in an isolated synthetic test when `lanveriq.client-cert-registration.enabled=true` and `lanveriq.client-cert-registration.trusted-ca-file` points to a reviewed, self-signed, currently valid CA certificate. Missing/invalid CA causes startup failure, never fallback to OS/public roots.
3. `ClientCertificateProfileValidator` uses Bouncy Castle PEM parsing and JDK PKIX signed-chain validation against **that independently configured** CA. It denies wrong CA, signature, invalid/excessive validity (90-day maximum), non-P-256 SPKI, CSR-SPKI mismatch, missing/extra SAN, wrong device `urn:uuid:<device_id>`, CA leaf, non-digitalSignature key usage and non-exclusive clientAuth EKU. Root is not accepted from caller input.
4. `ReviewedDeviceCertificateRegistrationService` is an **internal Spring bean only when explicitly enabled**, with **no HTTP endpoint**. It binds the acting administrator from Spring Security, never client JSON. The JPA repository locks a pending device, independently verifies active `owner_security_admin` group membership, required pending CSR/SPKI, no previously registered device credentials and nonexpired enrollment.
5. A successful registration transaction records the actual reviewing administrator, inserts the verified public leaf fingerprint/issuer/serial/SPKI with `device_credentials.status='pending'` and records `device_certificate.register` in `admin_access_log`. Device remains `pending_certificate`; it can **not** heartbeat or upload until a separate verified mTLS activation commits. Every failed update/insert/audit must roll back the whole transaction.
6. These checks **do not** prove offline signing custody, CSR retrieval review UX, administrator session/CSRF/Origin, signed-chain revocation (PKIX revocation is not enabled in this staging validator), TLS client-certificate provenance or real device operation. Do not expose the registrar without separate gates.

**2026-10-31 fixture correctness repair (SOURCE ONLY):** `PostgreSqlCertificateRegistrationIT` previously inserted the same fixed CSR SPKI digest for each `@BeforeEach`, violating V3's cross-device unique index after the first test. It now uses a per-device random 64-hex digest consistently in the pending CSR, simulated verified leaf and assertions. The simulated leaf validity also uses a bounded rolling time window rather than fixed 2026 dates; no production certificate lifetime or offline-CA policy changes. Actual PostgreSQL execution is still unverified.

**Additional default-OFF registration safety gates (source only):** The repository now follows the same device-first, fresh role/user row-lock authorization protocol as device revocation. A disabled administrator or revoked group role can no longer pass a stale pre-lock review snapshot. The allowed audit insertion must affect **exactly one row**; a PostgreSQL BEFORE INSERT trigger that `RETURN NULL` suppresses the audit causes the whole reviewed CSR and credential transaction to roll back. Java unit tests cover the two failure modes. The manually run PostgreSQL suite stages `silentlyDroppedAuditInsertRollsBackCertificateAndReview` and `disablingAdminDuringDeviceLockWaitRejectsStaleReviewAndAudit` (with observed PG lock wait). **No actual container execution evidence exists yet.** These are not proof of real CA, Caddy, PKI rotation or web security.

## Evidence matrix

| Case | Expected result | Current proof |
|---|---|---|
| REG-01 | Signed P-256 clientAuth-only leaf with exactly one authoritative device URI SAN validates under pinned dedicated CA | Synthetic Java certificate unit test |
| REG-02 | Wrong root/chain, CSR SPKI, SAN, EKU, KeyUsage, expiry or excessive lifetime rejected | Synthetic Java certificate unit tests |
| REG-03 | Anonymous/disabled principal cannot register; active scoped admin required | Java service/JPA unit tests |
| REG-04 | PostgreSQL locks pending device, checks group and CSR; inserts one pending credential, review marker and access audit | Opt-in PostgreSQL integration source; **NOT RUN** |
| REG-05 | Wrong-group admin rejected without registration; injected failure rolls back review, credential and audit | Opt-in PostgreSQL integration source; **NOT RUN** |
| REG-06 | Actual approved offline client-only CA, certificate registration, expired/revoked leaf, issuer serial collision, replay and concurrent review | **NOT VERIFIED** |
| REG-07 | Real authenticated console CSRF/Origin/permissions and trusted Agent Caddy mTLS activation | **NOT IMPLEMENTED/VERIFIED** |

Manually run `cd apps/server && ./gradlew integrationTest` with isolated Docker/JDK25, or dispatch [PostgreSQL 18 integration (manual, synthetic-only)](../../.github/workflows/server-postgres18-integration.yml) on `main`. Java unit CI compiles, but does not execute Docker tests. Record SHA, run link, exact PostgreSQL server version, sanitized JUnit logs and container digest. The initial manual workflow still has **zero observed runs** at document authoring time.

**Security stop:** Do not set the registration feature flag on any real LAN server or treat a green unit test as authorization to process real device/employee data. Mandatory before real pilot: audited CSR review, CA provision and rotation, certificate revocation checks, Caddy strict host/mTLS and unspoofable ingress, explicit account/group/CSRF checks on any future controller, and independent hands-on OS acceptance.
