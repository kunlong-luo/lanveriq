# Lanveriq v0.1 Linux-first 上线差距与验收状态

**基准：2026-11-13 · `main` [`758f8d9a`](https://github.com/kunlong-luo/lanveriq/commit/758f8d9a9bdedad04db656a5d685871b38ed3af9)。**

**上线判定：NOT RELEASE READY。** 当前已有真实 **隔离合成** Linux Rust/Reqwest→Caddy→Spring/Tomcat TLS1.3→PostgreSQL 18 的激活、心跳和严格 ACK 证据；但未完成获授权的 Pop!_OS 24.04 常驻 Agent、证书运营全生命周期、正式 Next.js 与 WSS、资产和断网恢复、生产部署、备份演练、安全与隐私审批。**L1 源码测试、L2 合成集成、L3 真实设备、授权试点、生产发布必须分别标记，不能互相替代。**

本文件是 A–E 的**当前状态入口**。带日期的旧 `backlog`、NXT、D0 和早期 CI“尚未测试”结论保留为历史，而不是 2026-11-13 的事实；变更完成状态必须附精确源码 SHA、真实 CI job/run 或实机人工验收证据。

## Linux-first v0.1 范围

- 首发只声明 **Pop!_OS 24.04 LTS / COSMIC Linux**：非 root systemd、批准的 CA/CSR/证书、心跳、CPU/RAM/磁盘/OS 和经许可网络资产、SQLx SQLite 断线队列、Spring/JPA/PG18、正式 Next.js 登录/设备列表/Monitoring Center、WSS 元数据提示和 REST 权威重同步；先在受批准 LAN/VPN 内试点，未批准公网入口。
- **Windows 11** 已有 SCM/CNG 合成基础，但原生密钥/服务 SID/MSI/升级/OS L3 单列后续支持门槛。Windows 未完成不应阻塞**明确标注为 Linux-only** 的 v0.1；同时不得宣称 Windows 已正式支持。
- **RustDesk R** 独立轨道，[Issue #3](https://github.com/kunlong-luo/lanveriq/issues/3) RD-2 BLOCKED，未证明服务端不可绕过的会话 admission、撤权、AUTH-01–16 与许可。不能因为 OSS rendezvous 成功而启用生产远控。
- v0.2 活动/安全审计和复杂报表；v0.3 经批准录制/审批/回放/删除，均不阻塞 Linux 设备管理 v0.1；若合同将其列为首发功能，则按相应独立发布门槛验收。
- 本次仅文档：生产 Agent 网络/采集、真实设备与员工数据、Console 正式业务开关、CA 私钥/公网入口与 RustDesk 一律保持默认关闭。不得自动部署。

## 2026-11-13 V13：新心跳 UUID 账本绑定服务端证书代际，隔离 L1 + L2 PASS

- **实际受测源码：** [`ce350bac`](https://github.com/kunlong-luo/lanveriq/commit/ce350bacfbcf1f7413430484a9dc48b73b12af53)。[Linux Batch A #43382446151](https://github.com/kunlong-luo/lanveriq/actions/runs/43382446151) 中 **PG18.6 Testcontainers 八组事务/迁移与独立 Rust CSR/Reqwest→Caddy→Spring/Tomcat 双跳 TLS1.3 两项 L2 作业均 SUCCESS**；[Java 单测 #43382446134](https://github.com/kunlong-luo/lanveriq/actions/runs/43382446134)、[PG 集成源码编译 #43382446098](https://github.com/kunlong-luo/lanveriq/actions/runs/43382446098)、[RD-2 default-OFF #43382446147](https://github.com/kunlong-luo/lanveriq/actions/runs/43382446147) 同源 SUCCESS。
- **已实现：** PostgreSQL V13 在 `device_heartbeat_request_ids` 新增 `credential_epoch_id UUID`。对每条新 INSERT，数据库按 device→credential 锁序确认活跃设备与当前未撤销/未过期证书，从不可变 `device_credentials.credential_epoch_id` 补足代际，拒绝错误指纹和自选错误 epoch（SQLSTATE 23514）。既有 `(device_id,request_id)` 唯一反重放键与 V11 UPDATE 不可变保护不变。V9–V12 历史账本不猜测回填 epoch，`CHECK NOT VALID` 只要求新记录非空；完整数据删除/保留策略仍未批准。
- **明确不代表：** 数据库 SQL 约束不能代替 TLS 身份验证；独立 Caddy mTLS 及 Hibernate/JPA 受控事务原有保护继续必需。V13 **未新增 Agent 签名 epoch 凭证、受审核证书轮换、可信本地 pin 安装、恢复旧 pending 的授权 API、自动重试、定时清理或生产联网**。Agent SQLite V7 仍保持旧发送入口默认拒绝；Pop!_OS 24.04 实机 L3 与隐私/发布审核未完成。Batch A #5 OPEN、v0.1 NOT RELEASE READY。

## 2026-11-13 SQLite V7：本地证书代际固定保护，L1 已通过

- **实际受测源码：** [`a13a3e7d`](https://github.com/kunlong-luo/lanveriq/commit/a13a3e7dd67f1c327eb4ed9632df68d1ee2306d4)。[Agent Rustfmt / 所有 Cargo 单测 / Clippy #43376633141 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43376633141)；[RD-2 fail-closed #43376633124 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43376633124)。本轮为**实际文件型 SQLite L1**，没有执行新能力的双跳 TLS/PG18 L2，也未在 Pop!_OS 实机 L3 运行。
- SQLx 迁移 `20261113000700_heartbeat_credential_pin.sql` 在 Agent 本地增加唯一固定的 `(device_id,credential_epoch_id,cert_sha256_fingerprint)` 身份及 seen 防丢失标记；SQL 触发器拒绝正常 UPDATE/DELETE。仅可在没有**任何已持久化心跳 checkpoint** 的库中显式首次固定，旧 pending/隔离无法事后归属给新证书。
- 已绑定后，现存未传入可信证书上下文的 `run_explicit_heartbeat_once` 与 `inspect_explicit_heartbeat_reconciliation` **一律拒绝执行**；旧发送器若在 pin 写入前读取过无绑定状态，`save_heartbeat_schedule_checkpoint` 同一 SQLite 写事务仍再次拒绝，从而封闭此竞态。新只读 `inspect_local_heartbeat_credential_continuity` 只输出本地匹配/不匹配/无绑定诊断，**匹配绝非授权证明**。
- **未实现：** 由可信服务端/CA 身份验证到本地 pin 的认证开通协议、携带已验证证书上下文的新版心跳驱动、已获批轮换及旧 pending 人工恢复、硬件密钥托管、真实 Linux systemd 服务联网与 Pop!_OS L3、生产隐私审批。不得将本地输入的指纹/UUID 当 mTLS 或操作员批准，也不得自动换证书、清除 SQLite 隔离、恢复重试或启用采集/远控。Batch A #5 继续 OPEN。

## 2026-11-13 V12 PostgreSQL 证书代际基础：PG18 真实 L2 作业通过

- 每条证书记录的 `credential_epoch_id` 是 PostgreSQL 生成的独立 UUIDv7（含 V9 遗留数据迁移回填），不允许 SQL 客户端指定，也不允许后续改写该身份字段；仍允许证书正常吊销。源码 [`5183ddac`](https://github.com/kunlong-luo/lanveriq/commit/5183ddacfd5b7c5e653b70412f5753c601bb9370)。[PG18 Testcontainers 真实迁移/负例 #43374592095](https://github.com/kunlong-luo/lanveriq/actions/runs/43374592095) 的**数据库作业 SUCCESS**；[Java L1 #43374592206](https://github.com/kunlong-luo/lanveriq/actions/runs/43374592206)、[源码编译 #43374592044](https://github.com/kunlong-luo/lanveriq/actions/runs/43374592044)、[RD-2 default-OFF #43374592077](https://github.com/kunlong-luo/lanveriq/actions/runs/43374592077) SUCCESS。**同一 CI 中真实 Rust CSR/Reqwest→Caddy→Spring/Tomcat TLS1.3 受控作业也已独立 SUCCESS**：#43374592095 两项实际 L2 作业均通过，仍不等于 Pop!_OS L3。
- **严格边界：** 当前 V12 只是数据库内服务端代际身份，不是服务器签名/下发给 Agent 的 epoch 凭据，不允许据此切换私钥、解除 SQLite pending/quarantine、按 TTL 删除 UUID 或恢复生产心跳；Batch A 仍 OPEN。完整安全条件见 [代际/保留策略](../architecture/heartbeat-request-ledger-epoch-retention.md)。

## Batch A V11 账本不可变与代际/保留策略（2026-11-13）

- [`93c24299`](https://github.com/kunlong-luo/lanveriq/commit/93c24299ed031c1c44b6f6158696125d9c97142a) 新增 PostgreSQL V11 `BEFORE UPDATE` 保护，拒绝任何已确认 UUID 或证书指纹归属变更；保留 V9 未绑定旧行及跨证书唯一主键，**没有启用按时限删除、自动轮换或恢复**。
- [Linux Batch A #43371701087](https://github.com/kunlong-luo/lanveriq/actions/runs/43371701087) 的 **PG18 Testcontainers 真实 V9→V11 迁移/反篡改测试作业已成功**；Java 单测 [#43371701100](https://github.com/kunlong-luo/lanveriq/actions/runs/43371701100)、集成测试源编译 [#43371700985](https://github.com/kunlong-luo/lanveriq/actions/runs/43371700985)、RD-2 默认拒绝 [#43371701023](https://github.com/kunlong-luo/lanveriq/actions/runs/43371701023) 均成功。**同运行的真实 Rust CSR/Reqwest→Caddy→Spring/Tomcat TLS1.3/PG18 双跳作业也已 SUCCESS**：两项隔离 L2 均通过，但不是本轮新 V11 的生产流量或真实 Pop!_OS L3。
- **未完成：** 证书生命周期签发/审核与 credential epoch、可安全执行的自动保留期清理、PG 删除权限/灾备回滚、受控操作员恢复、真实 Pop!_OS L3。策略见 [请求账本代际与保留规范](../architecture/heartbeat-request-ledger-epoch-retention.md)。Batch A #5 继续 OPEN。


## 2026-11-13 最新：只读恢复竞态加固 L1 验收成功

- 源码 [`b70b20c2`](https://github.com/kunlong-luo/lanveriq/commit/b70b20c265d45de1aaa53690a05e50949a04bd5f)、实际受测修正 [`cc85897f`](https://github.com/kunlong-luo/lanveriq/commit/cc85897f28949f4938baa69310d61ba44abf9717)。[Rustfmt/Agent 单测/Clippy #43368140495](https://github.com/kunlong-luo/lanveriq/actions/runs/43368140495) SUCCESS；[RD-2 默认拒绝 #43368140522](https://github.com/kunlong-luo/lanveriq/actions/runs/43368140522) SUCCESS。
- 新增 SQLite 同设备 checkpoint 二次核验：查账等待时发生**并发身份/隔离更新或任意 CAS generation 变化**，返回 `StaleDuringLookup`，而不是沿用过期 committed/unknown 观察。两项真实文件型 SQLite 并发负例已通过；查询保持只读，绝不重发或自动清除 quarantine。
- **仅 A3 受控本地检查子项 L1_PASS**，没有声称新检查器通过隔离真实网络 L2，更没有完成运营恢复许可、ledger epoch/retention、A2 PKI/常驻 systemd 及真实 Pop!_OS L3。Batch A 仍 OPEN。

## 最新 Batch A3 安全进展（2026-11-13，单独 L1）

- **只读恢复检查器已实现并通过 L1：** [Agent Rustfmt / 所有 Cargo 单测 / Clippy #43364626478](https://github.com/kunlong-luo/lanveriq/actions/runs/43364626478) SUCCESS（源 SHA [`864297bb`](https://github.com/kunlong-luo/lanveriq/commit/864297bbeb2c5f96191f29526b1afbe4f6eedeb7)）；[RD-2 default OFF #43364626482](https://github.com/kunlong-luo/lanveriq/actions/runs/43364626482) SUCCESS。新增 `heartbeat_reconciliation.rs` 及文件型 SQLite 负向测试：disabled / no pending / identity halt 不发起查账；同设备 pending 查得 committed、unknown、identity rejected、quarantine 和无效响应都**只返回诊断**，不改变持久 SQLite UUID、halt、generation，重启后仍不能发送心跳。已确认核对门槛只读且不会运行真实设备网络。
- **完成度仍是 A3 子项 L1_PASS、Batch A 整体 OPEN。** 没有完成经批准的操作员恢复授权/ledger retention 与 epoch、证书轮换代际验证、Pop!_OS systemd 常驻服务接线、真正设备 L3；切勿把 committed 账本 GET 当首次 POST ACK 或作为自动解除隔离依据。原有 Linux Batch A 网络 L2 证据属于早期不同 SHA，本次新 helper 没有声称单独网络 L2。

## 已通过的实际证据与边界

| 能力 | 实测证据 | 尚不代表 |
| --- | --- | --- |
| Rust CSR/Reqwest→Caddy→Tomcat TLS1.3→PG18 激活/心跳/严格 ACK；降级、Host/SNI、错证书、重放/吊销负例 | [Linux Batch A #43356545857](https://github.com/kunlong-luo/lanveriq/actions/runs/43356545857) SUCCESS，源码 `758f8d9a`；前序连续请求 [#43210930135](https://github.com/kunlong-luo/lanveriq/actions/runs/43210930135) | 真实 Pop!_OS/systemd、生产 CA/证书签发、真实联网 |
| PostgreSQL 18 集成事务和 token→CSR→审核测试源 | 同一 [Batch A #43356545857](https://github.com/kunlong-luo/lanveriq/actions/runs/43356545857) 的 PG18 作业；[集成源编译 #43356545891](https://github.com/kunlong-luo/lanveriq/actions/runs/43356545891) SUCCESS | 源码编译本身不证明数据库运行，也不证明生产发行/轮换 |
| SQLite ACK 丢失 quarantine / V10 绑定提交证书 / V9→V10 迁移 | [#43351181483](https://github.com/kunlong-luo/lanveriq/actions/runs/43351181483)、[#43352639007](https://github.com/kunlong-luo/lanveriq/actions/runs/43352639007)、[#43353817134](https://github.com/kunlong-luo/lanveriq/actions/runs/43353817134) SUCCESS | 安全解除 quarantine、请求 ID ledger retention/epoch、跨证书代际恢复；**404/429/5xx 均不证明未提交，不可凭此更换 UUID 重试** |
| Java 单测/RD-2 发布保护 | [#43356545809](https://github.com/kunlong-luo/lanveriq/actions/runs/43356545809)、[#43356545805](https://github.com/kunlong-luo/lanveriq/actions/runs/43356545805) SUCCESS | RustDesk 底层实际会话 ACL 和生产授权 |
| Next.js B+C UI 与历史合成 Chromium | [Next.js README](../../apps/web-next/README.md)、[合成浏览器 #42335190613](https://github.com/kunlong-luo/lanveriq/actions/runs/42335190613) | 正式 `/login` 仍是 LoginPreview，devices/monitoring 仍 `unconnected`；合成 staged 受保护读组件并未挂到正式页面 |

## A–E 任务差距、验收门槛

| 批次 | 当前状态 | 必须补齐的 P0 | 何时才可以关闭 |
| --- | --- | --- | --- |
| **A 身份和心跳** | 核心合成 L2 PASS；整批 OPEN | 经过批准的 token/CSR→审查签发→激活/吊销/轮换、密钥/代际/账本 retention、非 root Linux systemd 调度、受授权 ACK 不确定状态恢复；[Issue #5](https://github.com/kunlong-luo/lanveriq/issues/5) | 完整同身份正负合成验证，真实授权 Pop!_OS 安装/启停与失效/错误/重放/中断安全验证 |
| **B 资产和离线恢复** | 组件部分存在；运行闭环 OPEN | 实际受限采集 CPU/RAM/disk/OS/授权 NIC-IP，快照和调度接入常驻 Agent、PG18 HTTP/ACK、SQLite 离线队列/重启/旧 ACK/缺口 | 真实受限请求链与恢复/故障注入，没有假健康、无新旧快照倒退 |
| **C 管理员 Console** | 页面预览，正式数据未连 | Next.js 正式登录/注销/Session/CSRF、scoped REST/Orval、设备列表/详情/监控、SSR/RSC/Origin/RBAC、撤权清缓存、错误和新鲜度 UI | 实际 Spring + Chromium 的合成 A/B 分组权限和负例；公开 Pages 永远不连业务 |
| **D WSS/事件** | 完整运行闭环 OPEN | 受限 WSS 元数据提示、初始 REST 与重连/静默丢推送/标签恢复重同步、Agent 事件批次 ACK、基础诊断 | 状态在线/迟到/离线正确，断线/乱序/背压/跨组撤权与 A–D 连续业务 E2E 通过 |
| **E Linux 发布候选** | 生产/实机验收 OPEN | Pop!_OS DEB/systemd、安全密钥/证书、Caddy/私有 Node/Spring/PG18 部署、升级回滚、备份恢复、监控/日志、许可、安全/隐私审批 | Linux L1+L2+**真实获授权 Pop!_OS L3** + 恢复演练和独立发布签字；Windows 另行验收 |
| **R RustDesk** | RD-2 BLOCKED/default-OFF | 真正原生画面/输入/停止、不可绕过会话授权/撤销、AGPL/Pro 许可、AUTH-01–16 | 独立真实设备及授权/许可签字后才可解除发布锁 |

## 八个 P0 发布关口

1. G1：获授权的 Pop!_OS 24 非 root Agent 安装、身份/私钥/启停和受限上报。
2. G2：短期注册令牌、CSR、人工审批/签发、证书激活/吊销/轮换/遗失处置。
3. G3：完整心跳/资产/指标到 PG18，SQLite 断网/重启恢复，严格 ACK 与身份代际，未知提交不得重发。
4. G4：真正可登录的 Next.js 管理界面与受限设备/监控 API，撤权与跨组拒绝。
5. G5：授权 WSS/REST 实时状态、静默漏提示纠正、离线/错误/新鲜度语义。
6. G6：经审查的 Linux DEB/systemd、升级、回滚、卸载和制品校验。
7. G7：隔离 Caddy/Next.js/Spring/PG18/CA 部署、备份实际还原、日志与故障响应。
8. G8：真实 OS、最小化收集和合法告知/数据保留、安全/许可评审与发布签字。

## CI、治理与时间

- **L1** 按路径跑 Gradle/Cargo/pnpm、安全负例；**L2** 一次性 PG18/Caddy/Tomcat/Rust/Chromium 真实合成服务及错误场景；**L3** 授权真实 Pop!_OS 24、实际网络/证书/升级/恢复/浏览器/隐私。单测 PASS 或 L2 成功不可标为生产上线。
- 实际 [Linux Batch A workflow](../../.github/workflows/linux-batch-a-caddy-pg18-acceptance.yml) **仍对匹配的 `main` push 自动运行重型 L2**，也支持手动触发；与“重型全部 tag/dispatch”策略尚有差距。本轮仅修正文档，不修改 CI 触发器。
- [GitHub Pages workflow](../../.github/workflows/pages-static-preview.yml) 当前只有 `acceptance/v0.1-*` 标签或 `workflow_dispatch` 触发，只发布断开的静态界面，不是生产控制台。
- 历史旧功能分支/Draft PR 须先与 `main` 比对再清理；正式 GitHub Release 和分支规则/签名产物尚需建立。不会为本次文档更新合并/删除未检验代码。
- 2026-11-08 的“4–8 周合成闭环/2–4 个月试点”是历史计划区间，不是上线承诺。是否能上线取决于 G1–G8 的实测证据。

关联：[A–E 原批准计划](v0.1-batched-delivery-plan-2026-11-08.md) · [Roadmap](roadmap.md) · [DOD](definition-of-done.md) · [发布](../operations/release.md) · [Pop!_OS OS 验收](../testing/linux-popos24-agent-acceptance.md) · [人工验收](../testing/manual-acceptance.md)。
