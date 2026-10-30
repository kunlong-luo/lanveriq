# Cross-platform and protocol verification matrix

**Status:** planned checks, **not executed**. During early v0.x, **only unit tests run in GitHub Actions**. All real OS/browser/LAN validation below is **manual** and tracked in [Manual Acceptance](manual-acceptance.md). E2E automation is postponed.

## Platform coverage
| Case | Windows 11 x64 | Ubuntu LTS x64/X11 | Ubuntu LTS x64/Wayland | Method / milestone |
|---|---|---|---|---|
| Agent native service starts/stops | Planned | Planned | Planned | Manual v0.1 |
| CA root/hostname verified, invalid trust refused | Planned | Planned | Planned | Manual v0.1 |
| Device pairing/revoke and new ID on reinstall | Planned | Planned | Planned | Unit logic + manual v0.1 |
| Heartbeat online → stale → offline | Planned | Planned | Planned | Unit clock + manual v0.1 |
| SQLite WAL queue persists through restart | Planned | Planned | Planned | Rust unit + manual v0.1 |
| Event upload timeout → safe duplicate retry | Planned | Planned | Planned | Rust/Java unit + manual v0.1 |
| Browser WSS status & scoped subscription | Planned | Planned | Planned | Spring/React unit + manual browser v0.1 |
| Windows allowlisted Event Log | Planned | N/A | N/A | Unit + manual v0.2 |
| Linux auditd/journal event | N/A | Planned | Planned | Unit + manual v0.2 |
| Application name/active interval | Planned | Planned | **Unverified; may be unsupported** | Manual v0.2 |
| Screen capture & disclosure | Planned v0.3 | Planned v0.3 | **Portal-specific, unverified** | Permission manual v0.3 |
| Screen lock/logout creates gap | Planned v0.3 | Planned v0.3 | Planned v0.3 | Manual v0.3 |
| Installer upgrades/uninstalls | Planned v0.4 | Planned v0.4 | Planned v0.4 | Manual v0.4 |

Actual Linux distro/desktop/compositor versions must be recorded for each test; these cells do **not** promise supported status.

## Protocol unit tests (required)
| ID | Domain | Test |
|---|---|---|
| PROTO-001 | REST | Same device/sequence/payload retried; accepted once |
| PROTO-002 | REST | Same sequence/different payload; 409 and whole batch rollback |
| PROTO-003 | Agent SQLite | Commit event + cursor together |
| PROTO-004 | Agent SQLite | Lost server response; retain and replay pending |
| PROTO-005 | REST | Revoked device cannot send events |
| WS-001 | Handshake | Anonymous, expired or foreign-origin upgrade rejected |
| WS-002 | RBAC | Wrong-group device subscription rejected without leak |
| WS-003 | RBAC | Revoked session/group change blocks subsequent push |
| WS-004 | Dispatch | No WS notification before commit or after rollback |
| WS-005 | Reliability | Reconnect calls REST refresh; dropped WS notice is safe |
| WS-006 | Backpressure | Slow client gets bounded queue, resync/disconnect |
| WS-007 | Client | Duplicate and out-of-order notifications do not corrupt state |
| WS-008 | Privacy | Notifications contain no raw window titles, video or credentials |

## Manual browser/LAN WSS scenarios
| ID | Scenario | Expected result |
|---|---|---|
| MAN-WS-01 | Dashboard opens with admin session | WSS `system.ready` and REST snapshot |
| MAN-WS-02 | Windows Agent goes offline | Server-derived offline notification; UI updates |
| MAN-WS-03 | Router disconnects browser then reconnects | bounded reconnect and REST resync |
| MAN-WS-04 | User role/device group changes live | unauthorized notifications stop |
| MAN-WS-05 | Session expires while WSS connected | connection terminated or no further notifications |
| MAN-WS-06 | Tamper Origin/host/TLS | upgrade refused; no insecure fallback |
| MAN-WS-07 | Stop/restart Spring Boot | browser reconnects; refreshes from PostgreSQL |
| MAN-WS-08 | Burst of 100 heartbeats | no unbounded push/queue growth |

## Results policy
Never mark "PASS" without recording date, OS, version, reviewer and evidence. Unit-green is not production approval. Real employee information must not be used in test fixtures or issue screenshots.
