# FIDO MDS 信任链边界总表

**ACTIVE · 2026-11-22 · v0.1 NOT RELEASE READY。** 本表不替代 [CURRENT_STATUS](../CURRENT_STATUS.md) 的实际 CI/SHA。

| 已有阶段 | 已验证的离线合成能力 | 仍然没有证明 |
| --- | --- | --- |
| [D4-C9](fido-mds-payload-screen-d4-c9-2026-11-21.md)–[C11](fido-mds-duplicate-json-d4-c11-2026-11-21.md) | JWS 结构、重复键/模型拒绝 | 官方 BLOB 来源 |
| [C12](fido-mds-independent-provenance-d4-c12-2026-11-21.md) | 两把临时审核公钥签名绑定 | 两位真人审核与密钥托管 |
| [C13](fido-mds-public-r46-root-reference-d4-c13-2026-11-22.md) | 公开 GlobalSign R46 SHA-256 精确核对 | 经组织批准的根 |
| [C14](fido-mds-acquisition-receipt-d4-c14-2026-11-22.md) | 签名获取收据绑定 JWS/CRL | 真实官方 CRL/CDP 下载、HTTPS 来源 |
| [C15](fido-mds-acquisition-ledger-d4-c15-2026-11-22.md) | PG18 V30 DB-owned 摘要链/原子审计 | DB 管理员外部防回退见证 |
| [C16](fido-mds-detached-witness-d4-c16-2026-11-22.md) | 真实 PG18 数据库尾记录和离线见证签名比对 | 实际外部 WORM/KMS/可信时间或组织审计 |
| [C17](fido-mds-anchored-history-d4-c17-2026-11-22.md) | 完整离线见证历史/高水位校验，**同 SHA 真实 PG18 11 类≥148 测试 PASS**（[证据](fido-mds-anchored-history-d4-c17-2026-11-22.md)） | 外部 WORM 高水位保管、KMS/可信时间与真实防回退实测 |
| [C18a](fido-mds-db-owner-rewind-d4-c18a-2026-11-22.md) | 真实 PG18 owner 级删除回退合成负例已 **7/7 同 SHA PASS**，宿主文件保留高水位 | 未演练真实备份恢复/外部 Object Lock/KMS/WORM；不能关闭 G-02 |
| [C18b](fido-mds-real-pg-restore-d4-c18b-2026-11-22.md) | 真正 PostgreSQL 18.6 pg_dump/pg_restore 旧拒绝/新接受双向验收 **7/7 同 SHA PASS** | 尚无跨管理域 WORM/KMS、可信时间、组织审批、Pop!_OS L3 |
| [C18c](fido-mds-object-lock-version-readback-d4-c18c-2026-11-22.md) | 官方 AWS SDK 离线 S3 versionId/bytes SHA-256/COMPLIANCE 双响应负向检查，**6/6 同 SHA CI PASS（L1，真实 PG18 仅旧回归）** | 真实 S3 Object Lock/KMS、跨域签名高水位、可信时间与 AWS 删除/改保留实测均未做 |
| **C18d+** | 尚未批准/实现生产可信链 | 官方信任、外部审计、真人 MFA、生产 CA 和 Pop!_OS L3 |

签名有效≠受信来源；公开证书≠组织批准；数据库本地不可变≠外部见证；合成 CI≠上线。所有 `UntrustedSourceEvidence`、`UntrustedAcquisitionEvidence`、`UntrustedDetachedWitnessMatch` 均不得提升为真实 WebAuthn/设备身份/CA 权限。[发布 Gate](../planning/release-gap-register.md) 仍 OPEN。
