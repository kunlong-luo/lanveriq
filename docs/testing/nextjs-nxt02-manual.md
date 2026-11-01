# Next.js NXT-02 — manual browser and visual parity acceptance

**Status (2026-11-01): AUTOMATED PORT VERIFIED; MANUAL BROWSER
ACCEPTANCE NOT EXECUTED.** Do not claim full production UI or a
frontend cutover from unit tests and HTTP smoke alone.

Baseline: [Next.js CI #41288117979](https://github.com/kunlong-luo/lanveriq/actions/runs/41288117979)
on Git SHA `427242286b0af9e5bb3b4d151484c47d296560f8`.
Run only the isolated `apps/web-next` candidate on localhost, with
the **existing Vite app kept intact**, against no real backends.
No administrator credentials or real employee/device records are used.

## Independent manual browser matrix

| Category | Test | Expected evidence |
| --- | --- | --- |
| Route parity | /overview, /monitoring, /devices, /devices/[id], /enrollment, /settings, /login | Correct section heading, navigation active state and content |
| No fabricated devices | Navigate all seven routes before business access | No mock-device rows; overview shows em dashes and unconnected status, not zeros |
| Browser navigation | Sidebar links, back link, refresh | No unintended login POST, APIs, websocket or telemetry |
| Auth disabled | /login on desktop and mobile | Username/password and submit disabled; no credentials or cookies sent |
| Operations disabled | Enrollment and device detail | Issue credential, refresh and revoke controls are unavailable |
| Unauthorized details | /devices/<invalid> | 404; valid UUID does not show sensitive fields or claim authorization |
| Responsive layouts | 320, 390, 768, 1024 and 1440px viewports | No clipped form/controls; table horizontal scroll only inside table |
| Keyboard | Tab, Shift+Tab, Enter, Space, Arrow keys on detail tabs | Visible focus, logical order, tab controls operable; no keyboard trap |
| Screen reader | Major routes, table, status alerts, login | Correct H1, landmarks, table caption, labels and live messages |
| Reduced motion | System reduce-motion mode | No unnecessary transitions; accessible focus indicators remain |
| Security | Network inspection | No real /api/v1, /actuator business proxy, cookie, websocket, screen or remote-control request |
| Regression | Original apps/web preview | Old Vite routes still build and work independently |

Capture sanitized per-browser/OS evidence, viewport screenshots
and any keyboard/accessibility findings without usernames, tokens,
cookies, password fields or real device/employee data. Fail closure
if any route reveals mock devices as real or makes authenticated
requests. **NXT-03 Orval transport remains separately default-OFF;
PG18, Caddy TLS and browser security acceptance remain mandatory.**
