# A11-D4-C14 — offline acquisition-custody receipt (NOT official ingestion)

**Date:** 2026-11-22 · **Branch:** `main` only · **Exact-source CI:** PENDING · **Release:** **v0.1 NOT RELEASE READY**.

## Scope and threat model

FIDO Alliance [MDS overview](https://fidoalliance.org/metadata/) identifies `https://mds3.fidoalliance.org/` as the BLOB URL; the [MDS3 specification](https://fidoalliance.org/specs/mds/fido-metadata-service-v3.1-ps-20250521.html) requires certificate chain revocation information from the applicable CRL distribution locations. URL strings, a file SHA-256, TLS version and HTTPS status supplied by a caller **do not prove a download happened**. An attacker can also claim to have independently fetched a clean JWS or clean CRL, then swap the bytes after signing.

This stage provides an **unmounted offline receipt verifier**. It binds actual **previously crypto-verified but still untrusted** JWS and CRL bytes to one signed, versioned acquisition assertion with independently provisioned Ed25519 acquisition-key pinning and independent CRL source-URL allowlisting. It rejects evidence mismatches, not fake network connections; the transport fields remain **attestations made by the purported acquisition-key holder**.

## Implementation

- `StagedFidoMdsAcquisitionReceiptScreen` is package-private non-Bean, with **no web client, HTTP route, fetch, scheduled task, trust-store change, persistence, source acceptance or CA action**.
- Receipt binds exact **compact JWS SHA-256, root DER hash, BLOB sequence, source-time sequence, CRL DER-set SHA-256, each CRL DER SHA-256 and independently allowlisted HTTPS acquisition URL**, checkedAt, fetchedAt, receipt sequence, asserted `TLSv1.3`, `HTTP 200`, no redirect and declared MDS URL. All fields are signed under `lanveriq/fido-mds/acquisition-receipt/v1` with Java Ed25519.
- Require independently pinned acquisition public key SHA-256 distinct in purpose from C12's two reviewer keys (separation must be established **outside code**, not assumed by synthetic tests). Reject self-declared alternate acquisition signer, altered/unknown CRL location, wrong/duplicated URLs, altered CRL DER, different **valid signed** JWS, stale/future fetch, non-monotonic receipt sequence, non-200, redirects, alternate TLS, malformed or non-HTTPS URL.
- Preserve existing signature/PKIX/CRL/strict JSON/time verification and independent C12 reviewer signatures via an optional `inspectWithAcquisitionReceiptOfflineOnly` wrapper. The returned object remains `UntrustedSourceEvidence`; acquisition receipt check alone returns only `UntrustedAcquisitionEvidence`.
- Maximum 16 distinct CRL sources, maximum 24 hours between asserted acquisition and external checkedAt. URLs in tests are synthetic `*.invalid` and MUST NOT be regarded as FIDO official CRL endpoints. This is an intentional strict local refusal policy that must be reviewed against independently discovered, real official CRL distribution sources before a production design exists. No arbitrary CRL URLs from submitted JWS/cert are followed.
- Strong negative tests use disposable **real ES256 and RS256** JWS, PKIX signer, signed CRL, domain-separated signed time, **two distinct real Ed25519 review signatures**, and independently signed Ed25519 acquisition receipt. The source receipt key is separately provisioned in synthetic fixtures, not actually managed in KMS/HSM. Real Gradle JUnit XML gate requires `signedAcquisitionReceiptsBindExactJwsCrlAndOfflineCustodyAssertions` (**13 required verifier methods**).

## CI and release gates (do not claim PASS before actual evidence)

Java 25 real Gradle JUnit and 13 mandatory verifier methods; separate PostgreSQL 18.6 Testcontainers synthetic transactions; PostgreSQL integration source compile; Next.js fast checks; RD-2 remote default OFF, **all tied to the same functional SHA**. Passing does not establish official MDS acquisition, custody or legal clearance.

## Still blocked

**No real MDS/CRL downloader exists**; there is no independent TLS handshake capture, source certificate/CRL CDP URL verification, persistent append-only receipt and review-sequence ledger for these signatures, trusted UTC/time key custody, authorization of real acquisition and review keys, official FIDO usage/legal review, authoritative metadata status-admission policy, real WebAuthn administrator MFA, CA/HSM issuance, or Pop!_OS 24.04 physical L3. D4-C13's public R46 certificate identity is **not** trusted for production. The separate C13 R46 reference gate remains optional and unmounted; this C14 synthetic acceptance path does NOT make a self-issued test CA officially trusted.

**v0.1 NOT RELEASE READY. Production networking, device collection, recovery, remote control and certificate issuing remain OFF.**
