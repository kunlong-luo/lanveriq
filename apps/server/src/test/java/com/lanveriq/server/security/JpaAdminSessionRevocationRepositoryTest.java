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

    private static final class Fixture {
        final EntityManager em = Mockito.mock(EntityManager.class);
        final Query userLocks = Mockito.mock(Query.class);
        final Query currentGrants = Mockito.mock(Query.class);
        final Query update = Mockito.mock(Query.class);
        final Query audit = Mockito.mock(Query.class);
        final UUID issuer = UUID.randomUUID();
        final UUID target = UUID.randomUUID();
        final UUID group = UUID.randomUUID();

        Fixture() {
            when(em.createNativeQuery(anyString())).thenAnswer(invocation -> {
                String sql = invocation.getArgument(0);
                if (sql.contains("ORDER BY u.id FOR UPDATE OF u")) return userLocks;
                if (sql.contains("FOR SHARE OF issuer_scope, target_scope")) return currentGrants;
                if (sql.contains("UPDATE users AS target")) return update;
                if (sql.contains("INSERT INTO admin_access_log")) return audit;
                throw new AssertionError("Unexpected session revocation SQL");
            });
            for (Query query : List.of(userLocks, currentGrants, update, audit)) {
                when(query.setParameter(anyString(), Mockito.any())).thenReturn(query);
            }
            when(userLocks.getResultList()).thenReturn(List.of(issuer, target));
            when(currentGrants.getResultList()).thenReturn(List.of(issuer));
            when(update.getResultList()).thenReturn(List.of(7L));
            when(audit.executeUpdate()).thenReturn(1);
        }

        long rotate() {
            return new JpaAdminSessionRevocationRepository(em)
                    .rotateAllSessions(issuer, target, group);
        }
    }

    @Test
    void locksBothUsersThenFreshScopeGrantsBeforeAtomicUpdateAndAudit() {
        var fixture = new Fixture();
        assertEquals(7L, fixture.rotate());

        var capturedSql = ArgumentCaptor.forClass(String.class);
        verify(fixture.em, Mockito.times(4)).createNativeQuery(capturedSql.capture());
        var statements = capturedSql.getAllValues();
        assertTrue(statements.get(0).contains("ORDER BY u.id FOR UPDATE OF u"));
        assertTrue(statements.get(1).contains("issuer.disabled_at IS NULL"));
        assertTrue(statements.get(1).contains("owner_security_admin"));
        assertTrue(statements.get(1).contains("issuer_scope.device_group_id=:group_id"));
        assertTrue(statements.get(1).contains("target_scope.device_group_id=:group_id"));
        assertTrue(statements.get(1).contains("FOR SHARE OF issuer_scope, target_scope"));
        assertTrue(statements.get(2).contains("session_version + 1"));
        assertTrue(statements.get(2).contains("assigned.device_group_id = :group_id"));
        assertTrue(statements.get(3).contains("admin.sessions.revoke"));
        verify(fixture.userLocks).setParameter("issuer_id", fixture.issuer);
        verify(fixture.userLocks).setParameter("target_id", fixture.target);
        verify(fixture.currentGrants).setParameter("issuer_id", fixture.issuer);
        verify(fixture.currentGrants).setParameter("target_id", fixture.target);
        verify(fixture.currentGrants).setParameter("group_id", fixture.group);
        verify(fixture.update).setParameter("issuer_id", fixture.issuer);
        verify(fixture.update).setParameter("target_id", fixture.target);
        verify(fixture.update).setParameter("group_id", fixture.group);
        verify(fixture.audit).executeUpdate();
    }

    @Test
    void unknownUserDeniedBeforeAnyGrantCheckOrMutation() {
        var fixture = new Fixture();
        when(fixture.userLocks.getResultList()).thenReturn(List.of(fixture.issuer));
        assertThrows(AccessDeniedException.class, fixture::rotate);
        verify(fixture.currentGrants, never()).getResultList();
        verify(fixture.update, never()).getResultList();
        verify(fixture.audit, never()).executeUpdate();
    }

    @Test
    void revokedOrDisabledGrantDeniedAfterUserLockWithoutMutation() {
        var fixture = new Fixture();
        when(fixture.currentGrants.getResultList()).thenReturn(List.of());
        assertThrows(AccessDeniedException.class, fixture::rotate);
        verify(fixture.userLocks).getResultList();
        verify(fixture.currentGrants).getResultList();
        verify(fixture.update, never()).getResultList();
        verify(fixture.audit, never()).executeUpdate();
    }

    @Test
    void missingUpdateResultDoesNotWriteAudit() {
        var fixture = new Fixture();
        when(fixture.update.getResultList()).thenReturn(List.of());
        assertThrows(AccessDeniedException.class, fixture::rotate);
        verify(fixture.audit, never()).executeUpdate();
    }

    @Test
    void silentlySuppressedSessionAuditRejectsSuccessfulRevocation() {
        var fixture = new Fixture();
        when(fixture.audit.executeUpdate()).thenReturn(0);
        assertThrows(IllegalStateException.class, fixture::rotate);
        verify(fixture.update).getResultList();
        verify(fixture.audit).executeUpdate();
    }

    @Test
    void issuerMayRevokeOwnSessionsWithoutDemandingTwoDistinctUserRows() {
        var fixture = new Fixture();
        when(fixture.userLocks.getResultList()).thenReturn(List.of(fixture.issuer));
        assertEquals(7L, new JpaAdminSessionRevocationRepository(fixture.em)
                .rotateAllSessions(fixture.issuer, fixture.issuer, fixture.group));
        verify(fixture.audit).executeUpdate();
    }
}
