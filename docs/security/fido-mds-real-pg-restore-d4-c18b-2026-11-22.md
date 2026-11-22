# A11-D4-C18b — actual disposable PG18 custom-format backup/restore and witness anti-rollback

**2026-11-22 · `main` only · L2 7/7 same-SHA PASS · P0 G-02/G-08 OPEN · v0.1 NOT RELEASE READY.**

## Distinct from prior simulated rewind

[C18a](fido-mds-db-owner-rewind-d4-c18a-2026-11-22.md) deleted a V30 row with owner-level privileges. C18b uses **real `pg_dump --format=custom`, `pg_restore --list` and `pg_restore --exit-on-error`** inside the disposable PostgreSQL 18.6 Testcontainers image. Neither JUnit XML nor simulated SQL is substituted for the actual PostgreSQL binary operations. The `pg_dump` archive remains inside that disposable container; the signed high-water is stored only in a JUnit host temp file, **not external security-controlled WORM**.

## Real recovery test algorithm

1. Use Flyway-migrated PG18 and JPA transaction helpers to commit V26/V30 synthetic acquisition #1, with exactly one DB-owned audit. Construct and verify disposable Ed25519 signed C16 witness #1, then execute `pg_dump -Fc` of the database **before** acquisition #2.
2. Commit V26/V30 acquisition #2, verify the signature+head of a two-witness C17 history, and independently retain the sequence-2 head in a host `@TempDir` file. Execute a **second real** `pg_dump -Fc` after the transaction.
3. Create **two separate restore DBs** inside the PG18 container and restore old/new archives with `pg_restore --exit-on-error --no-owner --no-acl`. Never destroy or overwrite the application database and other PG18 test fixtures.
4. Use a standard Spring test-only `DriverManagerDataSource` and **Hibernate `SessionFactory.withOptions().connection(...).openSession()`** (no production handwritten JDBC DAO) to check the *actual restored database* with the existing C17 verifier; require old tail (1) **DENIED** for higher retained witness (2), fresh tail (2) **MATCH**, including real V30/V26 and audit checks.
5. Explicitly prove the safety boundary: a valid *old* witness list and its *old* cursor would match the restored old database; without externally durable high-water custody, a coordinated rollback remains undetectable.
6. Strict real JUnit XML gate must require `PostgreSqlFidoMdsAcquisitionReceiptIT.c18bActualPgDumpRestoreRejectsStaleBackupAndAcceptsFreshBackup` and 11 Testcontainers classes / at least **150** successful tests. The CI run must actually execute the PostgreSQL client commands. Never count compilation, fake XML or a different SHA as L2 PASS.

## Exact-SHA CI evidence (all isolated synthetic)

**Functional SHA [`4e9dcdac`](https://github.com/kunlong-luo/lanveriq/commit/4e9dcdac074200548317669e78cf8f66e405c3f3)** passed all **7/7 triggered CI workflows on this same SHA**: [Java 25 unit #45019585898](https://github.com/kunlong-luo/lanveriq/actions/runs/45019585898) · [real PG18.6 Testcontainers ≥150 #45019585915](https://github.com/kunlong-luo/lanveriq/actions/runs/45019585915) · [PG18 compilation #45019585907](https://github.com/kunlong-luo/lanveriq/actions/runs/45019585907) · [Next.js #45019585896](https://github.com/kunlong-luo/lanveriq/actions/runs/45019585896) · [documentation #45019585889](https://github.com/kunlong-luo/lanveriq/actions/runs/45019585889) · [RD-2 default-OFF #45019585873](https://github.com/kunlong-luo/lanveriq/actions/runs/45019585873) · [Linux Batch A Caddy TLS/PG18 #45019585916](https://github.com/kunlong-luo/lanveriq/actions/runs/45019585916). Real PostgreSQL 18.6 Testcontainers emitted `BUILD SUCCESSFUL in 1m 18s`; mandatory real Gradle XML gate reported **11 classes, at least 150 passing tests; none failed, errored or skipped**. This proves the new real `pg_dump -Fc`/`pg_restore --exit-on-error` old/fresh comparison ran inside the disposable PostgreSQL container, not that external WORM or production PITR exists. Later documentation-only SHA checks cannot be substituted for this evidence.

## What this does NOT prove

- **No actual cross-administration-domain WORM bucket/object, AWS S3 Object Lock mode/retention/version ID, KMS/HSM custody, independently governed high-water or CloudTrail.** [ADR-0012](../architecture/adrs/0012-independent-fido-witness-object-lock.md) stays *PROPOSED*, not deployed.
- **No externally retained database dump**; both dumps live in the disposable database container. No production `pg_basebackup`, WAL/PITR, continuous backup, offsite recovery, key rotation, data consent, authorized WebAuthn or CA issuance.
- No Pop!_OS 24.04 physical L3 and no asserted production RPO/RTO. Production networking, collection, auto-recovery, remote control and certificate issuance remain OFF.

**Next exit condition:** approve and provision a separate WORM/KMS administrative domain, validate explicit object version retention/read-back plus independent high-water custody and actually restore an externally preserved dump with independent oversight. Until then **G-01/G-02/G-08 OPEN; v0.1 NOT RELEASE READY.**
