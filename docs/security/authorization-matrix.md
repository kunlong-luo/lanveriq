# Server-side permission matrix

**Status:** v0.x design. Authorization is enforced on every API request, never by menu visibility, client-supplied role or LAN IP. Device group and purpose/time-window restrictions further limit any role.

Legend: V=view, M=manage, E=export, C=case-required view, —=deny. An authenticated user's role does not imply unrestricted device access.

| Resource / action | Owner/security admin | IT operator | Security auditor | Recording reviewer | Device user |
|---|---|---|---|---|---|
| Device inventory/health (assigned group) | V | V | V | V | own visible status |
| Create/revoke pairing and device keys | M | M if delegated | — | — | — |
| Inspect detailed audit/activity | V with logged reason | — by default | V with scope/reason | — | — |
| Change collection policy | M + organizational approval | — | — | — | — |
| Manage roles/device groups | M | — | — | — | — |
| Query collection gaps | V | V (health only) | V | V for approved case | own capture status |
| Create/approve recording access case | M / approval | — | request only | request only | — |
| Play/download recording | **C, not automatically** | — | — unless explicitly granted | C | — |
| Export event evidence | E with recorded purpose | — | E if separately authorized | — | — |
| Read admin access audit | V | — | V if independently authorized | — | — |
| Delete/retention policy | M with audit | — | — | — | — |

## Authorization predicate
For sensitive actions: authenticate principal -> check role permission -> check active device group scope -> check data category -> check time range -> check policy/approval/reason -> audit decision -> return minimal data. An owner cannot silently bypass an organizational recording approval requirement.

## Required control behavior
- API responds 403 for known out-of-scope identities; do not leak resource existence to callers outside their scope (use 404 where appropriate, consistently).
- Cursor-based pagination also filters by scope before rows are returned; no cross-group total counts or raw media paths.
- Video access uses server-mediated, expiring authorization, validates segment and requested byte range; every access/download recorded, no direct anonymous NAS link.
- Session cookies HttpOnly/Secure/SameSite; CSRF defenses on state-changing browser requests, and no tokens in localStorage.
- Role/group changes invalidate or re-evaluate existing access grants promptly. Revoke credentials closes ongoing session/stream where feasible.
- Administrative decisions and denied access must be in append-only audit records with protected retention.

## Test matrix
Anonymous read denied; wrong-group device read denied; operator event export denied; auditor playback denied; reviewer without case denied; expired case denied; owner without recording approval denied; device credential cannot call admin API; old/revoked credential denied; modified cursor cannot access another group.
