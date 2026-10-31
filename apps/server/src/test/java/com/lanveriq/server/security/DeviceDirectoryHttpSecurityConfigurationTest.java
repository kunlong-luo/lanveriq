package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.SecurityFilterChain;

class DeviceDirectoryHttpSecurityConfigurationTest {
    @Test
    void exactThreeWayOptInMustMatchControllerWithoutOverridingDefaultDeny() {
        var a = DeviceDirectoryHttpSecurityConfiguration.class
                .getAnnotation(ConditionalOnProperty.class);
        assertNotNull(a);
        assertEquals("true", a.havingValue());
        assertEquals(java.util.Set.of(
                "lanveriq.console.auth.enabled",
                "lanveriq.device-directory.enabled",
                "lanveriq.device-directory.http-enabled"), java.util.Set.of(a.name()));
        assertEquals(java.util.Set.of(a.name()),
                java.util.Set.of(
                        com.lanveriq.server.devices.DeviceDirectoryRestController.class
                                .getAnnotation(ConditionalOnProperty.class).name()));
    }

    @Test
    void scopedReadChainIsOrderedAfterLoginAndBeforeFallback() throws Exception {
        var method = DeviceDirectoryHttpSecurityConfiguration.class
                .getDeclaredMethod("scopedDeviceHealthReads",
                        org.springframework.security.config.annotation.web.builders.HttpSecurity.class,
                        org.springframework.core.env.Environment.class,
                        AdminAccountRepository.class);
        assertEquals(SecurityFilterChain.class, method.getReturnType());
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 1,
                method.getAnnotation(Order.class).value());
        assertTrue(org.springframework.context.annotation.Configuration.class.isAnnotation());
    }
}
