# Definition of done (DOD)

## Docs gate D0
- Requirements, architecture, server/agent storage, API, collection policy, threat model, testing, roadmap and operational plan exist and cross-reference consistently.
- No false implementation claims; unresolved jurisdiction and data scope documented.
- Maintainer reviews and accepts architecture + privacy scope before adding application code. **D-07 device certificate/route/CA and authenticated proxy identity** and the V0.1 schema/permission surface must be explicitly approved; unresolved D0 rows may not be silently treated as satisfied.
- English README and Chinese introduction consistent; internal relative links checked. **Static Mermaid/SVG validation does not replace actual rendering checks;** record unverified screenshot/Chinese font layout separately.
- Direct-main, unit-only CI and deferred E2E written as an explicit temporary ADR.

## Feature completion during v0.x
- Requirements/behavior explained; change committed atomically to main.
- Associated unit tests pass locally and, once implemented, GitHub CI. No requirement to add an E2E suite yet.
- Developer checks touched modules using formatter, typecheck/build where appropriate; CI **only** runs unit tests.
- Security-sensitive pathways receive documented targeted *manual* verification; missing checks labeled unverified.
- API schema and data migrations updated if changed; fail-safe on platform permission denial.
- No sensitive sample data, secrets, covert capabilities or unsupported compatibility claims.

## Release readiness (not early MVP)
Before first external real-data deployment or production label: cross-process integration checks, platform acceptance, installer signing evaluation, restore drill, penetration/privacy review, and CI strategy reassessment. These future gates do not block writing MVP code after D0, but they block production claims.
