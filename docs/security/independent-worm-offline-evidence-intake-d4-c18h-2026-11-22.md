# A11-D4-C18h — cloud-neutral WORM evidence intake preflight

**ACTIVE SPEC / OFFLINE CODE ONLY · 2026-11-22 · no independent WORM tested · G-02/G-08 OPEN · v0.1 NOT RELEASE READY.**

## Security claim and nonclaim

The C18g [physical acceptance protocol](independent-worm-custody-acceptance-d4-c18g-2026-11-22.md) covers WORM-01–11. This C18h Python 3 standard-library CLI checks **only the structure and declared completeness of a redacted, synthetic/disposable-lab metadata inventory** before an **independent security custodian** checks originals. Successful execution prints READY_FOR_INDEPENDENT_REVIEW (NOT WORM PASS). It cannot authenticate external signatures, reality of role names, physical WORM, object versions, corporate/root independence, signing-key custody, trusted time, actual negative test or review. **Fabricated JSON can pass preflight**, so its output must never feed production trust, release decisions, FIDO admission or certificates.

There is no vendor SDK, real provider adapter, Spring Bean, network operation or credential in this change. Originals must remain in independently controlled secure storage; never publish private keys, employee/device data, credentials, privileged topology or audit originals to public CI.

## Code, usage, outcomes

- [Offline standard-library intake checker](../../apps/server/scripts/inspect_worm_acceptance_bundle.py) and [synthetic negative regression suite](../../apps/server/scripts/test_inspect_worm_acceptance_bundle.py), wired into [server-unit CI](../../.github/workflows/server-unit.yml).
- Invoke only on explicitly authorized isolated lab metadata: python3 apps/server/scripts/inspect_worm_acceptance_bundle.py /secure/path/redacted-synthetic-lab-manifest.json
- Complete metadata -> exit 0: **READY_FOR_INDEPENDENT_REVIEW**; incomplete/contradictory metadata -> exit 2: **INCOMPLETE — FAIL CLOSED**. Exit 0 is *never* approval.
- The tool does not perform destructive testing, download evidence, verify a receipt signature or submit any data.

## Exact v1 contract

All fields mandatory, all unknown fields rejected. JSON must be UTF-8, <=128KiB, no duplicate keys or NaN.

| Field | Semantics |
| --- | --- |
| schema, environment, medium, functional_sha | Schema equals lanveriq.worm-evidence-intake.v1; environment equals DISPOSABLE_SYNTHETIC_LAB. Medium: LTO_WORM / MANAGED_IMMUTABLE / CEPH_WITH_EXTERNAL_CUSTODY; SHA exactly 40 lowercase hex. These declarations do not select a production provider |
| authorization | ticket_ref, scope_ref, nonproduction_only=true and destructive_scope_approved=true, all **claims** requiring external written authorization review |
| custody | domains keyed application, database, storage_api, **storage_root (CephX/RADOS/pool/host/hypervisor)**, backup, witness_signer, high_water, recovery, trusted_time, reviewer; shared_root_or_break_glass=false; privilege_inventory_ref, topology_ref, key_custody_ref, time_attestation_ref. Protected role domains must differ from all untrusted admin domains and from one another. **Different strings never prove different real superusers** |
| checkpoint | canonical UUID stream_id; positive previous_sequence, sequence=previous+1; different lowercase head_sha256 and previous_head_sha256, plus witness_key_fingerprint_sha256/readback_sha256; distinct opaque primary_version and recovery_version, external_publication_ref, timezone-qualified independent_time. Format checks DO NOT authenticate crypto, external high-water or time |
| tests | Exactly WORM-01 through WORM-11 with unique IDs and unique evidence_ref; each has id, status, actor_role, evidence_ref, reviewer_ref. FAIL/BLOCKED/NOT_RUN prevents completeness. NOT_APPLICABLE is permitted only for WORM-09 for non-LTO medium; LTO requires physical WORM-09 declared PASS. These are **self-reported** values, not observations independently proved by code |

Targeted synthetic negatives: shared Ceph underlay root/high-water, RGW API root/recovery, shared break-glass and signer, missing authorization, production scope, N-1 replay, repeated head, duplicate versions, missing external publication, repeated test/evidence, NOT_RUN WORM-04, invalid LTO exception, malformed/duplicate JSON, oversized input, bad SHA/time and unknown schema.

## No release-authority propagation

This check is L1-only and operates entirely on invented metadata. It has no access to real storage, root rights, legally approved retention or organizational identity. **The only permitted outcome is readiness for a real independent review, not WORM PASS.** Issue [#6](https://github.com/kunlong-luo/lanveriq/issues/6) controls the required physical approvals, readbacks, actual destructive lab tests, cross-administrator signing, recovery and decision. C18f prior synthetic baseline is independent of C18h source SHA; record actual CI run evidence separately. **G-02/G-08/G-05 OPEN; v0.1 NOT RELEASE READY; all production flags OFF.**

Ceph's [official RGW Object Lock analysis](https://www.ceph.io/en/news/blog/2025/rgw-deep-dive-3/) explicitly excludes direct RADOS, cluster-level administration and physical destruction from Object Lock API guarantees; intake must therefore distinguish storage_api from storage_root.

## Exact-SHA software CI evidence (verified 2026-11-22)

Functional source SHA [ea126cff212358620e2070726dc7262153995684](https://github.com/kunlong-luo/lanveriq/commit/ea126cff212358620e2070726dc7262153995684), **4/4 triggered workflows completed SUCCESS on that exact SHA**: [server-unit Java25 and C18h 23 synthetic Python negative tests #45024214643](https://github.com/kunlong-luo/lanveriq/actions/runs/45024214643) (the named new C18h test step, Java unit and FIDO source-time evidence step individually succeeded); [Next.js #45024214645](https://github.com/kunlong-luo/lanveriq/actions/runs/45024214645); [documentation authority #45024214648](https://github.com/kunlong-luo/lanveriq/actions/runs/45024214648); [RD-2 default-deny #45024214745](https://github.com/kunlong-luo/lanveriq/actions/runs/45024214745). This is **source/L1 + existing workflow checks only**. This C18h SHA did **not** run a new PG18 Testcontainers workflow, real Ceph/RADOS, real external WORM, real keys/time, true independent reviewer or Pop!_OS24 L3. The prior C18f real PG18 synthetic L2 success belongs to its own different SHA.

**G-02/G-08 remain OPEN; v0.1 NOT RELEASE READY.**
