# A11-D4-C10 — FIDO MDS offline duplicate-identifier denial

**Date:** 2026-11-21 · **Only development branch:** `main` · **State:** implementation submitted for exact-SHA CI; no production authorization · **Release:** **v0.1 NOT RELEASE READY**.

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

**Evidence pending** until the functional SHA passes `.github/workflows/server-unit.yml` (JDK 25 Gradle L1, JUnit method evidence) and `rustdesk-rd2-release-guard.yml` (default OFF). The prior D4-C9 SHA's success does not carry forward. PostgreSQL real transactions, official FIDO root provenance, external CRLs, trusted time and actual WebAuthn MFA are not validated by this source-only work.

## Boundaries / next steps

FIDO MDS [specification](https://fidoalliance.org/specs/mds/fido-metadata-service-v3.1.1-rd-20251016.html) and [2026 R46 transition guidance](https://fidoalliance.org/mds-changelog/) are only design references. Do **not** silently download/provision a root, fetch official BLOBs, accept FIDO license terms on behalf of the organization, enable metadata status-based admission or connect a real administrator. Future production requires independently approved signing-root provenance/custody, trusted time and revocation provenance, durable anti-rollback, actual administrator WebAuthn MFA, and Pop!_OS 24.04 hardware tests. Production networking, collection, auto-recovery, remote control and CA signing remain **OFF**.
