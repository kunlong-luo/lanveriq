# Monorepo layout and engineering conventions

**Current repo is docs-only. The tree below is planned and should not be pre-created with empty implementation packages.**

~~~text
lanveriq/
  apps/
    agent/                  # Rust service executable
    desktop/                # optional Tauri user-session UI
    server/                 # Java 25 + Spring Boot 4.1.2, Gradle Groovy build.gradle + Wrapper
    dashboard/              # React/Vite management UI
  crates/
    agent-core/
    agent-store/
    platform-windows/
    platform-linux/
    audit/
    transport/
    capture/                # later
  packages/
    api-types/              # if needed after API contracts stabilize
    ui/                     # shared UI (avoid overabstraction)
  deploy/
    compose.yaml
    caddy/
  docs/
    product/ architecture/ security/ development/
    planning/ testing/ operations/ governance/
  .github/workflows/
  README.md README.zh.md CHANGELOG.md
~~~

## Naming
Rust: snake_case modules, PascalCase types; Java: conventional packages such as `com.lanveriq.server.devices`; frontend: feature folders and explicitly named hooks/components. API resources kebab-case; JSON field names snake_case across server and agent with documented mapping in TS client. UUID device/event identifiers, UTC timestamps.

## Java backend build
**Gradle 9.8.2 Wrapper**, Groovy DSL `build.gradle`, Spring Boot Gradle plugin **4.1.2**, Java 25 toolchain and JaCoCo 0.8.16. Start with one `apps/server` Gradle module; Java domain packages stay logical modules. Use Spring JDBC/`JdbcClient` + Flyway for explicit PostgreSQL transactions. **Do not introduce Maven, a second Java build system or all dependencies from the unrelated IoT reference.** See [backend stack](backend-stack.md) and [Gradle reference](backend-gradle-reference.md).

## Versioning
Single pre-release project version, semver tags at verified milestone; Event `schema_version` and API `/api/v1` evolve compatibly. Avoid breaking installed agents without compatibility window. Database migrations via Flyway server and Rust versioned local migrations.

## Commit strategy
**Direct main** during v0.x pre-release; no feature branches or mandatory PRs. Commit in small functional/doc steps with descriptive conventional subjects (`docs:`, `feat:`, `fix:`, `test:`). No force push to main; revert bad commits, don't hide failures. Once more contributors or a production deployment, reconsider branches.

## Working hygiene
Tests for touched pure code, local compile/type/lint, formatting and secrets check; only unit tests run in GitHub CI. No E2E in early development. Document manual validation of auth/permission/durability paths. Don't fake functionality in README or mark unsupported Linux as fully supported.

## Security
No hardcoded secrets, private employee data in fixtures, hidden monitoring, insecure TLS, database credentials in frontend, or network-public PostgreSQL. CI tokens minimal permissions; production logs redacted.
