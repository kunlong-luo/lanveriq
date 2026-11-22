# A11-D4-C18c — offline S3 version-specific Object Lock read-back gate

**2026-11-22 · Staged offline L1 PASS · exact functional SHA `7489a1c5` · G-01/G-02/G-08 OPEN · v0.1 NOT RELEASE READY.**

## Scope

Add a package-private `StagedS3VersionedWitnessEvidenceReader` built on the **official AWS SDK for Java v2, pinned through the Gradle BOM at 2.59.5**. This class accepts *SDK request/response models and independently asserted expectations* to perform a bounded offline fail-closed check. No `S3Client` is constructed, no AWS credential or account is created, no HTTP/network is invoked, and no Spring bean/controller/CA/Agent/WebAuthn permission is installed.

The verification result is strictly `UntrustedVersionReadback`; it is not a trusted AWS attestation, a complete archive reader or a production FIDO MDS source. Even matching modeled responses can be forged by the caller. Production would need authenticated **live independently administered S3 read-back**, real KMS custody, durable independent high-water and trusted time.

## Fail-closed contract

- Require an **independently asserted** pinned bucket/key, **non-`null` exact version ID**, 12-digit expected bucket-owner account, expected SHA-256 of the bytes, and minimum required retention end.
- Require **both** `GetObjectRequest` and `GetObjectRetentionRequest` to match the pinned bucket/key/version/account and reject unversioned GET, ranged/part reads, wrong owner/key, wrong retention-version or returned version ID.
- Reject any indicated delete marker, content-range or content-length disagreement. Hash the **actual SDK ResponseBytes payload** using Java SHA-256 and compare to the pinned lowercase digest. Never confuse S3 ETag/multipart/encryption metadata with an independent content digest.
- Require **both** the version-specific object readback header and the separately modeled retention readback to explicitly say **COMPLIANCE**, with matching retain-until dates. Retention must still be in force at caller-provided observation time and satisfy the pinned minimum.
- Apply a 1 MiB offline readback size cap and reject empty bodies and malformed pins. The cap is not production streaming/archival design; a future authorized adapter needs streaming, archive rollover, and consistent version/read-back controls.
- No local code can attest that two modeled responses genuinely came from AWS, that the account/key/KMS/clock are independent, that deletion or retention-shortening is impossible, that the object is stored outside the DBA domain, or that an object version is bound to an approved C17 signed history. Keep **G-02 OPEN**.

## L1 negative evidence

`StagedS3VersionedWitnessEvidenceReaderTest` verifies one positive *untrusted* response plus wrong/missing version IDs, account/bucket/key mismatch, range/part read, wrong retention request, mismatched SDK returned version, delete marker, content-range, altered/empty/oversize bytes, false content-length, digest substitution, GOVERNANCE mode, missing retention, mismatched retention dates, too-short/expired retention and invalid external pins. All fixtures are created in-process; **no real AWS SDK network call or S3 Object Lock service is exercised**.

## Verified exact-SHA CI (synthetic/offline only)

The precise [C18c functional SHA `7489a1c5`](https://github.com/kunlong-luo/lanveriq/commit/7489a1c51625656c6d88b2ce2ee3d6c0f75a9255) achieved **6/6 triggered workflow SUCCESS**: [Java 25 full unit (offline SDK negative tests) #45020286625](https://github.com/kunlong-luo/lanveriq/actions/runs/45020286625) · [PG18 source compilation (no DB) #45020286709](https://github.com/kunlong-luo/lanveriq/actions/runs/45020286709) · [real PG18.6 existing regression ≥150 #45020286619](https://github.com/kunlong-luo/lanveriq/actions/runs/45020286619) · [Next.js preview #45020286646](https://github.com/kunlong-luo/lanveriq/actions/runs/45020286646) · [documentation gate #45020286677](https://github.com/kunlong-luo/lanveriq/actions/runs/45020286677) · [RD-2 OFF #45020286755](https://github.com/kunlong-luo/lanveriq/actions/runs/45020286755). Java 25 full `test`: `BUILD SUCCESSFUL in 53s` (C18c JUnit fixture compiled and exercised by the full unit suite). Actual PG18.6 Testcontainers *existing database regression*, 11 classes ≥150 PASS, `BUILD SUCCESSFUL in 1m 10s`, no failed/errored/skipped. The database run validates non-regression after dependency changes **only**; it is not a live S3 readback, S3 Object Lock service or AWS KMS integration test.

**C18c external AWS/S3 acceptance: NOT RUN**. Docs-only commits must not be counted as C18c feature validation.

## External live acceptance (NOT RUN)

1. Approve a distinct security-owned AWS account, bucket Object Lock COMPLIANCE retention and legal duration, separate KMS keys/reader IAM, independent high-water custody, trusted time and audit.
2. Use authorized AWS SDK client with versionId and `expectedBucketOwner` on both calls, inspect actual byte SHA-256 and version-specific `GetObjectRetention` (not unversioned HEAD/ETag). Confirm even a new version or delete marker does not hide a retained old version.
3. Under approved test account and organization policy, prove protected version DELETE and retention shortening are denied; GOVERNANCE, missing evidence and KMS disable/read failures deny. Independent CloudTrail and security reviewer must observe the exercise.
4. Recover real externally protected `pg_dump` or PITR snapshots into a separate PG18 environment, verify stale recovery is rejected against protected high-water and current restored DB is accepted.
5. Bind exact SHA, bucket region/version IDs, account/role IDs (without public secrets), retention/audit evidence, official FIDO source approvals and authorized security/SRE signatures. Do not upload sensitive AWS keys or employee data to public CI.

AWS semantics: [Object Lock official guide](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lock.html) · [version-specific retention read-back and permissions](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lock-managing.html) · [SDK setup](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/setup-project-gradle.html).

**Production network, collection, automatic recovery, RD-2 remote access, formal FIDO trust, real WebAuthn and CA issuing remain OFF.**
