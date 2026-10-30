# Contributing

Lanveriq is in a documentation-only planning phase. Please discuss scope and architecture in repository issues before submitting significant changes. Do not post real employee information, screenshots, telemetry, authentication tokens or recordings.

## Early development workflow
Maintainer has chosen **direct commits to main** (no mandatory PR or feature branches) for early v0.x work. Prefer small, reversible commits with clear `docs:`, `feat:`, `fix:` and `test:` subjects. Do not force-push main. If collaborators join or production deployments begin, revisit the policy.

## Reuse-first dependency rule
**Mandatory for every future architecture/coding change:** read [root AGENTS.md](AGENTS.md), [reuse-first engineering policy](docs/development/reuse-first-policy.md) and [ADR-0007](docs/architecture/adrs/0007-mature-ecosystem-first.md). Prefer stable built-ins, official SDKs and vetted maintained libraries over new homegrown frameworks; **UUIDs, database migrations, HTTP/TLS, API code generation, OS services and UI infrastructure should never be reimplemented casually**. Fewer tests of vendor internals is sensible, but authorization, retries, reliability and cross-platform integration **still require verification**.

## Before adding code
Read the [docs index](docs/README.md), [PRD](docs/product/requirements.md), [privacy](docs/security/privacy-and-consent.md), [ADRs](docs/architecture/adrs/0004-rapid-main-development.md), and [D0 gate](docs/governance/documentation-gate.md). No code until D0 review approval.

## Tests
During early MVP, GitHub CI will run only Rust/Java/React unit tests **after those projects exist**; E2E is postponed. Local build/lint/typechecks and targeted manual security validation are recommended. Do not skip tests by claiming a feature is complete without evidence.

## Licensing
The repository is publicly readable but has not yet selected a software license. Do not assume contributions or reuse rights; licensing and contribution terms must be resolved before a broader open-source contribution process.
