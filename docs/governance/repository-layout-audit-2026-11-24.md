# Lanveriq 仓库目录与文档去重审计（2026-11-24）

**状态：ACTIVE STRUCTURE AUDIT · `main` only · v0.1 NOT RELEASE READY。** 此报告描述已提交的仓库布局与裁剪边界，不代表设备安装/生产授权。[现行状态](../CURRENT_STATUS.md) · [发布缺口](../planning/release-gap-register.md) · [评分](../planning/project-scorecard.md)。

## 审计范围与判定原则

原始 `main` [23c94712](https://github.com/kunlong-luo/lanveriq/commit/23c947120d5b58db1f2d9783728d9b3482f277bc) 的 Git tree 有 **838 个跟踪文件，约 6.75 MB**；顶级构成为 `apps/`、`deploy/`、`docs/`、`.github/` 和七个根级文件。检查了 Git tree、主要入口 README/AGENTS、文档生命周期与 docs-governance、源代码/CI 入口和具体待删文档的现行索引引用。**不是逐行构建全仓库调用图，不宣称所有源文件都有运行时调用。**

删除标准：① 当前没有生产实现/测试/CI/文档链接或唯一安全失败证据依赖；② 内容已被现行单一规范取代，保留只会给出过时建议；③ Git 历史即可恢复；④ 删除后由 docs-governance 核验实际文档链接。**默认保留一切唯一 CI 失败、密钥/WORM/发布拒绝证据。** 空目录并未单独提交到 Git；不能拿“业务尚未启用”作为删除仍有源码/安全测试用途的理由。

## 保留目录与实际责任

| 路径 | 决定 | 为什么保留 |
| --- | --- | --- |
| `apps/web/` | **KEEP ACTIVE** | 唯一 Next.js App Router Web；组件、主题、页面、Vitest、Playwright 合成安全验证，非正式登录 |
| `apps/server/` | **KEEP ACTIVE** | Java25/Spring Boot4.2/JPA/Flyway/PG18，gradle wrapper、事务/API/mTLS/DB/安全测试；不能删 Spring test 数据 |
| `apps/agent/` | **KEEP ACTIVE** | Rust Agent、SQLite durable outbox、TLS1.3/mTLS 和 Linux/Windows 负测；生产服务仍 idle/default-OFF |
| `apps/desktop/` | **KEEP ACTIVE, NONINSTALLING** | Tauri2 GUI 预检、只读安装审核、严格 .deb 归档和签名/Polkit **LAB only**；不包含真正授权的安装 bridge |
| `deploy/caddy/` | **KEEP TEST/REFERENCE** | 真实双跳 mTLS 验证，不能把 reference Caddyfile 当正式公网配置 |
| `deploy/network/` | **KEEP SECURITY TEST** | LAN/WAN 接入威胁模型、只读 probe |
| `deploy/rustdesk/` 和 `deploy/rustdesk-synthetic/` | **KEEP ISOLATED** | 远控候选、协议、禁止发行的 RD-2 负测；**不表示支持远控或允许部署** |
| `.github/workflows/` | **KEEP CI** | 43 个按源码/手动门禁触发的工作流及历史基线；CI 通过不等于 L3，工作流是否必要依赖调用与证据而非最近是否触发 |
| `docs/architecture/`、`docs/security/`、`docs/product/` | **KEEP SPEC** | 运行边界、ADR/威胁模型/法律告知/产品行为。旧 ADR 只代表当时决策 |
| `docs/testing/` | **KEEP TEST EVIDENCE** | L1/L2/L3 的实际证据与历史失败；不能因为今天没跑而删除 |
| `docs/development/` | **KEEP HISTORICAL + SPEC** | 技术标准和日期实施报告，不是第二套现行规范 |
| `docs/governance/` | **KEEP DOCUMENT AUTHORITY** | 规则、旧证据归档、目录审核；历史 AGENTS 与 D0 不能冒充新授权 |
| `docs/planning/` | **KEEP LIVE-GAP + ROADMAP** | 唯一发布 Gate、DoD、真实业务下一步和评分；旧计划标记历史 |
| `docs/operations/` | **KEEP RUNBOOKS** | 操作边界、安装开发环境、备份/恢复/测试；非生产部署清单 |
| `docs/diagrams/` 和 `docs/visuals/` | **KEEP EXPLANATORY** | Mermaid 编辑图与 SVG 概念海报仍被文档/README 引用，**不是已运行界面的截图** |

Git tree 中**没有** `apps/web-next/`、旧 Vite Web 运行目录、第二套 Maven 应用、`node_modules/`、`target/`、`build/` 等已跟踪构建目录。它们不是“该保留的空目录”，无需伪造删除记录；`docs/visuals/assets/` 是 README 实际图片资源，不能因为不是产品代码就删掉。

## 本次直接删除和内容合并

| 内容 | 动作 | 理由 / 回溯 |
| --- | --- | --- |
| `docs/development/pagevela-reference.md` | **DELETE FROM MAIN**，由 Git 历史可恢复 | 2026-10-30 其他产品的 React/Vite/Zustand 迁移研究；当前 `apps/web` 唯一 Next.js，设计依据已在 [复用原则](../development/reuse-first-policy.md) 和 [现行 UI 设计系统](../development/frontend-design-system.md)；现行文档入口未链接其文件路径，继续保留会误导新人重新采纳旧 Vite Web |
| 根 `README.md`、`README.zh.md` | **REWRITE/SHORTEN** | 只保留产品、四应用目录地图、开发者命令、正确状态与权威链接。删除大段 11 月初的过期版本和假定未实现的 CI 状态 |
| `docs/README.md` | **REWRITE/SHORTEN** | 当前导航分层：权威入口/专题规范/测试证据/历史，不再平铺 L2B-1～20 的每个旧工作流 |
| 各 `apps/*/README.md` | **REWRITE/SHORTEN** | 本目录负责什么、如何在开发机跑测试、哪些默认关闭、去哪里看完整历史；不要在 README 重复所有安全报告 |
| `docs/CURRENT_STATUS.md` | **CONDENSE (LIVE-FACT)** | 只有当前能力、同 SHA 真实 CI、仍关闭的 Gate 与历史账本链接；保留旧精确运行和失败在历史专题/Git 历史 |
| `docs/planning/release-gap-register.md` | **CONDENSE (LIVE-GAP)** | 每个 Gate 一行状态、责任类型、完成所需实证；外部组织审批一律 OPEN |

**本次不删除** `docs/planning/release-readiness-2026-11-13.md`、`docs/governance/archive/AGENTS-pre-consolidation-2026-11-22.md`、D4/FIDO/WORM 日期报告及所有 GitHub Actions 失败记录；它们承担可追溯的失败/安全审计角色。旧临时签名实验只能作为历史合成事实，不能给安装界面授权。

## 防止回退与文档所有权

- 根/子 README 不得再重复 100 行以上的旧运行历史；稳定技术决策写 SPEC/ADR，精确 SHA/CI 写 CURRENT_STATUS/日期报告；**不能把历史 CI 当今天生产批准**。
- 唯一 Web 为 `apps/web`；`apps/desktop` 的 Vite 仅是 **Tauri 桌面窗口的静态前端构建器**，不是第二套后台 Web。
- GitHub `docs-governance` 验证现行规范必有且入口相对链接存在；本次增加仓库布局回归检查，拒绝旧 `apps/web-next` 等重复项目、标识漂移和新增虚构安装许可。
- 如需未来删除某个 CI/测试目录，先证明没有运行时、工作流、L2/历史证据或文档引用，再提交路径/审批清单；**用户要求清理不表示允许删安全负测**。

**效果评价**见 [量化评分](../planning/project-scorecard.md)。本报告仅提升可导航性/文档维护分，不会自动关闭生产 Gate；**v0.1 NOT RELEASE READY**。
