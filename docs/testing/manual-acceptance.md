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
| MAN-005a | Admin browser access and first-time pairing without existing Agent client cert | Works **only** on their intended protected routes; unrelated enrolled-Agent API is denied without valid client certificate once mTLS is selected |
| MAN-005b | Forged forwarded client-cert identity and revoked/expired/rotated Agent certificate | Cannot authenticate or upload; Spring checks bound active device record, not arbitrary client header |
| MAN-005c | Offline CSR signing/registration attack: altered CSR SPKI, leaf SAN/device ID, wrong issuer, serverAuth-only EKU, CA leaf or reused fingerprint | Console-admin certificate registration rejects all mismatches; private keys never enter server; pending device cannot heartbeat |
| MAN-005d | Host/SNI mismatch, reused HTTP/2 cross-host TLS connection or duplicate client-identity request header | Agent ingress/Spring reject without the verified permitted TLS identity regardless of Host or header supplied by caller |
| MAN-006 | Heartbeat start/stop | online -> stale -> offline accurate |
| MAN-007 | Disconnect LAN, generate synthetic events, reconnect | buffered data transferred once |
| MAN-008 | Stop server after PG commit, before client sees response | safe dedup on retry |
| MAN-009 | Power off/restart endpoint mid-queue | no committed rows silently lost |
| MAN-010 | Corrupt/unavailable local SQLite | degraded, diagnostic and no false healthy |
| MAN-011 | Revoked device attempts upload | refused and recorded |
| MAN-012 | Cross-device/cross-group API query | rejected |
| MAN-013 | Postgres backup and isolated restore | expected synthetic data restored |

## D0 contract/security negative scenarios (planned, NOT TESTED)
Use [D0-SEC-01–05, D0-PROTO-01–03, D0-PRIV-01, D0-OS-01](../governance/d0-decision-package-2026-10-30.md) to verify the exact *approved* API/CSR/CA/proxy/fingerprint and role-projected fixtures after code exists. The proposed manual CSR issuance must be exercised end to end, including response loss, premature activation refusal, expired/rotated device certs, untrusted forwarded headers and actual SSL transport isolation. Do not pass any of these on a static spec or a mocked Caddy process. Record actual Caddy, Rust TLS and Spring versions plus authorized pilot OS details without real employee artifacts.

## v0.1 device inventory checks
Use [INV-01 through INV-16](device-inventory-checklist.md): CPU model, RAM, OS/kernel, volumes, multi-NIC IPv4/IPv6/MAC, changing DHCP, stale/absent data, policy scope and 30s heartbeat versus 24h inventory. These checks have not been executed. Do not attach real internal addresses to public issue reports.

## v0.1 WSS manual checks
Also run [MAN-WS-01–09](test-matrix.md) with synthetic devices: login/Origin, status push, router/browser disconnect, REST resync, access-scope changes and server restart. Record actual outcomes separately; none have been executed yet.

## v0.2 checks
Scoped application metadata on supported OS; Windows and Linux allowlisted security events; unsupported Wayland activity shown honestly; clock drift; bounded event pagination; role permission matrix; retention deletion and access audit. Nothing may silently collect URL contents, clipboard, keyboard or messages.

## v0.3 recording safety and reliability
Explicit policy/notice and OS grant; visible capture state; denied portal permission fails closed; Wayland consent/token restore tested without bypass; lock/logout/suspend gap; clean closed-segment upload; checksum mismatch rejection; full disk clean stop; unauthorized playback denied; **requester self-approval rejected even for admin role**; independently approved playback audited; retention deletion. Record separate OS/compositor outcomes.

## Cross-platform matrix
See [platform/protocol test matrix](test-matrix.md) for Windows 11, Linux X11/Wayland and WS unit/manual cases.

## Exit criteria
A unit CI green badge **does not** substitute for these manual checks. Before processing real employee data, jurisdictional notice, policy, retention and trusted device identities must be approved. Before claiming production v1.0 quality, add formal integration/platform/E2E validation and a security review.
