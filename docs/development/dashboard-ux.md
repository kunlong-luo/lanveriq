# Management dashboard UX contract (planning only)

**Reference:** adopt Pagevela's feature-focused React components, cohesive design tokens and accessible shared UI; do not copy its resume-specific editor/PWA state model.

## Visual identity
Follow the [unified infographic/style guide](../visuals/STYLE_GUIDE.md) for brand colors and terms, but use actual accessible React components rather than treating the SVGs as UI source. The [concept dashboard](../visuals/assets/06-dashboard-concept.svg) uses **sample data**, not a working UI screenshot. See [frontend design dependencies](frontend-design-system.md) for planned UI/icon/animation choices.

## Page map / release
| Page | v0.1 | v0.2 | v0.3 |
|---|---|---|---|
| Sign in + admin bootstrap | Yes | strengthen role UX | policy-dependent MFA for restricted review |
| Overview: total, online/stale/offline, error/gap | Yes | more health detail | recorder health separate |
| **Monitoring Center /monitoring**: dedicated device/collector health, backlog, freshness and WSS state | **Yes** | richer source details | recording state only; **no live screen tiles** |
| Devices: search, group, last seen, Agent version | Yes | collector capabilities | screen recording status |
| Device details: health, identity, queue, **CPU/RAM/disks/OS/NIC/IP/MAC inventory** | Yes | activity/audit/gaps, GPU/security (optional) | recording sessions tab |
| Enrollment wizard / revoke credentials | Yes | device group reassignment | none |
| **Audit Center /audit**: activity timeline and scoped system events | — | **Yes** | contextual video links only with approval |
| Policy / admin roles / access log | Basic | Yes | recording access cases |
| **Recording & Playback Center /recordings + /review-cases** | — | — | **session catalog, case approval, gap-aware playback and access audit** |

## Information architecture
Overview → **Monitoring Center (V0.1)** → Devices / device detail (Hardware, Network, Health) → **Audit Center (V0.2)** → **Recording & Playback Center + Review Cases (V0.3)** → Settings / Admin Access Audit. Separate access control applies to every server-side route; hidden navigation alone never grants/revokes rights.

## Device asset view
V0.1 Device Detail includes a hardware/network tab: CPU model and cores, RAM installed/available, disks and free space, OS distribution/build/kernel, NIC list with multiple IPv4/IPv6 addresses, MAC when allowed, device ID and optional asset tag. Display inventory observation time separately from live heartbeat time. See [device inventory](../architecture/device-inventory.md) and [Monitoring Center](monitoring-center.md). 

## State ownership
- TanStack Query owns server state/cache and refresh timers.
- Zustand (if needed) owns local UI state such as table sorting, selection and dialogs, **not** a duplicate of device/event data.
- Each page uses a typed REST API client; UI never contacts PostgreSQL/SQLite directly or carries secrets in browser storage.

## Safety and accessibility
- All rows and video actions must be authorized on server; disable controls for unavailable/unsupported features, without misleading green badges.
- Use "No observations recorded" rather than "No activity"; clearly label clock gaps and recording unavailability. Avoid employee productivity scores.
- Accessible controls, keyboard focus, meaningful confirmation on policy expansion/revocation, consistent Pagevela-inspired shared components.
- Do not insert private screen previews into general overview tiles by default. The video area is restricted and requires case approval.

## Early performance rules
One bounded paginated REST device list and **one same-origin WSS connection** after authenticated login. First fetch authorized device IDs, subscribe, **refetch REST after `subscription.accepted`** and reconcile hints received during the fetch. Invalidate/refetch TanStack Query on later scope-filtered notifications; repeat the post-subscription refresh on WS reconnect. Show a clear reconnecting/degraded indicator; **low-frequency REST reconciliation (pilot 60–120 seconds while the page is visible) and tab-resume refresh are required safeguards** because a WSS hint may be silently lost without a disconnect. WSS remains the primary low-latency notification mechanism; do not poll unlimited event timelines. No SSR, CDN, external analytics or PWA background-sync dependency. See [WS contract](../architecture/websocket-contract.md).

## UI unit-test targets
WSS lifecycle: connect, get authorized device IDs, subscribe, and fetch the **post-subscription** REST snapshot; unit-test a change occurring between initial device-ID lookup and subscription acceptance. Notification invalidates query, reconnect repeats resubscribe + REST refetch, **silent lost hint with still-open WSS is caught by timed/tab-resume REST reconcile**, no live data shown from unauthorized subscription, session expiry indicates login required. Only unit tests are run in early CI.
Online/stale/offline formatting; empty/unavailable states; scope-gated actions; query error/loading; no cross-device display leakage; warning before revoke; no playback affordance without a case grant.
