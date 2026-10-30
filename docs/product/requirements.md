# Product requirements document (PRD)

**Status:** Draft for review, no code | **Product:** LAN-only endpoint management and scoped activity audit for 1–10 authorized company devices.

## Problem and outcomes
Small offices need a simple private-server tool to review endpoint availability, declared application activity and selected security events without external clouds, a message broker or a complex distributed deployment. The product favors predictable installation, lightweight agents and recoverable event delivery.

## Personas
1. Device user: knows collection scope; sees policy and capture status; can report an issue.
2. IT operator: pairs, revokes, updates and troubleshoots managed endpoints.
3. Security auditor: reviews approved device/time-range security and activity metadata.
4. Recording reviewer (later): accesses approved recorded periods with case reason.
5. Security admin: manages policy, scopes, RBAC, retention and audit logs.

## Functional requirements
| ID | Requirement | Milestone |
| --- | --- | --- |
| FR-01 | Single LAN server, administrator bootstrap, local-only runtime | v0.1 |
| FR-02 | Time-limited, one-use device enrollment and revocation | v0.1 |
| FR-03 | Authenticated heartbeat, OS/capability, health, last seen | v0.1 |
| FR-04 | Server-derived online/stale/offline device inventory | v0.1 |
| FR-05 | Bounded local SQLite outbox with replay after LAN outage | v0.1 |
| FR-06 | Idempotent event-batch ingestion with server acknowledgments | v0.1 |
| FR-07 | Scoped application activity and idle-time metadata | v0.2 |
| FR-08 | Allowlisted Windows Event Log and Linux audit events | v0.2 |
| FR-09 | Device/date event timeline, search, pagination, time zones | v0.2 |
| FR-10 | RBAC, device scope and sensitive-read/export audit log | v0.2 |
| FR-11 | Configurable event retention and disk-quota alerts | v0.2 |
| FR-12 | Optional disclosed, OS-authorized recording and indicator | v0.3 |
| FR-13 | Recorded segment integrity, upload, controlled playback | v0.3 |
| FR-14 | Report collection/recording gaps and unsupported platforms | v0.3 |
| FR-15 | Stable Windows and Linux packages, upgrade/rollback | v1.0 |

## Non-goals
Covert monitoring, hidden installs, permission bypass, keylogging, password/clipboard/message collection, webcam/microphone, arbitrary remote-shell, AI/LLM, public SaaS, enterprise MDM and high-volume video analytics. Having a company-owned device does not eliminate privacy obligations.

## Acceptance constraints
- Pilot on one Windows 11 device and one supported Linux desktop; all features must report their actual capability state.
- Enroll → heartbeat → disconnect → local queue → reconnect → duplicate-safe server delivery is reproducible.
- Staff receive clear prior notice and can see when optional screen recording is active.
- Admin role restrictions protect device-specific queries, video, exports and policy changes.
- No claims of perfect 24-hour visibility during suspend, logout, permissions denial, failures or offline time.
- Early CI only runs unit tests; separate targeted manual security and recovery checks are mandatory before deployment.

## Traceability
Every FR maps to a milestone item and a unit/manual acceptance method. Unresolved decisions and risks are tracked in [open decisions](../governance/open-decisions.md). No product code until [docs gate](../governance/documentation-gate.md).
