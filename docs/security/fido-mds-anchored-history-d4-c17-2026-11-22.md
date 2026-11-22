# A11-D4-C17 — anchored offline history verification (NOT external WORM)

**Date:** 2026-11-22 · **Branch:** `main` · **Functional code/test/workflow SHA:** [`9cb6b4ce`](https://github.com/kunlong-luo/lanveriq/commit/9cb6b4ce9f63d7ef45a48f5ad104f30fb1304d45) · **CI initially PENDING; see [CURRENT_STATUS](../CURRENT_STATUS.md) for subsequent exact-SHA outcomes.** v0.1 **NOT RELEASE READY**.

## Boundary and motivation

D4-C16 verifies one signed snapshot of a PostgreSQL V30 acquisition tail against an externally *asserted* witness cursor. It does not inspect the full retained history. D4-C17 adds a package-private, no-bean, no-endpoint, no-network **offline read-only verifier** for a *complete* separately retained chain, but does **not** install an actual external store or grant any FIDO, CA, agent or WebAuthn privilege.

`StagedFidoMdsAnchoredWitnessHistoryReader` accepts four independent-looking inputs that must actually be supplied by separate security custody to have real rollback value: an independently pinned Ed25519 public key, an externally retained genesis-to-head list of signed checkpoints, an independently retained stream/head sequence/digest/key fingerprint, and a caller-provided observation time. They are **not independently acquired by this code**. If a DBA or caller can replace all four inputs and the database, valid signatures only prove self-consistency, not freshness or independent trust.

## Enforced fail-closed checks

1. The journal must be non-empty and contain **at most 4096** signed checkpoints for this bounded synthetic verifier. This is a deliberate offline resource cap; a production archive needs an independently reviewed rollover/checkpoint design before the cap is reached.
2. Each signed checkpoint must have the **same stream**, contiguous witness numbers starting at **1**, exact prior signed-transcript SHA-256 linkage, nondecreasing acquisition sequence, valid canonical C16 transcript, and a full Ed25519 signature verified using the **separately pinned** public key. Witness/acquisition public-key hashes must be distinct.
3. The signed head digest, sequence, stream and witness key pin must match the **independently retained** high-water head. Missing entries, truncation, reorder, duplicates, forged signatures, wrong head, key swap, stream mix or timestamp assertion >5 minutes ahead of caller time are denied.
4. Only after all history checks pass, the last checkpoint is rechecked by the existing C16 verifier against the **actual current PostgreSQL V30 tail and V26 source/audit record**. This rejects older valid history when database history has advanced, but it cannot detect a coordinated database + external-journal rollback if no independent high-water custody exists.
5. Only `UntrustedDetachedWitnessMatch` is returned. No data is promoted to official MDS root, administrator credential trust, CA/issuer authority or production capability. No metadata source is downloaded, certificate or CRL chain is approved, key is provisioned, or trusted time is established.

## L1/L2 regression contract

Real PostgreSQL 18.6 Testcontainers now requires `PostgreSqlFidoMdsAcquisitionReceiptIT.c17AnchoredWitnessHistoryRejectsTruncationForgeryAndStaleDatabaseTail`, using disposable Ed25519 keys, real V26/V30/audit rows and a two-entry signed history. Negative cases cover truncated/reordered/duplicated entries, attacker signatures, forged high-water digest, foreign stream, wrong public-key pin, future-time assertion, old valid head and a database tail that has advanced. The strict Gradle XML gate now requires **five C15–C17 test methods**, and at least **148 synthetic PG18 integration cases over 11 real Testcontainers classes**. The PG18 workflow path filter includes the C17 source file.

**Evidence recording rule:** Record exact `9cb6b4ce` run IDs and PASS/FAIL/PENDING, including any earlier cancelled/failed SHA. Do not count Java compilation or fabricated XML as PostgreSQL execution. The C17 security result belongs to the functional code/test/workflow SHA, not later documentation-only changes.

## Still OPEN — required actual external evidence

- Approved append-only/WORM provider **outside PostgreSQL and outside the same rollback administrative domain**, with independent read-back and durable high-water retention, deletion/retention policy, audit and restore rehearsal.
- Witness key custody/rotation through an organizational HSM/KMS or equivalent approved mechanism, independently pinned verification key with dual control and emergency revocation.
- Trusted timestamp and official FIDO MDS BLOB/CRL authenticated acquisition/source review, independent CRL/PKIX revocation and anti-rollback verification; actual human hardware WebAuthn MFA.
- Separate real Pop!_OS 24.04 L3, tenant/RBAC/Origin restrictions, formal release review and applicable privacy/legal approval.

**G-01 and G-02 remain OPEN.** A successful synthetic C17 CI proves only this offline verifier, not external durability or official trust. Production networking, collection, automatic recovery, remote control and certificate issuance remain **OFF**.
