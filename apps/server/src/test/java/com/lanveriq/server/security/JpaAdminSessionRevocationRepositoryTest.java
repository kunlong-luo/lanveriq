package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

class JpaAdminSessionRevocationRepositoryTest {

    @Test
    void updateRequiresLiveOwnerGroupAndTargetGroupThenAudits() {
        var em = Mockito.mock(EntityManager.class);
        var update = Mockito.mock(Query.class);
        var audit = Mockito.mock(Query.class);
        when(em.createNativeQuery(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            return sql.contains("UPDATE users") ? update : audit;
        });
        when(update.setParameter(anyString(), Mockito.any())).thenReturn(update);
        when(audit.setParameter(anyString(), Mockito.any())).thenReturn(audit);
        when(update.getResultList()).thenReturn(List.of(7L));
        UUID issuer = UUID.randomUUID(), target = UUID.randomUUID(), group = UUID.randomUUID();

        long version = new JpaAdminSessionRevocationRepository(em)
                .rotateAllSessions(issuer, target, group);

        assertEquals(7L, version);
        var capturedSql = ArgumentCaptor.forClass(String.class);
        verify(em, Mockito.times(2)).createNativeQuery(capturedSql.capture());
        String sql = capturedSql.getAllValues().getFirst();
        assertTrue(sql.contains("session_version + 1"));
        assertTrue(sql.contains("assigned.device_group_id = :group_id"));
        assertTrue(sql.contains("issuer.disabled_at IS NULL"));
        assertTrue(sql.contains("owner_security_admin"));
        verify(update).setParameter("issuer_id", issuer);
        verify(update).setParameter("target_id", target);
        verify(update).setParameter("group_id", group);
        verify(audit).executeUpdate();
    }

    @Test
    void nonexistentOrWrongScopeFailsWithoutAudit() {
        var em = Mockito.mock(EntityManager.class);
        var update = Mockito.mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(update);
        when(update.setParameter(anyString(), Mockito.any())).thenReturn(update);
        when(update.getResultList()).thenReturn(List.of());

        assertThrows(AccessDeniedException.class, () ->
                new JpaAdminSessionRevocationRepository(em)
                        .rotateAllSessions(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));

        verify(update, never()).executeUpdate();
        verify(em, Mockito.times(1)).createNativeQuery(anyString());
    }
}
