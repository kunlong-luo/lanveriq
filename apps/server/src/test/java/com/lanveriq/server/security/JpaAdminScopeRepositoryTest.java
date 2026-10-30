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

/** SQL shape tests are unit-only; actual PostgreSQL SQL remains a manual gate. */
class JpaAdminScopeRepositoryTest {

    @Test
    void sensitiveGrantRequiresSeparateActiveDataGrantAndBoundGroup() {
        EntityManager entityManager = Mockito.mock(EntityManager.class);
        Query query = Mockito.mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), Mockito.any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(false);
        var repository = new JpaAdminScopeRepository(entityManager);
        UUID id = UUID.randomUUID();
        UUID group = UUID.randomUUID();

        assertFalse(repository.hasCurrentGrant(id, group, AdminAction.VIEW_NETWORK));

        var sql = ArgumentCaptor.forClass(String.class);
        verify(entityManager).createNativeQuery(sql.capture());
        assertTrue(sql.getValue().contains("user_data_grants"));
        assertTrue(sql.getValue().contains("g.revoked_at IS NULL"));
        assertTrue(sql.getValue().contains("u.disabled_at IS NULL"));
        verify(query).setParameter("user_id", id);
        verify(query).setParameter("group_id", group);
        verify(query).setParameter("allowed_roles", AdminAction.VIEW_NETWORK.allowedRoleCodes());
    }

    @Test
    void nonSensitiveHealthQueryStillRequiresCurrentGroupRole() {
        EntityManager em = Mockito.mock(EntityManager.class);
        Query query = Mockito.mock(Query.class);
        when(em.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyString(), Mockito.any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(true);

        assertTrue(new JpaAdminScopeRepository(em).hasCurrentGrant(
                UUID.randomUUID(), UUID.randomUUID(), AdminAction.VIEW_HEALTH));
        var sql = ArgumentCaptor.forClass(String.class);
        verify(em).createNativeQuery(sql.capture());
        assertFalse(sql.getValue().contains("user_data_grants"));
        assertTrue(sql.getValue().contains("s.device_group_id = :group_id"));
    }
}
