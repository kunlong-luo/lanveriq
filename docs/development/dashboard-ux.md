# Management dashboard UX contract (planning only)

**Reference:** adopt Pagevela's feature-focused React components, cohesive design tokens and accessible shared UI; do not copy its resume-specific editor/PWA state model.

## Visual identity
Follow the [unified infographic/style guide](../visuals/STYLE_GUIDE.md) for brand colors and terms, but use actual accessible React components rather than treating the SVGs as UI source. The [concept dashboard](../visuals/assets/06-dashboard-concept.svg) uses **sample data**, not a working UI screenshot. See [frontend design dependencies](frontend-design-system.md) for planned UI/icon/animation choices.

## Page map / release
| Page | v0.1 | v0.2 | v0.3 |
|---|---|---|---|
| Sign in + admin bootstrap | Yes | strengthen role UX | policy-dependent MFA for restricted review |
| Overview: total, online/stale/offline, error/gap | Yes | more health detail | recorder health separate |
| Devices: search, group, last seen, Agent version | Yes | collector capabilities | screen recording status |
| Device details: health, identity, queue | Yes | activity/audit/gaps tabs | recording sessions tab |
| Enrollment wizard / revoke credentials | Yes | device group reassignment | none |
| Activity timeline | — | Yes | contextual video links only with approval |
| Policy / admin roles / access log | Basic | Yes | recording access cases |
| Video playback | — | — | case-scoped access and audited downloads |

## Information architecture
Overview -> Devices -> Device detail (Health / Activity / Security / Gaps / Recording) -> Investigations -> Settings (Groups / Policy / Users / Retention) -> Admin Audit.

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
One bounded paginated REST device list and **one same-origin WSS connection** after authenticated login. Invalidate/refetch TanStack Query on scope-filtered status notifications, coalesce bursts and **always refetch on WS reconnect**. Show a clear reconnecting/degraded indicator; optional slow fallback polling may be added only for resilience, not as the primary v0.1 transport. No SSR, CDN, external analytics or PWA background-sync dependency. See [WS contract](../architecture/websocket-contract.md).

## UI unit-test targets
WSS lifecycle: connect + initial REST snapshot, subscribe to authorized IDs, status notification invalidates query, reconnection triggers REST refetch, no live data shown from unauthorized subscription, session expiry indicates login required. Only unit tests are run in early CI.
Online/stale/offline formatting; empty/unavailable states; scope-gated actions; query error/loading; no cross-device display leakage; warning before revoke; no playback affordance without a case grant.
