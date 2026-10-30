# Manual acceptance checklist (no E2E automation in early MVP)

**Status:** test plan only, not executed. This is intentionally separate from GitHub CI, which will run **only unit tests** during v0.x. Keep dated evidence without actual employee information.

## Evidence template
- Case ID / milestone:
- Test date / reviewer:
- Server OS, Java version, PostgreSQL version, TLS hostname:
- Agent OS build, desktop session (X11/Wayland), Rust version, Agent version:
- Privacy policy / notice version:
- Expected behavior / actual observation:
- Outcome: PASS / FAIL / BLOCKED / NOT TESTED:
- Screenshot or synthetic log reference (no real employee activity):
- Issue / remediation owner:

## v0.1 minimum hands-on tests
| ID | Test | Expected result |
|---|---|---|
| MAN-001 | Fresh server bootstrap with synthetic admin | login and no default password |
| MAN-002 | Valid temporary enrollment on Windows | new unique identity, one token use |
| MAN-003 | Valid temporary enrollment on Linux | same logical result |
| MAN-004 | Replay used/expired token | blocked and logged |
| MAN-005 | Invalid CA/hostname | connection fails, no TLS bypass |
| MAN-006 | Heartbeat start/stop | online -> stale -> offline accurate |
| MAN-007 | Disconnect LAN, generate synthetic events, reconnect | buffered data transferred once |
| MAN-008 | Stop server after PG commit, before client sees response | safe dedup on retry |
| MAN-009 | Power off/restart endpoint mid-queue | no committed rows silently lost |
| MAN-010 | Corrupt/unavailable local SQLite | degraded, diagnostic and no false healthy |
| MAN-011 | Revoked device attempts upload | refused and recorded |
| MAN-012 | Cross-device/cross-group API query | rejected |
| MAN-013 | Postgres backup and isolated restore | expected synthetic data restored |

## v0.1 WSS manual checks
Also run [MAN-WS-01–08](test-matrix.md) with synthetic devices: login/Origin, status push, router/browser disconnect, REST resync, access-scope changes and server restart. Record actual outcomes separately; none have been executed yet.

## v0.2 checks
Scoped application metadata on supported OS; Windows and Linux allowlisted security events; unsupported Wayland activity shown honestly; clock drift; bounded event pagination; role permission matrix; retention deletion and access audit. Nothing may silently collect URL contents, clipboard, keyboard or messages.

## v0.3 recording safety and reliability
Explicit policy/notice and OS grant; visible capture state; denied portal permission fails closed; Wayland consent/token restore tested without bypass; lock/logout/suspend gap; clean closed-segment upload; checksum mismatch rejection; full disk clean stop; unauthorized playback denied; permitted playback audit and retention deletion. Record separate OS/compositor outcomes.

## Cross-platform matrix
See [platform/protocol test matrix](test-matrix.md) for Windows 11, Linux X11/Wayland and WS unit/manual cases.

## Exit criteria
A unit CI green badge **does not** substitute for these manual checks. Before processing real employee data, jurisdictional notice, policy, retention and trusted device identities must be approved. Before claiming production v1.0 quality, add formal integration/platform/E2E validation and a security review.
