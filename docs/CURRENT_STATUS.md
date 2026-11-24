# Lanveriq 当前状态 / Current status

**LIVE-FACT · 2026-11-24 · `main` · v0.1 NOT RELEASE READY。** 本页只保留**目前有效的项目事实与下一步决策**；每次 CI 的详细失败、修复、确切命令留在[有日期的验收报告](README.md)和 [GitHub Actions](https://github.com/kunlong-luo/lanveriq/actions)，不在此反复追加整段历史。

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
| **签名索引日期的真实回放风险 L2B-27** | [`09a4e3b3` / #45395513243 10/10 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45395513243) | 先 `apt-get update` 较新有效签名 Release，后换成仍有效的**较旧 Date、同 Packages、同临时 signer**，APT 实际再次接受；说明没有独立的单调高水位证明。**未发生软件包降级**。[报告](development/desktop-tauri-l2b27-native-apt-signed-release-date-replay-oci-2026-11-24.md) |
| **Agent 实包 → 真签名源 → APT** | [`2af1cf34` / #45376989866 L2 9/9 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45376989866) | 临时 Ed25519 Signed-By 仓库，Ubuntu24 networkless OCI 内真实 install/reinstall/remove 和 8 项拒绝；没有正式发行者身份或实机安装。[详情](development/desktop-tauri-l2b23-real-agent-signed-apt-oci-2026-11-24.md) |
| **原生 Polkit + root-side D-Bus 身份** | [`91c4669d` / #45382618806 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45382618806) | 真实 D-Bus 发送者 UID/PID/内核启动时间，8 次 Polkit 拒绝与 nonce 负测；只有 **ReviewLab**，永远不执行安装。[详情](development/desktop-tauri-l2b25-real-dbus-peer-review-oci-2026-11-24.md) |
| **本机恶意规则使 Polkit 真正 YES** | [`f062f1443fbe1c6144f56b2e2a339b289ac70ee1` / #45385719766 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45385719766) | OCI 内假定本机规则覆盖后，root-side 审查仍 **NOT_APPROVED**、D-Bus 名称冒充与重放拒绝；**不是**可用安装器。[详情](development/desktop-tauri-l2b26-malicious-polkit-rule-override-real-os-2026-11-24.md) |
| **只读 Linux → TLS/PG18 → Next.js** | [`e6b56c88` / #45365942781 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45365942781) | 一次性 Ubuntu24 runner 的 OS/CPU/RAM、Rust SQLite/TLS1.3 mTLS、Spring JPA PG18 和真实 Chromium 合成链；不是员工终端。[详情](testing/linux-readonly-os-cpu-ram-to-nextjs-l2-2026-11-24.md) |
| **GUI 只读安全审查** | [`a42094bb` / #45368842676 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45368842676) | Tauri 原生编译/React 门禁/只读负测；没有安装按钮或产品 root helper。[详情](development/desktop-tauri-l2b21-install-gate-review-ui-2026-11-24.md) |

## 下一项退出目标（不等于批准上线）

**L2B-28 / G-05：** L2B-27 已在真实 OCI **证明仍有效的签名旧 Release 日期会被 APT 接受**；必须优先解决**独立批准的可信发行根、单调防回放高水位与签名工件、固定可信 APT 软件源、真实可审 root-side 最小权限事务、逐次操作者授权、可取消/恢复/回滚**。目前仅能在 disposable OCI 内继续证明安全负例；无实际授权就**不**把 ReviewLab/恶意 .rules/临时钥匙移到桌面或用户主机，也不允许 GUI 动态 root shell、systemd 自动启用、生产数据采集。Pop!_OS 24.04 COSMIC 实机 L3 须另取得设备所有人书面许可。

并行阻塞：**G-01/G-03/G-04** 官方 FIDO/MFA/CA，**G-02** 外部独立管理 WORM，**G-06** 生产合法业务全链，**G-07/G-08** 法务许可与 SRE，**G-09** UI/Pop!_OS L3，**G-10** Windows/RustDesk 范围书面决定。以 [唯一发布门禁](planning/release-gap-register.md) 为准。

## 证据、历史及维护

FIDO D4-C10、早期 NXT-28、旧合成测试 `2368163e588e6e94f74a748bdc6e162b9c4c6b9a` 与首轮失败，均保存在 [历史完整账本](planning/release-readiness-2026-11-13.md)、日期报告、[CHANGELOG](../CHANGELOG.md) 及 GitHub Actions；**它们不是当前生产批准**。技术决议按 [文档生命周期](governance/document-lifecycle.md) 维护，新功能必须先有真实**功能 SHA + run ID + PASS** 才更新本页；未执行标 **NOT RUN**。

**v0.1 NOT RELEASE READY · main · 生产联网/采集/远控/自动恢复/CA 签发 OFF。**
