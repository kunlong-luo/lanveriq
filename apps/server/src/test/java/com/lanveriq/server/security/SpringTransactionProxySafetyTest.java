package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

/**
 * Regression: Spring's class-based transaction proxies must be able to wrap
 * both services. Final service classes bypass/fail proxying and would allow
 * incomplete administrator bootstrap writes outside a managed transaction.
 */
class SpringTransactionProxySafetyTest {

    @Test
    void offlineBootstrapUsesManagedCommit() {
        var repository = Mockito.mock(FirstAdminBootstrapRepository.class);
        var encoder = Mockito.mock(PasswordEncoder.class);
        when(encoder.encode(any(CharSequence.class))).thenReturn("{argon2id}fake-only");
        var expected = new FirstAdminBootstrapRepository.BootstrappedAdmin(
                UUID.randomUUID(), UUID.randomUUID());
        when(repository.createFirstOwner(anyString(), anyString(), anyString()))
                .thenReturn(expected);
        var manager = Mockito.mock(PlatformTransactionManager.class);
        var status = Mockito.mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        var proxy = transactionalProxy(
                new FirstAdminBootstrapService(repository, encoder), manager);

        assertEquals(expected, proxy.bootstrap("admin", "Engineering",
                "synthetic-internal-password-123".toCharArray()));

        verify(manager).getTransaction(any(TransactionDefinition.class));
        verify(manager).commit(status);
    }

    @Test
    void offlineBootstrapRollsBackOnExistingAccount() {
        var repository = Mockito.mock(FirstAdminBootstrapRepository.class);
        var encoder = Mockito.mock(PasswordEncoder.class);
        when(encoder.encode(any(CharSequence.class))).thenReturn("{argon2id}fake-only");
        when(repository.createFirstOwner(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("Initial administrator already exists"));
        var manager = Mockito.mock(PlatformTransactionManager.class);
        var status = Mockito.mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        var proxy = transactionalProxy(
                new FirstAdminBootstrapService(repository, encoder), manager);

        assertThrows(IllegalStateException.class, () ->
                proxy.bootstrap("admin", "Engineering",
                        "synthetic-internal-password-123".toCharArray()));

        verify(manager).rollback(status);
    }

    @Test
    void groupAccessDenialRunsInsideSpringTransactionBoundary() {
        var repository = Mockito.mock(AdminScopeRepository.class);
        var manager = Mockito.mock(PlatformTransactionManager.class);
        var status = Mockito.mock(TransactionStatus.class);
        when(manager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        var proxy = transactionalProxy(new AdminScopeService(repository), manager);

        assertThrows(AccessDeniedException.class, () ->
                proxy.require(UUID.randomUUID(), UUID.randomUUID(),
                        AdminAction.VIEW_NETWORK));

        verify(manager).rollback(status);
    }

    @SuppressWarnings("unchecked")
    private static <T> T transactionalProxy(T target, PlatformTransactionManager manager) {
        var factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAdvice(new TransactionInterceptor(manager,
                new AnnotationTransactionAttributeSource()));
        return (T) factory.getProxy();
    }
}
