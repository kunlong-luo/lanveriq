# A11-D4-C18g — independent WORM custody decision and physical acceptance protocol

**ACTIVE SPEC / EXECUTION NOT AUTHORIZED · 2026-11-22 · main only · provider NOT SELECTED · independent WORM NOT RUN · G-02/G-08 OPEN · v0.1 NOT RELEASE READY.**

This is a **test plan and decision gate, not an executed WORM test, certification, production configuration, or authorization for destructive operations**. Last verified software baseline remains C18f functional SHA `118939b4` (7/7 same-SHA synthetic CI including disposable PG18). This C18g document supplies no new software PASS. Refer to [C18f candidate evaluation](independent-worm-provider-evaluation-d4-c18f-2026-11-22.md), [ADR-0012](../architecture/adrs/0012-independent-fido-witness-object-lock.md), [release gates](../planning/release-gap-register.md) and [backup plan](../operations/backup.md).

## 1. Security property and adversary boundary

**Target:** once witness sequence `N` and its signed checkpoint digest are published, an attacker who controls Lanveriq application, Java service, PostgreSQL superuser, database backup/PITR, host, CI or ordinary archive *writer* cannot erase/revise the retained checkpoint, replace it with a prior `N-1` checkpoint, shorten retention, replace its verification key, suppress an independent high-water, or induce acceptance of an older restored database. The independent custodian must be able to prove and replay the **latest published** sequence/digest even when the application is offline or compromised.

Distinguish three properties; evidence for one never implies the others:
- **P1 — API retention:** exact version's DELETE and retention shortening are denied through object APIs under scoped identities.
- **P2 — administrative-domain separation:** application/DB/backup/host/CI operators cannot access underlying WORM administrator, cluster-root, physical media, witness signing key, external time or independent high-water control plane.
- **P3 — root/destruction resilience:** under compromise of storage-root or loss of one site, a separately controlled copy/physical WORM and externally pinned latest sequence still let a different custodian *detect loss and recover*. No software Object Lock can make physical media indestructible; deliberate destruction is distinguished from modification prevention and recovery.

**Nonacceptance rule:** if the same person/credential, emergency break-glass path, cloud organization owner, hypervisor/OS root, backup/snapshot administrator or KMS deletion authority can roll back both PostgreSQL and every retained high-water copy, P2/P3 **FAIL** even when an S3/RGW Compliance call says `AccessDenied`.

**Ceph-specific factual boundary:** [Ceph's 2025 official Object Lock deep dive](https://www.ceph.io/en/news/blog/2025/rgw-deep-dive-3/) documents Compliance enforcement at **RGW/S3** while explicitly warning about `rados rm`, `radosgw-admin bucket rm --purge-objects`, physical destruction and Ceph cluster-admin bypass. [CephX/RADOS permissions](https://docs.ceph.com/en/latest/rados/operations/user-management/) and [pool deletion](https://docs.ceph.com/en/latest/rados/operations/pools/) must be audited independently. A Ceph cluster under Lanveriq's same root is **REJECTED** as standalone G-02 WORM; even separately owned Ceph cannot alone prove resistance to *its own* cluster-root. Do not execute these destructive example commands against live or shared infrastructure.

## 2. Conditional shortlist — decision remains OPEN

| Candidate | Provisional decision | Why it could qualify / what prevents approval |
| --- | --- | --- |
| Independent custody **physical LTO WORM** with serial-numbered, dual-controlled cartridges and separate readback site | **PREFERRED offline durable reference** for physical write-once claim, subject to lab feasibility | Requires actual compatible WORM media, immutable offline custody ledger, two-person removal, rotation/recovery, independent keys and **another timely high-water publication channel**. Tape alone may have unacceptable freshness/latency or RTO. |
| Independently administered managed immutability service, separate legal/admin/security account and external checkpoint keeper | **PREFERRED online high-water candidate** pending procurement/residency/security approvals | Must demonstrate real retention enforcement, separate identity/root/KMS/time, alternate recoverable copy and adversarial restore; provider marketing or API version reads alone are insufficient. |
| Separate Ceph RGW cluster with Object Lock Compliance | **CONDITIONAL supplementary copy only** until P3 proven by external WORM/custody | RGW success is only P1. Cluster CephX, RADOS, monitor/pool deletion, storage nodes, backup/root and physical access are a different attack plane. |
| Same-admin MinIO/Ceph/NAS, local immutable bit, PostgreSQL trigger/digest, fake read-only port | **REJECTED** for G-02 | One principal may erase/rewind database plus witness. Useful as disposable CI only. |

No provider, vendor SDK, WORM medium, retention term, budget or procurement is approved by this preference. Prefer **one externally custodied online high-water plus a geographically/administratively independent recovery copy**; introduce provider-specific adapters only after a separately recorded ADR/operational approval. The ordinary Lanveriq Web/Agent/Java main build remains cloud-neutral.

## 3. Decision inputs and accountable sign-off (all PENDING)

| Required owner | Must supply in an internal change/approval record |
| --- | --- |
| Product + privacy/legal | Data classification, allowed audit payload, **approved retention term** / legal hold, jurisdiction/residency, deletion obligations, license/procurement constraints |
| Security authority distinct from app/DB operator | In-scope adversaries, accepted residual risks, who owns immutable high-water, signing/public-key pin and independent clock |
| WORM independent custodian | Physical organization/account/site map; control of cluster/root/backup/snapshots/KMS/media; cross-admin separation with account/role inventory; break-glass and two-person approvals |
| PKI/key custodian | Independent nonexportable witness signing keys where feasible; verification key pin and rotation lineage; encryption key escrow/recovery; revocation, disabled/deleted-key response |
| SRE + external test reviewer | Written authorization for **dedicated disposable nonproduction media only**, change ticket, maintenance window, abort/recovery plan; exact retained artifact identifiers, reviewer signatures and secure report location |
| Release authority | G-02/G-08 independent ACCEPT/REJECT with signed evidence; source CI and historical D0 are never substitutes |

Never publish private keys, account IDs that expose access, real employee/device data, unredacted RADOS maps, access tokens, operator identities or sensitive audit bundles in public GitHub Actions or issue comments.

## 4. Controlled acceptance protocol — no production or destructive execution without approval

Use invented synthetic records, a physically separate security test account/cluster or purpose-built sacrificial media, and observers with different credentials. For each test record **start state, authorized actor, target/version/media ID, command intent (redacted), actual provider/OS result, ending state and independently verified readback**. A `403` alone is not a PASS. Readbacks MUST use exact object version/physical media ID, SHA-256, signed sequence/digest and actual retention-until verified against independently attested time.

| ID | Attack / positive control | Required result for PASS |
| --- | --- | --- |
| WORM-01 | Independently prove identities, IAM/CephX, root/hypervisor, RGW admin, snapshot, backup, DR and KMS rights | App/DB operators lack all archive, key, high-water rollback/break-glass paths; cross-domain auditor signs exported permission graph |
| WORM-02 | Publish two synthetic checkpoints N-1 and N with exact unique IDs and signed predecessor links | Independent writer/custodian attest N and store separately pinned high-water; sealed evidence read back by another identity |
| WORM-03 | Exact-version DELETE, overwrite, retention shorten, governance-bypass, legal-hold toggles, lifecycle/expiry and delete marker | Protected bytes/version still readable by independent reviewer; no downgrade/ambiguous `latest` fallback; **Compliance rather than Governance** if API mode used |
| WORM-04 | Attempt root-level underlying mutation, storage snapshot/pool rollback, restore/recreate, backup catalog deletion **only in authorized sacrificial infrastructure** | Either action is denied by independently controlled physical boundary **or** external surviving copy and high-water deterministically detect loss; standalone same-root Ceph cannot be marked PASS |
| WORM-05 | Disable/revoke writer and reader credentials; KMS signer/encryption disable or attempted deletion; rotate key | Fail closed; independent verify pin/rotation history survives; recovery uses separate approved custodian; no auto-trust |
| WORM-06 | Restore real synthetic PG18 `pg_dump`/`pg_restore` or PITR at N-1, replay valid old witness and mutate DB owner audit | External N remains; restored stale DB **DENIED**, N database **MATCHES** on separately pinned key/high-water; no shared state can be rewound |
| WORM-07 | Tamper with trusted-time source, skew system clock, force retention expiry, offline/high-water publisher downtime | Clock mismatch is flagged, retention not shortened; no assumed freshness; documented maximum publication lag and no unreviewed recovery gap |
| WORM-08 | Take one archive copy/site unavailable; try recovery from second legal/security-owned copy and independent index | Signed latest N, retention and key provenance recovered/read back; actual measured recovery objectives and residual DoS documented |
| WORM-09 | Prove real on-media WORM, write/eject/offline inventory and later independent readback (LTO path) | Actual media serial + custody chain + verified original N; non-WORM rewritable cartridge is negative control |
| WORM-10 | Attempt actor collusion / shared owner/break-glass simulation; evaluate audit tampering | Any jointly rewritable control path causes FAIL or explicitly declared out-of-scope risk; cannot pass by omitting privileged roles |
| WORM-11 | Review term end, legal hold handling and secure purge only after authorized expiry | Retention minimum never shortened; expiration and deletion procedures require independent legal authorization and post-event audit |

For each relevant scenario log **PASS / FAIL / BLOCKED / NOT RUN**, not just `expected`. WORM-04 may demonstrate **detect-and-recover rather than physical prevention**, and must explicitly state which guarantee passed. Missing response, inaccessible custodian, invalid key, missing version, stale pin, uncertain time, inaccessible immutable log, deletion success or unsigned report means **FAIL / BLOCKED** and no G-02 promotion.

## 5. Evidence bundle and high-water rules

Only **independent security ownership** may approve a bundle, with:
1. Environment and medium identity (product/version, physical serial or opaque object/version IDs), separation topology/role graph and evidence of who *cannot* access each admin plane; no secrets.
2. Timestamp source and attestation (not the application wall clock), retention legal basis/minimum and independent custodian acceptance.
3. Full synthetic signed witness chain, actual key fingerprint/pin, checkpoint **N sequence and digest**, previous hash, readback SHA-256, external publication receipt and at least two separately owned locations/copies; no evidence pointer solely inside PostgreSQL.
4. Original immutable provider/security audit and independent signed review of both successful positive readback and each negative attack; redacted logs, machine/firmware/library versions and exact target identifiers.
5. Real `pg_dump`/`pg_restore` or PITR commands/results in isolated PG18 using the externally surviving N; explicit old denial / new match and loss-recovery narrative.
6. Code functional SHA and run IDs **only for actually executed software tests**; separate physical-lab ticket, reviewer, signature and secure storage URI. DOC-only CI is labeled **DOCS PASS**, not WORM or L3 PASS.

**High-water protocol:** independently publish monotonically increasing `(stream_id, sequence, signed_head_sha256, predecessor_sha256, key_id, approved_timestamp)` before claiming N is protected. An append cannot silently replace N. If latest independent N is unavailable, or a restored DB is below N, the entire relevant trusted admission remains **DENIED / UNKNOWN**, never "best effort", and neither an older valid signature nor a DB-owned digest repairs it. Legal retention and key-loss recovery must be specified before publication begins. Backpressure, missed periodic publication and post-incident rekey must be reviewed; no arbitrary time interval is approved here.

## 6. Status / exit decision

**As of 2026-11-22: WORM-01–WORM-11 = NOT RUN; candidate selection = PENDING; legal term, external custodian, HSM/KMS, trusted time, media, source of second copy and physical approval = PENDING.** C18f 7/7 same-functional-SHA CI is solely an offline synthetic software baseline. **G-02/G-08 remain OPEN; G-05 Pop!_OS 24.04 physical L3 OPEN; v0.1 NOT RELEASE READY.**

**NEXT externally executable milestone:** approved custodian/security/legal jointly choose one actual medium and custody topology, authorize isolated sacrificial-lab scope, first run WORM-01/02/03/04/05/06/08 with independent proofs and negative controls, then WORM-07/09/10/11 as applicable. Reopen the design if any root authority jointly rewinds all copies. Do **not** add an AWS/S3 SDK or connect production just to run this acceptance plan. Production networking, collection, automatic recovery, RD-2 remote control, FIDO admission and certificate issuance remain default **OFF**.

## C18h offline evidence intake (source-only, no real WORM)

The [C18h metadata completeness checker](independent-worm-offline-evidence-intake-d4-c18h-2026-11-22.md) validates a **redacted synthetic-lab manifest** with WORM-01..11 and separate Ceph API/underlay root. Even complete claims receive **READY_FOR_INDEPENDENT_REVIEW**, never an authentic WORM PASS, operational permission or G-02 closure. Physical/independent sign-off remains external and NOT RUN.

## C18i independent-admin transitive control review (source-only)

[C18i privilege-graph preflight](independent-worm-admin-control-graph-d4-c18i-2026-11-22.md) requires full declared control ancestry, break-glass and Ceph MON/MGR/OSD/RADOS coverage; distinct names no longer suffice for preflight. An asserted graph cannot authenticate the underlying cluster, KMS or high-water. WORM-01–11 stay NOT RUN; do not close G-02/G-08 from source CI.

## C18j 现实交接规范（仍 NOT RUN）

[C18j 独立 WORM 选型与授权实验执行包](independent-worm-lab-execution-package-d4-c18j-2026-11-23.md)详细规定外部审核方、单调高水位发表失败态、八类真实崩溃/回退现场实验、独立受控破坏与私有证据签字。它是执行计划，不是实际安全批准或物理验收。WORM-01–11 仍 NOT RUN，G-02/G-08 OPEN。
