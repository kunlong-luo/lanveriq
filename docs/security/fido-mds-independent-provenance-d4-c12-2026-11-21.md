# A11-D4-C12 — independent offline FIDO MDS source-review seal

**2026-11-21 | `main` | 5/5 exact-functional-SHA synthetic CI SUCCESS | v0.1 NOT RELEASE READY.**

## Why the previous root-pin claim is insufficient

D4-C9–C11 validated real **ephemeral** ES256/RS256 JWS signatures, a proposed root and its corresponding caller-supplied hash, offline PKIX/CRLs, Ed25519 signed time and malformed/ambiguous metadata denial. A malicious claimant could also construct a self-consistent **fake** root, time key, signed JWS and matching CRL with those values. Their cryptographic consistency does **not** establish that the key is the official, independently approved FIDO signing authority.

This step introduces an **additional, still-untrusted, package-private offline governance proof** that covers exact already-verified evidence using **two independent pre-pinned Ed25519 verification keys**. It is not a replacement for FIDO certificate path validation or a claim that the organization has provisioned the keys.

## Actual implementation and boundaries

- `StagedFidoMdsIndependentReviewSeal`: immutably binds a versioned, domain-separated approval transcript to the exact **JWS SHA-256, PKIX root DER SHA-256, signer leaf SHA-256, CRL DER-set SHA-256, independently claimed time-key SHA-256, exact signed-time-proof SHA-256, checkedAt, BLOB number, signed-time sequence, governance review sequence, validity window**, and declared MDS source.
- Require **two distinct, separately provisioned Ed25519 public keys** with independently pinned SHA-256 key fingerprints and both correct 64-byte signatures over the **same transcript**. One key, repeated key, unpinned/forged signer, forged source, stale/expired proof, window greater than seven days, reviewed-data swap or replayed governance review sequence **fail closed**.
- Source declaration is scoped to `https://mds3.fidoalliance.org/` as listed by [FIDO Alliance MDS overview](https://fidoalliance.org/metadata/). **A signed URL is only a declaration, NOT independently verified network provenance**; official source fetch/ETag/cache and revocation distribution are not implemented.
- A separate `inspectWithIndependentReviewOfflineOnly` method first calls all existing crypto, strict JSON, PKIX/CRL, metadata uniqueness and time checks, then withholds **even an untrusted evidence object** unless the independent two-key review seal validates. The older `inspectOfflineOnly` is a legacy *test primitive* and must never be used as an approval or future production trust source.
- Keys, roots, approval decisions and review sequences are supplied solely by disposable **synthetic tests**; **no real governance public keys or official MDS root are installed**. No Spring Beans, HTTP requests, downloader, CA/issuer or WebAuthn4J trusted configuration is added.

## Test and acceptance requirements

- Genuine disposable ES256 and RS256 signing leaf/CA and CRL/PKIX, exact JWS/time proof, two independent real Ed25519 governance signatures; valid fixtures must return only `UntrustedSourceEvidence`.
- Rejection of caller-minted alternative signatures, one real signer plus attacker, duplicated key/pin, missing keys/proof, incorrect authority pin, replayed review sequence, expired/overlong review validity, forged source, replaced **independently signed valid JWS**, replaced fresh signed CRL/time and mutable signature buffers.
- Java 25 real Gradle test XML gate now requires `independentPinnedReviewerKeysBindExactUntrustedSourceAndRejectSelfAssertion`; **ten** mandatory real verifier methods, not self-written XML or source-existence checks. Cohesive L2 actual PostgreSQL 18.6 Testcontainers and frontend/RD-2 default-OFF gates must be verified **at the new exact source SHA** before marking accepted.

## Exact-SHA CI acceptance — 5/5 SUCCESS

[functional SHA `2d39d694`](https://github.com/kunlong-luo/lanveriq/commit/2d39d694fb1724f47276a1ef2f4a94d424580895) passed **5/5 exact-SHA synthetic CI workflows**: [Java 25/Gradle and real JUnit XML showing all TEN mandatory source-time/provenance methods #44895483823](https://github.com/kunlong-luo/lanveriq/actions/runs/44895483823), [PostgreSQL 18.6 Testcontainers real synthetic transactions + mandatory JUnit guard #44895483791](https://github.com/kunlong-luo/lanveriq/actions/runs/44895483791), [PostgreSQL integration source compile #44895483778](https://github.com/kunlong-luo/lanveriq/actions/runs/44895483778), [Next.js frozen fast unit/type/build #44895483732](https://github.com/kunlong-luo/lanveriq/actions/runs/44895483732), and [RD-2 fail-closed default-OFF #44895483711](https://github.com/kunlong-luo/lanveriq/actions/runs/44895483711).

GitHub server-unit logs show `BUILD SUCCESSFUL` and **`PASS: real Gradle JUnit evidence includes all 10 required synthetic offline source-time tests`**. The PostgreSQL workflow ran both actual PostgreSQL 18.6 Testcontainers transactions and its mandatory XML evidence check, not just a source compile. This authenticates the *test executions* for `2d39d694` only; it does not authenticate FIDO origin, governance key custody, independent human reviewers, source CRL URLs or real device release.

These are **synthetic offline tests only**: NO independently approved official FIDO R46 trust anchor, signer/CRL distribution provenance, government/organization-held reviewer key provisioning, production trusted time, persistent source review or official MDS ingestion, real admin WebAuthn MFA, CA/KMS signing or Pop!_OS 24.04 physical acceptance. Production networking, collection, auto-recovery, remote control and issuer OFF; v0.1 NOT RELEASE READY.

## Official source and remaining production blockers

The [FIDO Alliance MDS overview](https://fidoalliance.org/metadata/) currently references the **GlobalSign R46 root certificate** for MDS BLOB signature verification and public MDS3 download. These references establish official documentation, **not a Lanveriq-reviewed root fingerprint, accepted key custody, PKIX production trust anchor, official CRL collection or trusted time**. [MDS changelog](https://fidoalliance.org/mds-changelog/) documents the 2026 v3.1.1 changes; [relying-party usage terms](https://fidoalliance.org/metadata-usage-terms-for-relying-parties-or-service-providers-2/) require separate legal/product review.

Still missing independently authenticated official root-source material, genuine CRL retrieval/distribution evidence, independently managed RFC-compliant time source, organization-held reviewer key custody, **actual two-person identity verification**, persistent review-sequence/metadata rollback protection for these governance seals, official MDS source/ETag caching, status-admission policy, real WebAuthn MFA, issuer/HSM, Pop!_OS 24.04 L3 and release authorization. No real FIDO BLOB or certificate is fetched or installed. **v0.1 NOT RELEASE READY: production networking, collection, automatic recovery, remote control and CA issuance remain OFF.**
