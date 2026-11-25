# Lanveriq（澜维巡）

[English](README.md) · **简体中文** · [文档导航](docs/README.md)

Lanveriq 是面向**明确告知并合法授权**的办公终端的 Linux-first、自托管资产清单、设备健康与审计平台。首发目标：**Pop!_OS 24.04 / COSMIC**。

> **v0.1 NOT RELEASE READY。** 尚无可供普通用户分发的一键签名 Agent 安装器，也没有获批准的 root Polkit 安装事务、Pop!_OS 实机 L3、正式 CA/MFA、独立 WORM 与法务/SRE 签收。生产联网、自动采集、自动恢复、远控、证书签发保持 **OFF**；合成 CI PASS 不是生产授权。

[唯一当前事实与 CI](docs/CURRENT_STATUS.md) · [唯一发布 Gate G-01～G-10](docs/planning/release-gap-register.md) · [Linux 当前交付路线](docs/planning/linux-v01-reuse-first-delivery-path-2026-11-24.md) · [目录去重审计](docs/governance/repository-layout-audit-2026-11-24.md)

## 目录保留/删除决策（以实际代码为准）

| 路径 | 负责什么 | 决定 |
| --- | --- | --- |
| [`apps/web/`](apps/web/README.md) | **唯一 Web 后台**：Next.js 16 / React 19 / Tailwind 4 / Base UI；公开 Pages 仅断开式演示 | **保留** |
| [`apps/server/`](apps/server/README.md) | Java 25 / Spring Boot 4.2 / **Gradle** / JPA-Hibernate / Flyway / PostgreSQL 18；mTLS、业务事务与 API | **保留** |
| [`apps/agent/`](apps/agent/README.md) | Rust/Tokio/SQLx SQLite/reqwest-rustls；Linux 只读采样、durable outbox、严格 ACK，生产自动采集仍关闭 | **保留** |
| [`apps/desktop/`](apps/desktop/README.md) | Tauri 2 + React 的本机安装资格与状态**只读 GUI**；Vite 仅构建 Tauri 窗口，不是第二套 Web | **保留** |
| [`deploy/caddy/`](deploy/caddy/Caddyfile.reference) | TLS 1.3 / 双 mTLS 参考配置、隔离联调；不是现成的公网生产部署 | **保留** |
| [`deploy/network/`](deploy/network/verify_dual_access.py) | LAN/WAN 边界负测 | **保留** |
| [`deploy/rustdesk/`](deploy/rustdesk/client-release-lock.json)、[`deploy/rustdesk-synthetic/`](deploy/rustdesk-synthetic/verify_rd2_release_boundary.py) | RustDesk 候选与 RD-2 发行拒绝测试；**远控 OFF**，尚无许可批准 | **保留（隔离）** |
| [`docs/`](docs/README.md) | 技术规范、现行状态/Gate、日期验收、不可替代的安全失败证据、固定 Git 历史索引 | **保留并去重** |
| [`.github/workflows/`](.github/workflows/docs-governance.yml) | 按需 L1、隔离 L2、RD-2、文档与发行证据 CI；不是实机或生产签收 | **保留** |

根目录的 [`AGENTS.md`](AGENTS.md)、[`CONTRIBUTING.md`](CONTRIBUTING.md)、[`SECURITY.md`](SECURITY.md)、[`CHANGELOG.md`](CHANGELOG.md) 都有独立用途，保留。根目录**不是** Gradle/Maven 工程。不得恢复 `apps/web-next/`、旧 Vite Web、第二 Maven 后端或独立 Agent 控制平面；现行仓库也不存在这些第二套项目。

## 文档组织方式

```text
docs/
├── README.md             # 唯一文档导航
├── CURRENT_STATUS.md     # 唯一当前功能/CI 事实
├── architecture/adrs/    # 架构、协议与架构决策
├── security/             # 身份、FIDO、WORM、隐私、安全规范与历史证据
├── development/          # 技术标准、UI 规范、独立日期实施/CI 报告
├── testing/              # L1/L2/L3 验收、真实失败与反例
├── planning/             # 当前 Linux 路线、唯一 Gate、DoD/评分、历史验收账本
├── governance/archive/   # 文档规则、目录审计、固定 SHA 历史原文索引
├── operations/           # 开发/备份/恢复/发布操作边界
├── product/              # 产品需求与用户体验方案
├── diagrams/             # Mermaid 源图
└── visuals/              # SVG 概念图、非真实 UI 截图
```

**已从工作树删除**不再维护的旧 `docs/planning/backlog.md`、`roadmap.md`、`v0.1-batched-delivery-plan-2026-11-08.md` 和更早的 `v0.1-implementation-sequence.md`；它们的**完整原文仍在 [固定 Git SHA 历史索引](docs/governance/archive/README.md)**。历史 ADR/失败测试不因重名或体积大而删除。当前只使用 [Linux 实施路线](docs/planning/linux-v01-reuse-first-delivery-path-2026-11-24.md)、[CURRENT_STATUS](docs/CURRENT_STATUS.md) 与 [release-gap register](docs/planning/release-gap-register.md) 制定下一步。文档 CI 递归检查全部 `docs/**/*.md` 本地相对链接并拒绝旧路径复活。

## v0.1.0-dev.1 源码开发预览候选（尚未发布）

[候选发布说明](docs/operations/source-preview-v0.1.0-dev.1.md) · [离线源码门禁](tools/release/verify_source_preview.py) · [L1 #45557337790 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45557337790)。本轮只有**源码预览候选和自动负测**，未创建 Git Tag / GitHub Release，没有可下载的正式 Agent `.deb`、GUI 安装器或获准的生产部署。门禁拒绝伪造正式签名根、APT 高水位、Polkit/L3 批准、生产开关与发布附件；已有 CI 证据绑定精确功能 SHA 和 Run ID，但**不是独立可信发行证明**。

## 开发者入口（不是用户安装说明）

在合法隔离开发机上，Web 本地**断开式预览**：

```bash
cd apps/web
corepack enable
corepack prepare pnpm@12.0.0 --activate
pnpm install --frozen-lockfile
pnpm test
pnpm dev
# http://127.0.0.1:3100
```

其他组件分别在对应目录执行 `cd apps/server && ./gradlew test`、`cd apps/agent && cargo test --workspace --locked`、`cd apps/desktop && pnpm test`。后端独立 PostgreSQL/loopback 配置参见[开发环境指南](docs/operations/installation.md)。**普通终端用户不应自行编译 Rust、使用 `dpkg -i` 或启用 systemd 作为“安装方式”。**

正在进行的隔离业务链增量：[真实断网后 Rust SQLite 清单恢复 → mTLS 精确 ACK → PG18 revision 推进 → Next.js 手动刷新](docs/development/linux-inventory-reconnect-exact-ack-nextjs-l2-2026-11-25.md)。**该项已按功能 SHA [`db570181`](https://github.com/kunlong-luo/lanveriq/commit/db570181d603a7f069a85a23c009658e9a8bed4c) 通过[真实隔离 Linux L2 #45554279736（两个 jobs SUCCESS）](https://github.com/kunlong-luo/lanveriq/actions/runs/45554279736)。** 这不代表正式 Agent 可安装、具备后台自动恢复或生产采集授权。

## G-05 原生 APT 与第二签名身份见证回放拒绝

[L2B-32 原生 Ubuntu OCI #45566961126 — 17/17 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45566961126) 保留前 15 项真实 APT 与不同 Rust ELF 验收，并用**单独生成的 CI 实验 GPG 见证签名者**做新增负测：APT 仍接受较旧但有效签名的 Release，见证预检却依序列/摘要拒绝；有效旧见证、发布者冒充和篡改见证也被拒绝。五项轻量源级负测已前移至 Rust 编译之前。[L2B-32 报告（含首轮失败）](docs/development/desktop-tauri-l2b32-separated-gpgv-witness-signed-apt-replay-oci-2026-11-25.md)。**尚非外部独立保管的高水位、正式发行签名或获批 Agent 安装器；G-05 OPEN。**

## 安全与下一步

已有 [Agent→mTLS→Spring/PG18→Next.js 隔离合成链路](docs/testing/linux-readonly-os-cpu-ram-to-nextjs-l2-2026-11-24.md)，并有 [原生 signed-APT 有效旧 Release 被接受的风险证据](docs/development/desktop-tauri-l2b27-native-apt-signed-release-date-replay-oci-2026-11-24.md)。[L2B-28](docs/development/desktop-tauri-l2b28-apt-replay-evidence-boundary-2026-11-24.md) 只能阻止伪造高水位/发布批准的**源级声明**，并未实现真实可信防回退器、正式签名者或安装权限。

必须补齐独立发行/可信时间/防回退高水位、受限 Polkit 安装授权、获得设备所有人许可的 COSMIC 实机 L3、正式管理员 MFA/CA、独立 WORM、法务许可及 SRE 灾备验收，才能重新评审 G-01～G-10。详见 [AGENTS](AGENTS.md)、[安全政策](SECURITY.md) 和 [发行 runbook](docs/operations/release.md)。**v0.1 NOT RELEASE READY。**
