# A11-D4-C9 — FIDO MDS signing-BLOB entry-shape fail-closed gate

**Date:** 2026-11-21 · **Branch:** `main` only · **Implementation:** Spring Boot / Java 25 offline security package · **State:** exact functional SHA CI pending · **Release:** **v0.1 NOT RELEASE READY**.

## Scope, history and source of authority

This is **D4-C9**, not D4-C8: D4-C8/V29 already covered PostgreSQL history-TRUNCATE and same-owner fence-reset refusal. D4-C5/C6 implemented an **unapproved, unmounted** PKIX/JWS/CRL/signed-time source verifier; D4-C7/V28 and D4-C8/V29 remain immutable synthetic-untrusted database fences. This patch does not modify SQL migrations or imply that the provided fingerprints are approved organizational signing roots.

The official FIDO [Metadata Service v3.1.1 draft](https://fidoalliance.org/specs/mds/fido-metadata-service-v3.1.1-rd-20251016.html) defines a Metadata BLOB payload entry with a model identifier, `metadataStatement`, `statusReports`, and `timeOfLastStatusChange`. Its processing rules say to check status reports separately and ignore unknown extensions for compatibility. **This offline preflight is intentionally a conservative local shape screen, not a full metadata statement parser or a status/admission policy.** FIDO's [official MDS changelog](https://fidoalliance.org/mds-changelog/) records the 2026-08-31 R3→R46 root transition and the optional third x5c cross-certificate; NXT-D4-C5 already handles signer-first prefix validation to an explicit caller-provided anchor, without treating the cross-certificate or system CA store as trusted.

## Implementation

- Add package-private, non-Bean `apps/server/src/main/java/com/lanveriq/server/security/StagedFidoMdsPayloadShapeScreen.java` to scan an **already JWS+PKIX/CRL-verified** BLOB `entries` collection. It has no HTTP, file, credential, cache, database, cert-issuance or WebAuthn4J consumer.
- Reject missing/non-array entries, oversized arrays, non-object entries, no/ambiguous model identifier (`aaid`, `aaguid`, or lowercase attestation certificate key identifiers), missing/empty `metadataStatement` object, missing/non-array `statusReports`, missing/invalid/future `timeOfLastStatusChange`, missing/non-string status in any report, future/malformed `effectiveDate`, or rogue-list URL without accompanying hash. Bounded collection/field sizes are conservative **local screening limits**, not claims of exact specification thresholds. No rogue URL is fetched.
- **Unknown status names, including forward-compatible values, are not interpreted as certified.** An entry marked `REVOKED` may have a valid *shape*, but this primitive produces **no allow decision at all**. A separate authenticated policy must reject revoked/compromised/uncertified authenticators as appropriate; that is not implemented or enabled here.
- Wire `StagedFidoMdsPayloadShapeScreen.requireUntrustedEntryShapes` into the existing `StagedFidoMdsSourceVerifier.inspectOfflineOnly` after cryptographic JWS signature and certificate checks, before creating any `UntrustedSourceEvidence`. Invalid entries cause the same fail-closed `AccessDeniedException`. No `trust=true` field, trusted cache, allowed credential, MFA or certificate can be created.
- Add dedicated `StagedFidoMdsPayloadShapeScreenTest` (three model families, malformed entries/date/status, rogue-list coupling, oversize, unknown and revoked statuses) and **fully signed synthetic** `StagedFidoMdsSourceVerifierTest` cases proving the chain still yields only untrusted evidence when shape is valid, and rejects a cryptographically valid but structurally malformed BLOB. Extend `assert-fido-source-time-junit.py` to require both newly executed integrated test methods in real Gradle JUnit XML, not a hand-written success log.

## Acceptance — L1 and evidence

Required source SHA (record after CI), on `main` only:

1. `.github/workflows/server-unit.yml`: Java 25 Gradle test suite with **new tests actually executed**, and the Python JUnit evidence gate verifies mandatory method names exactly once; no skipped/failing tests.
2. `.github/workflows/rustdesk-rd2-release-guard.yml`: RD-2 remote control remains OFF. Neither new verifier nor source tests must become Spring Beans or bind an HTTP route.
3. No production HTTPS, official MDS download, device enrollment, WebAuthn admin MFA, CA issuance, Agent network/collection, auto-recovery or RustDesk is enabled by these changes.

For local manual source-only verification (optional; CI is the release-evidence source):

```bash
cd apps/server
./gradlew test --tests '*StagedFidoMdsPayloadShapeScreenTest' --tests '*StagedFidoMdsSourceVerifierTest'
cd ../..
python3 apps/server/scripts/assert-fido-source-time-junit.py
```

The sample generated keys, leaf certificate with `mds.fidoalliance.org` SAN, root, CRL, Ed25519 time key and metadata statements are **disposable synthetic fixtures**, not approved FIDO or organizational artifacts.

## Still blocking actual launch

A source-level shape check alone cannot establish FIDO origin. **Outstanding**: securely acquire and independently approve the current R46 trust anchor and its provenance; run real official MDS signed-BLOB, issuer-CRL and chain verification against independently approved trusted time and durable global anti-rollback; process per-authenticator status reports (including REVOKED/compromise) and attestation trust boundaries; enable actual administrator WebAuthn MFA only after separate replay/session/RBAC acceptance; run real Pop!_OS 24.04 hardware tests. Until those gates finish, **production networking, collection, automatic recovery, remote control and CA signing remain OFF**.
