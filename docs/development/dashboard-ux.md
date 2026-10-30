# Management dashboard UX contract (planning only)

**Reference:** adopt Pagevela's feature-focused React components, cohesive design tokens and accessible shared UI; do not copy its resume-specific editor/PWA state model.

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
One bounded paginated device list; 10–30 s polling while visible, throttle when hidden; no WebSockets for v0.1. No SSR, CDN, external analytics or PWA background-sync dependency.

## UI unit-test targets
Online/stale/offline formatting; empty/unavailable states; scope-gated actions; query error/loading; no cross-device display leakage; warning before revoke; no playback affordance without a case grant.
