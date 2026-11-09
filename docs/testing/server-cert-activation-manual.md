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

## 2026-11-09 Rust Agent activation wire (source-only)

The separate Rust `AgentTlsTransport::send_activation(&ActivationRequest)` is **not wired into the Agent service or scheduler**. It sends only an explicit synthetic/approved invocation to the exact `/api/v1/devices/{deviceId}/activate` path over the existing fixed-origin TLS1.3-only reqwest/Rustls client. The JSON request has exactly `schema_version`, `device_id`, a bounded ASCII `agent_version` and a nonnil `request_id`; no CSR private key, enrollment token, CA key or device data is sent.

The Rust response classifier counts activation as accepted **only** when an HTTP 200 body has exactly the five Java `DeviceActivationReceipt` properties: version 1, same device UUID, same request UUID, state `active` and bounded UTC time. Unknown fields, missing fields, old or swapped IDs, malformed timestamps and oversized bodies are rejected. Authentication denials and 5xx remain failure/backoff dispositions. This is an L1 local contract, **not an observed network/TLS/PKI/PG18 test**; Spring's disabled-by-default route and reviewed credential SQL remain authoritative. Actual offline-signed P-256 leaf + CSR/key custody, OS store/ACL verification, dual-hop TLS and disposable PG18 activation + heartbeat remain OPEN before any endpoint pilot or runtime wiring.

## 2026-11-09 Linux offline key/CSR source boundary

The Linux Agent now has a callable-but-not-scheduled `create_offline_p256_csr` helper implemented with fixed-path system OpenSSL 3, P-256 ECDSA/SHA-256, a private owner-only directory/key and signature verification. Its pure/isolated unit tests generate fresh synthetic keys and reject unsafe directories. The private key never leaves local mode-0600 storage; `Debug` is redacted. **No online endpoint enrollment, Admin approval, private issuer, real Windows/Linux service identity, secure hardware storage, mTLS active session, PostgreSQL 18 transaction or authorized real device** is tested/approved by these assertions. Do not present OpenSSL file permissions as equivalent to a non-exportable OS key. The documented A2 L2/L3 and approved provisioning lifecycle remain OPEN.


## Windows CNG synthetic native crypto gate (2026-11-09)

The separate agent-windows-cng-csr-synthetic.yml workflow can exercise an *actual* disposable current-user Microsoft Software KSP ECDSA P-256 signing key. It checks its non-exportable export policy and signing-only usage, produces a public SHA-256 PKCS#10 CSR without exporting a private key, validates CSR signature and curve, rejects tampering, deletes the disposable CNG key and removes the public CSR. **Native disposable CNG key/CSR validation PASSED in [#42623158786](https://github.com/kunlong-luo/lanveriq/actions/runs/42623158786).**

Even a PASS is not evidence of Windows service account SID/ACL, hardware-backed TPM, production certificate issuance, Rustls/reqwest non-exportable key signing or actual TLS/PG18 activation. No normal Agent runtime or real endpoint is involved.


**Observed Windows native L2 subset:** The disposable Microsoft CNG user-key → .NET SHA256/P256 CSR signing, signature check, non-exportable CNG flag and key-deletion scenario ran successfully in [#42623158786](https://github.com/kunlong-luo/lanveriq/actions/runs/42623158786). No real endpoint or key from a user was present. The next two-job Windows→Linux acceptance passes **only the public CSR** through a one-day CI artifact and invokes WindowsCngCsrInteropTest to verify it with Spring's exact Bouncy Castle policy; the negative mutated-signature check is included. The full two-job workflow [#42623589859](https://github.com/kunlong-luo/lanveriq/actions/runs/42623589859) passed at main d7eff3bc: Windows CNG generated and removed its user key, uploaded only the public CSR, and Java 25/Bouncy Castle verified the actual Windows signature and denied a mutated CSR. This proves real cross-OS PKCS#10 interoperability in disposable CI only. Even if both succeed, non-exportable CNG provider integration into reqwest/Rustls and actual Windows service-key custody still require independent tests.

**Extra cleanup assertion:** A subsequent revision additionally requires CngKey.Exists to be false after deleting the synthetic CNG user key; report this new stronger check as PASSED only when a fresh exact-head Windows CI run succeeds. Machine-scope service ACL/private key persistence and non-exportable CNG-backed reqwest/rustls client TLS are still NOT_RUN.


## 2026-11-09 Windows rustls-cng signer proof (isolated, unsigned client leaf)

New **separate** `rustls-cng-nonexportable-signing-probe` Windows CI job calls the existing disposable Microsoft Software KSP CSR test with `-ProbeRustlsSigning`. An isolated Rust crate `rustls-cng-probe` uses `rustls-cng 0.7.1` and `rustls 0.23.46` (without changing the production Agent's Cargo.lock), reopens the non-exportable user-scope CNG P-256 key in another process through native NCrypt, chooses the Rustls ECDSA_NISTP256_SHA256 signature scheme, signs a fixed challenge and outputs **only** its public DER signature. Independent .NET `ECDsaCng.VerifyData` rejects a wrong message and a changed signature. No private-key export, credential store persistence, operator approval, network or real service installation.

**Confirmed source revision `dbc6e8224e437af52601fd6c201000d950ce4c65`: SUCCESS in [Windows isolated synthetic CI #42626308264](https://github.com/kunlong-luo/lanveriq/actions/runs/42626308264).** The native rustls-cng signing probe, standalone Windows CNG CSR and cross-platform Java25 verifier all completed successfully. The source proof is narrower than completed client mTLS: no actual certificate resolver, trusted issuer, TLS handshake, Caddy→Tomcat proxy or PostgreSQL transaction is invoked. The disposable KSP key is deleted after every test, and the normal Agent binary remains IDLE/default-OFF. This new check must pass before asserting that `rustls-cng` is compatible with our chosen Rustls ABI.

Earlier synthetic current-user key-deletion negative check and Windows CNG CSR→Java Bouncy Castle proof were confirmed green in [#42623937763](https://github.com/kunlong-luo/lanveriq/actions/runs/42623937763).
