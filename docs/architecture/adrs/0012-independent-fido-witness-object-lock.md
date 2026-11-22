# ADR-0012: Cloud-neutral independently governed immutable witness archive

- **Date:** 2026-11-22
- **Status:** **ACCEPTED FOR DEVELOPMENT — cloud-neutral boundary only**; **actual storage provider, custody and production permission remain PROPOSED / NOT APPROVED**.
- **Context:** D4-C15–C18b validated synthetic PostgreSQL acquisition receipts, detached Ed25519 witness history and isolated database backup/rollback. The missing G-02 requirement is durable, independently governed, non-rollbackable retention outside the same DB/admin domain. C18c/d temporarily introduced AWS SDK Java S3 as a possible implementation before the operator chose a cloud provider. The maintainer subsequently clarified that **AWS/S3 is not a required Lanveriq technology or deployment target**.
- **Decision scope:** cloud-independent source architecture and offline negative tests only; this ADR **does not approve a deployment, WORM medium, HSM/KMS, production network, identity, real object version or trusted time**.
- **Supersedes:** earlier **AWS-preferred vendor selection** within ADR-0012 (historic exact commits are preserved in Git). No prior ADR ID is deleted. Complements [ADR-0007](0007-mature-ecosystem-first.md), [G-02](../../planning/release-gap-register.md) and [A11-D4-C18e](../../security/fido-cloud-neutral-witness-d4-c18e-2026-11-22.md).

## Decision — core must not depend on S3 or another cloud

The default backend is Java 25 / Spring Boot 4 / Gradle / JPA-Hibernate / PostgreSQL 18. Its **main source and Gradle dependencies SHALL NOT include AWS SDK, S3, MinIO or another vendor-specific object storage client merely for security-witness research**. Devices, heartbeat, inventory, WSS and UI **never require S3**.

A minimal, package-private `ReadOnlyArchivePort` represents separately maintained evidence for a **specific immutable object version**. It has two methods: read **one exact pinned version's bytes** and read **that same version's retention evidence**. Inputs include administrative domain, opaque object key/version token, externally fixed lowercase SHA-256 and minimum retention-until. Offline verification checks bounded actual bytes (1 MiB prototype), digest, returned exact-version identity, two agreeing claimed non-bypassable retention records and caller-supplied observation time, and fails closed on missing/inconsistent evidence.

- **All provider claims remain UNTRUSTED.** The port may lie or be controlled by the same administrator. The prototype does not independently authenticate storage, trusted time, signer identity, deletion/retention denial, KMS/HSM, an external root, a full signed historical C17 witness sequence or an official FIDO MDS BLOB. It cannot authorize anything.
- **Future provider adapters remain out of the default server classpath** unless the actual deployment architecture/operations team approves that provider and its independent security boundary. Use the provider's mature official SDK in a separately enabled adapter after selection, not a hand-rolled storage or cryptographic protocol.
- A single host directory, normal PostgreSQL WAL/dump, container-local disk, ordinary NAS, MinIO or any self-hosted S3-compatible service under the **same administrative domain** is not an independent G-02 WORM. A physical read-only/write-once medium or independently governed retention-enforcing service *might* qualify only after independent threat-model and restore evidence.
- No real writer is added; sealed immutable storage custody, legal retention, authorized operator roles and eventual audit must be separately designed.

## Candidate providers (NO provider selected)

| Candidate | Notes and acceptance risks |
| --- | --- |
| Independently governed on-prem WORM / compliance storage | Potentially fits Pop!_OS/Linux-first and cloud-optional deployment; requires verified write-once retention enforcement, separate admin domain, accessible evidence, lifecycle/restore planning |
| Independently governed object store with supported retention/version semantics | Requires a real provider audit, immutable retention modes, version-specific readback and deletion/shortening denials; compatible S3 API alone is insufficient |
| AWS S3 Object Lock COMPLIANCE or equivalent managed provider | **Optional**, never a default dependency. Requires explicit cloud approval, separate account, credentials, KMS/permissions/CloudTrail and cost/data residency review |
| Ordinary filesystem, database-only digest/WAL or backup under same DBA | **Rejected** as independent G-02 custody; administrator may rewind both data and its evidence |

## 2026-11-22 source-backed candidate evaluation (no provider approved)

See [C18f security-domain comparison and acceptance matrix](../../security/independent-worm-provider-evaluation-d4-c18f-2026-11-22.md). The vendor-neutral decision holds regardless of candidate API. In particular, Ceph RGW's Object Lock explicitly covers the S3 API surface but **not necessarily raw RADOS/root storage administrative operations**; independent physical custody is a separate property and must be tested. LTO WORM media may provide physical write-once records, but sequential/slow readback, catalog custody, media extraction, and timely high-water publication have to be designed and proven. Neither automatically closes G-02.

## External acceptance gates (OPEN)

1. Organization and legal approve data classification, retention duration, actual provider, independent security owner and threat model. No public sensitive secrets.
2. Independently approve/custody signing and encryption keys (if applicable), trusted time and audited separation of PostgreSQL/app admins from evidence writer/reader/admin. Demonstrate that the database admin cannot rewrite or discard the high-water.
3. On *actual selected provider*: pin explicit version/object/medium ID, read and hash bytes, verify provider-evidenced non-bypassable retention, test deletion and shortening denial, new-version/delete-marker survival (where applicable), permission/availability failures and retention expiry.
4. Use a separately retained real witness history/high-water with actual isolated `pg_dump`/`pg_restore` and/or PITR. Confirm restored older DB denies but matching latest DB passes, even after a hypothetical DBA takeover. Preserve exact versions, source SHA, run IDs, independent audits and signatures.
5. Approve concrete deployment/operator runbooks and explicit release authority. Only then consider closing **G-02**. Production networking, collection, automatic recovery, remote control, official FIDO trust, real admin MFA and CA issuance remain OFF meanwhile.

**This accepts only a cloud-neutral development direction; provider selection, independent WORM evidence and production release remain NOT APPROVED. v0.1 NOT RELEASE READY.**
