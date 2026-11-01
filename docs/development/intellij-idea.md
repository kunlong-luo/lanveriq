# IntelliJ IDEA / Gradle / Rust / Web — importing Lanveriq

**Status:** V0.1 development only; project import is not product deployment. No need to start PostgreSQL, Redis, Docker, a real Agent, or monitoring to index and compile source.

## Recommended: open the whole repository (IDEA 2026.x + JDK 25)

1. From the IntelliJ IDEA welcome screen choose **Open** → the **`lanveriq/` repository root** (not `apps/web`). If asked, trust only your own reviewed checkout.
2. The root **`settings.gradle`** declares the composite Gradle build `lanveriq-workspace` with `includeBuild('apps/server')`. The root **`gradlew`, `gradlew.bat` and `gradle/wrapper/`** are exact Git blob copies of the independently supported **Gradle 9.8.2** backend wrapper, *not* a second version.
3. Choose **Project SDK = JDK 25**, and in **Settings → Build, Execution, Deployment → Build Tools → Gradle**, choose **Gradle JVM = JDK 25** and **Distribution = Gradle Wrapper**. Allow dependency downloads during the initial import (disable Gradle Offline Mode if dependencies are not cached).
4. Wait for indexing/Gradle sync. The **Gradle** tool window should show the root composite and included **`lanveriq-server`** build, and the `apps/server/src/main/java` code should be indexed with the Spring Boot 4/JPA dependencies.
5. Java code belongs to **`apps/server`**. The Java Spring project still works by itself with `cd apps/server && ./gradlew test` (Windows: `gradlew.bat test`). **No Maven files**, and no requirement to run either Web or Rust when opening IDEA.

If the root composite is not linked by your IDE version, open the Gradle tool window → **Link Gradle Project** → choose **`apps/server/build.gradle`**. This is the documented official IntelliJ import method. Avoid linking the same Gradle build twice; if necessary unlink the stale entry, then resync.

## Verify the failure outside the IDE

Run from the **repository root**, without starting the server:

~~~powershell
# Windows PowerShell
java -version
.\gradlew.bat --version
.\gradlew.bat projects
cd apps/server
.\gradlew.bat test
~~~

~~~bash
# Linux / macOS; first use chmod +x only in your own checkout if
# executable permissions were lost by an unusual ZIP/Windows transfer
java -version
./gradlew --version
./gradlew projects
cd apps/server
./gradlew test
~~~

The Gradle root project is a **workspace/import adapter**. Its `projects` command confirms root model loading; `apps/server` is a separately runnable included build. No test above contacts an employee device, activates the API or modifies your shared `spark-db`.

## Rust and React in IDEA

- **Rust Agent:** source is at `apps/agent`; Cargo workspace is the root `Cargo.toml`, toolchain `rust-toolchain.toml` pins 1.94.0. Install a compatible Rust plugin or use **RustRover** if necessary; enable Cargo workspace integration separately. Indexing Rust source does not run monitoring.
- **React Web:** source is at `apps/web`, **not a Gradle module**. Use IDE JavaScript/TypeScript support if present. In the IDE terminal: `cd apps/web`, `corepack enable`, `corepack prepare pnpm@12.0.0 --activate`, `pnpm install --frozen-lockfile`, `pnpm dev`. Visit `http://127.0.0.1:5173`; all records are synthetic. A dedicated Web IDE such as WebStorm is an alternative if your IDEA edition lacks relevant JS support.
- **Backend startup:** use the Java Gradle `bootRun` task only when you have **already prepared** the dedicated `lanveriq` development database, scoped account and environment variables in [installation](../operations/installation.md). Do not enable any default-OFF live API, scheduled capture or upload merely to fix the IDE.

## Troubleshooting

| Symptom | Inspect first | Safe fix |
|---|---|---|
| `No Gradle projects found` | Opened root before update / no Gradle sync | `git pull`; reopen root and use **Sync Gradle** or manually link `apps/server/build.gradle` |
| `Unsupported class file major version` / toolchain 25 missing | IDE version, Project SDK and Gradle JVM | Install **JDK 25** and select it for **both** project SDK and Gradle JVM; update IDEA if it lacks Java 25 support |
| `Plugin not found` / dependency downloads fail | Gradle Offline Mode, proxy, certificate trust and access to Plugin Portal/Maven Central | Disable offline mode for first import; use approved network/proxy, do not disable TLS verification |
| `Cannot run GradleWrapperMain` | Checkout integrity / wrapper JAR | Re-check out the tracked wrapper; never download random `gradle-wrapper.jar` binaries |
| Duplicate Java modules | Both root composite and nested Gradle linked | Unlink the duplicate build from the Gradle tool window, keep a single authoritative backend import |
| `web` or `agent` appears as plain files | These are pnpm/Cargo projects | Enable the matching IDE language support; do not convert them to Java modules |
| IDEA sync succeeds but `bootRun` fails | Runtime DB connection / feature flags | Compilation/import succeeded; follow the separate restricted development database runbook instead of broadening DB permissions |

Do **not** delete shared databases, Docker volumes, credentials or your actual runtime data as an IDE troubleshooting technique. Avoid blindly deleting IDE caches if a specific Gradle/JDK error is available.

Authoritative IDE guidance: [JetBrains Gradle project setup](https://www.jetbrains.com/help/idea/gradle.html).
