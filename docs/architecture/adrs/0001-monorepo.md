# ADR-0001: Single repository and modular monolith

**Status:** Accepted design direction | **Date:** 2026-10-30

## Decision
Use one GitHub repository containing Rust Cargo workspace (agent/core), optional Tauri app, Java/Spring Boot modular-monolith server, and React/Vite dashboard with typed API contract. One deployable API service initially. Development on main during the pre-release phase; branch-policy exception is documented in ADR-0004.

## Reasons
One maintainer, 1–10 endpoints, cross-language contracts, preference for fast iteration and straightforward release artifacts. Separate build tools (Cargo, Maven, pnpm) remain independent; no global orchestration framework is needed.

## Consequences
Easy atomic updates, shared docs, coherent releases. CI must eventually split jobs by affected paths. Strong internal module boundaries prevent tightly coupled modules.

## Revisit
Multiple teams, high-ingest deployment, independent availability/scaling needs or version compatibility changes.
