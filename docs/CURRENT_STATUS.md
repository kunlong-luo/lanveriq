# Lanveriq 当前状态 / Current status

**LIVE-FACT · 2026-11-28 · `main` · v0.1 NOT RELEASE READY。** 本页只保留**目前有效的项目事实与下一步决策**；每次 CI 的详细失败、修复、确切命令留在[有日期的验收报告](README.md)和 [GitHub Actions](https://github.com/kunlong-luo/lanveriq/actions)，不在此反复追加整段历史。

> **未获批准的生产能力全部关闭：** 生产联网、设备/员工采集、自动恢复、远控、官方 FIDO 来源提升信任和 CA 证书签发均为 **OFF**。目前无正式签名的一键 Agent 安装包、有效的用户端 root 安装 broker、获许可 Pop!_OS 24.04 COSMIC 实机 L3、管理员 WebAuthn MFA、生产 CA、独立外部 WORM/可信时间与法务/SRE 签收。**G-01–G-10 OPEN。**

## 唯一实际应用和技术栈

| 目录 | 现行实现 | 真正可运行的边界 |
| --- | --- | --- |
| [`apps/web`](../apps/web/README.md) | **唯一 Web Console**：Next.js 16.4.5、React 19、Tailwind 4、Base UI | [Pages](https://kunlong-luo.github.io/lanveriq/) 是断开业务的演示；真实接口仅临时本机授权验收或隔离 CI L2 |
| [`apps/server`](../apps/server/README.md) | Java 25、Spring Boot 4.2.0、Gradle、JPA/Hibernate、Flyway、PostgreSQL 18.7 | mTLS/PG18 合成事务；真实 CA、正式管理员登录/MFA 仍锁定 |
| [`apps/agent`](../apps/agent/README.md) | Rust 1.100、SQLx SQLite、rustls mTLS | 手动 once/send 已合 main；用户提供 Pop!_OS once/SQLite 日志，send 本机 HTTP200/精确 ACK PASS（维护者日志）；生产默认 OFF |
| [`apps/desktop`](../apps/desktop/README.md) | Tauri 2 + React；Vite **只构建桌面窗体** | 只读兼容性/安装资格；**无实际 GUI 安装/服务启动/联网** |
| [`deploy`](../deploy/caddy/Caddyfile.reference) | Caddy mTLS、LAN/WAN 负测、RustDesk 隔离候选 | 无公网生产路由、远控 RD-2 保持 OFF |

不存在 `apps/web-next`、第二套 Vite Web 或 Maven 后端。目录责任和裁剪依据以 [README 结构地图](../README.zh.md)、[目录治理审计](governance/repository-layout-audit-2026-11-24.md) 为准。

## 本机 MVP（个人开发环境，非生产 L3）

**MVP-01～07 DONE（7/7）。** [Linux L2 #45969893816 PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45969893816)；维护者本机 PASS，非独立 L3。MVP-08 **BLOCKED**：GPUI [全量 #46105475398](https://github.com/kunlong-luo/lanveriq/actions/runs/46105475398) 三 jobs PASS；[任务账本](planning/local-mvp-tasks.md)保存全部 CI 与下一步。按 [ADR-0014](architecture/adrs/0014-gpui-desktop-candidate.md) 保留 Tauri 回退；22 许可缺项（2/3 归档一致，mac 清单差异）；Release/ABI/合成 Wayland PASS；COSMIC 实机 NOT RUN；验收脚本 CI PASS；BOM 范围已解释，Release 产物/LLD map 待全量 CI。

## 最近确实通过的关键验收

| 子链路 | 精确功能 SHA / 实际 CI | 证明与限制 |
| --- | --- | --- |
| **dev.1 源码预览 L1（未发布）** | [`a1473c9e` / #45557337790 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45557337790) | 仅源码合约与安全拒绝测试；无 tag/Release、正式签名或可安装包；全部 Gate OPEN。[候选说明](operations/source-preview-v0.1.0-dev.1.md) |
| **Linux 清单断网→恢复→精确 ACK→PG18→Next.js（隔离 L2）** | [`db570181` / Linux #45554279736 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45554279736) · [Agent unit #45554279741 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45554279741) | 真实 Caddy 断网保留 Rust SQLite revision 2、恢复 mTLS 后同一原始请求取得精确 ACK 并提交 PG18 latest=2；revision 3 在过期/吊销凭据下仍待发；真实 Chromium 经授权手动刷新，仅证明 Ubuntu runner 合成链。[报告](development/linux-inventory-reconnect-exact-ack-nextjs-l2-2026-11-25.md) |
| **APT 冷缓存双回放 L2B-33** | [c177f281 / #45569177575 20/20](https://github.com/kunlong-luo/lanveriq/actions/runs/45569177575) | 旧有效双签名被分离 RO CI 锚点拒绝；**非外部高水位**。[报告](development/desktop-tauri-l2b33-cold-cache-double-replay-anchored-oci-2026-11-25.md) |
| **原生 Polkit + root-side D-Bus 身份** | [`91c4669d` / #45382618806 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45382618806) | 真实 D-Bus 发送者 UID/PID/内核启动时间，8 次 Polkit 拒绝与 nonce 负测；只有 **ReviewLab**，永远不执行安装。[详情](development/desktop-tauri-l2b25-real-dbus-peer-review-oci-2026-11-24.md) |

**发行预览边界：** `v0.1.0-dev.1` 仅完成了[源码预览候选 L1](operations/source-preview-v0.1.0-dev.1.md)，**NOT PUBLISHED**，无正式安装包；不能将源码预览的自动负测计为 G-05 已通过。

## G-06 断网/ACK/WAL 业务链（隔离 L2）

[恢复后迟到/重复/同版冲突 #45571463449 两 jobs PASS](development/linux-inventory-post-reconnect-idempotency-l2-2026-11-25.md)（功能 `9ba85399`）；[PG18 已提交但 Rust 未收 ACK→原始请求重送 #45581861955 PASS](development/linux-inventory-ack-withheld-replay-l2-2026-11-25.md)（`f83aeab6`，非真实 TCP 断线）；[真实 PG18 SIGKILL/WAL 恢复后 Rust 重送 #45583968475 两 jobs PASS](development/linux-inventory-pg18-crash-wal-replay-l2-2026-11-25.md)（`3e5b7009`，首轮 #45583252985 端口问题 FAIL 保留原报告）。**真实生产身份与业务授权未完成，G-06 OPEN。**

## G-08 最新备份 L2

**G-08：** [单 runner PG18 恢复并启动 Spring/JPA #45618414247 L2 PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45618414247)（[首轮 FAIL 留证](development/linux-pg18-restored-spring-jpa-context-l2-2026-11-25.md)）；[新增跨 GitHub runner 原生备份恢复](development/linux-pg18-cross-runner-artifact-restore-l2-2026-11-26.md) 功能 `b31f45ef` / [#45742025955 PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45742025955)。**同 GitHub 管理域非独立保管；真实跨物理机、KMS/WORM、SRE 签收 NOT RUN；G-02/G-08 OPEN。**

## G-02/G-08 当前外部实体机恢复准备（不等于 L3）

[非 root、手动获批、非生产 PG18 隔离恢复工具](../apps/server/scripts/pg18-physical-dr-lab.sh) · [G-08 L1 #45743205846 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45743205846)（只有未授权/CI/同名源机拒绝）；[SRE 实体机器执行卡](operations/g08-physical-dr-operator-card-2026-11-26.md) 已备。**真实不同物理机恢复、外部 WORM/KMS、RPO/RTO、独立签收均 NOT RUN。** [工程评分 71/100、Gate 0/10 关闭](planning/project-scorecard.md)，**不能上线**。

## 下一项退出目标（不等于批准上线）

**G-05/G-09：** [Tauri 可视化 AgentConfig v1 空闲草稿](development/desktop-tauri-agent-idle-config-ui-2026-11-26.md) 功能 `a70c4243`；[Tauri #45748162585 两 job PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45748162585)，GUI-only .deb 同 SHA CI **PENDING**。此前 [Tauri #45746181071](https://github.com/kunlong-luo/lanveriq/actions/runs/45746181071) 与 [GUI-only .deb #45746180976](https://github.com/kunlong-luo/lanveriq/actions/runs/45746180976) PASS。草稿仅本机只读生成；无正式签名 Agent/Polkit broker/COSMIC L3。**71/100，Gate 0/10，NO-GO**。

## 证据、历史及维护

FIDO D4-C10、早期 NXT-28、旧合成测试 `2368163e588e6e94f74a748bdc6e162b9c4c6b9a` 与首轮失败，均保存在 [历史完整账本](planning/release-readiness-2026-11-13.md)、日期报告、[CHANGELOG](../CHANGELOG.md) 及 GitHub Actions；**它们不是当前生产批准**。技术决议按 [文档生命周期](governance/document-lifecycle.md) 维护，新功能必须先有真实**功能 SHA + run ID + PASS** 才更新本页；未执行标 **NOT RUN**。

**v0.1 NOT RELEASE READY · main · 生产联网/采集/远控/自动恢复/CA 签发 OFF。**
