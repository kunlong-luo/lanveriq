# A11-D4-C18i — transitive WORM custody privilege-graph preflight

**ACTIVE SECURITY SPEC / SOURCE-ONLY · 2026-11-22 · main · NO REAL ADMIN/CUSTODY/WORM TEST · G-02/G-08 OPEN · v0.1 NOT RELEASE READY.**

This follows [C18h metadata intake](independent-worm-offline-evidence-intake-d4-c18h-2026-11-22.md) and [C18g physical acceptance](independent-worm-custody-acceptance-d4-c18g-2026-11-22.md), tracked by [Issue #6](https://github.com/kunlong-luo/lanveriq/issues/6). **No actual external provider or WORM medium is approved or contacted.** Production networking, collection, automatic recovery, FIDO trust, remote control and CA issuance stay default OFF.

## Gap corrected

C18h v1 distinguished role labels, but different storage_root and high_water names can both be administered by one parent organizational root, common identity provider, hypervisor, backup console or emergency break-glass account. Comparing names does not detect transitive compromise. Ceph's official [RGW Object Lock explanation](https://www.ceph.io/en/news/blog/2025/rgw-deep-dive-3/) explicitly places the raw RADOS/cluster root outside RGW/S3 API retention enforcement; [CephX permissions](https://docs.ceph.com/en/latest/rados/operations/user-management/) include monitor, manager and OSD administration.

## C18i fail-closed contract

The offline [Python checker](../../apps/server/scripts/inspect_worm_acceptance_bundle.py) now demands schema version **lanveriq.worm-evidence-intake.v2** and rejects v1 rather than silently accepting a weaker inventory. All previous C18h checks remain. The custody.control_graph field is mandatory:

- **principals:** bounded 10–128 unique opaque administrative principal IDs, including each declared role domain.
- **root_principals:** at least two explicitly inventoried root administrators. Every role domain must have a direct or transitive root.
- **edges:** 2–512 directed control relationships, exactly controller/target/capability/evidence_ref. Accepted dangerous capabilities: ROOT_ADMIN, CLUSTER_ADMIN, ROLE_ADMIN, IDENTITY_ADMIN, BREAK_GLASS, BACKUP_RESTORE, KEY_ADMIN and MEDIA_ADMIN. A read-only grant is not permission to override retention and cannot masquerade as an admin edge.
- **surfaces:** exact required review references for identity provider, break-glass, host root, hypervisor, backup restore, signing key/KMS, high-water, recovery and trusted time. For CEPH_WITH_EXTERNAL_CUSTODY additionally require RGW admin, CephX MON/MGR/OSD and RADOS pool/namespace. These are opaque pointers, not authenticated evidence.
- **inventory_ref / completeness_asserted:** pointers and a declared inventory completeness flag, not verification of the inventory.

Interpret each edge as **controller → target** and compute all transitive control ancestors. If one principal can administer both an untrusted domain (application, DB, storage API, storage root, backup) and protected domain (witness signing key, independent high-water, recovery copy, trusted clock, reviewer), fail closed. The same applies when distinct protected domains share a controlling ancestor. Reject cycles, undeclared principals, duplicate grants, missing root assignments, skipped Ceph privilege surfaces, and weak v1 downgrade.

Examples that must fail: common org-root controlling both DB and external high-water; backup-admin → intermediary → recovery-admin; Ceph-RADOS-root → signer-key-admin. Passing this negative topology consistency gate **never proves** the declared graph is truthful.

## Limitations, evidence, nonauthorization

An attacker may fabricate an independent-looking graph or omit hidden delegation. The checker cannot query CephX/RADOS, IdP/IAM, hypervisors, HSM/KMS or actual WORM disks; it cannot authenticate signed witnesses, time, physical denial or key rotation. The human reviewer must obtain original audit evidence from separate administrators and test the entire [WORM-01..11](independent-worm-custody-acceptance-d4-c18g-2026-11-22.md) protocol on **authorized disposable nonproduction hardware**, with real per-version readback and an independent retained high-water. Do not execute destructive Ceph commands against live or shared clusters.

The CLI remains either INCOMPLETE or READY_FOR_INDEPENDENT_REVIEW; neither is WORM PASS. The original C18h function SHA and CI describe historical v1, not current v2. [Targeted synthetic negative tests](../../apps/server/scripts/test_inspect_worm_acceptance_bundle.py) cover original and new transitive privilege failures. New feature SHA CI must actually finish before declaring software PASS; no new real PG18 L2 or physical L3 is implied. **G-02/G-08/G-05 OPEN; v0.1 NOT RELEASE READY; no vendor SDK and no production authorization.**

## Exact source SHA and software CI — verified

Functional source SHA [87f7ada98b0f9a7c80c3b44752c90a9f0eca173e](https://github.com/kunlong-luo/lanveriq/commit/87f7ada98b0f9a7c80c3b44752c90a9f0eca173e) passed **4/4 actually triggered same-functional-SHA workflows**: [Java25 server-unit and 38 Python synthetic permission-negative tests #45024983963](https://github.com/kunlong-luo/lanveriq/actions/runs/45024983963) (actual job log: Ran 38 tests; Gradle BUILD SUCCESSFUL in 45s); [Next.js fast #45024983993](https://github.com/kunlong-luo/lanveriq/actions/runs/45024983993); [documentation authority #45024983961](https://github.com/kunlong-luo/lanveriq/actions/runs/45024983961); [RD-2 default OFF #45024984048](https://github.com/kunlong-luo/lanveriq/actions/runs/45024984048). All SUCCESS. **PG18 L2 NOT RUN on C18i SHA; real external WORM, real Ceph/RADOS root, independent signatures/keys/time and Pop!_OS physical L3 NOT RUN.** Previous C18f PostgreSQL18.6 synthetic L2 belongs to a different historical source SHA. This C18i graph preflight's empty error list is not release evidence, only synthetic metadata eligibility for separate independent human audit. **G-02/G-08 OPEN; v0.1 NOT RELEASE READY.**
