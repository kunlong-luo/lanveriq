# A11-D4-C10 — FIDO MDS offline duplicate-identifier denial

**Date:** 2026-11-21 · **Only development branch:** `main` · **State:** five exact-SHA synthetic CI gates PASSED; no production authorization · **Release:** **v0.1 NOT RELEASE READY**.

## Problem and fail-closed contract

The earlier D4-C9 screen rejects malformed individual FIDO MDS 3.1.1 metadata entries but does not reject **multiple structurally valid entries claiming the same model identifier**. A future lookup might silently select the first or last metadata/status record. This must be denied before any metadata is considered, including in the current untrusted-source lane.

The existing package-private `StagedFidoMdsPayloadShapeScreen.requireUntrustedEntryShapes` now maintains an invocation-local bounded set of identifiers while scanning up to the existing 50,000-entry upper limit:

- **FIDO2 AAGUID:** ASCII hex UUID, normalized to lowercase before comparison. Different letter case is not a different model.
- **FIDO UAF AAID:** ASCII `4hex#4hex`, normalized to uppercase before comparison.
- **U2F `attestationCertificateKeyIdentifiers`:** existing lowercase 40-hex syntax, now unique within a single entry **and across the BLOB**. Different models may have multiple distinct identifiers, but an identical identifier cannot be interpreted twice.
- Identifier namespaces remain separate; this change intentionally does not infer that an AAGUID equals an AAID or certificate-key identifier.
- A BLOB with distinct AAID/AAGUID/U2F entries remains structurally acceptable but yields **no trusted result**. Unknown/revoked statuses remain opaque and never grant registration, MFA or attestation trust.

This is a conservative *local structural gate*, not an assertion that every published metadata entry will meet the project's exact identity restrictions or an approved official MDS acceptance policy. It must not be mistaken for trusted FIDO origin.

## Actual source and automated checks

- `apps/server/src/main/java/com/lanveriq/server/security/StagedFidoMdsPayloadShapeScreen.java`: refuse repeated model identities with no new dependencies, bean, route, external fetch, cache or database mutation.
- `StagedFidoMdsPayloadShapeScreenTest`: repeated AAGUID/AAID with case differences, repeated U2F SHA-1 identifiers both within one entry and across entries, and distinct mixed families.
- `StagedFidoMdsSourceVerifierTest.cryptographicallyValidDuplicateModelIdsStillRejectBeforeUntrustedEvidence`: true disposable ES256 **and** RS256 JWS, PKIX/CRL and exact-JWS-bound Ed25519 time evidence; duplicate entry combinations are rejected even after valid crypto. Distinct signed models still return `UntrustedSourceEvidence`.
- `apps/server/scripts/assert-fido-source-time-junit.py` requires the integration method in **actual executed Gradle JUnit XML**. A test source file or a self-created XML report alone is not success.

**Exact-source acceptance: 5/5 SUCCESS.** [functional SHA `2368163e`](https://github.com/kunlong-luo/lanveriq/commit/2368163e588e6e94f74a748bdc6e162b9c4c6b9a) passed **5/5 exact-SHA synthetic CI gates**: [Java 25/Gradle unit and 8 required real JUnit XML methods #44893055170](https://github.com/kunlong-luo/lanveriq/actions/runs/44893055170), [PostgreSQL 18.6 Testcontainers synthetic transactions #44893055206](https://github.com/kunlong-luo/lanveriq/actions/runs/44893055206), [PostgreSQL integration-source compile #44893055072](https://github.com/kunlong-luo/lanveriq/actions/runs/44893055072), [Next.js frozen install/tests/typecheck/build #44893055069](https://github.com/kunlong-luo/lanveriq/actions/runs/44893055069), and [RD-2 default-OFF #44893055089](https://github.com/kunlong-luo/lanveriq/actions/runs/44893055089). These tests use disposable synthetic PKIX/CRLs/signers, NOT official FIDO provenance, production trusted time or live MFA. The new test `cryptographicallyValidDuplicateModelIdsStillRejectBeforeUntrustedEvidence` was part of eight mandatory executed verifier JUnit XML methods; the unit evidence gate failed closed unless each appeared once without skip/failure. PostgreSQL acceptance covers only existing synthetic database flows, **not a durable official MDS authority**. Prior D4-C9 evidence remains historical and is not substituted for the D4-C10 functional SHA.

## Boundaries / next steps

FIDO MDS [specification](https://fidoalliance.org/specs/mds/fido-metadata-service-v3.1.1-rd-20251016.html) and [2026 R46 transition guidance](https://fidoalliance.org/mds-changelog/) are only design references. Do **not** silently download/provision a root, fetch official BLOBs, accept FIDO license terms on behalf of the organization, enable metadata status-based admission or connect a real administrator. Future production requires independently approved signing-root provenance/custody, trusted time and revocation provenance, durable anti-rollback, actual administrator WebAuthn MFA, and Pop!_OS 24.04 hardware tests. Production networking, collection, auto-recovery, remote control and CA signing remain **OFF**.
