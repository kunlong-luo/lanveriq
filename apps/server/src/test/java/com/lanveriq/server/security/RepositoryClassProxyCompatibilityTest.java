package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL integration Spring contexts apply exception translation and
 * transaction proxies to @Repository beans. A final repository class can
 * compile and pass Mockito tests, but CGLIB fails to create its proxy before
 * Flyway-backed integration tests can even start. This is a no-DB regression
 * test of every candidate repository's real class-proxy compatibility.
 */
class RepositoryClassProxyCompatibilityTest {

    @Test
    void everyRealJpaRepositoryIsSubclassProxyable() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Repository.class));
        var beans = scanner.findCandidateComponents("com.lanveriq.server");
        assertTrue(beans.size() >= 13, "All JPA repositories must be scanned");
        for (var bean : beans) {
            var type = Class.forName(bean.getBeanClassName());
            assertFalse(Modifier.isFinal(type.getModifiers()),
                    () -> "Spring repository may not be final: " + type.getName());
            var factory = new ProxyFactory();
            factory.setTargetClass(type);
            factory.setProxyTargetClass(true);
            factory.addAdvice((MethodInterceptor) invocation -> invocation.proceed());
            var proxy = factory.getProxy();
            assertTrue(type.isInstance(proxy),
                    () -> "Spring cannot CGLIB proxy: " + type.getName());
        }
    }
}
