# ADR-0012: independent witness archive with version-scoped WORM and approved key custody

- **Date:** 2026-11-22
- **Status:** **PROPOSED** (architecture candidate only; NOT ACCEPTED for production, no account or credential has been provisioned)
- **Context:** A11-D4-C15–C18a can check local PostgreSQL digests, Ed25519 checkpoint chains and an independently **supplied** high-water. C18a [real PG18 synthetic owner rewind](https://github.com/kunlong-luo/lanveriq/commit/5afbee8aa3f35a2ee916a34f4968d23242790aba) proves a detached host fixture stays ahead when a DB owner deletes recent rows, while explicitly showing that jointly rolled back external history defeats the offline verifier.
- **Supersedes:** None. Complements [ADR-0002](0002-postgres-sqlite.md), [release gate G-02](../../planning/release-gap-register.md), [C18a](../../security/fido-mds-db-owner-rewind-d4-c18a-2026-11-22.md).
- **Approval scope:** research and design only. Any external account, encryption key, retention policy, identity, network connection or independent test requires separate authorized approval.

## Decision candidate, NOT deployment

Prefer **a dedicated security-owned AWS account with Amazon S3 Versioning + Object Lock COMPLIANCE retention, a separately governed AWS KMS key and independent read-only audit role**, subject to security, legal and organization approval. Use the **official AWS SDK for Java 2.x** for a future minimal storage adapter, not hand-rolled S3 authentication/HTTP, cryptographic protocol or custom object store. No dependencies, credentials, buckets, endpoints or Spring Beans are added in this ADR or C18a.

- Treat **every version ID** as part of the immutable witness receipt identity: bucket/region, exact key, version ID, expected SHA-256, evidence stream ID/sequence, witness public-key pin and retention-until claims. Require exact-version GET/read-back and rehash of **actual bytes**, plus independently fetched version-scoped Object Lock mode/retention and KMS policy audit. Never infer protection from a key name, unversioned HEAD or a caller-provided JSON statement.
- Require an independently pinned and *externally custody-held* high-water sequence/digest that cannot be rolled back by the same identities that administer PostgreSQL, the application or the primary storage writer. Store witness transcript/signature bytes and separate high-water authorization/evidence in independent retention/administrative domains; merely a second file in the application host cannot close G-02.
- Require Object Lock **COMPLIANCE** (not bypassable GOVERNANCE) for the approved retention period, provider-side versioning and independent permissions/CloudTrail data events. Explicitly test that protected **specific object versions** cannot be deleted, retention cannot be shortened and old versions remain readable even after newer versions or delete markers are created. Legal hold by itself is not sufficient as privileged users may remove it.
- Separate KMS key-custody policy (authorized signing key and encryption keys may be different mechanisms), rotation, break-glass, monitored disable/deletion, and trusted-time/retention evidence. Encryption is not equivalent to integrity or WORM; a deleted/disabled KMS key may make locked objects unreadable.
- Final external acceptance must restore *actual* `pg_dump`/`pg_restore` or PITR snapshots into an isolated database, retain the latest independently verified WORM high-water unchanged, deny stale PostgreSQL tails, and positively match fresh current snapshots. Bind artifacts to exact source SHA, WORM object version, independent review and test run. No sensitive production keys or employee data in public CI.

## Alternatives and risks

| Option | Tradeoff |
| --- | --- |
| AWS S3 Object Lock COMPLIANCE + dedicated security-owned account | Mature managed WORM/version API; requires cloud provisioning, legal retention selection, cross-account IAM and KMS governance, provider outage/cost/exit planning |
| Other independently administered WORM storage with audited retention/version APIs | Possible; only acceptable after equivalent non-bypassability, versioned byte read-back and restore-rehearsal evidence |
| Self-hosted S3-compatible storage in same administrator domain | Useful for development fixtures, **not sufficient** for independent DBA/cloud-root rollback resistance |
| PostgreSQL-only SHA/trigger/backup or local file | Rejected as independent evidence: administrator can erase/restore both history and cursor together |
| Object Lock GOVERNANCE or legal hold alone | Insufficient default: privileged governance bypass and authorized legal-hold removal must be considered |

## Exit conditions before changing this status

1. Independent security/legal approval of provider, account ownership, retention duration and data classification; AWS cost/operability reviewed.
2. Tested IAM minimum permissions and cross-account reader, versioned COMPLIANCE write/read-back, immutable version deletion/retention-shortening failures, replacement/delete-marker survival, CloudTrail/KMS visibility and audited role separation.
3. Real KMS/HSM custody of signing keys and encrypted object access, key rotation/revocation and trusted clock evidence.
4. Separate durable high-water retention checked after real PostgreSQL 18 restore/PITR rollback, archive tamper and missing-version failure injection; version IDs, exact run SHA and signed independent reviews recorded.
5. Explicit new production authorization; until then **production networking, collection, auto-recovery, remote control, official FIDO trust and certificate issuance remain OFF; v0.1 NOT RELEASE READY**.

## Maintained upstream references (implementation must reverify versions/terms)

- [AWS S3 Object Lock behavior, compliance/governance, versioning and delete markers](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lock.html).
- [S3 version-scoped Object Lock handling and governance bypass](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lock-managing.html).
- [S3 Object Lock configuration](https://docs.aws.amazon.com/AmazonS3/latest/userguide/object-lock-configure.html).
- [AWS KMS key deletion risks](https://docs.aws.amazon.com/kms/latest/developerguide/deleting-keys.html).

**This ADR is a proposal only; GitHub CI and local synthetic keys do not authorize or establish a real external trusted source.**
