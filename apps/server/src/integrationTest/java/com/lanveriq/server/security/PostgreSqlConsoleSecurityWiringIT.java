package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Manual-only actual Boot application component-scan + PG18 + Servlet/Spring
 * Security acceptance. Unlike the @WebMvcTest slice, this test NEVER imports
 * individual authentication configuration classes to make them appear.
 *
 * MockMvc exercises actual Spring filters and servlet routing; this remains
 * distinct from a real Tomcat TLS/ALPN, Caddy or browser acceptance.
 * No user/device is created, no credentials are supplied, no Agent APIs run.
 */
@Testcontainers
@AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "lanveriq.bootstrap.enabled=false",
        "lanveriq.console.auth.enabled=true",
        "lanveriq.console.auth.origin=https://console.lanveriq.internal",
        "lanveriq.device-directory.enabled=true",
        "lanveriq.device-directory.http-enabled=true",
        "lanveriq.monitoring.overview-http-enabled=true"
})
class PostgreSqlConsoleSecurityWiringIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("lanveriq_console_security_wiring_synthetic")
            .withUsername("lanveriq_test")
            .withPassword("synthetic-test-only");

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mvc;

    @Test
    void bootDiscoversConditionalConsoleChainsAndBlocksAnonymousReads()
            throws Exception {
        // Regression for the real 403: a @WebMvcTest can pass by importing a
        // security config that component scanning did not actually discover.
        for (String bean : new String[]{
                "consoleAuthenticationOnly",
                "scopedDeviceHealthReads",
                "consoleMonitoringOverviewReads",
                "denyBusinessRoutes"}) {
            assertTrue(context.containsBean(bean),
                    "Real Boot servlet context must construct security chain " + bean);
        }
        mvc.perform(get("/api/v1/auth/session")
                        .header("Host", "console.lanveriq.internal"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/v1/auth/csrf")
                        .header("Host", "console.lanveriq.internal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.csrfToken").isNotEmpty());
        mvc.perform(get("/api/v1/devices")
                        .header("Host", "console.lanveriq.internal"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/monitoring/overview")
                        .header("Host", "console.lanveriq.internal"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/devices/nonexistent/activate")
                        .header("Host", "console.lanveriq.internal"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/auth/csrf")
                        .header("Host", "agent.lanveriq.internal"))
                .andExpect(status().isForbidden());
    }
}
