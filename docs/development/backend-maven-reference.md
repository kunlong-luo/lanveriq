# Spring Boot 4.1.2 — proposed Maven POM reference (not a generated project)

**Documentation-only example**, to be copied to `apps/server/pom.xml` after D0 owner approval and then locally built/tested. See [backend stack/versions](backend-stack.md).

~~~xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.1.2</version>
    <relativePath/>
  </parent>

  <groupId>com.lanveriq</groupId>
  <artifactId>lanveriq-server</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <name>Lanveriq Server</name>
  <description>LAN-only modular-monolith API and admin WebSocket notifier</description>
  <properties>
    <java.version>25</java.version>
  </properties>

  <dependencies>
    <!-- REST / Servlet / WebSocket -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-websocket</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- Explicit SQL / PostgreSQL / migrations -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-flyway</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-database-postgresql</artifactId>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>

    <!-- Restricted diagnostics and controlled OpenAPI JSON -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springdoc</groupId>
      <artifactId>springdoc-openapi-starter-webmvc-api</artifactId>
      <version>3.2.0</version>
    </dependency>

    <!-- Unit testing only in early GitHub CI -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-security-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
~~~

**Important:** This is a **proposed, uncompiled sample**. Do not claim artifacts, tests, or OS packaging exist. Spring Boot parent manages dependency and plugin versions where applicable; do not add scattered explicit versions. For local developer use enable `springdoc.api-docs.enabled` only behind an internal trusted development profile and **set it false in deploy/prod configuration**; block sensitive Actuator endpoints at proxy and Spring Security layers.

Once code exists, generate Maven Wrapper and verify `./mvnw -version`, `./mvnw -q test`, `./mvnw -q package` locally, then add GitHub CI running **only real unit tests**. Do not add `-DskipTests` or dummy smoke jobs to create a green badge.
