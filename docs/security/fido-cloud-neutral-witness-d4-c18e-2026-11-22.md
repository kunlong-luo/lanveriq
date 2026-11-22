# A11-D4-C18e — cloud-neutral, untrusted, immutable witness evidence screen

**2026-11-22 · ACTIVE cloud-neutral source · 6/6 exact-functional-SHA isolated CI PASS · G-02/G-08 OPEN · v0.1 NOT RELEASE READY.**

## Why this change

The core Lanveriq agent, server and Next.js interface do **not need S3**. S3 was a proposed G-02 WORM storage candidate in ADR-0012, not a pre-approved deployment requirement. C18c and C18d added AWS SDK types to the main Gradle runtime in anticipation of future external evidence custody; this design choice has been withdrawn by the maintainer. Their exact-SHA successes and initial C18d Mockito failure remain in the [historical C18c](fido-mds-object-lock-version-readback-d4-c18c-2026-11-22.md) and [C18d](fido-mds-bounded-s3-readonly-d4-c18d-2026-11-22.md) specs, but those Java AWS-specific source/tests are **removed from active main**.

## New active core contract

- `StagedImmutableWitnessArchiveReader` is package-private; its `ReadOnlyArchivePort` has two methods: `readExactVersion(VersionLocator)` and `readExactVersionRetention(VersionLocator)`. No vendor SDK, writable storage adapter, client factory, network bean, or default profile.
- `VersionLocator`: **opaque administrative domain, object key and exact version token**. `IndependentlyAssertedPin`: same identity, exact lowercase SHA-256 and minimum retain-until. No platform-specific account numbering or AWS bucket assumptions.
- `VersionedObjectRead` supplies bounded input bytes, declared length, version-specific protection and retention claims; `VersionedRetentionRead` supplies a second claim for the **same version**. Both must say `NON_BYPASSABLE_WORM`, match exactly in retain-until and exceed pin minimum / observation time. A label alone cannot prove real WORM.
- Read no more than 1 MiB + 1 byte; verify exact length and SHA-256 **over bytes actually returned**, never an ETag or caller-claimed digest. Reject empty/mutated/oversized bodies, wrong versions, conflicting or bypassable retention, old/invalid pins, provider exceptions or interrupted streams. Always close opened streams, including on denial.
- `UntrustedArchiveMatch` is untrusted-only and contains no trusted-source or signing authorization. Neither the port nor a local clock certifies a provider; full C17 signed history matching and actual independent custody are still separate requirements.
- Add `test_cloud_neutral_witness_boundary.py` as a Gradle workflow gate to block AWS SDK, S3Client or similar cloud-vendor coupling from reentering main server sources or the default Gradle dependency list.

## Test scope and exact evidence

`StagedImmutableWitnessArchiveReaderTest`: one positive **untrusted** match; invalid/expired pins produce **zero calls**; changed version or retention version is denied; false length, altered or oversized body, empty bytes denied; bypassable/unknown/conflicting/expired retention denied; interrupted data source and provider failures denied; stream closed. A real Java25 Gradle unit run is required to call this PASS. The Python architecture checker checks source restrictions, not actual WORM behavior.

**Functional SHA [`5eec9a0a`](https://github.com/kunlong-luo/lanveriq/commit/5eec9a0a343b2f59b1645ff7aa4212d60b314671): PASS for this limited source scope.** All **6/6 same-SHA triggered CI SUCCESS**: [Java25 L1 + cloud-neutral 3/3 Python gate #45021925812](https://github.com/kunlong-luo/lanveriq/actions/runs/45021925812) · [real PostgreSQL18.6 11 classes ≥150 #45021925835](https://github.com/kunlong-luo/lanveriq/actions/runs/45021925835) · [integration source compilation only #45021925894](https://github.com/kunlong-luo/lanveriq/actions/runs/45021925894) · [Next.js #45021925819](https://github.com/kunlong-luo/lanveriq/actions/runs/45021925819) · [docs governance #45021925849](https://github.com/kunlong-luo/lanveriq/actions/runs/45021925849) · [RD-2 default-OFF #45021925866](https://github.com/kunlong-luo/lanveriq/actions/runs/45021925866). Java25 actual `test`: `BUILD SUCCESSFUL in 39s`; Python cloud-neutral boundary: 3/3 OK; actual disposable PG18.6 database regression: `BUILD SUCCESSFUL in 1m 27s`, 11 Testcontainers classes ≥150 pass and zero failed/errored/skipped. Integration compilation is **source only**; PG18 regression proves old behavior still works without AWS dependencies, **not independent archival storage**. **Actual independent WORM or HSM/KMS: NOT RUN**. Prior C18c/d exact-SHA results are historical only.

## G-02 external work still outstanding

A formally selected, independently administered WORM/compliance provider or qualified physical medium with real immutable version/retention enforcement, separately governed signer/KMS/HSM and trusted time; real read-back, delete/shorten-retention denial and high-water persistence after isolated PG18 restoration. Neither SQLite nor PostgreSQL alone solves it. No real employee data or external access was used.

[ADR-0012](../architecture/adrs/0012-independent-fido-witness-object-lock.md) · [CURRENT_STATUS](../CURRENT_STATUS.md) · [G-02 register](../planning/release-gap-register.md).

**Production networking, collection, auto-recovery, remote control RD-2, official FIDO trust, live administrator MFA and certificate issuance remain OFF. v0.1 NOT RELEASE READY.**
