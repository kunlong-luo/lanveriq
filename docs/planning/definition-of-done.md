# Lanveriq Definition of Done — 现行交付验收

**ACTIVE · 2026-11-22 · v0.1 NOT RELEASE READY.** D0 technical sign-off authorizes source development, NOT production. Old unit-only CI rules have been superseded by [L1/L2/L3](../testing/strategy.md).

## 每次功能提交
- [ ] 查看真实 `main` SHA、代码与锁文件、相关 ADR/需求、[CURRENT_STATUS](../CURRENT_STATUS.md) 和 [release-gap register](release-gap-register.md)。
- [ ] 明确功能范围、边界、不授权行为、完整性/认证/重放/回滚风险；优先成熟平台/SDK/库，禁止不必要自研。
- [ ] 目标安全/数据库/ACK/证书变更有**实际执行**的负向测试和 L1，证明拒绝伪造、越权、过期、吊销、重复或审计缺失。
- [ ] 涉及 Flyway/JPA/PKIX/TLS/SQLite/角色/事务则有相应 **L2 真 PostgreSQL18 Testcontainers、真实 TLS/Reqwest 或 Chromium 合成链路**；不能只编译或伪造 XML。
- [ ] L1/L2 结果归属于**确切功能 SHA**；每项 run ID/URL、实际环境和 PASS/FAIL/PENDING/NOT RUN 都有记录，旧失败 SHA 不得删改。
- [ ] 新 DB 迁移检查 Flyway 版本、旧 Vxx 升级、历史不可变、角色分离、审计与事务并发；正式生产开关保持 OFF。
- [ ] 改动 SPEC/API/测试文档及 CURRENT_STATUS，如变更 Gate 则同步 [release-gap register](release-gap-register.md)。区分 docs SHA 与 feature SHA，更新 ADR 的取代关系。
- [ ] 只在 main 可回滚提交，没有强推；交接说明做了什么、无法证明什么、下一项有何可验收退出标准。

## 额外 Gate
UI 要满足唯一 Next.js `apps/web`、Base UI/Tailwind 设计 tokens、断开/未知/拒绝态和键盘/触控/滚动/窄屏/无障碍验收，不得伪造设备在线数。原生桌面 UI 遵循[桌面设计系统](../development/desktop-design-system.md)，实际检查导航、状态、手动数据、窄窗、主题、键盘与禁用边界。本机 MVP 的首要桌面为维护者实际 Pop!_OS 24.04 KDE/X11，其他桌面分开验收，不强制切换；本机观察不得替代发行 L3。2026-11-28 维护者批准本机开发结项与商业发行分离：MVP-08 保留锁/许可工程证据、原生/只读等价、实机及迁移验收；权属、第三方正式许可与发行批准独立归 G-05/G-07/G-09，不阻塞通过本机验收后的非发行源码迁移；不授权违反许可、分发或安装。OS 真实支持必须有 Pop!_OS24 systemd 非 root/会话/密钥/断网恢复/安装升级 L3 证据。正式发布还必须有官方 FIDO/CRL、外部 WORM/可信时间、真实 WebAuthn MFA、CA/HSM、隐私许可、备份恢复和组织正式审批；合成 CI 不能关闭。

## 交付记录格式
`task ID, feature SHA, docs SHA, modified files, actual workflow/run links, L1/L2/L3 + environment, negative tests, PASS/FAIL/PENDING/NOT RUN, remaining blockers, next exit criterion`.

[Engineering playbook](../governance/engineering-playbook.md) · [Doc authority](../governance/document-lifecycle.md) · [Current status](../CURRENT_STATUS.md).
