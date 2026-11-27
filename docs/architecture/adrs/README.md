# ADR 索引（现行与历史）

**ACTIVE INDEX · 2026-11-24 · v0.1 NOT RELEASE READY。** ADR 是开发决策，不构成生产权限。当前实际代码和 CI 查 [CURRENT_STATUS](../../CURRENT_STATUS.md)。

| 决议 | 状态/取代说明 |
| --- | --- |
| [0001 Monorepo](0001-monorepo.md) · [0002 PostgreSQL/SQLite](0002-postgres-sqlite.md) | 目录/存储原则，具体实现以源码和锁文件为准 |
| [0003 合法告知](0003-disclosed-capture.md) | 设计原则，真实法律审批 OPEN |
| [0004 快速 main](0004-rapid-main-development.md) | **部分 Supersedes**：main-only 保留，unit-only 被 [L1/L2/L3](../../testing/strategy.md) 取代 |
| [0005 REST/WSS](0005-rest-plus-websocket.md) · [0006 Agent REST/WSS](0006-agent-rest-dashboard-ws.md) | 由 0009 细化 |
| [0007 成熟生态优先](0007-mature-ecosystem-first.md) | **ACTIVE** |
| [0008 Next.js App Router](0008-nextjs-app-router-console.md) | **ACTIVE**，正式取代 React/Vite，`apps/web` 唯一 |
| [0009 Agent Transport](0009-agent-transport-selection.md) | **ACTIVE**：reqwest/rustls、REST/JSON、mTLS、SQLite/ACK |
| [0010 RustDesk](0010-rustdesk-remote-control-integration.md) · [0011 双网](0011-rustdesk-dual-network-access.md) | 候选方向，RD-2/公网未放行 |
| [0012 外部见证 WORM 存储方案](0012-independent-fido-witness-object-lock.md) | **ACCEPTED FOR DEVELOPMENT: 云中立存储抽象；供应商/独立托管 PROPOSED/未批准**。AWS/S3 只保留为可选候选，已从默认服务端依赖移除 |

| [0013 Tauri 图形安装器 + 独立 headless Agent](0013-tauri-desktop-installer-idle-agent.md) | **ACCEPTED FOR DEVELOPMENT**：v0.1 Pop!_OS24 GUI 安装/配置/诊断方向，Agent systemd 独立无托盘后台运行；Tauri 2 **只读安装准备 UI** 已在 L2B-21 的 React/Rust 合成 CI 验证；**真正的签名 Agent 安装器/受限 Polkit/真实 Pop!_OS L3 未实现或未批准** |

| [0014 GPUI 原生桌面迁移候选](0014-gpui-desktop-candidate.md) | **ACCEPTED FOR DEVELOPMENT / candidate only**：冻结 Tauri 扩展，待证据才部分取代 0013 GUI 选型；安装/权限/后台生命周期与 G-05/G-09 约束不变。 |

新 ADR 必须记录 Status(Proposed/Accepted-for-development/Superseded/Rejected)、Date、Context、Alternatives、Decision、Tradeoffs、Supersedes、Approval scope 与 Explicit non-authorizations。旧已取代决议仍保留原历史。[文档标准](../../governance/document-lifecycle.md)。
