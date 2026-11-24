# Agent → Caddy mTLS → Spring Boot → PostgreSQL → Next.js 单设备业务闭环 L2 验收

**ACTIVE SPEC + VERIFIED L2 EVIDENCE · 2026-11-24 · 仅隔离合成验收 · FULL-CHAIN PASS（指定功能 SHA）· v0.1 NOT RELEASE READY。**

适用范围：`main`；复用 [Linux Batch A](../../.github/workflows/linux-batch-a-caddy-pg18-acceptance.yml)、[真实双跳 TLS/PG18 脚本](../../apps/server/scripts/verify-agent-upstream-tls.sh)、[Rust 单次合成 Agent](../../apps/agent/examples/linux_batch_a_http_synthetic.rs)、[Console Chromium 验收](../../apps/server/scripts/verify-synthetic-console-browser.py) 和 [Next.js 只在 CI 临时挂载的受权 UI 候选](../../apps/web/src/staged-console-workspace.tsx)。本规范是保留拒绝条件的集成验收契约，**其当前功能 SHA 已实际通过单次合成 L2**；这不授权任何正式设备或控制台生产能力。状态依据 [CURRENT_STATUS](../CURRENT_STATUS.md)，发布门禁依据 [release-gap register](../planning/release-gap-register.md)。

## 一项 P0，不是五项互不相干的绿色测试

**必须在同一 GitHub Actions 运行、同一功能 SHA、同一隔离 PostgreSQL 18 实例中建立数据因果关系：**

`Rust CSR + 合成短时客户端证书 → Rust reqwest/rustls TLS1.3 → Caddy 校验 Agent mTLS → Caddy 到 Spring/Tomcat mTLS → Spring JPA/Flyway PG18 提交 → Spring 当前角色/组授权设备读取 API → Caddy 同源 HTTPS → Chromium 中的 Next.js 设备列表/监控`。

* 仅在一次性 GitHub Linux runner 创建合成根、密钥、管理员、设备 UUID 和封闭服务；Caddy/PG/Next 仅环回或受控 runner 内网。不使用员工/真实设备信息、不连接正式 PKI、不启动安装后的 Agent。
* 只用现有 `apps/agent` Rust 示例的一次性明确触发请求，沿用现有 Reqwest/rustls、SQLx SQLite、严格 ACK 和心跳 request ID；不用新增另一个自研协议、桌面守护程序、认证栈或采集引擎。
* Console 管理员通过**真实** Spring 首管理员离线 bootstrap、Cookie/CSRF/登录流程获得一个显式设备组 scope。不得因为测试账户是 owner 就跳过当前组授权；测试设备的组授权须经数据库中真实 scope 查询生效。
* 此前 Linux Batch A 业务模式使用 `CONSOLE_MODE=0`，而独立 Next.js/Chromium 验收读取**另行填充的合成设备**。该断点现通过 CI-only `LANVERIQ_SYNTHETIC_CHAIN_READ=1` / `LANVERIQ_SYNTHETIC_CHAIN_UI=1`、同一 PG18 与源 Rust UUID 绑定的真实浏览器验收修复。禁止拼接不同 SHA、不同 runner 或不同 PG 数据库的日志声称完成。

## 同一条设备记录的正向最小退出条件

1. **默认拒绝前置**：未打开 CI 明确开关时，Agent 激活/心跳与 Console 业务读路由继续拒绝。只允许测试入口临时传入必要 profile/feature switches，绝不更改 `application.yml` 的生产默认。
2. **设备源**：以既有固定合成 UUID `00000000-0000-7000-8000-000000000001` 为唯一链路关联键；初始测试库不存在该设备，之后仅创建一次审核后的测试证书绑定。
3. **写入确认**：真实 Rust 进程完成激活、至少一次成功心跳且拿到严格 ACK；由 PostgreSQL 查询证明同一设备的持久 request ID/状态/接收时间，拒绝“仅 HTTP 200”冒充提交。
4. **受权读取**：真实 Spring HTTP API `GET /api/v1/devices` 与 `GET /api/v1/monitoring/overview` 返回同一设备 UUID、实际 `last_seen_at` / `heartbeat_received_at`、准确授权总数；任何测试专用字段都不得添加到生产响应。
5. **真实展示**：在真实 TLS 信任验证下，经 Caddy 与 Spring Session 登录，再打开一次性挂载的 Next.js 受权候选界面；Chromium DOM 中必须出现**来自第 3 步提交的 UUID 所属设备行**以及与第 4 步一致的受权计数。仅在 React 固定数组中出现相同标签，不算通过。
6. **可追溯断言**：测试脚本自动关联 Rust 实际 request ID、PG 行、Spring 返回记录及 UI DOM，明确记录检查结果（可输出固定合成 UUID 和布尔/计数；不可泄漏 Cookie、密码、私钥、完整头或 SQL 凭证）。

## 必测拒绝与恢复路径（优先复用现有验收）

- **错误/未注册/过期/吊销客户端证书**：Agent 请求在正确的 TLS/数据库边界被拒，PG 已确认数据不被改写；TLS1.2-only、伪造指纹、错误 SNI 不能绕过 mTLS。
- **ACK 丢失与重放**：Rust SQLite 待决状态不能因未知结果变成已确认；相同 request ID 重放被拒或按现有确认查询安全分类，PG 不多写一次。
- **未登录/错误 Host/Origin/跨组与会话撤销**：匿名请求不得拿到设备行；跨组读取与不存在 ID 表现一致；logout/撤销后 Next.js 不残留上一管理员记录；Page/RSC/prefetch 也受同一授权约束。
- **一致性/离线状态**：数据库无新心跳、受权总数不一致、服务不可达时不得虚构“在线”或展示跨会话缓存。不要把最近心跳当成当前连通性的绝对证明。
- **功能边界**：库存 API 的真实 Agent 发送与 UI 展示应在上述心跳闭环通过后再单独纳入同一个链路；不得拿本地静态库存 fixture 代替网络写入。生产采集、自动任务、远控、证书正式签发、WebAuthn 正式信任仍为 OFF。

## 实施顺序与证据

1. 在现有验收 runner 引入一个**显式的 full-chain CI 开关**，限制 GitHub Actions/Linux/环回 DNS/临时 CA；先解决现有 `BUSINESS_MODE` 与 `CONSOLE_MODE` 互斥，不通过开启正式服务配置绕过。
2. 复用同一数据库中的设备审核/激活事务与管理员受权 scope；先验证真实 Spring 已授权非空页面，再接 Caddy/Next.js/Chromium。更新现有脚本而非复制两套认证/设备读取实现。
3. 为非空设备 Browser 验收添加严格“Rust request→PG→API→DOM”关联负例，复用现有 CI 临时 App Router fixture 和 Spring Session 保护；正常 `apps/web` 仍不自动启用后端。
4. 到可跑的单个验收里程碑后执行 L1（脚本与前端约束）和 L2（真 Rust/Caddy/Tomcat/PG18/Chromium），保存**功能 SHA、工作流 run/attempt、逐项 PASS/FAIL/SKIPPED/NOT RUN、失败原始记录**；如果任一步未执行，不得标“链路 PASS”。
5. 通过后才将下一项改为固定字段库存上报/读模型，不扩大密码学测试或第三方采集工具矩阵。任何真正用户安装前仍需独立 G-02、G-05、G-07、G-08、G-09 授权验收。

## 2026-11-24 实际执行与可复核证据

- **功能源码 SHA**：[2380a6a748b8cb57a8d96a64640d47aac7f63ce0](https://github.com/kunlong-luo/lanveriq/commit/2380a6a748b8cb57a8d96a64640d47aac7f63ce0)，[Linux Batch A #45360530843](https://github.com/kunlong-luo/lanveriq/actions/runs/45360530843) attempt 1 **overall SUCCESS**，两项 L2 jobs **SUCCESS**（Ubuntu24 GitHub runner、Rust 1.100、Java25、实际 Caddy2.11.7、PG18.7、Next.js16/Node24、真实 Chromium/Playwright）。
- **同一次运行的实际 PASS**：Rust Linux CSR 生成 + 合成证书、Agent Reqwest TLS1.3 → Caddy → Tomcat mTLS 的激活/双心跳/严格 ACK、SQLite 崩溃后待决和查询、PG18 持久 request IDs；同一数据库中的管理员真实首次 bootstrap、Cookie/CSRF/登录、scope 限制的 Spring 设备列表/详情/监控 API；Chromium 以独立 NSS 测试 CA 验证真实 HTTPS，临时 CI-only Next.js 组件实际渲染一台 Rust-origin 合成设备，API 和 DOM 设备标签/授权总数同源为一，未授权设备详情 404、注销后 DOM 清空且 API 401。真实 PG18 Testcontainers 事务回归同 SHA SUCCESS。
- **自动拒绝**：默认 OFF、TLS1.2-only、无/错误证书、伪造指纹与 Host、错误 Caddy upstream/root、ACK 歧义与重放、证书过期/吊销、跨组详情与注销后读取。临时 [Next.js App Router fixture](../../apps/web/scripts/install-synthetic-rust-chain-fixture.mjs) 仅在一次性 runner 生成，不存在于正式 `apps/web/src/app`；[Browser 合成检查](../../apps/server/scripts/verify-synthetic-rust-chain-browser.py) 不启用 TLS 验证绕过或假 HTTP 响应。
- **完整失败历史**：[首次同源 #45359523006](https://github.com/kunlong-luo/lanveriq/actions/runs/45359523006) **overall FAIL**：Spring/PG/Rust/同设备已授权 HTTP 均 PASS，新增 Chromium 阶段 FAIL；原因是新浏览器缺少已在旧套件中使用的固定 Console DNS→127.0.0.1 解析参数。后由 2380a6a7 恢复本地 Chromium resolver、禁代理和严格 CA 校验并通过全程验收。中途因 main 新提交取消的 workflow 不算 PASS。
- **尚未证明**：正常部署的 Next.js /login 业务授权入口、普通用户 GUI Agent 安装/配置/卸载、真实 Pop!_OS24 COSMIC 物理 L3、生产授权联网与采集、独立 WORM/可信根/发行者签名、Polkit 最小权限 broker、法务与运维批准。库存网络写入、受控读模型与 Web 显示**不是本次通过内容**，为下一工程里程碑。

**结论：FULL-CHAIN（合成心跳/单设备）L2 PASS @ 2380a6a7 / #45360530843；FULL-CHAIN（真实库存/UI）NOT RUN；v0.1 NOT RELEASE READY；G-02/G-05/G-07/G-08/G-09 OPEN。**
