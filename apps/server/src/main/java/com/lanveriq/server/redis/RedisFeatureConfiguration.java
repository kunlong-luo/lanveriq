package com.lanveriq.server.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/** No Redis business beans unless explicitly opted in. Default server stays DB-only. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "lanveriq.redis", name = "enabled", havingValue = "true")
public class RedisFeatureConfiguration {

    @Bean
    RedisDeviceRefreshHints redisDeviceRefreshHints(StringRedisTemplate template) {
        return new RedisDeviceRefreshHints(template);
    }
}
