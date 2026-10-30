package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AdminAuthenticationAuditTest {

    @Test
    void unknownLoginFailureAndThrottlingNeverStoreSuppliedUsernameOrNetworkIp() {
        var em = Mockito.mock(EntityManager.class);
        var query = Mockito.mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        var audit = new AdminAuthenticationAudit(em);

        audit.loginDenied();
        audit.loginThrottled();

        var sql = ArgumentCaptor.forClass(String.class);
        verify(em, Mockito.times(2)).createNativeQuery(sql.capture());
        assertTrue(sql.getAllValues().get(0).contains("'denied'"));
        assertTrue(sql.getAllValues().get(1).contains("'rate_limited'"));
        for (var statement : sql.getAllValues()) {
            assertFalse(statement.contains("password"));
            assertFalse(statement.contains("username"));
            assertFalse(statement.contains("remote_ip"));
        }
    }

    @Test
    void allowedLoginAndLogoutWriteOnlyBoundPrincipalUuid() {
        var em = Mockito.mock(EntityManager.class);
        var query = Mockito.mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), Mockito.any())).thenReturn(query);
        UUID id = UUID.randomUUID();
        var audit = new AdminAuthenticationAudit(em);
        audit.loginAllowed(id);
        audit.logoutAllowed(id);
        verify(query, Mockito.times(2)).setParameter("principal", id);
        verify(query, Mockito.times(2)).executeUpdate();
    }
}
