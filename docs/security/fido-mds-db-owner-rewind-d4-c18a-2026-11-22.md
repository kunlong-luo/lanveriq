# A11-D4-C18a — database-owner rewind versus detached host-retained high-water

**2026-11-22 · P0 G-02 remains OPEN · isolated PostgreSQL 18.6 synthetic only · v0.1 NOT RELEASE READY.**

## Why this increment

D4-C17 checked signed historical witness evidence versus a supplied independently retained high-water and the current PostgreSQL V30/V26/audit tail. The missing targeted negative scenario was **an actual database owner bypassing PostgreSQL-local immutability**. C18a adds that isolated scenario without building a second authorization implementation or pretending to deploy external storage. This is *one subtask* of C18, not a declaration that external WORM/KMS exists.

## Rehearsal steps and trust boundary

1. Use a disposable real PostgreSQL 18.6 Testcontainers database. Create two normal V26/V30 source/acquisition receipts under synthetic owner and session, including actual DB-computed digests and audit. Generate disposable Ed25519 signed C16 checkpoints and the C17 verifiable history; confirm head #2 matches live DB.
2. Write the independent **test fixture** high-water (stream UUID, latest witness sequence, signed-checkpoint SHA-256, signer public-key SHA-256) with JDK `Properties` to a JUnit `@TempDir` file **outside the PostgreSQL container**. This is not an external admin domain, durable WORM, trusted source or production backup.
3. Using the isolated test DB owner/migrator privilege, `ALTER TABLE ... DISABLE TRIGGER USER` on the acquisition and audit tables, delete the newest V30 receipt and matching audit, then re-enable triggers in a `finally` block before committing. Do not weaken or remove production migrations or the runtime role separation. This is a **controlled owner-level rewind simulation**, not real `pg_dump` / `pg_restore` / PITR.
4. Reload the higher host-stored head and assert the signed C17 history is now rejected by the actual PostgreSQL tail; reject an attempt to count a merely valid old signature as fresh evidence.
5. Explicitly assert the limitation: if an attacker also replaces the whole witness history *and* its external head with earlier genuine material, the existing verifier can match the earlier DB. This is why independent WORM retention and custody are mandatory rather than an optional UI/CI improvement.

## Accepted exact-source evidence — L1/L2 isolated synthetic only

**Functional SHA [`5afbee8a`](https://github.com/kunlong-luo/lanveriq/commit/5afbee8aa3f35a2ee916a34f4968d23242790aba)**: [Java 25 unit #45018857210](https://github.com/kunlong-luo/lanveriq/actions/runs/45018857210) · [PG18.6 real Testcontainers #45018857143](https://github.com/kunlong-luo/lanveriq/actions/runs/45018857143) · [PG integration compile #45018857237](https://github.com/kunlong-luo/lanveriq/actions/runs/45018857237) · [Next.js #45018857182](https://github.com/kunlong-luo/lanveriq/actions/runs/45018857182) · [RD-2 OFF #45018857219](https://github.com/kunlong-luo/lanveriq/actions/runs/45018857219) · [Docs #45018857180](https://github.com/kunlong-luo/lanveriq/actions/runs/45018857180) · [Linux Batch A #45018857171](https://github.com/kunlong-luo/lanveriq/actions/runs/45018857171). **7/7 triggered workflows SUCCESS on the same SHA.** Real PostgreSQL 18.6 job log reports `BUILD SUCCESSFUL in 1m 27s` and strict JUnit XML `all 11 Testcontainers classes, at least 149 passing tests including separate migrator/runtime PG18 privileges; no failed, errored or skipped cases`. The specific C18a method is mandatory in the gate. No real restore, WORM, S3 version, KMS/HSM custody, actual trusted time or Pop!_OS L3 is claimed.

## Evidence required before declaring this synthetic subtask done

- Functional SHA of test and evidence-gate change, real Java 25 unit and integration compilation, genuine PG18 Testcontainers run ID; **11 classes ≥149 executed passing cases**, including `PostgreSqlFidoMdsAcquisitionReceiptIT.c18DbOwnerRewindRejectsSurvivingHostRetainedWitnessHighWater`. The strict XML gate must deny omission/failure/skip/duplicate. Status is **PASS for the isolated synthetic C18a subtask only**, with exact-SHA links above. Actual external G-02 is OPEN.
- Exact-SHA RD-2 default-OFF (PASS/CANCELLED/NOT RUN separately), and docs-only outcomes must be recorded separately. No guessed successful CI outcome.
- G-02 remains **OPEN** until independent S3 Object Lock/WORM or comparable real storage, approved KMS/HSM and retention policy, off-PG secured high-water, explicit independent trust root, **actual backup + restore rehearsal with new and old snapshots**, rollback denial, and independent security sign-off.

**Never use this code to trust a real FIDO source, approve a passkey, authorize an agent, sign a CA certificate, enable network/collection/auto-recovery/remote control or claim Pop!_OS physical L3.**
