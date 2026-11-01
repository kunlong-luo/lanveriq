package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** SQL order checks only; PostgreSQL rollback/concurrency require real DB evidence. */
class JpaFirstAdminBootstrapRepositoryTest {

    @Test
    void locksSeededRoleBeforeCheckingExistingAccountsAndWritesAuditLast() {
        var em = Mockito.mock(EntityManager.class);
        var queries = new ArrayList<String>();
        UUID roleId = UUID.randomUUID(), groupId = UUID.randomUUID(), userId = UUID.randomUUID();
        when(em.createNativeQuery(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            queries.add(sql);
            Query query = Mockito.mock(Query.class);
            when(query.setParameter(anyString(), Mockito.any())).thenReturn(query);
            if (sql.contains("FROM roles")) {
                when(query.getSingleResult()).thenReturn(roleId);
            } else if (sql.contains("count(*) FROM users")) {
                when(query.getSingleResult()).thenReturn(0L);
            } else if (sql.contains("INSERT INTO device_groups")) {
                when(query.getSingleResult()).thenReturn(groupId);
            } else if (sql.contains("INSERT INTO users")) {
                when(query.getSingleResult()).thenReturn(userId);
            } else if (sql.contains("INSERT INTO user_role_scopes")
                    || sql.contains("INSERT INTO admin_access_log")) {
                when(query.executeUpdate()).thenReturn(1);
            }
            return query;
        });

        var result = new JpaFirstAdminBootstrapRepository(em)
                .createFirstOwner("owner", "{argon2id}hash", "Engineering");

        assertEquals(userId, result.userId());
        assertEquals(groupId, result.groupId());
        assertEquals(6, queries.size());
        assertTrue(queries.get(0).contains("FOR UPDATE"));
        assertTrue(queries.get(1).contains("count(*) FROM users"));
        assertTrue(queries.get(2).contains("INSERT INTO device_groups"));
        assertTrue(queries.get(3).contains("INSERT INTO users"));
        assertTrue(queries.get(4).contains("INSERT INTO user_role_scopes"));
        assertTrue(queries.get(5).contains("INSERT INTO admin_access_log"));
        assertTrue(queries.stream().noneMatch(sql -> sql.contains("INSERT INTO user_data_grants")));
    }

    @Test
    void existingDisabledOrOtherAdminAccountFailsClosedBeforeAnyInsert() {
        var em = Mockito.mock(EntityManager.class);
        Query roleQuery = Mockito.mock(Query.class), countQuery = Mockito.mock(Query.class);
        when(em.createNativeQuery(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (sql.contains("FROM roles")) {
                return roleQuery;
            }
            if (sql.contains("count(*) FROM users")) {
                return countQuery;
            }
            throw new AssertionError("Unexpected write after existing admin");
        });
        when(roleQuery.getSingleResult()).thenReturn(UUID.randomUUID());
        when(countQuery.getSingleResult()).thenReturn(1L);
        assertThrows(IllegalStateException.class, () ->
                new JpaFirstAdminBootstrapRepository(em)
                        .createFirstOwner("owner", "{argon2id}hash", "Engineering"));
        verify(em, Mockito.times(2)).createNativeQuery(anyString());
    }
    @Test
    void silentlySuppressedGrantRejectsBootstrapBeforeAudit() {
        var writes = new ArrayList<String>();
        var em = mockBootstrapWithSuppressedInsert(writes, "INSERT INTO user_role_scopes");
        assertThrows(IllegalStateException.class, () ->
                new JpaFirstAdminBootstrapRepository(em)
                        .createFirstOwner("owner", "{argon2id}hash", "Engineering"));
        assertTrue(writes.stream().anyMatch(sql -> sql.contains("INSERT INTO user_role_scopes")));
        assertFalse(writes.stream().anyMatch(sql -> sql.contains("INSERT INTO admin_access_log")));
    }

    @Test
    void silentlySuppressedAuditRejectsCompletedBootstrap() {
        var writes = new ArrayList<String>();
        var em = mockBootstrapWithSuppressedInsert(writes, "INSERT INTO admin_access_log");
        assertThrows(IllegalStateException.class, () ->
                new JpaFirstAdminBootstrapRepository(em)
                        .createFirstOwner("owner", "{argon2id}hash", "Engineering"));
        assertTrue(writes.stream().anyMatch(sql -> sql.contains("INSERT INTO user_role_scopes")));
        assertTrue(writes.stream().anyMatch(sql -> sql.contains("INSERT INTO admin_access_log")));
    }

    private static EntityManager mockBootstrapWithSuppressedInsert(
            List<String> statements, String suppressed) {
        var em = Mockito.mock(EntityManager.class);
        UUID roleId = UUID.randomUUID(), groupId = UUID.randomUUID(), userId = UUID.randomUUID();
        when(em.createNativeQuery(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            statements.add(sql);
            Query query = Mockito.mock(Query.class);
            when(query.setParameter(anyString(), Mockito.any())).thenReturn(query);
            if (sql.contains("FROM roles")) {
                when(query.getSingleResult()).thenReturn(roleId);
            } else if (sql.contains("count(*) FROM users")) {
                when(query.getSingleResult()).thenReturn(0L);
            } else if (sql.contains("INSERT INTO device_groups")) {
                when(query.getSingleResult()).thenReturn(groupId);
            } else if (sql.contains("INSERT INTO users")) {
                when(query.getSingleResult()).thenReturn(userId);
            } else if (sql.contains("INSERT INTO user_role_scopes")
                    || sql.contains("INSERT INTO admin_access_log")) {
                when(query.executeUpdate()).thenReturn(sql.contains(suppressed) ? 0 : 1);
            }
            return query;
        });
        return em;
    }

}
