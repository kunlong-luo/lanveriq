# Lanveriq 当前状态 / Current status

**LIVE-FACT · 2026-11-25 · `main` · v0.1 NOT RELEASE READY。** 本页只保留**目前有效的项目事实与下一步决策**；每次 CI 的详细失败、修复、确切命令留在[有日期的验收报告](README.md)和 [GitHub Actions](https://github.com/kunlong-luo/lanveriq/actions)，不在此反复追加整段历史。

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
| **L2B-28 发行边界 L1** | [`03420e61` / #45400903033 PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45400903033) | 拒绝伪造发行批准；**非真实防回退高水位**。[报告](development/desktop-tauri-l2b28-apt-replay-evidence-boundary-2026-11-24.md) |
| **APT 冷缓存双回放 L2B-33** | [c177f281 / #45569177575 20/20](https://github.com/kunlong-luo/lanveriq/actions/runs/45569177575) | 旧有效双签名被分离 RO CI 锚点拒绝；**非外部高水位**。[报告](development/desktop-tauri-l2b33-cold-cache-double-replay-anchored-oci-2026-11-25.md) |
| **Agent 实包 → 真签名源 → APT** | [`2af1cf34` / #45376989866 L2 9/9 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45376989866) | 临时 Ed25519 Signed-By 仓库，Ubuntu24 networkless OCI 内真实 install/reinstall/remove 和 8 项拒绝；没有正式发行者身份或实机安装。[详情](development/desktop-tauri-l2b23-real-agent-signed-apt-oci-2026-11-24.md) |
| **原生 Polkit + root-side D-Bus 身份** | [`91c4669d` / #45382618806 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45382618806) | 真实 D-Bus 发送者 UID/PID/内核启动时间，8 次 Polkit 拒绝与 nonce 负测；只有 **ReviewLab**，永远不执行安装。[详情](development/desktop-tauri-l2b25-real-dbus-peer-review-oci-2026-11-24.md) |
| **本机恶意规则使 Polkit 真正 YES** | [`f062f1443fbe1c6144f56b2e2a339b289ac70ee1` / #45385719766 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45385719766) | OCI 内假定本机规则覆盖后，root-side 审查仍 **NOT_APPROVED**、D-Bus 名称冒充与重放拒绝；**不是**可用安装器。[详情](development/desktop-tauri-l2b26-malicious-polkit-rule-override-real-os-2026-11-24.md) |
| **只读 Linux → TLS/PG18 → Next.js** | [`e6b56c88` / #45365942781 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45365942781) | 一次性 Ubuntu24 runner 的 OS/CPU/RAM、Rust SQLite/TLS1.3 mTLS、Spring JPA PG18 和真实 Chromium 合成链；不是员工终端。[详情](testing/linux-readonly-os-cpu-ram-to-nextjs-l2-2026-11-24.md) |
| **GUI 只读安全审查** | [`a42094bb` / #45368842676 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45368842676) | Tauri 原生编译/React 门禁/只读负测；没有安装按钮或产品 root helper。[详情](development/desktop-tauri-l2b21-install-gate-review-ui-2026-11-24.md) |

**发行预览边界：** `v0.1.0-dev.1` 仅完成了[源码预览候选 L1](operations/source-preview-v0.1.0-dev.1.md)，**NOT PUBLISHED**，无正式安装包；不能将源码预览的自动负测计为 G-05 已通过。

## G-06 新验收：恢复后乱序、冲突仍保持 PG18 原值

[专项报告](development/linux-inventory-post-reconnect-idempotency-l2-2026-11-25.md)：功能 SHA [`9ba85399`](https://github.com/kunlong-luo/lanveriq/commit/9ba853991245765eaf313d3b99d9707c01373942)、[真实 Linux Batch A #45571463449（两 jobs SUCCESS）](https://github.com/kunlong-luo/lanveriq/actions/runs/45571463449)、[Server unit #45571463462 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45571463462)。新增恢复后重复、旧版本迟到、同版本冲突的 mTLS/PG18 负测；**仍只适用于隔离 CI，G-06 OPEN**。

## 最新 G-06 L2 验收与故障注入

[ACK 未交付 Rust、PG18 已提交、原始 SQLite 请求经真实 mTLS 重送](development/linux-inventory-ack-withheld-replay-l2-2026-11-25.md) 已在功能 SHA f83aeab6 的 [Linux #45581861955 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45581861955)、[Agent #45581861744 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45581861744)、[Server #45581861736 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45581861736) 验收。这是合成未交付 ACK，不是 TCP 真正断线。新增 [PG18 SIGKILL/WAL 恢复→Rust 重送](development/linux-inventory-pg18-crash-wal-replay-l2-2026-11-25.md) 功能 9ee4681e，[Linux L2 #45583252985](https://github.com/kunlong-luo/lanveriq/actions/runs/45583252985) **PENDING**；不能把前一项 PASS 冒充本项验收。G-06/G-08 继续 OPEN。

## 下一项退出目标（不等于批准上线）

**G-05 下一项：** [L2B-33 20/20 原生 L2](development/desktop-tauri-l2b33-cold-cache-double-replay-anchored-oci-2026-11-25.md) 已验证冷缓存双签名同序列回放由 RO CI 批准摘要拒绝；**仍无独立管理域正式发行/见证根、可信时间或不可回退高水位**。下一步须具名发行/安全主体独立批准密钥、持久版本/摘要及恢复规则，再实测合法离线、重装、升级/恢复；Polkit root broker 和 Pop!_OS COSMIC 实机 L3 另行授权。生产安装/联网/采集/远控 OFF。

并行阻塞：**G-01/G-03/G-04** 官方 FIDO/MFA/CA，**G-02** 外部独立管理 WORM，**G-06** 生产合法业务全链，**G-07/G-08** 法务许可与 SRE，**G-09** UI/Pop!_OS L3，**G-10** Windows/RustDesk 范围书面决定。以 [唯一发布门禁](planning/release-gap-register.md) 为准。

## 证据、历史及维护

FIDO D4-C10、早期 NXT-28、旧合成测试 `2368163e588e6e94f74a748bdc6e162b9c4c6b9a` 与首轮失败，均保存在 [历史完整账本](planning/release-readiness-2026-11-13.md)、日期报告、[CHANGELOG](../CHANGELOG.md) 及 GitHub Actions；**它们不是当前生产批准**。技术决议按 [文档生命周期](governance/document-lifecycle.md) 维护，新功能必须先有真实**功能 SHA + run ID + PASS** 才更新本页；未执行标 **NOT RUN**。

**v0.1 NOT RELEASE READY · main · 生产联网/采集/远控/自动恢复/CA 签发 OFF。**
