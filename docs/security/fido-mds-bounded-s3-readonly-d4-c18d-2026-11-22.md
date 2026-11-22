# A11-D4-C18d — bounded read-only S3 SDK bridge (NOT live Object Lock acceptance)

> **HISTORICAL / SUPERSEDED in active main by [A11-D4-C18e](fido-cloud-neutral-witness-d4-c18e-2026-11-22.md).** These accurate old-SHA results describe a withdrawn AWS SDK prototype, **not currently compiled code, an adopted AWS provider or a live WORM**. Original test evidence and failures remain for audit.

**2026-11-22 · L1 PASS at corrected SHA 2003edaa · external AWS NOT RUN · G-02/G-08 OPEN · v0.1 NOT RELEASE READY.**

## What was implemented

A package-private `StagedS3VersionedWitnessReadbackClient` uses the pinned official AWS SDK Java v2 **S3Client interface provided by an explicit caller**, constructing both `GetObjectRequest` and `GetObjectRetentionRequest` for the *same* externally pinned bucket, object key, non-null version ID and expected 12-digit bucket owner. It checks the pin **before** calling the provided SDK client, makes one object read and one retention read, and delegates final actual-byte SHA-256 / COMPLIANCE / matching and future retention validation to the previously tested [D4-C18c offline inspector](fido-mds-object-lock-version-readback-d4-c18c-2026-11-22.md).

**No S3 client is constructed, registered as a Spring bean, configured with credentials or contacted by any production caller.** The method is deliberately called `readSpecificVersionOfflineTestOnly`: injecting a real S3Client **would** perform outbound SDK calls, which is explicitly *not authorized by this stage*. All L1 tests use a mocked S3Client.

The read path uses AWS SDK `ResponseInputStream<GetObjectResponse>`, rejects a declared body longer than **1 MiB**, reads at most **1 MiB + 1 byte** to detect a misleading Content-Length, aborts overly large or wrong-version streams rather than fetching the rest, and closes the response stream. It rejects a missing body, inconsistent count, missing/foreign response version and delete-marker evidence. SDK 403/404/timeout, mid-read IOException, missing retention permission, GOVERNANCE downgrade, digest substitution and every other observed failure are **deny**; there is no retry to a new/unversioned object. It does **not** write, lock, delete, rotate or provision anything.

## Exact L1 verification

`StagedS3VersionedWitnessReadbackClientTest` uses **Mockito-only** S3Client mocks plus real AWS SDK model/stream objects: exact two-request arguments and order, no other SDK operations, malformed pins produce **zero client interactions**, denied object GET or retention request yields fail closed, changed response version rejects, oversize declared/actual content rejects without requesting retention, broken streams deny, and downgrade to GOVERNANCE denies. It checks only the read-only adapter + C18c inspector behavior, not any actual S3 network or AWS policy.

**CI acceptance:** exact functional SHA + [server unit workflow](../../.github/workflows/server-unit.yml) Java25 L1 PASS; [integration compile](../../.github/workflows/server-integration-compile.yml) and existing PG18 regression only prove no backend breakage. ## Actual L1 evidence and preserved failure

Initial code/docs [`94424351`](https://github.com/kunlong-luo/lanveriq/commit/94424351d46b9e0903e343d289f8f1ef0b966bd1): [initial Java 25 #45020864719 — FAIL (Mockito overload ambiguity)](https://github.com/kunlong-luo/lanveriq/actions/runs/45020864719) because Java could not choose AWS SDK's overloaded `getObject` / `getObjectRetention` for untyped Mockito `argThat`. The defect was in test compile code, not an AWS network call. [PG18 source compilation #45020864720 — SUCCESS, on initial SHA only](https://github.com/kunlong-luo/lanveriq/actions/runs/45020864720); [initial Next.js #45020864698 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45020864698); [initial doc gate #45020864689 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45020864689); [initial RD-2 #45020864701 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45020864701). These results are **not** a 5/5 acceptance set.

Corrected feature SHA [`2003edaa`](https://github.com/kunlong-luo/lanveriq/commit/2003edaa0b9614ecd138507cd465b0f7fe3d64f1) added explicit `argThat` generic request types. [Java 25 L1 #45020964141 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45020964141) with actual `BUILD SUCCESSFUL in 54s`; [RD-2 default-OFF #45020963983 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45020963983). The **only new security-feature L1** validated at that SHA is the full Java unit suite containing the C18d Mockito tests. No PG18 Testcontainers or live S3 test was triggered for the corrected SHA. The read-only class itself was unchanged by the correction; L2 database test scope is unrelated.

**Real S3 delete/retention/KMS/cross-account proof: NOT RUN; G-02 OPEN.**

## Not yet proven; next independent acceptance criterion

- No actual independent AWS account, S3 Object Lock COMPLIANCE bucket, cross-account role, retained object version, real KMS encryption/signature keys, TrustedTime source, independently retained high-water, cloud audit, or physical Pop!_OS 24.04.
- The **caller** can still fabricate a valid-looking expected pin or mock SDK evidence; results remain `UntrustedVersionReadback`, never authority to trust FIDO metadata, admit a passkey, issue CA certificates or activate endpoints.
- A real organization-approved external acceptance must create a compliance-retained test object, record immutable version ID, exercise cross-account `s3:GetObjectVersion` and `s3:GetObjectRetention` with KMS decrypt, and independently prove delete/shorten-retention denial and old-version readability after newer version/delete marker. Bind protected high-water to actual `pg_dump`/restore rollback verification and separate reviewer signatures. Cloud/role provisioning and live calls require explicit approval.

AWS SDK source reference: [ResponseInputStream abort vs. close](https://docs.aws.amazon.com/java/api/latest/software/amazon/awssdk/core/ResponseInputStream.html); [S3 Object Lock guide](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lock.html).

**Production collection, network, auto-recovery, remote control RD-2, live WebAuthn and CA/FIDO trust remain default OFF; v0.1 NOT RELEASE READY.**
