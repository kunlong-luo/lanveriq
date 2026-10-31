package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lanveriq.server.devices.DeviceDirectoryRestController;
import com.lanveriq.server.devices.DeviceDirectoryService;
import com.lanveriq.server.devices.MonitoringOverviewRestController;
import com.lanveriq.server.devices.MonitoringOverviewService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Source-generated OpenAPI, NOT a hand-authored schema. The nested app is a
 * synthetic-only build-time context with no DB, Docker, TLS or real login.
 * It exposes /v3/api-docs ONLY in this test's MockMvc runtime, never the
 * real application where docs remain disabled and business routes deny.
 */
@SpringBootTest(classes = DeviceDirectoryOpenApiContractTest.ContractOnlyApplication.class,
        properties = {
                "springdoc.api-docs.enabled=true",
                "springdoc.swagger-ui.enabled=false",
                "springdoc.paths-to-match=/api/v1/devices,/api/v1/devices/*,/api/v1/monitoring/overview",
                "lanveriq.console.auth.enabled=true",
                "lanveriq.console.auth.origin=https://console.lanveriq.internal",
                "lanveriq.device-directory.enabled=true",
                "lanveriq.device-directory.http-enabled=true",
                "lanveriq.monitoring.overview-http-enabled=true",
                "spring.main.lazy-initialization=false"
        })
@AutoConfigureMockMvc
class DeviceDirectoryOpenApiContractTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            FlywayAutoConfiguration.class
    })
    @Import({DeviceDirectoryRestController.class,
            DeviceDirectoryHttpSecurityConfiguration.class,
            MonitoringOverviewRestController.class,
            MonitoringOverviewHttpSecurityConfiguration.class})
    static class ContractOnlyApplication {
        // Spring Security docs permission exists ONLY in this nested test
        // application. Never relax the main application's deny-all filter.
        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        SecurityFilterChain allowGeneratedDocsOnlyForTest(HttpSecurity http)
                throws Exception {
            return http.securityMatcher("/v3/api-docs", "/v3/api-docs/**")
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.GET, "/v3/api-docs",
                                    "/v3/api-docs/**").permitAll()
                            .anyRequest().denyAll())
                    .build();
        }
    }

    @Autowired private MockMvc mvc;
    @MockitoBean private DeviceDirectoryService directory;
    @MockitoBean private MonitoringOverviewService monitoring;
    @MockitoBean private AdminAccountRepository accounts;

    @Test
    void springdocReallyGeneratesMachineReadableRestContract() throws Exception {
        var response = mvc.perform(get("/v3/api-docs")
                        .header("Host", "console.lanveriq.internal"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        String body = response.getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("\"openapi\""));
        assertTrue(body.contains("\"/api/v1/devices\""));
        assertTrue(body.contains("\"/api/v1/devices/{deviceId}\""));
        assertTrue(body.contains("\"/api/v1/monitoring/overview\""));
        assertTrue(body.contains("\"consoleSession\""));

        Path output = Path.of("build", "contracts", "openapi.json");
        Files.createDirectories(output.getParent());
        Files.writeString(output, body, StandardCharsets.UTF_8);
    }
}
