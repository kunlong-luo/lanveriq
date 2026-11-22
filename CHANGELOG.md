> **D4-C15 acceptance baseline verified 2026-11-22:** [functional SHA `9e5e0b08`](https://github.com/kunlong-luo/lanveriq/commit/9e5e0b080ae4a4843288584a885f37627d004b1d) passed **5/5 exact-SHA triggered synthetic CI workflows**: [Java 25 Gradle unit #45014246209](https://github.com/kunlong-luo/lanveriq/actions/runs/45014246209), [PostgreSQL 18.6 Testcontainers real synthetic transaction tests and mandatory 11-class / ≥146 successful JUnit gate #45014246195](https://github.com/kunlong-luo/lanveriq/actions/runs/45014246195), [PG integration-source compile #45014246249](https://github.com/kunlong-luo/lanveriq/actions/runs/45014246249), [RD-2 default-OFF guard #45014246280](https://github.com/kunlong-luo/lanveriq/actions/runs/45014246280), and [Linux Batch A real Caddy→Spring/Tomcat double TLS1.3, PostgreSQL 18.6, disposable Rust Agent and strict ACK synthetic chain #45014246327](https://github.com/kunlong-luo/lanveriq/actions/runs/45014246327). **Next.js workflow was NOT triggered for this functional SHA by its path filter**, and must not be counted as a sixth result; a separate docs commit triggers its own regression. The earlier `4519a203` failures are preserved below; neither source signature verification nor official FIDO trust is claimed for the V30 database. The V30 receipt is **acquisition_untrusted only**, with no PostgreSQL Ed25519 verification, real official FIDO MDS/CRL fetch, authentic CRL provenance, organizational trust-root admission, independently managed signing keys or trusted timestamp, actual human reviewer MFA, off-database audit witness, production CA/issuer or Pop!_OS 24.04 L3. The Linux Batch A success is isolated synthetic acceptance, NOT production Agent release. **v0.1 NOT RELEASE READY; production networking, collection, auto-recovery, remote control and certificate issuance OFF.**

> **D4-C15 first-source CI correctly blocked (2026-11-22):** Exact SHA `4519a203` failed real PG18 because one new test fixture omitted V26's required predecessor hash, and two preexisting tests used exact 29/20 migration counts instead of post-V30 30/21. Fix only the fixture and assertions, preserving V26/V30 fail-closed SQL. Do not report an accepted C15 functional SHA until the repaired exact-source Testcontainers gate passes. [D4-C15 details](docs/security/fido-mds-acquisition-ledger-d4-c15-2026-11-22.md).

## 2026-11-22 — D4-C15 PostgreSQL 18 immutable untrusted acquisition receipt ledger (5/5 exact-SHA synthetic CI PASS)

- Forward-only V30 staging table, DB-owned signed-manifest/signature/chained digests, V26 source hash and sequence binding, V25 stream serialized monotonic history, immutable rows and DB-owned atomic audit with suppression detection, plus new unmounted Java writer.
- Add 3 required true PostgreSQL 18.6 Testcontainers tests, mandatory real XML evidence gate, workflow path wiring and detailed [D4-C15 boundary report](docs/security/fido-mds-acquisition-ledger-d4-c15-2026-11-22.md).
- No signature verification in DB, real FIDO downloading/trust, trusted time or production authority. v0.1 NOT RELEASE READY. **Accepted exact-functional-SHA synthetic evidence:** [functional SHA `9e5e0b08`](https://github.com/kunlong-luo/lanveriq/commit/9e5e0b080ae4a4843288584a885f37627d004b1d) passed **5/5 exact-SHA triggered synthetic CI workflows**: [Java 25 Gradle unit #45014246209](https://github.com/kunlong-luo/lanveriq/actions/runs/45014246209), [PostgreSQL 18.6 Testcontainers real synthetic transaction tests and mandatory 11-class / ≥146 successful JUnit gate #45014246195](https://github.com/kunlong-luo/lanveriq/actions/runs/45014246195), [PG integration-source compile #45014246249](https://github.com/kunlong-luo/lanveriq/actions/runs/45014246249), [RD-2 default-OFF guard #45014246280](https://github.com/kunlong-luo/lanveriq/actions/runs/45014246280), and [Linux Batch A real Caddy→Spring/Tomcat double TLS1.3, PostgreSQL 18.6, disposable Rust Agent and strict ACK synthetic chain #45014246327](https://github.com/kunlong-luo/lanveriq/actions/runs/45014246327). **Next.js workflow was NOT triggered for this functional SHA by its path filter**, and must not be counted as a sixth result; a separate docs commit triggers its own regression. The V30 receipt is **acquisition_untrusted only**, with no PostgreSQL Ed25519 verification, real official FIDO MDS/CRL fetch, authentic CRL provenance, organizational trust-root admission, independently managed signing keys or trusted timestamp, actual human reviewer MFA, off-database audit witness, production CA/issuer or Pop!_OS 24.04 L3. The Linux Batch A success is isolated synthetic acceptance, NOT production Agent release. **v0.1 NOT RELEASE READY; production networking, collection, auto-recovery, remote control and certificate issuance OFF.**

> **D4-C14 pre-acceptance negative-fixture fix (2026-11-22):** Real Java CI for initial source SHA `8a5a933b` caught a test-fixture failure: the test-side signing transcript rejected malicious URL values before the actual inspector could exercise rejection. Keep URL enforcement in the inspector but permit delimiter-safe negative transcript construction, and bind leaf DER, exact signed-time key/proof and receipt fingerprints. First CI is NOT a PASS; reassess only a new functional SHA with complete CI.

## 2026-11-22 — A11-D4-C14 offline MDS/CRL signed acquisition receipt (4/4 exact-SHA triggered CI PASS)

- Add unmounted source-acquisition evidence verifier that binds original signed JWS and each CRL DER fingerprint to a separately pinned Ed25519 receipt, explicit HTTPS source allowlist, bounded time and strictly increasing receipt sequence.
- Integrate with prior C12 offline two-reviewer gate; add disposable true ES256/RS256 and independent Ed25519 key regression covering malicious URL, swapped actual JWS/CRL, non-200, redirects, stale time and forged acquisition signature. Expand real JUnit XML gate to 13 required methods.
- [Detailed C14 scope and hazards](docs/security/fido-mds-acquisition-receipt-d4-c14-2026-11-22.md). **4/4 same-source triggered checks SUCCESS:** [functional SHA `d619d77c`](https://github.com/kunlong-luo/lanveriq/commit/d619d77c468c7f0f4253048efd4b2d112f0ca3ab) passed **4/4 exact-SHA workflow gates triggered by the source paths**: [Java 25 real Gradle unit tests, executed XML confirms all THIRTEEN FIDO verifier methods #45013121429](https://github.com/kunlong-luo/lanveriq/actions/runs/45013121429), [PostgreSQL 18.6 Testcontainers real synthetic transactions #45013121314](https://github.com/kunlong-luo/lanveriq/actions/runs/45013121314), [PG18 integration-source compilation #45013121358](https://github.com/kunlong-luo/lanveriq/actions/runs/45013121358), [RD-2 fail-closed OFF #45013121291](https://github.com/kunlong-luo/lanveriq/actions/runs/45013121291). **Next.js workflow was not triggered by this backend-only fix SHA** (path filter); its separate documentation-source regression must not be counted as part of the 4/4 functional test runs. **No live network, download, true provenance, persistent production custody or FIDO trust.** v0.1 NOT RELEASE READY; production OFF.

## 2026-11-22 — A11-D4-C13 published R46 root identity guard (5/5 exact-SHA CI PASS)

- Add an unmounted, offline-only exact published-SHA256 GlobalSign R46 self-signed certificate identity screen; public certificate reference fixture from GlobalSign, independently cross-confirmed by CCADB. Reject fake roots even with otherwise correctly signed disposable ES256/RS256 JWS+CRL+time+two-key review proof.
- New optional offline verifier lane validates exact known official root DER against the claimed root before C12 review; returns only UNTRUSTED evidence. Extend strict real JUnit XML gate from 10 to 12 mandatory source-verifier tests. [Security contract](docs/security/fido-mds-public-r46-root-reference-d4-c13-2026-11-22.md).
- **Exact-functional-SHA synthetic CI ACCEPTED:** [functional SHA `e73e6824`](https://github.com/kunlong-luo/lanveriq/commit/e73e6824a1ad09cc9d389f5d6392409078408e58) passed **5/5 exact-SHA synthetic CI**: [Java 25 real Gradle + 12 mandatory executed FIDO JUnit methods #45011880180](https://github.com/kunlong-luo/lanveriq/actions/runs/45011880180), [PostgreSQL 18.6 real Testcontainers synthetic transactions #45011880254](https://github.com/kunlong-luo/lanveriq/actions/runs/45011880254), [PostgreSQL integration-source compile #45011880190](https://github.com/kunlong-luo/lanveriq/actions/runs/45011880190), [Next.js fast source/typecheck/build #45011880239](https://github.com/kunlong-luo/lanveriq/actions/runs/45011880239), [RD-2 fail-closed default-OFF #45011880178](https://github.com/kunlong-luo/lanveriq/actions/runs/45011880178). Public R46 identity is not independently governed production trust; no official blob download, CRL/time custody, live WebAuthn MFA, trusted CA or Pop!_OS L3. Production remains OFF, v0.1 NOT RELEASE READY.

## 2026-11-21 — A11-D4-C12 independent pinned source-review signature boundary (5/5 exact-SHA CI PASS)

- Add a package-private, unmounted governance proof requiring TWO distinct separately pinned Ed25519 verification keys over exactly the synthetic JWS, leaf/root, CRL hashes, time-key/proof, claimed official-source URL and expiry/review-sequence evidence. Refuse self-asserted roots without the independent seals; preserve existing crypto/PKIX/CRL/time/JSON gates.
- Add genuine ES256/RS256 signed-fixture negative proof and mandate ten executed real Gradle verifier JUnit methods; source-only/no real FIDO or production keys. [D4-C12 acceptance plan](docs/security/fido-mds-independent-provenance-d4-c12-2026-11-21.md).
- **Accepted synthetic-only functional SHA and mandatory JUnit: [functional SHA `2d39d694`](https://github.com/kunlong-luo/lanveriq/commit/2d39d694fb1724f47276a1ef2f4a94d424580895) passed **5/5 exact-SHA synthetic CI workflows**: [Java 25/Gradle and real JUnit XML showing all TEN mandatory source-time/provenance methods #44895483823](https://github.com/kunlong-luo/lanveriq/actions/runs/44895483823), [PostgreSQL 18.6 Testcontainers real synthetic transactions + mandatory JUnit guard #44895483791](https://github.com/kunlong-luo/lanveriq/actions/runs/44895483791), [PostgreSQL integration source compile #44895483778](https://github.com/kunlong-luo/lanveriq/actions/runs/44895483778), [Next.js frozen fast unit/type/build #44895483732](https://github.com/kunlong-luo/lanveriq/actions/runs/44895483732), and [RD-2 fail-closed default-OFF #44895483711](https://github.com/kunlong-luo/lanveriq/actions/runs/44895483711).** Neither two test keys nor public documentation is actual FIDO/organizational approval. Real signer provenance, trusted time, CRL custody, reviewer WebAuthn MFA, OS L3 and production release remain OPEN/OFF.

## 2026-11-21 — A11-D4-C11 strict raw signed-JWS JSON parsing (5/5 exact-SHA CI PASS)

- Block duplicate JSON member names in ORIGINAL compact JWS header/payload bytes at any nesting depth using built-in Jackson 3 strict streaming parser, including encoded-key collisions. Prevent mismatch between parsers' discarded first/last values.
- Integrate with unmounted offline FIDO source verifier; test genuine signed disposable ES256/RS256 BLOBs containing duplicate root/entry/status/extension keys and retain unique synthetic untrusted evidence.
- [D4-C11 security and CI gate](docs/security/fido-mds-duplicate-json-d4-c11-2026-11-21.md). **Exact-source CI ACCEPTED:** [functional SHA `7282bc44`](https://github.com/kunlong-luo/lanveriq/commit/7282bc446c2b9ac27a8f0fea4830fbcd29f829a6) passed **5/5 same-SHA synthetic CI gates**: [Java 25 Gradle unit and real JUnit XML proving all 9 mandatory verifier tests #44894793997](https://github.com/kunlong-luo/lanveriq/actions/runs/44894793997), [PostgreSQL 18.6 Testcontainers real synthetic transactions #44894793992](https://github.com/kunlong-luo/lanveriq/actions/runs/44894793992), [PG integration-source compile #44894793990](https://github.com/kunlong-luo/lanveriq/actions/runs/44894793990), [Next.js fast tests/typecheck/build #44894793972](https://github.com/kunlong-luo/lanveriq/actions/runs/44894793972), and [RD-2 fail-closed default OFF #44894793994](https://github.com/kunlong-luo/lanveriq/actions/runs/44894793994). **No official FIDO trust, approved MDS source, production time/CRL custody, live WebAuthn MFA or Pop!_OS 24.04 L3.** No FIDO root trust, admin MFA, physical Pop!_OS authorization or production switches; v0.1 NOT RELEASE READY.

## 2026-11-21 — Documentation authority and current-state reorganization

- Establish [one current-status and exact-SHA CI authority](docs/CURRENT_STATUS.md), replace the oversized docs landing index with a topic-oriented [navigation page](docs/README.md), and preserve its full prior version in [historical archive](docs/governance/documentation-index-snapshot-2026-11-21.md). No historical evidence was deleted.
- Align both root READMEs, AGENTS, planning/backlog/legacy sequence, release runbook and D0 documentation gate to the same source-of-truth and security boundaries. Fix stale test-matrix preamble asserting all database/browser automation is unexecuted; keep real Pop!_OS L3 marked not accepted.
- Repair all relative Markdown links inside the relocated historical docs-index snapshot (the original tree-relative paths were no longer valid under `docs/governance/`). Add an offline, fail-closed current-status/broken-link regression and path-filtered CI triggers for the authoritative docs and root READMEs. No network fetch and no application feature changes.
- **Documentation-only change; NOT a new functional acceptance.** The verified D4-C10 functional SHA is `2368163e` (5/5 synthetic CI), NXT-28 UI SHA is `e76cd345` (4/4 disconnected UI CI). Production remains NOT RELEASE READY and all privileged switches OFF.

## 2026-11-21 — A11-D4-C10 signed MDS duplicate-model denial (5/5 exact-SHA CI SUCCESS)

- **Exact-source CI SUCCESS:** [functional SHA `2368163e`](https://github.com/kunlong-luo/lanveriq/commit/2368163e588e6e94f74a748bdc6e162b9c4c6b9a) passed **5/5 exact-SHA synthetic CI gates**: [Java 25/Gradle unit and 8 required real JUnit XML methods #44893055170](https://github.com/kunlong-luo/lanveriq/actions/runs/44893055170), [PostgreSQL 18.6 Testcontainers synthetic transactions #44893055206](https://github.com/kunlong-luo/lanveriq/actions/runs/44893055206), [PostgreSQL integration-source compile #44893055072](https://github.com/kunlong-luo/lanveriq/actions/runs/44893055072), [Next.js frozen install/tests/typecheck/build #44893055069](https://github.com/kunlong-luo/lanveriq/actions/runs/44893055069), and [RD-2 default-OFF #44893055089](https://github.com/kunlong-luo/lanveriq/actions/runs/44893055089). These tests use disposable synthetic PKIX/CRLs/signers, NOT official FIDO provenance, production trusted time or live MFA.
- Reject reused normalized AAGUID, AAID and U2F attestation certificate identifiers across an already signed BLOB, including within U2F lists. Distinct mixed models still produce only untrusted source evidence.
- Execute disposable ES256/RS256 + PKIX/CRL + bound Ed25519 time negative/positive fixture tests, with a strict real-JUnit-XML execution gate (eight mandatory verifier cases).
- [D4-C10 security contract](docs/security/fido-mds-duplicate-identifiers-d4-c10-2026-11-21.md). No trusted FIDO provenance, approved external time/CRL, real WebAuthn MFA or physical Pop!_OS acceptance. v0.1 **NOT RELEASE READY**; production networking/collection/recovery/remote/CA OFF.
## 2026-11-21 — NXT-28 compact-height navigation consistency (four exact-SHA CI successes)

- **NXT-28 functional acceptance:** [functional SHA `e76cd345`](https://github.com/kunlong-luo/lanveriq/commit/e76cd34505dd8e49833ab9c0b61421288c43c353) passed FOUR same-SHA checks: [Next.js L1 #44882686781](https://github.com/kunlong-luo/lanveriq/actions/runs/44882686781), [real Chromium #44882686713](https://github.com/kunlong-luo/lanveriq/actions/runs/44882686713), [disconnected Pages build/deploy #44882686808](https://github.com/kunlong-luo/lanveriq/actions/runs/44882686808), and [RD-2 default-OFF #44882686775](https://github.com/kunlong-luo/lanveriq/actions/runs/44882686775). Chromium screenshot artifact: `nxt18-visual-qa-e76cd34505dd8e49833ab9c0b61421288c43c353` (ID `14698677524`). These tests cover the disconnected single `apps/web` UI only.
- Make desktop Next.js sticky navigation independently scrollable in short windows while hiding its native internal bar, without clipping document scrolling.
- Align the Base UI mobile sheet with the canonical white/ink tokens, preserving focus trap, Escape return and scroll gestures. Add Chromium 1024×520 / 390×520 checks, CSS source regression and no-network/cookie assertions.
- The first browser run exposed an old wheel test targeting screen coordinate (0,0), now occupied by the scrolling sidebar. Adjusted the wheel test to target the main content, verified the independent sidebar separately, and reran all four gates on functional SHA `e76cd345`.
- [NXT-28 implementation/acceptance](docs/development/frontend-compact-navigation-nxt28-2026-11-21.md). **v0.1 NOT RELEASE READY**: official FIDO provenance/trusted time, admin WebAuthn and Pop!_OS physical gates remain open; production networking, collection, recovery, remote and CA signing OFF.

## 2026-11-21 — A11-D4-C9 FIDO MDS offline BLOB entry-shape gate (5/5 same-SHA CI PASS)

- **D4-C9 exact-source acceptance — 5/5 SUCCESS:** functional SHA [`4dc3b5f1`](https://github.com/kunlong-luo/lanveriq/commit/4dc3b5f19d8d125d528e2c2eccf1905fb375e34d) passed [Java 25 / Gradle L1 #44881397946](https://github.com/kunlong-luo/lanveriq/actions/runs/44881397946) (real JUnit XML proved **all seven mandatory verifier tests** executed), [PostgreSQL 18.6 Testcontainers real synthetic transactions #44881397931](https://github.com/kunlong-luo/lanveriq/actions/runs/44881397931), [PostgreSQL integration source compile #44881397981](https://github.com/kunlong-luo/lanveriq/actions/runs/44881397981), [Next.js fast source/build/HTTP #44881397980](https://github.com/kunlong-luo/lanveriq/actions/runs/44881397980), and [RD-2 remote default-OFF #44881397956](https://github.com/kunlong-luo/lanveriq/actions/runs/44881397956). These cover **ephemeral synthetic signers/keys, offline signed-time claim and untrusted records only**; they do NOT establish an approved official FIDO signing source, licensed MDS usage, trusted time, source CRL provenance, live WebAuthn MFA or production readiness. **v0.1 NOT RELEASE READY**.

- Add `StagedFidoMdsPayloadShapeScreen`, a package-private fail-closed screen for all entries within **already cryptographically verified** MDS-shaped JWS. Require nonambiguous authenticator model identifiers, metadata statement objects, status reports and dated status history; reject malformed/future dates and unpaired rogue-list URLs. Unknown statuses remain opaque; `REVOKED` is **not** treated as trusted.
- Integrate immediately after the existing ES256/RS256 PKIX/CRL verification in `StagedFidoMdsSourceVerifier`. Valid cryptographic signatures with malformed metadata entries must still be rejected; valid signed synthetic entries can only return `UntrustedSourceEvidence` and cannot grant registrations, login, reviewer or CA privileges.
- Add shape-specific JUnit negative fixtures, truly signed synthetic JWS integration tests, and require both new integrated cases in the executed Gradle JUnit XML evidence guard. No migrations, Beans, routes, real roots, trusted clocks, HTTP fetch, new dependencies or production switches.
- This is **D4-C9** (D4-C8 already names V29 database history controls). [Implementation and remaining blockers](docs/security/fido-mds-payload-screen-d4-c9-2026-11-21.md). Official MDS trust and live administrator WebAuthn remain unapproved; **v0.1 NOT RELEASE READY**.

## 2026-11-21 — NXT-27 Shared evidence states (four same-SHA CI successes)

- **NXT-27 exact-source CI SUCCESS:** functional SHA [`ff66556d72c078082ba8cfd08a97107d8ba47cf0`](https://github.com/kunlong-luo/lanveriq/commit/ff66556d72c078082ba8cfd08a97107d8ba47cf0) passed all four same-SHA gates: [Next.js frozen install, SSR/Vitest/source-negative tests, typecheck, production build and HTTP #44880424373](https://github.com/kunlong-luo/lanveriq/actions/runs/44880424373), [real Chromium 11-route UI, monitoring/detail evidence states, keyboard/mobile, 15 screenshots and disposable NXT-26 authorized directory interaction fixture #44880424398](https://github.com/kunlong-luo/lanveriq/actions/runs/44880424398), [public disconnected static Pages build/deploy #44880424353](https://github.com/kunlong-luo/lanveriq/actions/runs/44880424353), [RD-2 remote default-OFF #44880424374](https://github.com/kunlong-luo/lanveriq/actions/runs/44880424374). Chromium artifact `nxt18-visual-qa-ff66556d72c078082ba8cfd08a97107d8ba47cf0` (ID `14697751956`). This proves only the isolated, disconnected UI; it does **not** approve release, live device data, FIDO MDS or Pop!_OS physical tests.

- Introduce a source-owned, pure `EvidenceNotice` / `EvidenceValue` presentation API in `apps/web/src/components/ui/evidence-state.tsx`. Distinguish **unconnected**, **restricted**, and **unverified** rather than implying zero, online/offline, or a completed authorization decision.
- Reuse these components across monitoring activity/trend, inventory coverage, device-detail summary and Base UI hardware/network/health tabs; remove obsolete chart-specific CSS and align all evidence visuals to the current `console-polish.css` tokens.
- Add semantic, contextual accessible labels; preserve ordinary disabled actions, no API/login/device calls, no browser storage, Base UI tab keyboard interaction and public Pages isolation. Extend SSR/source/real Chromium negative checks. [NXT-27 implementation and acceptance](docs/development/frontend-evidence-state-nxt27-2026-11-21.md).
- Production authentication, device connectivity, collection, auto-recovery, remote control and certificate issuance remain OFF. v0.1 NOT RELEASE READY. NXT-27 exact-functional-SHA CI is recorded above.

## 2026-11-21 — NXT-26 Base UI directory Select (four same-SHA CI successes)

- **NXT-26 exact-source acceptance PASSED:** functional SHA [`0750f6b55c0bc9a69416e6014485dcf213ba1406`](https://github.com/kunlong-luo/lanveriq/commit/0750f6b55c0bc9a69416e6014485dcf213ba1406) passed all four same-SHA workflows: [Next.js frozen install, tests, typecheck, build and HTTP #44879640236](https://github.com/kunlong-luo/lanveriq/actions/runs/44879640236), [real Chromium 11 pages, 15 screenshots and CI-only popup/group/sort/keyboard/revocation acceptance #44879640279](https://github.com/kunlong-luo/lanveriq/actions/runs/44879640279), [disconnected public Pages build and deployment #44879640285](https://github.com/kunlong-luo/lanveriq/actions/runs/44879640285), and [RD-2 default OFF #44879640264](https://github.com/kunlong-luo/lanveriq/actions/runs/44879640264). Visual artifact `nxt18-visual-qa-0750f6b55c0bc9a69416e6014485dcf213ba1406` (ID `14697451711`). This is isolated UI/source acceptance, NOT live admin auth/device access, production release or physical Pop!_OS acceptance.
- Earlier two Chromium fixture failures below are retained as debugging history; the final active-popup locator fix has passed, without removing any authorization or keyboard assertions.

- Replace the two `NativeSelect` dropdowns in the one shared `DeviceDirectory` with controlled Base UI React 1.9 `Select` popups, including labels, keyboard focus, item indicators, viewport-safe overlays and 44px touch controls. Reuse installed dependency; no custom dropdown logic or new packages.
- Retire the unused native-select component; bring the text input focus token into the current indigo palette. Apply new dropdown styles in `console-polish.css` without changing backend or adding real-data access.
- Strengthen Vitest/source probes, real disconnected Chromium layout checks and the disposable authenticated-page **synthetic** browser fixture for popup selection, sorting, page replacement, permission revocation and safe reset. See [NXT-26 acceptance](docs/development/frontend-base-ui-select-nxt26.md).
- Second real Chromium [#44879458885](https://github.com/kunlong-luo/lanveriq/actions/runs/44879458885) confirmed the sort option and group popup actually work. Base UI retains a closed sort popup during transitions; strict lookup of all `.lv-select-popup` instances matched an old closed popup and the new open one. The CI-only fixture now waits for **`.lv-select-popup[data-open]`**, keeping every real select and authorization assertion. New same-SHA acceptance pending.
- First real Chromium [#44879221414](https://github.com/kunlong-luo/lanveriq/actions/runs/44879221414) passed the normal UI/screenshot gates but failed its later CI-only sort-popup visibility check, leaving exact-SHA acceptance **OPEN**. The fixture now waits for the actual portal mount before checking ARIA options rather than racing an immediate DOM probe; no safety or interaction assertions are removed. New exact-SHA rerun required.
- Real API/login, RustDesk, collection, recovery and certificate issuance remain OFF. v0.1 NOT RELEASE READY.

## 2026-11-21 — NXT-25 Web documentation consistency and regression gate (four same-SHA CI successes)

- Reconcile the active `apps/web` Next.js 16.4.5 tree with the ADR-0008, current engineering/technology standards, CSS design tokens, NXT-23/24 migration audit, browser testing matrix, operations runbooks, release notes, IDEA setup, doc index, README and AGENTS guidance.
- Remove contradictory active claims that `apps/web` is both a Vite and Next.js app, that the current Next.js `web-unit.yml` should be deleted, or that the retired `craftwork-theme.css` and old purple/lime colors are the active CSS. Preserve original historical CI references with explicit date/status markers.
- Add a **source-only documentation consistency script** and negative fixtures, run on `main` through the existing Next.js fast workflow. The check does not connect to devices, credentials, Spring, PostgreSQL or external endpoints.
- NXT-24's **five same-functional-SHA successes on `6ed47c47` remain historical acceptance**; the new NXT-25 results are independent. **NXT-25 exact-source checks PASSED:** implementation SHA [`b37797d9da5d9d622df39c968b92547c2f00402d`](https://github.com/kunlong-luo/lanveriq/commit/b37797d9da5d9d622df39c968b92547c2f00402d) passed [Next.js L1 with documentation negative tests #44877774039](https://github.com/kunlong-luo/lanveriq/actions/runs/44877774039), [real Chromium/UI/network isolation and 15 screenshots #44877774015](https://github.com/kunlong-luo/lanveriq/actions/runs/44877774015), [disconnected static Pages build+deploy #44877774094](https://github.com/kunlong-luo/lanveriq/actions/runs/44877774094) and [RD-2 remote default-OFF #44877774084](https://github.com/kunlong-luo/lanveriq/actions/runs/44877774084), **all on the same SHA**. Chromium artifact `nxt18-visual-qa-b37797d9da5d9d622df39c968b92547c2f00402d` (ID `14697086467`). This is documentation/preview-source acceptance, NOT release or live system approval. **v0.1 NOT RELEASE READY**; production features stay OFF.

## 2026-11-21 — NXT-24 Rename canonical Next.js directory to `apps/web` (five same-SHA CI successes)

- Move all canonical Next.js 16 App Router sources from `apps/web-next/` to **`apps/web/`** with a history-preserving Git tree rename. The previously deleted Vite frontend is not restored.
- Move Next.js fast CI from `.github/workflows/web-next-unit.yml` to `.github/workflows/web-unit.yml`, updating Chromium, public Pages, synthetic Caddy, generated contract and RD-2 negative gates to new paths. Update runtime fixture basename safety and single-UI source checks with regression tests.
- Update Chinese/English README, Pop!_OS runbooks, design and architecture docs for the new working directory. Retain static-only preview, credential/session disablement and all production release gates OFF.
- **NXT-24 exact-source acceptance:** functional SHA `6ed47c472b86b6385aff9f6e0c71ab70f0ef8b6c` passed **all five same-SHA GitHub workflows**: [Next.js frozen pnpm, single-UI source gates, TypeScript/build/HTTP #44876019214](https://github.com/kunlong-luo/lanveriq/actions/runs/44876019214), [real Chromium UI, keyboard/mobile and screenshots #44876019297](https://github.com/kunlong-luo/lanveriq/actions/runs/44876019297), [disconnected static Pages build+deploy #44876019222](https://github.com/kunlong-luo/lanveriq/actions/runs/44876019222), [RD-2 remote default-OFF #44876019189](https://github.com/kunlong-luo/lanveriq/actions/runs/44876019189), and [Java server unit/negative checks #44876019234](https://github.com/kunlong-luo/lanveriq/actions/runs/44876019234). Chromium artifact `nxt18-visual-qa-6ed47c472b86b6385aff9f6e0c71ab70f0ef8b6c` (ID `14695613816`). This is disconnected UI/source acceptance **only**, not production rollout, real WebAuthn MFA or FIDO MDS source trust.

## 2026-11-21 — NXT-23 Remove obsolete Vite app; make Next.js sole Web UI (four same-SHA UI CI successes)

- Remove 32 tracked files under legacy `apps/web` and the legacy Vite-only `web-unit.yml` workflow. Do not delete the Git history. Canonical `apps/web` already has separately staged CSRF/session/read-only projection and a generated Springdoc/Orval contract bridge; no business APIs are connected.
- Normalize English/Chinese root READMEs and IDEA launch guidance to `apps/web`, `http://127.0.0.1:3100/overview`. Remove conflicting `5173` launch recipe and untrue Vite health-only runtime claims.
- Add `verify-single-ui.mjs` plus negative fixture tests to both Next.js fast source workflow and disconnected static Pages workflow. All production capabilities remain disabled. **NXT-23 exact-source UI acceptance:** functional commit [`e54af66aa7803b43c2b85a6bff7efd419b44a428`](https://github.com/kunlong-luo/lanveriq/commit/e54af66aa7803b43c2b85a6bff7efd419b44a428) passed all FOUR same-SHA workflows: [Next.js frozen pnpm, single-UI guard, tests/typecheck/build/HTTP #44874669194](https://github.com/kunlong-luo/lanveriq/actions/runs/44874669194), [actual Chromium 11 routes/keyboard/mobile/screenshots #44874669221](https://github.com/kunlong-luo/lanveriq/actions/runs/44874669221), [disconnected static Pages export+deploy #44874669171](https://github.com/kunlong-luo/lanveriq/actions/runs/44874669171) and [RD-2 default OFF #44874669193](https://github.com/kunlong-luo/lanveriq/actions/runs/44874669193). Visual artifact `nxt18-visual-qa-e54af66aa7803b43c2b85a6bff7efd419b44a428` (ID `14695855747`). This is not production authorization.

## 2026-11-21 — NXT-22 keyboard-first local workspace navigation (exact-SHA UI acceptance)

- Replace disabled Console header search with an accessible Base UI Dialog command palette for nine allowlisted public preview pages. Add Ctrl/⌘+K, local filtering, Escape, mobile >=44px trigger and negative empty state; real-device or business search is **not enabled**.
- Add route whitelist Vitest, source checks and real Chromium keyboard/mobile/negative business-route assertions. Exact functionality SHA `18284b101f9baf31a13024de1671918305534310` **PASSED** [Next fast #44873877638](https://github.com/kunlong-luo/lanveriq/actions/runs/44873877638), [real Chromium + 15 screenshots #44873877578](https://github.com/kunlong-luo/lanveriq/actions/runs/44873877578), [static Pages #44873877610](https://github.com/kunlong-luo/lanveriq/actions/runs/44873877610) and [RD-2 OFF #44873877615](https://github.com/kunlong-luo/lanveriq/actions/runs/44873877615). Production and remote control stay OFF.

## 2026-11-21 — Branch consolidation + NXT-21 editorial Console (non-production)

- All three old refs merged/verified without regression; `main` is the sole remote branch after [cleanup #44873229717](https://github.com/kunlong-luo/lanveriq/actions/runs/44873229717), with the temporary write-enabled workflow removed.
- The canonical Next.js 16 Console was styled with a Craftwork-inspired original light editorial layout, dark ink active navigation and restrained card accents; no third-party paid assets were imported. Functional commit `e43886a3` passed the same SHA [Next fast](https://github.com/kunlong-luo/lanveriq/actions/runs/44873138900), [Chromium + 15 screenshots](https://github.com/kunlong-luo/lanveriq/actions/runs/44873138797), [static Pages](https://github.com/kunlong-luo/lanveriq/actions/runs/44873138865), and [RD-2 OFF](https://github.com/kunlong-luo/lanveriq/actions/runs/44873138989).
- Disconnected static-only design acceptance; v0.1 not approved, no real device/control/network/collection/issuer capabilities enabled.

# Changelog

## 2026-11-07 — Canonical Next.js 16.4.5 App Router frontend cutover

- Made `apps/web` the sole forward-development management UI, upgrading from Next.js 16.4.3 to the **November security-fixed stable 16.4.5**. The actual pnpm dependency lockfile was generated, frozen-installed, typechecked and built in [automatic GitHub Actions #42366826585](https://github.com/kunlong-luo/lanveriq/actions/runs/42366826585); the temporary write-capable updater workflow was removed afterward.
- Centralized the five Next.js workspace links in `src/lib/console-navigation.ts`, with regression tests for all main routes, nested device details and non-navigation API paths. The existing Next.js App Router pages keep **disabled credentials, unconnected business data and no real Agent/device operations**.
- Promoted Next.js to the canonical entry point in Chinese/English READMEs, ADR-0008, front-end design system, installation runbook and agent rules. Kept `apps/web` Vite/React Router as inert legacy migration/reference only. Springdoc → Orval Java CI now verifies the **Next.js** client dependency graph, not both duplicate Vite and Next projects. [Server CI #42367306699](https://github.com/kunlong-luo/lanveriq/actions/runs/42367306699) passed after the cutover.
- Kept [GitHub Pages](https://kunlong-luo.github.io/lanveriq/) as a **public, disconnected static Next.js export**, never a full-stack session host. [Next.js source/build #42367117962](https://github.com/kunlong-luo/lanveriq/actions/runs/42367117962) and [Pages deployment #42367117971](https://github.com/kunlong-luo/lanveriq/actions/runs/42367117971) passed; the live overview returned HTTP 200 with no-data warnings.
- [Caddy/real Spring/PG18/Chromium #42367182217](https://github.com/kunlong-luo/lanveriq/actions/runs/42367182217) passed **after the Next.js 16.4.5 update**, including NXT-10 direct-origin/HTML/RSC protections and NXT-11 **public static JS identity neutrality** across anonymous, both synthetic administrators and post-logout fetches. Automated standalone browser UI/keyboard/responsive CI was also introduced; it has no real database or login.
- Production Caddy/Next.js namespace, actual account login, employee/device data access, Agent collection, private cache variants and organizational/privacy approval remain **OPEN**. No real rollout or monitoring permission is implied.


## 2026-11-01 — Docs synchronized after successful Web TypeScript 7 CI

- Synced root English/Chinese readmes and the WEB-01 backlog status to reflect anonymous Spring Boot health-only connectivity while keeping all five demo devices explicitly synthetic and all authenticated business routes disabled.
- The first Web CI attempt surfaced TypeScript 7's removed `baseUrl` compiler setting; removed that deprecated setting while retaining the source-relative `@/*` path mapping, and verified the subsequent Web unit + typecheck + Vite build succeeded.

## 2026-11-01 — Vite / shadcn-style UI foundation and health-only backend link

- Retained React 19/Vite 8; added a shadcn/ui-compatible Card source, CLI project configuration, import aliases and Tailwind 4 semantic theme tokens without introducing new runtime dependencies.
- Added a strictly localhost/exact-path Vite dev proxy for anonymous Spring Boot `GET /actuator/health`; TanStack Query makes one initial request and only manual retries, showing checked/healthy/unavailable states in the console. No proxy for `/api`, no credentials, no device records, no WSS/polling.
- Added synthetic response unit tests and updated navigation SSR tests; admin login and real device operations stay intentionally disabled. This is not full PostgreSQL/Caddy/browser-session acceptance.

## 2026-11-01 — Local backend startup evidence and read-only HTTP verification

- Recorded operator-observed **PostgreSQL 18.6/Flyway V1–V8/Hibernate/Tomcat 8080 local startup success** separately from the still-unpassed 58-case manual Testcontainers gate.
- Added `apps/server/scripts/verify-local-server.sh` to perform exactly two **GET-only** loopback checks for health HTTP 200/UP and unauthenticated device listing HTTP 403; no database access, password, employee data, Agent action or remote connection.
- Added synthetic local HTTP fixture tests and Bash syntax validation in automatic server CI. They prove only the probe behavior; independent PostgreSQL 18 and Caddy/browser acceptance remains manual.

## 2026-11-01 — One-command local PostgreSQL setup without DBeaver

- Added manual-only `apps/server/scripts/setup-local-postgres.sh`, a guarded Linux wrapper around PostgreSQL's own `psql` to avoid DBeaver SQLSTATE 25001 transaction handling. It checks PostgreSQL 18/SCRAM and exact role/database state, requires human confirmation, handles narrowly scoped partial NOLOGIN-role creation, and refuses unexpected database ownership and PUBLIC grants.
- Uses psql's interactive `\\password` command for a hidden strong password, enabling LOGIN only after SCRAM password storage is verified; neither scripts nor logs contain plaintext credentials. Existing credentials are not reset.
- Kept the reviewed SQL and Flyway migrations separate. No shared database, real device or production service is accessed by GitHub CI.

## 2026-11-01 — Manual, secret-free PostgreSQL database provisioning
- Added `apps/server/scripts/provision-lanveriq-database.sql` as an explicitly invoked psql bootstrap, **not a Flyway migration**. It fails closed if not connected to the postgres maintenance database as an authorized cluster administrator, or if the lanveriq role/database already exists.
- Creates only a `NOLOGIN` non-superuser database owner and dedicated lanveriq database, revoking default PUBLIC database privileges. A human sets the SCRAM password via interactive `\password` and enables LOGIN only afterward.
- Updated English/Chinese setup and installation instructions. No database/container was contacted or modified to create this script. Ordinary Java unit CI must never execute cluster provisioning.

## 2026-11-01 — Repository root boundary audit
- Kept the repository root limited to shared README/governance/security/changelog files, `.github` automation, `apps`, shared Caddy ingress under `deploy/caddy`, and `docs`. Root `.gitignore` ignores generated build outputs across subprojects.
- Migrated the Rust-only `Cargo.lock` and `rust-toolchain.toml` into `apps/agent`, and made its `Cargo.toml` a standalone single-crate manifest with exact edition/MSRV/license fields. The prior root Rust workspace manifest was removed.
- Relocated the Agent-only experimental systemd unit from `deploy/systemd/` to `apps/agent/deploy/systemd/`; the shared cross-component Caddy reference stayed under `deploy/caddy`. Updated Agent Linux/Windows CI working directories and installation/IDE docs to match.
- Java Gradle remains exclusive to `apps/server`, React pnpm exclusive to `apps/web`; no new root build-wrapper files. This is an organizational cleanup and does **not** approve real Agent collection, an installed service or deployment.

## 2026-10-31 — Opt-in real PostgreSQL 18 Testcontainers acceptance harness
- Introduced a separate Gradle 9.8.2 `integrationTest` suite based on Spring Boot 4.1.2 `@ServiceConnection` and managed Testcontainers 2.0.5 `postgres:18.6`, rather than H2, ad hoc JDBC or reinvented database setup.
- Added real-Flyway V1–V6/JPA tests for UUIDv7, exact replay, both identity conflicts, whole-batch rollback, forged/expired/revoked device credentials, immutable-event trigger and concurrent duplicate/conflicting uploads.
- Normal automatic Java 25 CI retains unit tests and `bootJar` and now **compiles** database integration sources without executing Docker. A separately manual-only synthetic GitHub workflow executes `integrationTest` and archives short-lived JUnit results.
- **No actual PostgreSQL 18 integration execution has been observed yet.** Caddy/mTLS and Agent live HTTP remain out of scope; no live monitoring authorized.


## 2026-10-31 — SRV-03 JPA atomic event ingestion foundation
- Added Flyway V6 immutable canonical `endpoint_events` with native uuidv7(), PostgreSQL JSONB, two device-scoped unique keys and deny-UPDATE/DELETE trigger; no seeded events.
- Added Spring Boot 4.1/Jackson 3 event DTOs and bounded operational-gap policy, active registered credential/device row locking, JPA PostgreSQL bulk `ON CONFLICT DO NOTHING` then mandatory exact event ID/sequence/digest comparison. Spring AOP commits before the complete ACK is exposed; conflicts roll back all rows.
- Added Java 25 unit negatives for wrong identity, invalid order/schema/source, duplicate event IDs, reused sequence/changed digest, reused event ID/changed sequence, and transaction commit/rollback. Existing security denies all device business HTTP routes.
- **Gate still open:** real PG18 Flyway V1–V6 concurrency, Caddy/mTLS fingerprint trust, real Agent upload and retention/backup verification. [Manual SRV-03 acceptance](docs/testing/server-event-ingestion-manual.md).


## 2026-10-31 — AGT-03 staged Rustls transport and durable batch identity
- Added pinned Reqwest 0.13.5 Rustls-only HTTPS/mTLS client with exact agent origin, dedicated trusted CA, PEM certificate/private identity, HTTPS-only, no proxies/redirects, bounded timeouts/response size; typed synthetic heartbeat and batch envelope (no real OS collectors).
- Added SQLx second migration for stable in-flight batch UUID and fingerprint; next attempt after response loss or restart reuses exact batch and excludes newer queued events. Local events are pruned only after full-sequence valid 200 response; 401/403 pause, 409 quarantine, 429/5xx backoff decision.
- **Boundary:** normal Agent executable never connects or sends, real OS key custody/active cert is missing, SRV-03 server API not yet implemented, no real Caddy/PG18/TLS integration or supported production monitoring. [Manual acceptance](docs/testing/agent-transport-manual.md).


## 2026-10-31 — AGT-02 synthetic SQLx WAL outbox
- Added SQLx 0.9.0 and `libsqlite3-sys =0.37.0` patched bundled SQLite; committed Cargo.lock. AGT-02 standalone synthetic-only SQLite migration and single-connection WAL/FULL storage are not invoked from the Agent service.
- Added atomic persisted event/sequence/cursor with UUIDv7, SHA-256 payload digest, durable replay, explicit sparse ACK, synthetic bounded quota-gap reporting, latest-only inventory snapshots with independent revisions, and unit tests for failure rollback and invalid fixtures.
- Real endpoint OS, abrupt power loss, disk corruption, server-verified ACK, transport, Caddy/PostgreSQL 18 and production monitoring remain **NOT TESTED**; see [AGT-02 acceptance](docs/testing/agent-sqlite-manual.md).


## 2026-10-31 — AGT-01 Rust idle lifecycle and OS service adapters
- Created Rust 1.94 workspace, committed Cargo.lock, Tokio 1.54.1 idle lifecycle with cooperative stop, strict Serde/TOML versioned config, Clap CLI, tracing, bounded 64 KiB config and negative tests. No network, enrollment, inventory, SQLite or screen capture.
- Added experimental Linux systemd sandboxed unit with SIGTERM handling, Windows SCM dispatcher/Stop status through the maintained `windows-service` 0.8.1 crate and separate Linux/Windows unit build workflow.
- [Rustfmt, Linux/Windows Cargo unit tests and Clippy passed](https://github.com/kunlong-luo/lanveriq/actions/runs/41146480955) with the committed root lockfile. Actual real Pop!_OS/systemd and Windows 11 SCM service install/stop, credential custody, OS privileges, linked SQLite version, endpoint TLS and any monitoring are **NOT TESTED/IMPLEMENTED**. [Manual AGT-01 acceptance](docs/testing/agent-lifecycle-manual.md).


## 2026-10-31 — AUTH-01 login abuse limits, revocable sessions and authentication audit
- Added Bucket4j 8.21.0 + Caffeine 3.3.1 with bounded local rate-limit state for canonical login and transport peer, a JDK two-permit concurrent Argon2id budget, plus request-size guard and generic 429 responses; never trust user-supplied forwarded IP headers.
- Added Flyway V5 `users.session_version` and separate append-only `admin_auth_log`; browser auth session checks live disabled/version state, internal group-scoped owner-only revocation transaction increments the epoch and audits allowed action. Failed/unknown login audit stores no submitted user identifier.
- Login/out audit handlers fail closed for unavailable audit DB (including session invalidation on a failed successful-login audit). Source unit tests cover rate limits, padded-name quota separation, concurrency permit cleanup, epoch changes, group revocation, audit and malformed traffic.
- **Evidence boundary:** Gradle unit tests/bootJar can verify source behavior, not actual PG18 V1–V5 migration or concurrent DB changes, live Caddy/TLS/CSRF/browser behavior, device sessions, audit retention, or real monitored endpoints. Console login stays OFF by default.



## 2026-10-30 — AUTH-01 staged console browser sessions (disabled by default)
- Added conditional Spring Security native console-only form login/logout, HttpSession CSRF JSON bootstrap and session fixation rotation, strict exact HTTPS Host/Origin/Fetch-Site checks ordered BEFORE CSRF/logout/login, and Secure/HttpOnly/SameSite=Strict `__Host-` cookie configuration (15-minute inactivity timeout).
- Feature flag `LANVERIQ_CONSOLE_AUTH_ENABLED=false` stays off; no device, monitoring or WSS routes are opened. Unit tests cover malformed origins, cross-site and duplicate headers, missing CSRF bootstrap and no-store response. See [manual acceptance plan](docs/testing/console-session-manual.md).
- Real PG18, Caddy hostname/SNI/mTLS, runtime browser sessions, login throttling, security audit, disabled-session invalidation and rate-limited login **have not been tested or completed**.


## 2026-10-30 — AUTH-01 offline first-admin bootstrap
- Added explicit opt-in non-web Spring Boot CLI that uses an interactive terminal password prompt (not shell/env), Argon2id through Spring Security, a PostgreSQL owner-role row lock and a single JPA transaction for the first administrator, group, assigned owner scope and audit log. There is no default admin and no automatic `internal_operational` data grant.
- Added Java 25 unit tests for canonical input/secret redaction, missing confirmation, non-web-only restrictions, first-admin existence guard, locking order and audit. [Build + unit CI passed](https://github.com/kunlong-luo/lanveriq/actions/runs/41067769799); a separate subsequent fix prevents HTTP filter bean construction in offline mode.
- Removed `final` from Spring `@Transactional` first-admin and group-scope services to permit class-based transaction proxying. [Java 25 AOP commit/rollback tests passed](https://github.com/kunlong-luo/lanveriq/actions/runs/41068354128) alongside unit tests and `bootJar`; actual database behavior remains untested.
- PostgreSQL 18 migration/transaction concurrency, CLI process execution, real browser sessions, Caddy/TLS and WSS are **not yet verified**. The PG18 manual runbook now includes a separate synthetic first-admin acceptance checklist.


## 2026-10-30 — AUTH-01 internal administrator identity and group scope
- Added Spring Data JPA read-only user entity/repository, Spring Security `DaoAuthenticationProvider` and Argon2id password hashes (64 MiB/3 iterations); no default credentials, plaintext fallback or publicly enabled login.
- Added device-group-scoped `AdminScopeService` that queries live active user/role/group state, with separate `internal_operational` grants (Flyway V4; none seeded) for raw IP/MAC and other sensitive network detail. Added authenticated-principal-only internal token issuance facade.
- [Java 25 real unit CI and Boot JAR compilation passed](https://github.com/kunlong-luo/lanveriq/actions/runs/41066726762), including DAO provider, invalid credentials, disabled users, role changes and forged principal negative checks. PostgreSQL 18 V1–V4 migrations and live admin login/TLS/CSRF/WSS remain **NOT TESTED**.


## 2026-10-30 — SRV-02 pending enrollment and CSR validation
- Added maintained Bouncy Castle 1.86 PKCS#10 parsing and P-256/SHA-256 CSR signature validation; malformed, wrong-key and unsupported-curve requests are rejected in JUnit tests.
- Added one-transaction JPA PostgreSQL token consume (conditional UPDATE), pending server-side UUIDv7 device insert, public CSR digest/SPKI insert and Flyway V3 uniqueness/size checks. No raw JDBC, homegrown CSR parser, live TLS endpoint or certificate issuer.
- [Java 25 CI compilation, JUnit and bootJar passed](https://github.com/kunlong-luo/lanveriq/actions/runs/41065309965). PostgreSQL migrations, concurrency and failed-transaction rollback are **NOT TESTED**. The manual PG18.6 script now checks V1/V2/V3, but still has **not been executed**.

## 2026-10-30 — SRV-02 internal identity and issuance slice
- Added V2 PostgreSQL admin users, roles, group scope and admin access audit schema with **no default administrator**; Flyway V1/V2 have **not** been run against a real PG18 instance.
- Added secure 256-bit one-use pairing token issuance through a group-authorized JPA native SQL operation, with hashed verifier storage, 1–15-minute expiry, audit and fail-closed handling. The token is **not** an Agent credential and no HTTP enrollment/CSR/mTLS activation or consumer endpoint exists yet.
- [Java 25 JUnit + Boot JAR packaging CI passed](https://github.com/kunlong-luo/lanveriq/actions/runs/41061058441). Added a separately run [synthetic manual PG18.6 smoke check](docs/testing/server-postgres18-manual.md); its startup, SQL and authorization results are **NOT TESTED** until observed on an actual Docker/JDK25 host.


## 2026-10-30 — V0.1 implementation begins (SRV-01)
- D0 technical baseline approved for V0.1 coding only; real employee-data collection and live pilots still require separate authorization and security/platform evidence.
- Added `apps/server` Java 25 + Gradle 9.8.2 Wrapper (official JAR checksum verified), Spring Boot 4.1.2, Spring Data JPA/Hibernate, Flyway/PostgreSQL 18 foundation and deny-by-default Spring Security configuration.
- Added V0.1 server-received heartbeat connectivity classifier with meaningful JUnit unit tests and a unit-only GitHub Actions workflow. **JDK 25 unit CI passed** ([run 41058974204](https://github.com/kunlong-luo/lanveriq/actions/runs/41058974204)), including Java/test compilation and JaCoCo. Actual PostgreSQL 18 migration/`bootJar` and mTLS enrollment are **not yet verified**; endpoint telemetry and dashboard are not implemented.


## 2026-10-30 — final documentation closure audit
- Audited 90 existing Markdown files (446 local links), 20 Mermaid source pages and 8 SVG concept images structurally; [final closure report](docs/governance/final-closure-review-2026-10-30.md). No real browser/OS/build test claims.
- Reconciled event `schema_version`/UUIDv7 fixtures, SQLx SQLite migration state, health-versus-IP/MAC permissions, silently lost WSS hints and timed REST reconciliation, V0.3 UTC media frame authorization, and separated enrollment/browser/mTLS PKI design **pending D-07 sign-off**.


## 2026-10-30 — Ecosystem reuse decision
- [ADR-0007](docs/architecture/adrs/0007-mature-ecosystem-first.md) selects mature third-party libraries over custom SDKs/async SQLite workers. Rust Agent chooses **SQLx 0.9 SQLite** (single-connection write pool and built-in migrations); React uses **Orval-generated OpenAPI types/Query hooks**. Reconciled architecture and plans; no application code has been compiled.

## 2026-10-30 — JPA-only application persistence (maintainer direction)
- Selected Spring Data JPA / Hibernate ORM as the **only direct application DB access layer**, with PostgreSQL-specific native SQL via JPA `EntityManager` or repository `@NativeQuery` for transactional event ingest/inventory revisions; removed the **direct** `spring-boot-starter-jdbc` choice. PostgreSQL JDBC driver/HikariCP still underpin Hibernate transitively; Flyway continues schema migrations. This is a documentation decision, not compiled code.

## 2026-10-30 — Gradle backend selection (maintainer direction)
- **Gradle Groovy DSL replaces Maven** for the Java backend. Version baseline: Gradle 9.8.2, Java 25 LTS, Spring Boot 4.1.2, dependency-management 1.1.7, JaCoCo 0.8.16.
- Added [backend Gradle reference](docs/development/backend-gradle-reference.md), removed Maven/POM reference, selected minimal JDBC/REST/WSS/Flyway/Actuator/security dependencies rather than copying Kafka/Redis/InfluxDB/JPA/geo/OTel from an unrelated IoT build; **no code or built dependencies yet**.

## 2026-10-30 — document consistency audit
- Reconciled V0.1 base RBAC, Monitoring Center, recording review-case APIs, agent/DB authorization arrows, capture and source-gap milestones, revised segment trial and inventory flow.
- Statically reviewed the 83 existing Markdown docs, 20 Mermaid diagrams and 8 SVG assets; recorded remaining non-rendered/manual and organizational decisions in [audit report](docs/governance/documentation-review-2026-10-30.md).


All notable project changes will be documented here. No product binaries or running services exist yet.

## [Unreleased] — documentation planning baseline, 2026-10-30

### Added
- English README and Chinese project introduction.
- Product requirements, user journeys, nonfunctional targets and acceptance criteria.
- LAN system architecture, component boundaries, PostgreSQL schema and SQLite/WAL agent outbox design.
- Device enrollment and event API contracts, platform compatibility matrix, later recording design.
- Security/privacy, device authorization, threat model, retention and operational runbooks.
- Development roadmap, issue-sized backlog, unit test strategy and docs-first gate.
- Architecture decisions for monorepo, databases, disclosed recording and rapid main-first development.
- Pagevela architecture reference and selective transfer matrix.
- Detailed atomic event delivery and reinstall identity rules, organizational policy lifecycle, permission matrix, health/gap semantics, v0.1 manual acceptance and implementation sequence.
- Initial polling-first draft was **superseded** by v0.1 REST + authenticated WebSocket dashboard notifications, retaining REST for Agent durability; new WS contract and ADR-0005/0006.
- Eight coordinated self-contained 16:9 SVG stakeholder infographics (overview, data flow, tech stack, capabilities, enrollment, sample dashboard, offline recovery, privacy) with visual design guide, asset inventory and terminology matrix.
- Device inventory V0.1 design for OS/CPU/RAM/disks, multi-NIC MAC and IPv4/IPv6, device identity separation, metric sampling, restricted access and retention; inventory ER/data-flow diagrams and acceptance checklist.
- 16 GitHub Mermaid diagram pages with linked index: system/component/deployment, registration, durable event delivery, WS flow/lifecycle, PostgreSQL/SQLite models, RBAC, capture, coverage, trust, retention and dashboard navigation.
- Cross-platform/WS test matrix, troubleshooting and release runbooks.
- Documentation-only internal link and Mermaid fence checks; no actual diagram rendering, runtime testing or product code.

### Policy
Direct main commits during pre-release; CI only runs unit tests once code exists; no E2E until a later quality stage. **No functional implementation has begun.**
