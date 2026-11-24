# DESKTOP-L2B-28 — Signed APT 旧 Release 回放风险的发行证据拒绝边界

**ACTIVE SOURCE CONTRACT · 2026-11-24 · CI NOT RUN at authoring · G-05 OPEN · v0.1 NOT RELEASE READY。**

## 输入事实和问题

真实 [L2B-27](desktop-tauri-l2b27-native-apt-signed-release-date-replay-oci-2026-11-24.md) 功能源码 `09a4e3b386db603a530b271b7f779481c1dd7890`、[原生 APT Ubuntu24 OCI #45395513243](https://github.com/kunlong-luo/lanveriq/actions/runs/45395513243) 已在临时、正确签名的两个 `InRelease` 间证明：后发的 **Date 更早但仍有效** 的签名索引被 `apt-get update` 接受。Packages 内容相同，**未进行真实 Agent 软件降级**，也无独立持久可信高水位。

## 本阶段源代码范围

1. 将 `docs/planning/desktop-release-evidence-boundary.json` 从 v1 升级为 v2，在独立 `apt_replay_observation` 记录中固定**原 L2 功能 SHA / run ID / 观察事实 / 临时签名者 / 没有真正包降级、安装或独立可信高水位**。
2. `apps/desktop/tools/verify_desktop_release_evidence_boundary.py` 使用现有**精确字段+精确类型+不可有重复 JSON key** 的拒绝优先校验，不允许自填已批准签名、独立高水位或可信时钟。
3. 新增回归负例：篡改签名回放结论、将 CI 临时签名者冒充正式签发者、声称发生真实软件降级、伪造高水位/可信时钟审批、删除回放证据必须拒绝。由 [desktop-release-boundary-offline](../../.github/workflows/desktop-release-boundary-offline.yml) 低成本 CI 自动检查。

**不在范围：** 本合同**不验签**、**不创建独立密钥或可信时间**、**不存储不可回退高水位**、**不阻止 APT 原生接受旧签名元数据**、**不发行任何 Agent**，更不会开放 Tauri 安装 IPC、Polkit/root D-Bus 实际安装事务或 systemd 启动。JSON 是版本控制下的**不可信声明与回归证据**，不能作为操作授权凭据。

## 下一真实验收条件（未通过）

独立发行/安全管理主体批准发行者身份、受限 `Signed-By` 公钥、密钥轮换/撤销、脱离本机 root/业务 DB 的单调高水位与可信时间，再以真实签名旧 `InRelease`、不同 Packages/版本、相同日期不同摘要、断网、时钟回拨、错误签发者、重装与软件降级在隔离原生 APT L2 做严格拒绝；每个信任锚有独立不可变证据。以后还须授权的 Pop!_OS24 COSMIC L3、真实受限 Polkit broker、operator consent、安装/启动/采集分权。

[CURRENT_STATUS](../CURRENT_STATUS.md) · [G-05](../planning/release-gap-register.md) · [发行 runbook](../operations/release.md) · [文档治理](../governance/document-lifecycle.md)

**全部 Gate OPEN · v0.1 NOT RELEASE READY · 生产联网、采集、远控、自动恢复和 CA 证书签发仍 OFF。**
