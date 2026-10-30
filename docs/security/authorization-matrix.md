# Server-side permission matrix

**Status:** v0.x design. Authorization is enforced on every API request, never by menu visibility, client-supplied role or LAN IP. Device group and purpose/time-window restrictions further limit any role.

Legend: V=view, M=manage, E=export, C=case-required view, —=deny. An authenticated user's role does not imply unrestricted device access.

| Resource / action | Owner/security admin | IT operator | Security auditor | Recording reviewer | Device user |
|---|---|---|---|---|---|
| Device health and **non-sensitive hardware summary** (assigned group) | V | V | V within scope | V within scope | own local status |
| Full network inventory (MAC, private IP, interface list, asset tag) | **V only with explicit `internal_operational` grant** | **V only with assigned group + `internal_operational` grant** | — unless separately granted | — | only policy-approved own local view |
| Create/revoke pairing and device keys | M | M if delegated | — | — | — |
| Inspect detailed audit/activity | V with logged reason | — by default | V with scope/reason | — | — |
| Change collection policy | M + organizational approval | — | — | — | — |
| Manage roles/device groups | M | — | — | — | — |
| Query collection gaps | V | V (health only) | V | V for approved case | own capture status |
| Request recording access case | request | — | request | request | — |
| Approve recording access case | M **only for an independent authorized approver** | — | — | — | — |
| Play/download recording | **C, not automatically** | — | — unless explicitly granted | C | — |
| Export event evidence | E with recorded purpose | — | E if separately authorized | — | — |
| Read admin access audit | V | — | V if independently authorized | — | — |
| Delete/retention policy | M with audit | — | — | — | — |

## D-17/D-18/D-20 V0.1 selected technical authorization baseline

**Collection eligibility:** Only explicitly enrolled, managed, previously notified organization endpoints. V0.1 permits device identity, declared OS/CPU/RAM/volume capacity, Agent health, bounded network interface/IP/MAC diagnostics **only where the organization's approved notice and purpose allow them**. Never collect OS user identities, browser history, screen contents, serial/TPM identifiers, keystrokes or unapproved application/audit sources. Missing permission means `not_collected` with honest capability state, not a workaround or fabricated data.

**Proposed V0.1 read policy, independently enforced per REST request and WSS dispatch:**

| Data/action | Required authorization | Payload policy |
|---|---|---|
| Assigned-device online/stale/offline + Agent/backlog + OS/CPU/RAM summary | authenticated admin/IT role **and** server-verified device-group membership | summary only; no raw IP/MAC, asset tag, account name or cross-group counts |
| Full IP/MAC/NIC/address inventory, asset tag and server-observed remote IP | permitted operator/security role **and** assigned group **and** explicit `internal_operational` grant | bounded detailed REST response only; audited access and export; **never raw network addresses in WSS** |
| Device enrollment, revocation, role/group changes | security admin or explicitly delegated IT operation | state-changing CSRF-protected REST; audited; no write over WSS |
| V0.1 operational `collection_gap` health summary | assigned IT operator group | no detailed sensitive audit-event payload |
| V0.2 audit/activity event payload, any recording/playback | explicitly **not available** in V0.1 | server denies, even if endpoint sends unsupported fields or frontend draws a hidden route |

**D0 design assumptions, NOT an approved real-data retention policy (D-18):** current device inventory until replaced/retired under approved asset policy; up to **7 days** of *justified* network/IP/MAC diagnostic changes (do not create that history if not approved); heartbeat + host samples proposed 7 days; collection-gap and security-access log retention follow [retention table](../security/data-retention.md). Require explicit legal/organizational authorization before actual employee data, plus protected backups, expiry and restore-time purge enforcement. These values are candidates, not law, consent or production defaults.

**Privacy/authorization negative cases:** wrong group must not see device existence, counts or network details; health-only role cannot read MAC/IP via pagination/filter/export, error bodies or WSS; revoked sessions must stop receiving hints; device-issued client credentials cannot use browser admin APIs; previously approved operator loses sensitive access promptly upon group/grant revocation. Data classification (`internal_operational`) is a **server-enforced predicate**, not a React visibility flag.

Technical baseline recorded in [D-17/D-18/D-20](../governance/open-decisions.md) and [D0 gate](../governance/documentation-gate.md). V0.1 implementation is allowed after recorded D0 approval, **but no real employee-data collection may start** until the independent organizational/legal notice, purposes, retention and access-grant checks are accepted. Production privacy defaults must be approved separately.

## V0.1 baseline authorization
**Before any V0.1 dashboard release**, authenticate administrator sessions and enforce assigned device-group scope on REST inventories, monitoring counts, device details and WSS subscriptions. Role-scope checks cannot be deferred to the V0.2 detailed-audit phase. Private IP/MAC details require an additional `internal_operational` permission rather than mere heartbeat/overview visibility. Do not send raw NIC/IP/MAC through WSS. See [device inventory](../architecture/device-inventory.md).

## Authorization predicate
For sensitive actions: authenticate principal -> check role permission -> check active device group scope -> check data category -> check time range -> check policy/approval/reason -> audit decision -> return minimal data. An owner cannot silently bypass an organizational recording approval requirement. For V0.3 review cases, `approved_by` must identify an authorized person **other than the requester**, and the server must reject self-approval regardless of role.

## Required control behavior
- For an identified device outside a principal's assigned scope, return the same non-enumerating `404 not_found` as for a nonexistent device; use `403 forbidden` when a principal is authenticated and entitled to the device but lacks the separate requested sensitive-data permission. Apply consistently to REST, detail views, cursor pages, error envelopes and summary counts. Audit the real denial reason server-side without exposing hidden identities.
- Cursor-based pagination also filters by scope before rows are returned; no cross-group total counts or raw media paths.
- Video access uses server-mediated, expiring authorization, verifies **approved UTC case time → allowable media frames/segments**, and checks each requested byte range. **An HTTP Range by itself cannot authorize frames outside the approved time window.** Every access/download is recorded; no direct anonymous NAS link.
- Session cookies HttpOnly/Secure/SameSite; CSRF defenses on state-changing browser requests, and no tokens in localStorage.
- Role/group changes invalidate or re-evaluate existing access grants promptly. Revoke credentials closes ongoing session/stream where feasible.
- Administrative decisions and denied access must be in append-only audit records with protected retention.

## Test matrix
Anonymous read denied; wrong-group device read denied; operator event export denied; auditor playback denied; reviewer without case denied; expired case denied; owner without recording approval denied; requester attempting to approve own review case denied; device credential cannot call admin API; old/revoked credential denied; modified cursor cannot access another group.
