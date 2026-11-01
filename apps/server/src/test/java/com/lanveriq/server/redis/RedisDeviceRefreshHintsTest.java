package com.lanveriq.server.redis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisDeviceRefreshHintsTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(RedisFeatureConfiguration.class)
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class));

    @Test
    void offByDefaultWithNoRedisServerOrBusinessBean() {
        runner.run(context -> assertFalse(context.containsBean("redisDeviceRefreshHints")));
    }

    @Test
    void explicitOptInCreatesOnlyTransientHintBeanWithoutConnecting() {
        runner.withPropertyValues("lanveriq.redis.enabled=true").run(context ->
                assertTrue(context.containsBean("redisDeviceRefreshHints")));
    }

    @Test
    void storesOnlyOpaqueUuidHintWithBoundedTtl() {
        var template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        var hints = new RedisDeviceRefreshHints(template);
        var device = UUID.fromString("018f2e10-7800-7000-8000-000000000001");
        assertTrue(hints.bestEffortMarkChanged(device));
        verify(values).set(eq(RedisDeviceRefreshHints.PREFIX + device),
                eq("1"), eq(RedisDeviceRefreshHints.TTL));
        when(values.get(RedisDeviceRefreshHints.PREFIX + device)).thenReturn("1");
        assertTrue(hints.bestEffortHasHint(device));
        when(values.get(RedisDeviceRefreshHints.PREFIX + device)).thenReturn(null);
        assertFalse(hints.bestEffortHasHint(device));
    }

    @Test
    void transientRedisFailureCannotInventAStatusOrPreventDbCommit() {
        var template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(template.opsForValue()).thenReturn(values);
        var hints = new RedisDeviceRefreshHints(template);
        var device = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("synthetic Redis unavailable"))
                .when(values).set(RedisDeviceRefreshHints.PREFIX + device,
                        "1", RedisDeviceRefreshHints.TTL);
        assertFalse(hints.bestEffortMarkChanged(device));
        when(values.get(RedisDeviceRefreshHints.PREFIX + device))
                .thenThrow(new DataAccessResourceFailureException("synthetic Redis unavailable"));
        assertFalse(hints.bestEffortHasHint(device));
    }
}
