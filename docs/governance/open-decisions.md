# Open decisions and pre-code sign-offs

**Status:** design proposals are written; product owner review is not yet recorded. Do not mark these as approved merely because a document is committed.

| ID | Decision | Current assumption | Required by |
|---|---|---|---|
| D-01 | Deployment jurisdiction, lawful purposes and employee notice | company devices, prior specific notice; jurisdiction not defined | pilot with employee data |
| D-02 | First supported Linux distribution, compositor and CPU arch | **Pilot candidate supplied:** Pop!_OS 24.04 LTS x86_64, KDE Plasma 5.27.12/KWin, kernel 7.2.6-76070206-generic, RTX 4050 Laptop; `XDG_SESSION_TYPE` still unknown, X11/Wayland must be validated separately | v0.1 deployment |
| D-03 | Allowed application fields and Windows/Linux event source list | coarse app name, scope allowlist, no URL/title by default | v0.2 |
| D-04 | Recording policy, OS prompts and user status UX | opt-in later, visible state, fail closed | v0.3 |
| D-05 | Actual video/event retention and backup periods | event 30d, video 14d drafts | real data pilot |
| D-06 | Admin MFA rollout and approved recording access workflow | MFA required before real-data recording | v0.3 |
| D-07 | Server certificate provisioning, device auth and key storage | proposed Caddy internal CA with verified root distribution + unique per-device proof-of-possession; exact rotation mechanics pending | v0.1 |
| D-08 | Software distribution license and dependency audit | unlicensed public repo until formal choice | first public release |
| D-09 | Storage quotas & capture codec from measured pilot | SQLite WAL, video H.264 initial trial | v0.3 |
| D-10 | Production testing strategy | CI unit-only now, integration/E2E before v1.0 | v1.0 |
| D-11 | Atomic batch envelope and event source whitelist | v0.1 all-or-nothing; frozen wire names required | before v0.1 code |
| D-12 | Policy validity and client behavior during disconnect | last approved scope only; sensitive collection fails closed | before v0.2 |
| D-13 | Agent local DB damage, replacement device workflow | new device identity after lost local DB; explicitly audit link | before v0.1 |
| D-14 | Administration scope grants and case approval | role + device group + purpose + time; no default recording access | before v0.2 |
| D-15 | WebSocket handshake/session lifecycle and initial subscription cap | proposed same-origin Secure HttpOnly cookie + strict Origin + <=5 connections per user + <=10 device IDs; verify exact handler mechanics | before WS-01 |
| D-16 | WSS push timing and failure thresholds | WSS metadata-only, best-effort, **post-subscription REST reconcile**, proposed <2s normal push, limits to validate | v0.1 manual acceptance |
| D-17 | Device hardware/IP/MAC inventory field scope | V0.1 CPU/RAM/disks/OS, permitted NICs and multiple IPv4/IPv6; no hardware serial/TPM by default; last-seen and inventory freshness distinct | before v0.1 schema |
| D-18 | Inventory retention and sensitive network address exposure | Current record plus proposed 7-day IP/MAC change log; role/group restrictions and no WSS address payload | before real managed endpoints |
| D-19 | Inventory snapshot reliability | Stable snapshot_id, **monotonic inventory_revision**, digest, bounded retry and no older snapshot overwrite; local latest pending snapshot on outage | before v0.1 Agent/API code |
| D-20 | Baseline V0.1 authorization design | Admin session, device-group scoped REST inventory/monitoring and WSS; validate Origin/CSRF, revoke credentials; fine-grained audit/video access staged separately | before V0.1 auth code |
| D-21 | Recording access approval and storage lifecycle | Case approval must predate playback; independent approver, byte-range time scoping and verified retention/delete; no direct file/NAS access | before V0.3 |
| D-22 | Initial client compatibility/protocol freeze | Stable field limits, optional/missing capability states, inventory revisions, API error codes, 30s heartbeat and 60–120s metrics require tested platform fixtures | before V0.1 client/server rollout |

## Decision process
Record who approved, date, consequences and relevant ADR/PRD changes. Any deployment collecting real employee data requires D-01, D-03, D-05 and D-07 resolved even if code exists. The documentation gate can be **ready for technical review** while legal/operational approvals remain open.

## Version facts
Exact crate/package patch versions should be pinned from authoritative official release notes when code starts; a planned major line is not proof a particular dependency is already validated.
