# Gradle 9.8.2 + Spring Boot 4.1.2 — Groovy DSL reference

**Reference example (retained for design history).** The actual [SRV-01 Gradle project](../../apps/server/build.gradle) and [server README](../../apps/server/README.md) were committed after D0 approval, including an official tagged Gradle 9.8.2 wrapper JAR with verified SHA-256. This reference is **not the authoritative live build script**; read actual files and unit-CI results for current behavior. This follows the maintainer's example style but excludes unrelated Kafka/Redis/InfluxDB/geo/OTel dependencies; **Spring Data JPA/Hibernate is selected**. Canonical version policy: [backend-stack.md](backend-stack.md).

## Proposed `apps/server/build.gradle`

~~~groovy
plugins {
    id 'java'
    id 'jacoco'
    id 'org.springframework.boot' version '4.1.2'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'com.lanveriq'
version = '0.1.0-SNAPSHOT'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    // If an approved network mirror is necessary, add it explicitly
    // and verify artifact source, TLS and repository precedence.
}

dependencies {
    // Spring Boot 4 MVC / security / WSS / validation
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-websocket'
    implementation 'org.springframework.boot:spring-boot-starter-validation'

    // PostgreSQL: JPA/Hibernate repositories, connection pool and migrations
    // Includes Spring JDBC + HikariCP transitively; no direct JDBC starter.
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-flyway'
    implementation 'org.flywaydb:flyway-database-postgresql'
    runtimeOnly 'org.postgresql:postgresql'

    // Internal-only diagnostics and controlled OpenAPI JSON
    implementation 'org.springframework.boot:spring-boot-starter-actuator'
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-api:3.2.0'

    // Early CI: real unit tests only, no container-dependent tests
    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

jacoco {
    toolVersion = '0.8.16'
}

tasks.named('test') {
    useJUnitPlatform()
    finalizedBy tasks.named('jacocoTestReport')
}

tasks.named('jacocoTestReport') {
    dependsOn tasks.named('test')
    reports {
        xml.required = true
        html.required = true
    }
}

// No unconditional 83% coverage gate during the first real unit-test phase.
// Review measured coverage and add a meaningful threshold after the baseline exists.
~~~

**JPA runtime defaults (application configuration, not build.gradle):** use `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`. Hibernate does not automatically implement PostgreSQL idempotency: event batches and revision-aware inventory updates must use versioned, parameter-bound native SQL inside JPA transactions and verified unique constraints. Validate DB-default UUIDv7 identity generation against real PostgreSQL 18.

**Security is in application configuration, not a build-script toggle:** disable `springdoc.api-docs.enabled` in deployment/production and restrict non-health Actuator endpoints. Do not expose Spring management ports on the LAN. Do not enable `spring-boot-starter-cache` or blanket `@Cacheable` on identity/device-group authorization without correct revocation/invalidation.

## Proposed `apps/server/settings.gradle`

~~~groovy
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = 'lanveriq-server'
~~~

## Wrapper and invocation

**Actual wrapper is already committed; for future official wrapper regeneration/upgrade**, from `apps/server` with an approved Gradle installation:

~~~bash
gradle wrapper --gradle-version 9.8.2 --distribution-type bin
./gradlew --version
./gradlew test
./gradlew jacocoTestReport
./gradlew bootJar
~~~

Commit **both** wrapper scripts and their generated `gradle/wrapper/gradle-wrapper.jar` (Gradle-provided binary, not an arbitrary downloaded agent JAR) plus `gradle-wrapper.properties`. Review the Wrapper JAR checksum; optionally add a verified distribution SHA-256 in `gradle-wrapper.properties`.

Do not commit Gradle caches, `.gradle/`, `build/`, the full distribution ZIP, or downloaded OpenTelemetry Javaagent JARs. Unlike the user example, **do not make every build download an unused OTel javaagent**.

The early CI Java job should run **only `./gradlew test`** from `apps/server`, with real test source. The JaCoCo HTML/XML report is available when tests run; JUnit 6 is imported via Spring Boot's dependency BOM.

## Explicitly excluded from the reference

`spring-boot-starter-jdbc` (direct), `spring-boot-starter-data-redis`, `spring-boot-starter-kafka`, InfluxDB, JTS/Hibernate Spatial, Lombok, MapStruct, Zen Engine, OpenTelemetry agent, Testcontainers Kafka/PostgreSQL and unrelated log encoders are **not part of V0.1**. Review additions by specific feature/benchmark, rather than cloning an IoT application's entire build.
