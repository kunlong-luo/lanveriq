> **2026-11-24 L2B-25 — 真实系统 D-Bus 根侧 OS 同行身份拒绝 L2，不是安装步骤：** [Ubuntu24 OCI #45382618806 / 功能 SHA 91c4669d](https://github.com/kunlong-luo/lanveriq/actions/runs/45382618806) 验证系统总线真实 UID/PID、内核四 UID 和 start-time、原生 Polkit 8 次拒绝、跨进程同 UID nonce 重放/恶意参数拒绝及同服务 9 项决策单测。root-side `ReviewLab` 是**只在 networkless disposable CI 容器运行、无 APT/dpkg/systemctl 的拒绝型实验**，即使模拟 Polkit 放行也不安装 Agent。**不能复制其测试 D-Bus policy / CI 公钥/服务到 Pop!_OS 主机；没有正式签名发行根、可执行/可取消的安装 broker、管理员活动会话、跨版本升级或实机 L3，v0.1 NOT RELEASE READY。** [详细报告](../development/desktop-tauri-l2b25-real-dbus-peer-review-oci-2026-11-24.md)。

> **2026-11-24 L2B-23 本地 APT Agent 生命周期工程 L2 PASS（非发行许可）：** [功能 SHA 2af1cf34 / Ubuntu24 #45376989866 9/9 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45376989866)，真实 Rust Agent package 已在 CI 一次性临时 Ed25519 签名 InRelease/Packages 的 APT file: 源上，经过专属 Signed-By 校验，**只在 networkless/cap-drop-all Ubuntu24 OCI 内**完成原生 apt-get install、同版 --reinstall、remove，并保留模拟独立 operator state；错误密钥、冒充签名、unsigned、Packages/真实 deb 篡改、过期/未来 Date 与包名混淆的 8 项安全拒绝均 PASS。首轮 [#45376908241 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45376908241) 是 workflow YAML step name 非法冒号、0 jobs，修正后才进行真实 L2。**绝非已批准产品签名/正式 APT 仓库/可用 Polkit broker/用户 GUI 安装、跨版本升级、Pop!_OS COSMIC L3 或生产运营许可**；所有 G-05/G-07/G-08/G-09 等仍 OPEN，**v0.1 NOT RELEASE READY**。[真实安装边界与下一阶段](../development/desktop-tauri-l2b23-real-agent-signed-apt-oci-2026-11-24.md)。

> **2026-11-24 G05-LNX4 实际原生 Agent 包管理 L2 子项通过：** [功能 SHA eeec5f38 / Ubuntu24 CI #45339799209](https://github.com/kunlong-luo/lanveriq/actions/runs/45339799209) **overall SUCCESS**。在无网、无 systemd 的一次性 Ubuntu24 OCI 中，真实 locked Rust Agent release `.deb` 经严审后由原生 `dpkg` 安装、同版重装、卸载，验证 ELF `--help`、文件所有权和独立操作员状态保留；同一 CI 的原生 systemd DynamicUser 测试仅使用**合成 CSR 进程**，验证禁网和密钥隔离，未安装/启动 Agent 系统服务。**这是工程 L2 证据，不是可分发/签名安装包、Polkit 授权、正式 Pop!_OS24 COSMIC L3 或运营/法务/WORM 审批。** 下一阶段应遵守 [复用优先真实交付路径](../planning/linux-v01-reuse-first-delivery-path-2026-11-24.md)，优先复用 APT/systemd、补最小用户安装权限和设备→Spring/PG18→Next.js 合成端到端业务，而非继续增加独立重复签名实验。[LNX4 完整证据](../development/agent-linux-real-dpkg-disposable-lnx4-2026-11-24.md)。**v0.1 NOT RELEASE READY；G-02/G-05/G-07/G-08/G-09 OPEN。**

> **2026-11-24 L2B-20 APT 验证子项：** [Ubuntu24 真实 `apt-get update` #45334019405](https://github.com/kunlong-luo/lanveriq/actions/runs/45334019405) 9/9 PASS（合成包索引 + 一次性 Ed25519 GPG 签名的 InRelease + 专属 Signed-By keyring）；未授权的 key/signature、Release/Packages 篡改、过期/未来元数据都由 APT 拒绝。**仓库只有虚构占位包，无真实 Agent .deb 或安装/上传，私钥是 disposable CI 测试材料。** 发行者/密钥独立批准、可信根安装、合法包/许可、Polkit broker 和 Pop!_OS24 COSMIC 物理 L3 均未通过；v0.1 NOT RELEASE READY。[L2B-20](../development/desktop-tauri-l2b20-real-apt-signed-by-offline-ci-2026-11-24.md)。

> **2026-11-24 L2B-19 接收端信任根候选评审：** [Ubuntu24 #45328874536](https://github.com/kunlong-luo/lanveriq/actions/runs/45328874536) 中 34/34 root pin、撤销、有效期与模拟 anti-rollback 测试 PASS。**同一 CI 管理的 pin/时间/高水位不能作为生产可信根、受信任时间源或设备防回滚证明**。正式方案优先评审成熟 TUF 根轮换/targets/snapshot/timestamp 和 Sigstore 离线签名 bundle，仍须独立组织批准和真实密钥托管，Agent 包/Polkit/Pop!_OS L3 均 NOT RUN。v0.1 NOT RELEASE READY。[L2B-19](../development/desktop-tauri-l2b19-receiver-root-preprovision-lab-2026-11-24.md)。

> **2026-11-24 L2B-18 实际双签名密码学 LAB：** [Ubuntu24 #45326441957](https://github.com/kunlong-luo/lanveriq/actions/runs/45326441957) 30/30 成功，仅使用一次性 CI 中的两把不同 Ed25519 私钥，检查同一声明、测试包 SHA-256 与 source/run/attempt 绑定及签名/重放拒绝。**不是经过法律/安全部门独立批准的发行者密钥或双审**；签名根、撤销、可信时间、密钥保管及可分发 Agent 包仍 OPEN/NOT IMPLEMENTED，未经授权不得执行 dpkg、Polkit 或启动 Agent。v0.1 NOT RELEASE READY。[L2B-18 退出条件](../development/desktop-tauri-l2b18-dual-signature-custody-lab-2026-11-24.md)。

> **2026-11-24 L2B-17 发行边界：** [39/39 DENY-ALL 安装意图安全测试 #45324617901](https://github.com/kunlong-luo/lanveriq/actions/runs/45324617901) **PASS**；拒绝伪造发行签名/Polkit、任意包路径/Unit/脚本和提前确认的安装/联网/采集/远控意图。**没有真实授权、安装桥或可信签名工件，更没有启用 Agent**。G-02/G-05/G-07/G-08/G-09 仍 OPEN，v0.1 NOT RELEASE READY。[L2B-17](../development/desktop-tauri-l2b17-agent-install-intent-polkit-threat-contract-2026-11-24.md)。

> **2026-11-24 L2B-16 新离线安全门禁：** [GitHub Ubuntu24 #45324049249](https://github.com/kunlong-luo/lanveriq/actions/runs/45324049249) 使用 30 项正负测试及现有 Tauri 源码守卫确认仓库没有误将历史 unsigned GUI-only Debian L2 提升为正式 Agent 安装器许可。[状态边界 JSON](../planning/desktop-release-evidence-boundary.json) 显示可信发行者签名、最小 Polkit、Pop!_OS24 COSMIC 物理 L3、WORM/法务/运维仍 OPEN/NOT RUN。这**不是**外部签名验真、发布授权或生产可信身份，所有生产联网/采集/远控和签发依旧关闭。v0.1 NOT RELEASE READY。[L2B-16](../development/desktop-tauri-l2b16-release-evidence-denial-contract-2026-11-24.md)。

> **2026-11-24 GUI-only L2 成功不等于发布：** [完整 GUI-only .deb 审计 #45322290248](https://github.com/kunlong-luo/lanveriq/actions/runs/45322290248)（c4f04e44）真实未签名包、安全归档、Syft 局部 SBOM、ELF、官方 ABI、Ubuntu OCI APT 模拟和临时实验签名均 SUCCESS；[原生只读预览 CI #45322718592](https://github.com/kunlong-luo/lanveriq/actions/runs/45322718592)（ea62c63c）React/只读原生 Rust 两个 job 也 SUCCESS。**这两个 run 都不授权上传、下载、安装或发布 Agent**，正式发行者签名/密钥、完整许可证评估、最小 polkit 授权、Pop!_OS24/COSMIC 实体 L3、G-02 WORM、G-07/G-08/G-09 仍 OPEN，v0.1 NOT RELEASE READY。[L2B-15 证据清单](../development/desktop-tauri-l2b15-current-source-deb-l2-success-handoff-2026-11-24.md)。

> **2026-11-22 CURRENT AUTHORITY:** [CURRENT_STATUS](../CURRENT_STATUS.md) and [release-gap register](../planning/release-gap-register.md) govern release facts, [engineering playbook](../governance/engineering-playbook.md) controls execution. FIDO, admin MFA, CA, employee data and Pop!_OS L3 remain OPEN. v0.1 NOT RELEASE READY.

> **2026-11-21 现行发布边界：** 以 [CURRENT_STATUS](../CURRENT_STATUS.md) 和 [Linux-first 发布账本](../planning/release-readiness-2026-11-13.md) 的最新证据为准：D4-C10 FIDO 元数据拒绝检查已通过 5/5 **合成** CI，NXT-28 单一 Next.js 断开式预览已通过 4/4；这些不能替代真实 FIDO 来源审批、管理员 WebAuthn、Pop!_OS 24.04 L3、正式业务授权、签名发行/备份恢复。**没有任何生产发布批准；所有生产联网、采集、自动恢复、远控、CA 签发保持关闭。** 本文件旧 2026-11-08/13 片段保留为历史。

# Lightweight pre-release and migration plan

**Status 2026-11-08:** **no production release approval**. Following owner authorization, v0.1 is being developed in **cohesive business batches**; L1 relevant fast checks continue on push, expensive synthetic Caddy/Spring/PG18/Chromium and browser visual checks run on explicitly intended milestone tags or workflow dispatch. Public Pages is a disconnected design preview and **automatically deploys only when the canonical `apps/web` UI source or its approved static workflow changes on `main`**, not when backend/Agent/docs-only source changes; its publishing still does not enable any production service. See [approved plan](../planning/v0.1-batched-delivery-plan-2026-11-08.md) and [three-tier tests](../testing/strategy.md). Previously successful CI runs provide historical evidence, not permission for real accounts/endpoints/Agent data.

## Milestone gates — development versus production

| State | Who/what can approve it | Required evidence |
| --- | --- | --- |
| Source change | Automated L1 on matching paths | Gradle/Cargo/pnpm source checks and focused safety negatives |
| Business batch A/B/C/D complete | Synthetic-only L2 workflow dispatch | Genuine disposable PG18, Caddy/TLS/Agent test path, negative RBAC and error/recovery proof; log run ID/SHA |
| v0.1 release candidate | L3 `acceptance/v0.1-*` milestone tag or explicit dispatch | Full Chromium, origin/session/RBAC, integration suites, packaging, restore and platform checklist |
| Authorized pilot | Organizational/legal/data-owner approval plus operator | Actual authorized Pop!_OS 24.04 device for Linux-only v0.1 (Windows requires its own later native L3), notice/data minimization/retention, CA/key custody, enrollment/revoke/stop, restore |
| Production release | Explicit separate release sign-off | Real deployment topology/security assessment, upgrade/rollback/restore, patch/installer/signed artifact, privacy and observability |

No tag or synthetic green check alone authorizes real deployment; the default-OFF feature flags must remain closed until independent approval.

## Historical 2026-11-08 device-protocol preflight

> The historical TLS1.2-minimum/never-tested claim below was superseded by actual isolated TLS1.3/PG18 L2 [#43356545857](https://github.com/kunlong-luo/lanveriq/actions/runs/43356545857). The remaining real blockers are Agent systemd/PKI/custody/recovery, authentic Next.js/WSS, approved real OS and recovery/production sign-off.

## Original preflight snapshot

The Batch A heartbeat HTTP controller exists only behind **two default-OFF flags**, and the running Rust Agent still has no live enrollment/transport. **DO NOT enable live device ingestion** before: (1) decide/implement/assert the Agent→Caddy minimum TLS version (current Rust `tls_version_min(TLS_1_2)` is **not** proof of TLS1.3-only); (2) validate both Caddy client-identity CA and independent Caddy→Tomcat upstream client certificate, exact SNI/Host and forged-header rejection; (3) run Rust/Java JSON golden fixtures, strict heartbeat/inventory/batch ACK/PKI revocation negative cases; (4) run actual disposable Agent→Caddy→Tomcat→PG18 Batch A L2 and authorized Windows/Linux + key custody acceptance L3. These are separate from previous synthetic Console HTML/RSC/RBAC successes. Canonical checklist: [device transport stack](../architecture/device-communication-stack.md), [document audit](../governance/documentation-audit-2026-11-08.md), [Batch A](../development/batch-a-first-heartbeat-implementation.md).


- Changes are small, atomically committed and documented; never force-push main.
- Run relevant local Rust formatting/compiler, Java compilation, TS typecheck/build and actual unit tests where code exists.
- Updated schema/API? Update contracts + migration and test compatibility. Authorization/credential changes require targeted manual validation.
- Only tag a milestone as an experimental preview after reproducing startup, TLS, enroll/heartbeat and stop/restart on synthetic endpoints.

## Proposed versioning
`v0.1.x`: enrollment, heartbeat, outbox, dashboard with REST + WSS.
`v0.2.x`: app/security activity, scoped search, retention.
`v0.3.x`: approved recording and media flow.
`v0.4.x`: installers, recovery, diagnostics.
`v1.0`: after security, platform and integration acceptance.

The repository now contains working source and passing synthetic integration, but **no approved production installer/Compose bundle or formal GitHub Release**; examples below are still release targets.

## Upgrade/rollback design
- Version Java/Flyway PostgreSQL migrations and Rust SQLite migrations explicitly. Always back up and test restore before destructive schema changes. Avoid rolling back by replacing a live SQLite/WAL database file.
- Support overlapping API/event schema versions for previously enrolled agents. Agent must refuse unsupported server policy expansions.
- Preserve device_id and monotonically increasing sequences across compatible upgrades; lost keys/DB trigger fresh enrollment and historical linkage.
- Record installer architecture, checksums and upgrade path for Windows 11 x64 and first validated Linux configuration.

## Release readiness boundary
Passing synthetic integration/Chromium CI is **necessary but insufficient** for production rollout. NXT-10 demonstrated disposable direct-origin isolation and protected HTML/RSC; NXT-11 reviews publicly cacheable static assets, explicit Next.js network namespace/firewall topology, and real login usability separately. The production Caddy Console remains disabled and Next.js authorized routes are unmounted. Formal v1.0 still requires real deployment integration/negative tests, independent UI accessibility review, real-device tests, signed distribution review, organization/privacy sign-off and exercised backup/restore.
