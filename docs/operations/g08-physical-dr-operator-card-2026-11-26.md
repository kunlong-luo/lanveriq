# G-02/G-08 — 独立实体机器 PostgreSQL 18 恢复演练：SRE 执行卡

**2026-11-26 · OPERATOR PROCEDURE READY / L3 NOT RUN / G-02 G-08 OPEN / v0.1 NOT RELEASE READY。**

本卡是可执行的**审批后非生产演练步骤**；不授权任何人连接生产、复制员工数据库、触碰生产密钥或执行破坏性操作。唯一上位验收条件见 [备份操作边界](backup.md) 和 [WORM-01～11](../security/independent-worm-custody-acceptance-d4-c18g-2026-11-22.md)。[已通过的 GitHub 双 runner L2](../development/linux-pg18-cross-runner-artifact-restore-l2-2026-11-26.md) 不是此项 L3。

## 进入演练前的 6 个独立批准条件（均 NOT RUN）

| 前置 | 必须由谁提供；不能接受什么 |
| --- | --- |
| A. 变更与隐私 | 授权实体/测试时间/停测标准/仅合成数据，具名法务、安全、SRE 非生产审批；**没有许可即不开始** |
| B. 管理域映射 | 独立保管人审计应用管理员/PG superuser/Hypervisor-root/介质删除权/快照/备份/KMS/恢复/可信时间/紧急提权的**最终控制者**，不是不同用户名的假分权 |
| C. 可追溯介质 | 保管人从独立保管介质交付原生 PG18 custom archive；记录介质与版本 ID、合法保留期、双人交接、独立只读见证时间及**外部** SHA256 和高水位；不要把归档上传公共 Actions |
| D. 新旧实体机器 | 源机和恢复机不同且可由独立 SRE 查证实际硬件/资产登记和机房/物理访问记录；仅 hostname 不足为证。恢复机为**专用隔离测试机**，不可与业务/共享 Docker host 混用 |
| E. 供应链与操作权限 | 恢复机事先安装并审过 `postgres:18.7` OCI 镜像及 Docker，非 root、私有目录、无发布端口/公网出站；运行 Docker 的账户实际上具有宿主机高权限，必须纳入权限矩阵 |
| F. 可恢复范围 | DB 全量清单/子表/Flyway、WAL/PITR 恢复点、配置、密钥封存、CA、证书/附件依赖和恢复顺序有逐项记录；单份 `pg_dump` **不能替代** WAL/PITR 或系统整体恢复 |

任一关键批准缺失→**BLOCKED / NOT RUN**，不得生成“已验收”签字表。

## SRE 非生产恢复执行（目标机，必须事先独立授权）

使用[隔离恢复脚本](../../apps/server/scripts/pg18-physical-dr-lab.sh)；脚本从不在 CI/Agent/服务器自动触发，不会向生产数据库连接、覆写现有库、切流或修改宿主机服务。仅在指定的独立测试机器执行。用独立保管人外部记录的摘要，不能从同一个可改写的归档目录照抄“见证”文件。

```bash
# 审批后，由恢复机操作员在非 root 会话手动执行；值仅为占位符。
# 事先把批准的合成 custom pg_dump 放入只属于操作者的私有目录，
# 本机已存在、经审核的 postgres:18.7 镜像；脚本不会拉取新镜像。
LANVERIQ_G08_SACRIFICIAL_LAB_CONSENT=YES \
  bash apps/server/scripts/pg18-physical-dr-lab.sh --lab-only \
  /private/approved-synthetic-pg18.dump \
  '<independent-custodian-pinned-archive-sha256>' \
  '<independent-custodian-pinned-full-row-and-flyway-sha256>' \
  '<source-host-inventory-label>'
```

第二个全行指纹是[共享的 PostgreSQL SQL 序列化](../../apps/server/scripts/pg18-inventory-canonical-fingerprint.sql)的 SHA256，必须由源环境**外部独立保管并验签后**提供；仅提供原始 64 位小写十六进制摘要不代表保管可信。脚本严格拒绝未批准的参数、root、GitHub CI、非私有/软链接文件、同名源机、摘要不匹配、无本地批准镜像等；成功时原生 `pg_restore --single-transaction` 到**新建无网络 PostgreSQL 18.7 临时容器**，重启并复核 inventory/Flyway，退出即销毁临时实例。输出的 `PASS` 仅证明**操作者当前主机的孤立数据库恢复**，仍是 `REVIEW_REQUIRED`；不能证明不同物理实体、可信签名、高水位和生产切流。

## 必须保留的现场证据（敏感内容存独立私有保管库，不放 GitHub）

1. **批准记录**：具名审批人、变更票、合法合成范围、不可变存档位置、停测/回滚负责人及允许时间窗。
2. **独立保管**：保管域与源数据库最终控制权图、WORM 介质/对象版本与回读、KMS 与密钥撤销权限、外部可信时间/高水位、权限负测原文、失败记录。
3. **机器与传输**：源/目标硬件资产和物理地理证据、系统/PG18/Docker 版本、源/目标不同 root/管理账户的不可互相控制证据；主机名只能辅助。
4. **恢复原生证据**：源库一致恢复点与最后已确认写入、独立备份和指纹、隔离 `pg_restore` 日志、Flyway/全行摘要、服务恢复功能检查、失败回滚及所有销毁确认。**不存生产库原文或私钥**。
5. **时间测量**：外部核实的故障实际开始时间、最后可恢复的持久写入时间、真实服务功能恢复完成时间；**RPO = 故障发生前尚未恢复的数据时间跨度**，**RTO = 故障发生到业务恢复完成**。脚本打印的仅是本机 SQL 恢复耗时，不是 RPO/RTO。试行目标 RPO ≤24h、RTO ≤4h，超过目标则 FAIL。
6. **独立签收**：不可自审的 SRE、Security、Legal 逐人核对原始证据和所有 WORM-01～11；所有争议、失败和未执行项目按 FAIL/BLOCKED/NOT RUN 留存，不能以“工具 PASS”或文档模拟签字替代。

**验收签字状态：NOT RUN。** 该程序没有被本仓库 CI 调用执行真实跨物理机恢复，也没有独立托管人、真实受信任签章或 RPO/RTO。下一次真实演练前，先指定独立保管域及审批责任人；之后才按本卡**在获批准的非生产实体机器**执行，并将敏感证明放到保管域，公开项目只写不含敏感数据的签收索引。**G-02/G-08 OPEN；v0.1 NOT RELEASE READY；生产默认 OFF。**
