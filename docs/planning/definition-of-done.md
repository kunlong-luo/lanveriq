> **2026-11-13 当前状态优先说明：** [Linux-first 上线差距与验收账本](release-readiness-2026-11-13.md) 是最新证据入口。`main` `758f8d9a` 的 Rust→Caddy→Tomcat TLS1.3→PG18 严格 ACK 已通过 [隔离 L2 #43356545857](https://github.com/kunlong-luo/lanveriq/actions/runs/43356545857)，不应再按旧记录认定“完全未运行”。但整批 A/B/C/D/E、正式 Pop!_OS 设备运行、Next.js/WSS/生产部署仍未完成；首发范围收敛到 **Pop!_OS 24 Linux-only**，Windows 11 另行 L3 验收，远控 RD-2 继续 OFF。本文原有时间线保留为历史，任何生产或真实数据启用须单独获批。

# Definition of done (DOD)

## Docs gate D0
**Status as of 2026-10-30: D0 technical baseline approved for V0.1 coding**, with selected PKI, versioned API, minimum authorization and pilot target recorded in [signed D0 decision package](../governance/d0-decision-package-2026-10-30.md). All bullets below were **design-review criteria**, not evidence that software runs. Separate organizational/legal permission and real security/OS/DB verification remain mandatory before a real-data pilot.

- Requirements, architecture, server/agent storage, API, collection policy, threat model, testing, roadmap and operational plan exist and cross-reference consistently.
- No false implementation claims; unresolved jurisdiction and data scope documented.
- Maintainer reviews and accepts architecture + privacy scope before adding application code. **D-07 device certificate/route/CA and authenticated proxy identity** and the V0.1 schema/permission surface must be explicitly approved; unresolved D0 rows may not be silently treated as satisfied.
- English README and Chinese introduction consistent; internal relative links checked. **Static Mermaid/SVG validation does not replace actual rendering checks;** record unverified screenshot/Chinese font layout separately.
- The earlier direct-main, unit-only CI decision is preserved as **historical ADR-0004**; the **2026-11-08 owner-approved L1/L2/L3 test model supersedes it**. See [active test strategy](../testing/strategy.md).

## Feature completion during v0.x
- Requirements/behavior explained; change committed atomically to main.
- **L1:** related source/typed DTO/security negative unit tests, format, type and compile checks are green on the tested SHA; never mark a default-OFF feature production-ready by typecheck alone.
- **L2:** a cohesive P0 business batch (A, B, C or D) cannot be marked completed until its relevant disposable PostgreSQL/Caddy/Agent/Next.js integration and negative cases have actually executed and passed; avoid entire E2E on every tiny push.
- **L3:** v0.1 milestone/first pilot requires the complete security browser/cross-platform suite, real authorized Pop!_OS Linux acceptance for the Linux-only release (Windows native L3 separately required for Windows support), PKI/key custody, restore and privacy/operations approvals. Missing or unexecuted checks remain explicitly OPEN.
- API schema and data migrations updated if changed; fail-safe on platform permission denial.
- No sensitive sample data, secrets, covert capabilities or unsupported compatibility claims.

## Release readiness (not early MVP)
Before first external real-data deployment or production label: cross-process integration checks, platform acceptance, installer signing evaluation, restore drill, penetration/privacy review, and explicit release approval. The updated CI strategy is recorded in the [batch plan](v0.1-batched-delivery-plan-2026-11-08.md). These future gates do not block writing MVP code after D0, but they block production claims.
