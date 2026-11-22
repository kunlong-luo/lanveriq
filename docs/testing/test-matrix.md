> **2026-11-22 当前判定：** 测试矩阵是计划/覆盖范围，不是 CI 实跑计数；实际 D4-C16 6/6 同 SHA 合成 CI、真实 PG18 ≥147 JUnit 与尚未完成的 Pop!_OS 实机 L3 分别查看 [CURRENT_STATUS](../CURRENT_STATUS.md)、[发布缺口](../planning/release-gap-register.md)。

# Cross-platform and protocol verification matrix

**当前状态（2026-11-21）：** 本文件是**场景与覆盖计划矩阵**，不是“所有自动化未执行”的实时结论。实际完成程度以 [CURRENT_STATUS](../CURRENT_STATUS.md)、[L1/L2/L3](strategy.md) 和每项 GitHub Actions 功能 SHA 为准。已执行：Linux 隔离 Rust→Caddy/Tomcat TLS1.3→PG18/ACK、PG18.6 Testcontainers、Next.js Chromium 等**合成**检查；最新 D4-C16 6/6 同 SHA 合成 CI PASS。**未执行/未批准：Pop!_OS 24.04 实机 L3、生产登录/设备链路、可信 FIDO MDS 官方来源和发布批准。** 下方单元/手动表格的 `Planned` 表示对应 **OS 实机和业务端到端验收未通过**，不否定已有隔离 CI。

## Platform coverage
| Case | Windows 11 x64 | Pop!_OS 24.04 主机（实际会话待核对） | 备用 X11/Wayland 会话（条件性） | Method / milestone |
|---|---|---|---|---|
| Agent native service starts/stops | Planned | Planned | Planned | Manual v0.1 |
| CA root/hostname verified, invalid trust refused | Planned | Planned | Planned | Manual v0.1 |
| Device pairing/revoke and new ID on reinstall | Planned | Planned | Planned | Unit logic + manual v0.1 |
| Heartbeat online → stale → offline | Planned | Planned | Planned | Unit clock + manual v0.1 |
| SQLite WAL queue persists through restart | Planned | Planned | Planned | Rust unit + manual v0.1 |
| Event upload timeout → safe duplicate retry | Planned | Planned | Planned | Rust/Java unit + manual v0.1 |
| Browser WSS status & scoped subscription | Planned | Planned | Planned | Spring/React unit + manual browser v0.1 |
| CPU model/cores, RAM total and disk volumes/capacity | Planned | Planned | Planned | Rust unit + manual v0.1 inventory |
| IPv4/IPv6, multi-NIC, MAC nullable and source IP split | Planned | Planned | Planned | Rust/Java unit + manual v0.1 inventory |
| Windows allowlisted Event Log | Planned | N/A | N/A | Unit + manual v0.2 |
| Linux auditd/journal event | N/A | Planned | Planned | Unit + manual v0.2 |
| Application name/active interval | Planned | Planned | **Unverified; may be unsupported** | Manual v0.2 |
| Screen capture & disclosure | Planned v0.3 | Planned v0.3 | **Portal-specific, unverified** | Permission manual v0.3 |
| Screen lock/logout creates gap | Planned v0.3 | Planned v0.3 | Planned v0.3 | Manual v0.3 |
| Installer upgrades/uninstalls | Planned v0.4 | Planned v0.4 | Planned v0.4 | Manual v0.4 |

**历史报告的候选测试设备（尚未现场确认或通过 L3）：** Pop!_OS 24.04 LTS x86_64, KDE Plasma 5.27.12/KWin, NVIDIA RTX 4050 Laptop, kernel 7.2.6-76070206-generic as reported by the maintainer. **Session protocol has not been confirmed**; 表格中 Linux 两列是条件性测试范围，**不是**两种桌面会话均已安装或验收的证据。 Record `echo $XDG_SESSION_TYPE`, portal backend and versions before marking results. These cells do **not** promise supported status.

## Device inventory unit/manual cases
See [inventory checklist INV-01–16](device-inventory-checklist.md) for hardware/NIC field accuracy, rate-limited sampling, snapshot retries, authorization and OS-specific unavailability.

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
| WS-005 | Reliability | Reconnect re-subscribes then refetches REST; dropped WS hint is eventually reconciled |
| WS-006 | Backpressure | Slow client gets bounded queue, resync/disconnect |
| WS-007 | Client | Duplicate and out-of-order notifications do not corrupt state |
| WS-008 | Privacy | Notifications contain no raw window titles, video or credentials |
| WS-009 | Initial race | State changes after initial device-ID REST read but before subscription accepted; post-subscription REST refresh sees it |
| WS-010 | Silent dropped hint | WSS remains open but a hint is lost; tab resume/periodic REST reconciliation eventually sees committed data |

## Manual browser/LAN WSS scenarios
| ID | Scenario | Expected result |
|---|---|---|
| MAN-WS-01 | Dashboard opens with admin session | WSS `system.ready`, authorized `subscription.accepted`, **then** authoritative REST snapshot |
| MAN-WS-02 | Windows Agent goes offline | Server-derived offline notification; UI updates |
| MAN-WS-03 | Router disconnects browser then reconnects | bounded reconnect and REST resync |
| MAN-WS-04 | User role/device group changes live | unauthorized notifications stop |
| MAN-WS-05 | Session expires while WSS connected | connection terminated or no further notifications |
| MAN-WS-06 | Tamper Origin/host/TLS | upgrade refused; no insecure fallback |
| MAN-WS-07 | Stop/restart Spring Boot | browser reconnects; refreshes from PostgreSQL |
| MAN-WS-08 | Burst of 100 heartbeats | no unbounded push/queue growth |
| MAN-WS-09 | WSS remains connected but a change hint is suppressed | Visible-page periodic REST reconcile or tab resume refetches committed state |

## Results policy
Never mark "PASS" without recording date, OS, version, reviewer and evidence. Unit-green is not production approval. Real employee information must not be used in test fixtures or issue screenshots.
