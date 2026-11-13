# Batch A — 心跳请求 UUID 账本：证书代际与保留策略

**日期：2026-11-13 · 状态：安全约束已部分实现，完整 epoch/retention 协议尚未批准。**
**适用：** Linux-first v0.1、Spring Boot 4 / Hibernate / PostgreSQL 18、Rust Agent / SQLite。
此文是实现约束与后续验收清单，**不是自动重试、删除或授权恢复的许可**。

## 已实现并验证的基础约束

1. **V9：** `device_heartbeat_request_ids` 的主键是 `(device_id, request_id)`。服务器在当前设备与有效证书行锁下，先插入请求 UUID，再提交心跳最新状态；重放拒绝不改变 `last_seen_at`。
2. **V10：** 新提交行记录服务器验证的 `client_cert_sha256_fingerprint`，而不是 JSON/Header 自报值。只读账本 GET 必须由同设备、**同一张当前有效的证书**读取；同设备其他证书无法把旧 UUID 当作自己的确认。V9 无法可信溯源的旧行保持 `NULL` 且永不正向返回。
3. **V11：** 对已有账本行的所有 SQL `UPDATE` 均拒绝（包括修改主键、证书指纹、`NULL` 旧行归属和无实际变化的更新）。原唯一键不变；不回填旧行；不启用清理任务。这是**UPDATE 不可变**，并不宣称数据库管理员不可能 `DELETE` 或 `TRUNCATE`。
4. **Agent A3：** SQLite 写前持久保存 pending UUID；不确定响应/409/401/403 要持久保留并隔离；查账诊断也不解除隔离。查账期间 checkpoint/generation 变化必须返回 stale，而不是认证旧查询结果。
5. **响应语义：** 只有首次 POST 的严格、对应请求 ID 的 200 ACK 可以按现行受控一次性流程清除 pending；账本 GET 200 只是已提交记录的证明，**不是历史 POST ACK**；404、429、5xx、无效响应绝不许可更换 UUID 或重试。

**实际测试证据：** [V11 源码 `93c24299`](https://github.com/kunlong-luo/lanveriq/commit/93c24299ed031c1c44b6f6158696125d9c97142a)；[Linux Batch A PostgreSQL 18 Testcontainers 真实迁移与事务 #43371701087](https://github.com/kunlong-luo/lanveriq/actions/runs/43371701087) 的 **PG18 迁移/事务作业与 Rust→Caddy→Spring 双跳 TLS1.3 作业均已 SUCCESS**；这是同一源 SHA 的隔离 L2，不能推断获批真实设备运行。

## 目前不允许的操作

- **不按 TTL 批量删除**已提交 UUID：即使记录早于 30/90/365 天，旧请求仍可能迟到或重放；没有可信设备代际/请求有效期协议前，删除会重新开放重复提交窗口。
- 不对 V9 未绑定记录人工猜测回填证书、复制到新证书或清空后重建。
- 不通过证书轮换、管理员切换、设备重新登录、进程重启或查询 404 自动重建 pending UUID；原有设备 UUID 不得复用以绕过重放账本。
- 不为此开启生产入口、后台轮询、设备库存/活动收集、远控或部署到用户 Pop!_OS。

**V12（2026-11-13，服务端代际身份基础）：** [`7b3a36f7`](https://github.com/kunlong-luo/lanveriq/commit/7b3a36f786e4e4f059b601e3b6be0e65d1f99f07) / [测试补全 `5183ddac`](https://github.com/kunlong-luo/lanveriq/commit/5183ddacfd5b7c5e653b70412f5753c601bb9370) 在 PostgreSQL 18 的 `device_credentials` 新增唯一非空 `credential_epoch_id`，为历史证书回填独立 UUIDv7；`BEFORE INSERT` 不接受 SQL 自选代际 ID，`BEFORE UPDATE` 阻止改写代际及原证书身份字段，但允许有效的 status/revoked_at 更新。**它不是签名授权凭据、可排序/递增代际序号、请求 ID 账本归属替代品或 Agent 可用的轮换协议。** PG18 真实迁移、旧 V9 证书回填、伪造代际/身份更新与正常吊销负例已由 [Linux Batch A #43374592095](https://github.com/kunlong-luo/lanveriq/actions/runs/43374592095) 中 **PG18 Testcontainers 作业 SUCCESS** 证实；同 SHA [Java L1 #43374592206](https://github.com/kunlong-luo/lanveriq/actions/runs/43374592206)、[集成源编译 #43374592044](https://github.com/kunlong-luo/lanveriq/actions/runs/43374592044)、[RD-2 default-OFF #43374592077](https://github.com/kunlong-luo/lanveriq/actions/runs/43374592077) 均 SUCCESS；**同次运行中独立 Rust→Caddy→Spring/Tomcat TLS1.3/PG18 作业现也 SUCCESS，两项隔离 L2 全部通过**；不是实际部署或完整轮换验收。

## 受控证书轮换与 epoch 的后续必需设计（未实现）

1. **审批与状态机：** 限定管理员在已授权设备上先冻结常驻发送，审核该设备的 pending/quarantine、旧证书状态及 SQLite 请求身份；启动新 CSR/新密钥与独立证书审核发行，记录可审计的操作员/原因/设备/旧新证书指纹。
2. **服务端 epoch：** 应有服务端发行并签名/事务绑定的代际标识，和 `device_id`、具体 credential ID、有效期及撤销事件绑定；严禁仅从当前时钟、客户端序号或证书 CN 推断代际。明确每个 epoch 的允许请求边界，并保持跨代际的 UUID 唯一性/可拒绝证明。
3. **设备持久状态：** Agent 对已批准 epoch、证书私钥托管、pending UUID 与最后成功确认版本做不可静默回退的持久绑定；证书切换期间旧 pending 继续隔离。**新证书的账本查询不能替代旧证书的已提交证据**。
4. **人工恢复协议：** 只有经 RBAC 审批、可信双端证书与账本正向证明、独立审计并具备崩溃/重复执行幂等性的恢复事务，才可解除单条持久隔离；从未收到正向证据时保持 unresolved，不能“当未提交”重发。
5. **可选有限保留：** 需要先证明旧 epoch 不可能再提交任何请求，包括离线设备、旧凭证重放、备份恢复和多服务器回滚；必须设计不可绕过的拒绝门槛/墓碑并经真实 PG18 + 网络负例验证，才能考虑删除旧账本。**截至当前明确禁止自动 TTL/cron retention 清理。**

## 数据主体删除与故障恢复边界

- V11 保持 V9 原有 `devices(id) ON DELETE CASCADE` 的设备关联删除行为；这与“已接受请求自动按时间删除”完全不同。正式数据删除需要独立管理员权限、撤销证书、终止 Agent、审计/隐私批准以及**不得重新激活同一设备标识**的约束。尚未在本轮引入数据删除 API。
- 对设备级别的硬删除、数据库恢复/回滚、主从故障转移与旧节点重新加入必须有独立反重放评估；普通运维角色不能默默清理账本。当前 V11 **不能防止拥有删除权限的数据库操作者抹除账本**，生产角色权限/不可变审计与备份恢复验收仍是 P0。

## 后续验收清单

- L2 PG18：V9→V10→V11 真实升级、旧行 NULL 保留、新行可写、旧/新行 UPDATE 拒绝、旧 UUID 冲突仍拒绝；真实证书轮换后查询不串代。
- L2 网络：Caddy/Tomcat TLS1.3/mTLS 正反向、撤销/过期、不同证书/同设备、多设备错配、409 严格 ACK、404/429/5xx 不代表重试许可。
- L3 Pop!_OS 24：经组织/设备所有者授权的服务安装、密钥安全托管、断线/重启/时钟偏移/旧 epoch 回放及审批恢复；禁止以合成 CI 代替实机批准。
- 另行评审：PG 角色删除权限、备份/恢复后 UUID 不丢失、审计保留与合法数据删除冲突处理、跨代际阻断及允许保留期。

跟踪：[Batch A Issue #5](https://github.com/kunlong-luo/lanveriq/issues/5)、[Linux-first 上线验收账本](../planning/release-readiness-2026-11-13.md)。
