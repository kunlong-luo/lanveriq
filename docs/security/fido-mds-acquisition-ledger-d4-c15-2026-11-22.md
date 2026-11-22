# A11-D4-C15 — immutable acquisition receipt ledger in PostgreSQL 18

**2026-11-22 | `main` | 5/5 exact-functional-SHA synthetic CI SUCCESS | v0.1 NOT RELEASE READY.**

## Purpose and non-goals

D4-C14 added a **synthetic, independently signed** offline acquisition-custody manifest. D4-C15 persists the signed manifest bytes, Ed25519 signature bytes and receipt digests in PostgreSQL 18, tying them to an existing immutable V26 **untrusted** source receipt. Neither a Java argument nor a database row becomes an official FIDO trust anchor.

The DB DOES NOT cryptographically verify the acquisition signature, prove a network fetch, confirm a CRL distribution URL, authenticate a reviewer, attest independent trusted time, or enable any production feature. Only the separate C14 verifier checks signature under an externally asserted key; the newly added Java writer is a **package-private non-Bean, unmounted staging primitive**, not an API endpoint. Testcontainers intentionally inserts **dummy 64-byte signatures** through direct SQL to prove the DB rejects history/audit violations *even when cryptographic provenance is absent*. Never use this table as authorization.

## Forward-only V30 schema and invariants

- New `admin_fido_mds_acquisition_receipts_staging` with `acquisition_untrusted` as its only possible state. It retains **signed manifest bytes (128–32768)**, **64-byte signature bytes**, acquisition public key fingerprint, original JWS and CRL-set SHA-256, source-receipt foreign key, monotonically increasing sequence, previous acquisition digest, immutable actor and session, and PostgreSQL-computed manifest/signature/record SHA-256.
- Every insert must reference a **V26 source_untrusted receipt in the same V25 synthetic stream**, with matching V26 JWS/CRL-set hash and source receipt sequence. A real **FOR UPDATE stream row lock** serializes competing writes and checks the current enabled owner, unchanged session and `owner_security_admin` role. First acquisition sequence must equal 1, with no predecessor. Every later receipt is exactly one greater, points to the actual immutable predecessor hash, and retains the same synthetic acquisition-key fingerprint. These are only owner-scoped staging constraints, not global anti-rollback.
- Reject duplicate source receipt, sequence, signed manifest hash, or signature hash per stream; refuse source/owner swaps, skipped sequence, altered predecessor or key change. Mandatory state is fixed to `acquisition_untrusted`, never promotable.
- AFTER INSERT trigger automatically inserts a corresponding `admin_access_log` record. If any BEFORE INSERT trigger silently drops the audit, V30 raises an exception and rolls back the entire writer transaction. New audit-action UPDATE/DELETE guards, record UPDATE/DELETE guards, and **BEFORE TRUNCATE** guards protect both history and audit under normal database privileges, including `TRUNCATE ... CASCADE`. A privileged DB owner/superuser can disable triggers; this is not independent external witness or tamper-proof log.
- `StagedFidoMdsAcquisitionReceiptLedger` (unmounted, no production grants) cross-checks provided C14 synthetic evidence fingerprints against the exact acquisition transcript and signed bytes, then attempts an optimistic append within a caller-owned transaction. **The V30 database trigger, not the optimistic Java query, enforces races, history and actor safety.** It does not verify signature/key provenance; the caller must run the C14 verifier separately before staging.
- No existing migration, source receipts or trust configuration is edited; V30 does not create a route, Spring bean, network client, background job, accepted authority or CA.

## Real acceptance plan

One new independent `PostgreSqlFidoMdsAcquisitionReceiptIT` Java 25 Testcontainers class (actual PostgreSQL **18.6**, Flyway V30, Spring transaction manager) must execute and pass **three mandatory methods**:

1. DB-owned signed-manifest/signature digest computation, source binding and exactly one audit for a valid-shaped synthetic direct-SQL receipt.
2. Failure of out-of-order receipt, owner/source mix-up, mismatched JWS digest, replay, wrong predecessor, acquisition-key pin swap, UPDATE, DELETE and TRUNCATE; original rows remain.
3. Audit suppression within an actual PostgreSQL trigger rolls back acquisition inserts; retry succeeds after removing suppression; audit mutations and TRUNCATE fail.

`scripts/assert-postgres18-junit.py` is extended to require the new full class+methods in **actual Gradle JUnit XML**, not file existence. An additional Python gate test deletes each synthetic case to verify required failures. The PG18 CI workflow path filter includes V30, the Java writer and its integration class, so the real container job runs at the **exact function SHA**. Java 25 tests, PG18 compile, Next.js and RD-2 protection must be reported precisely as triggered, not invented.

## Real exact-source acceptance — 5/5 triggered workflows SUCCESS

[functional SHA `9e5e0b08`](https://github.com/kunlong-luo/lanveriq/commit/9e5e0b080ae4a4843288584a885f37627d004b1d) passed **5/5 exact-SHA triggered synthetic CI workflows**: [Java 25 Gradle unit #45014246209](https://github.com/kunlong-luo/lanveriq/actions/runs/45014246209), [PostgreSQL 18.6 Testcontainers real synthetic transaction tests and mandatory 11-class / ≥146 successful JUnit gate #45014246195](https://github.com/kunlong-luo/lanveriq/actions/runs/45014246195), [PG integration-source compile #45014246249](https://github.com/kunlong-luo/lanveriq/actions/runs/45014246249), [RD-2 default-OFF guard #45014246280](https://github.com/kunlong-luo/lanveriq/actions/runs/45014246280), and [Linux Batch A real Caddy→Spring/Tomcat double TLS1.3, PostgreSQL 18.6, disposable Rust Agent and strict ACK synthetic chain #45014246327](https://github.com/kunlong-luo/lanveriq/actions/runs/45014246327). **Next.js workflow was NOT triggered for this functional SHA by its path filter**, and must not be counted as a sixth result; a separate docs commit triggers its own regression.

The PG18 job log states **`BUILD SUCCESSFUL in 1m 3s`** and **`PG18 synthetic JUnit evidence verified: all 11 Testcontainers classes, at least 146 passing tests ... no failed, errored or skipped cases.`** The two isolated Linux Batch A jobs separately succeeded: actual double TLS 1.3/mTLS and negative TLS 1.2/forged-certificate checks, plus PG18 startup and synthetic Rust Reqwest/strict ACK transactions. Neither the Java source compilation nor a fabricated XML report was used as a substitute for the real Testcontainers outcome. The acceptance baseline is **only `9e5e0b08`**; source `4519a203` correctly failed first, was repaired with unweakened V26/V30 guards, and is preserved in the failure history below.

The V30 receipt is **acquisition_untrusted only**, with no PostgreSQL Ed25519 verification, real official FIDO MDS/CRL fetch, authentic CRL provenance, organizational trust-root admission, independently managed signing keys or trusted timestamp, actual human reviewer MFA, off-database audit witness, production CA/issuer or Pop!_OS 24.04 L3. The Linux Batch A success is isolated synthetic acceptance, NOT production Agent release. **v0.1 NOT RELEASE READY; production networking, collection, auto-recovery, remote control and certificate issuance OFF.**

## Remaining release blockers

Real FIDO MDS/CRL fetching with authenticated TLS evidence, independent R46 root trust approval, legitimate CRL custody/source and PKIX revocation material, real trusted clock, keys stored in HSM/KMS with individual human identities, independent reviewer WebAuthn MFA, off-database tamper-evident witness, runtime DB-role split and verified minimal privileges, metadata status admission, CA/issuer safeguards, and Pop!_OS 24.04 physical L3 are **OPEN**. Production networking, collection, auto-recovery, remote control and certificate issuance remain **OFF**. v0.1 **NOT RELEASE READY**.

## First exact-SHA CI failure and correction

**Initial functional SHA `4519a203` is NOT accepted.** Real PostgreSQL integration found a test-fixture error in the new V30 negative test: the test created a second V26 source receipt without supplying V26's mandatory database-owned predecessor digest. The immutable V26 guard correctly denied it. Independently, legacy migration tests still expected 29 Flyway versions (or 20 upgrades from V9); forward-only V30 correctly makes those totals **30** and **21**. The repaired feature SHA must run the **entire** real PostgreSQL 18.6 + strict 11-class JUnit gate again, along with Java source compilation and RD-2 protections. These failures do not authorize altering or relaxing V26/V30 security constraints.
