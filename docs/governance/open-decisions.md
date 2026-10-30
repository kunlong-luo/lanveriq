# Open decisions and pre-code sign-offs

**Status:** design proposals are written; product owner review is not yet recorded. Do not mark these as approved merely because a document is committed.

| ID | Decision | Current assumption | Required by |
|---|---|---|---|
| D-01 | Deployment jurisdiction, lawful purposes and employee notice | company devices, prior specific notice; jurisdiction not defined | pilot with employee data |
| D-02 | First supported Linux distribution, compositor and CPU arch | Ubuntu LTS x64 first, X11/Wayland individually verified | v0.1 deployment |
| D-03 | Allowed application fields and Windows/Linux event source list | coarse app name, scope allowlist, no URL/title by default | v0.2 |
| D-04 | Recording policy, OS prompts and user status UX | opt-in later, visible state, fail closed | v0.3 |
| D-05 | Actual video/event retention and backup periods | event 30d, video 14d drafts | real data pilot |
| D-06 | Admin MFA rollout and approved recording access workflow | MFA required before real-data recording | v0.3 |
| D-07 | Server certificate provisioning and device key storage | internal CA + unique device keys | v0.1 |
| D-08 | Software distribution license and dependency audit | unlicensed public repo until formal choice | first public release |
| D-09 | Storage quotas & capture codec from measured pilot | SQLite WAL, video H.264 initial trial | v0.3 |
| D-10 | Production testing strategy | CI unit-only now, integration/E2E before v1.0 | v1.0 |

## Decision process
Record who approved, date, consequences and relevant ADR/PRD changes. Any deployment collecting real employee data requires D-01, D-03, D-05 and D-07 resolved even if code exists. The documentation gate can be **ready for technical review** while legal/operational approvals remain open.

## Version facts
Exact crate/package patch versions should be pinned from authoritative official release notes when code starts; a planned major line is not proof a particular dependency is already validated.
