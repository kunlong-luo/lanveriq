# Lanveriq 当前状态 / Current status

**LIVE-FACT · 2026-11-26 · `main` · v0.1 NOT RELEASE READY。** 本页只保留**目前有效的项目事实与下一步决策**；每次 CI 的详细失败、修复、确切命令留在[有日期的验收报告](README.md)和 [GitHub Actions](https://github.com/kunlong-luo/lanveriq/actions)，不在此反复追加整段历史。

> **未获批准的生产能力全部关闭：** 生产联网、设备/员工采集、自动恢复、远控、官方 FIDO 来源提升信任和 CA 证书签发均为 **OFF**。目前无正式签名的一键 Agent 安装包、有效的用户端 root 安装 broker、获许可 Pop!_OS 24.04 COSMIC 实机 L3、管理员 WebAuthn MFA、生产 CA、独立外部 WORM/可信时间与法务/SRE 签收。**G-01–G-10 OPEN。**

## 唯一实际应用和技术栈

| 目录 | 现行实现 | 真正可运行的边界 |
| --- | --- | --- |
| [`apps/web`](../apps/web/README.md) | **唯一 Web Console**：Next.js 16.4.5、React 19、Tailwind 4、Base UI | [Pages](https://kunlong-luo.github.io/lanveriq/) 是断开业务的演示；真实接口只在临时 CI L2 联调 |
| [`apps/server`](../apps/server/README.md) | Java 25、Spring Boot 4.2.0、Gradle、JPA/Hibernate、Flyway、PostgreSQL 18.7 | mTLS/PG18 合成事务；真实 CA、用户登录/管理员 MFA 仍锁定 |
| [`apps/agent`](../apps/agent/README.md) | Rust 1.100、Tokio、SQLx SQLite、reqwest/rustls mTLS | Rust Agent、durable ACK/outbox、有限 Linux OS/CPU/RAM 隔离 CI；禁止生产自动采集 |
| [`apps/desktop`](../apps/desktop/README.md) | Tauri 2 + React；Vite **只构建桌面窗体** | 只读兼容性/安装资格；**无实际 GUI 安装/服务启动/联网** |
| [`deploy`](../deploy/caddy/Caddyfile.reference) | Caddy mTLS、LAN/WAN 负测、RustDesk 隔离候选 | 无公网生产路由、远控 RD-2 保持 OFF |

不存在 `apps/web-next`、第二套 Vite Web 或 Maven 后端。目录责任和裁剪依据以 [README 结构地图](../README.zh.md)、[目录治理审计](governance/repository-layout-audit-2026-11-24.md) 为准。

## 最近确实通过的关键验收

| 子链路 | 精确功能 SHA / 实际 CI | 证明与限制 |
| --- | --- | --- |
| **dev.1 源码预览 L1（未发布）** | [`a1473c9e` / #45557337790 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45557337790) | 仅源码合约与安全拒绝测试；无 tag/Release、正式签名或可安装包；全部 Gate OPEN。[候选说明](operations/source-preview-v0.1.0-dev.1.md) |
| **Linux 清单断网→恢复→精确 ACK→PG18→Next.js（隔离 L2）** | [`db570181` / Linux #45554279736 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45554279736) · [Agent unit #45554279741 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45554279741) | 真实 Caddy 断网保留 Rust SQLite revision 2、恢复 mTLS 后同一原始请求取得精确 ACK 并提交 PG18 latest=2；revision 3 在过期/吊销凭据下仍待发；真实 Chromium 经授权手动刷新，仅证明 Ubuntu runner 合成链。[报告](development/linux-inventory-reconnect-exact-ack-nextjs-l2-2026-11-25.md) |
| **APT 冷缓存双回放 L2B-33** | [c177f281 / #45569177575 20/20](https://github.com/kunlong-luo/lanveriq/actions/runs/45569177575) | 旧有效双签名被分离 RO CI 锚点拒绝；**非外部高水位**。[报告](development/desktop-tauri-l2b33-cold-cache-double-replay-anchored-oci-2026-11-25.md) |
| **原生 Polkit + root-side D-Bus 身份** | [`91c4669d` / #45382618806 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45382618806) | 真实 D-Bus 发送者 UID/PID/内核启动时间，8 次 Polkit 拒绝与 nonce 负测；只有 **ReviewLab**，永远不执行安装。[详情](development/desktop-tauri-l2b25-real-dbus-peer-review-oci-2026-11-24.md) |
| **只读 Linux → TLS/PG18 → Next.js** | [`e6b56c88` / #45365942781 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45365942781) | 一次性 Ubuntu24 runner 的 OS/CPU/RAM、Rust SQLite/TLS1.3 mTLS、Spring JPA PG18 和真实 Chromium 合成链；不是员工终端。[详情](testing/linux-readonly-os-cpu-ram-to-nextjs-l2-2026-11-24.md) |
| **GUI 只读安全审查** | [`a42094bb` / #45368842676 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45368842676) | Tauri 原生编译/React 门禁/只读负测；没有安装按钮或产品 root helper。[详情](development/desktop-tauri-l2b21-install-gate-review-ui-2026-11-24.md) |

**发行预览边界：** `v0.1.0-dev.1` 仅完成了[源码预览候选 L1](operations/source-preview-v0.1.0-dev.1.md)，**NOT PUBLISHED**，无正式安装包；不能将源码预览的自动负测计为 G-05 已通过。

## G-06 断网/ACK/WAL 业务链（隔离 L2）

[恢复后迟到/重复/同版冲突 #45571463449 两 jobs PASS](development/linux-inventory-post-reconnect-idempotency-l2-2026-11-25.md)（功能 `9ba85399`）；[PG18 已提交但 Rust 未收 ACK→原始请求重送 #45581861955 PASS](development/linux-inventory-ack-withheld-replay-l2-2026-11-25.md)（`f83aeab6`，非真实 TCP 断线）；[真实 PG18 SIGKILL/WAL 恢复后 Rust 重送 #45583968475 两 jobs PASS](development/linux-inventory-pg18-crash-wal-replay-l2-2026-11-25.md)（`3e5b7009`，首轮 #45583252985 端口问题 FAIL 保留原报告）。**真实生产身份与业务授权未完成，G-06 OPEN。**

## G-08 最新备份 L2

**G-08：** [单 runner PG18 恢复并启动 Spring/JPA #45618414247 L2 PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45618414247)（[首轮 FAIL 留证](development/linux-pg18-restored-spring-jpa-context-l2-2026-11-25.md)）；[新增跨 GitHub runner 原生备份恢复](development/linux-pg18-cross-runner-artifact-restore-l2-2026-11-26.md) 功能 `b31f45ef` / [#45742025955 PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45742025955)。**同 GitHub 管理域非独立保管；真实跨物理机、KMS/WORM、SRE 签收 NOT RUN；G-02/G-08 OPEN。**

## G-02/G-08 当前外部实体机恢复准备（不等于 L3）

[非 root、手动获批、非生产 PG18 隔离恢复工具](../apps/server/scripts/pg18-physical-dr-lab.sh) · [G-08 L1 #45743205846 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45743205846)（只有未授权/CI/同名源机拒绝）；[SRE 实体机器执行卡](operations/g08-physical-dr-operator-card-2026-11-26.md) 已备。**真实不同物理机恢复、外部 WORM/KMS、RPO/RTO、独立签收均 NOT RUN。** [工程评分 71/100、Gate 0/10 关闭](planning/project-scorecard.md)，**不能上线**。

## 下一项退出目标（不等于批准上线）

**G-05/G-09：** [Tauri 只读诊断及 Rust/Clippy #45745776008 双 job PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45745776008)（功能 `a6db3afd`）；[176 包原生 APT 双 runner #45746180961 两 job PASS](development/desktop-ubuntu24-apt-native-cache-rotation-l2-2026-11-26.md)（`c411d3ea`）；[GUI-only `.deb` #45745775902 归档审计 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45745775902)（仅无签名 GUI、不含 Agent）。无正式 Agent 签名、可用 Polkit broker/独立高水位、COSMIC L3。**评分 71/100、0/10 Gate，生产 OFF。**

## 证据、历史及维护

FIDO D4-C10、早期 NXT-28、旧合成测试 `2368163e588e6e94f74a748bdc6e162b9c4c6b9a` 与首轮失败，均保存在 [历史完整账本](planning/release-readiness-2026-11-13.md)、日期报告、[CHANGELOG](../CHANGELOG.md) 及 GitHub Actions；**它们不是当前生产批准**。技术决议按 [文档生命周期](governance/document-lifecycle.md) 维护，新功能必须先有真实**功能 SHA + run ID + PASS** 才更新本页；未执行标 **NOT RUN**。

**v0.1 NOT RELEASE READY · main · 生产联网/采集/远控/自动恢复/CA 签发 OFF。**
