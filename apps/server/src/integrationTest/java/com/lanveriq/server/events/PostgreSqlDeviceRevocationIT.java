package com.lanveriq.server.events;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.lanveriq.server.security.AdminPrincipal;

/**
 * Manual-only, synthetic PostgreSQL 18.6 service integration. Neither real
 * administrator login, device PKI, HTTP revocation nor actual endpoints are
 * exercised. The device-revocation service is explicitly opted in here.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "lanveriq.bootstrap.enabled=false",
        "lanveriq.console.auth.enabled=false",
        "lanveriq.device-revocation.enabled=true"
})
class PostgreSqlDeviceRevocationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("lanveriq_revocation_synthetic")
            .withUsername("lanveriq_test")
            .withPassword("synthetic-test-only");

    @Autowired
    private DeviceRevocationService revocation;

    @Autowired
    private DeviceHeartbeatService heartbeat;

    @Autowired
    private EntityManager manager;

    @Autowired
    private PlatformTransactionManager transactions;

    private UUID deviceId;
    private UUID adminId;
    private UUID wrongGroupAdminId;
    private VerifiedDeviceCertificate credential;

    @BeforeEach
    void createSyntheticScopedAdminAndDevice() {
        new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
            UUID group = insertGroup();
            UUID unrelated = insertGroup();
            deviceId = (UUID) manager.createNativeQuery("""
                    INSERT INTO devices(group_id,enrollment_state)
                    VALUES (:group,'active') RETURNING id
                    """).setParameter("group", group).getSingleResult();
            String fingerprint = UUID.randomUUID().toString().replace("-", "").repeat(2);
            manager.createNativeQuery("""
                    INSERT INTO device_credentials(
                        device_id,client_cert_sha256_fingerprint,issuer_id,
                        serial_number,csr_spki_digest,status,issued_at,expires_at)
                    VALUES(:device,:fingerprint,'synthetic-revocation-ca',
                        :serial,:spki,'active',
                        clock_timestamp()-INTERVAL '1 hour',
                        clock_timestamp()+INTERVAL '1 hour')
                    """)
                    .setParameter("device", deviceId)
                    .setParameter("fingerprint", fingerprint)
                    .setParameter("serial", UUID.randomUUID().toString())
                    .setParameter("spki", "a".repeat(64))
                    .executeUpdate();
            credential = new VerifiedDeviceCertificate(deviceId, fingerprint);
            adminId = insertScopedAdmin(group);
            wrongGroupAdminId = insertScopedAdmin(unrelated);
        });
        loginAs(adminId);
    }

    private UUID insertGroup() {
        return (UUID) manager.createNativeQuery("""
                INSERT INTO device_groups(name) VALUES (:name) RETURNING id
                """).setParameter("name", "revoke-synthetic-" + UUID.randomUUID())
                .getSingleResult();
    }

    private UUID insertScopedAdmin(UUID group) {
        UUID actor = (UUID) manager.createNativeQuery("""
                INSERT INTO users(normalized_login,password_hash)
                VALUES (:name,'synthetic-DO-NOT-LOGIN') RETURNING id
                """).setParameter("name", "revoke-test-" + UUID.randomUUID())
                .getSingleResult();
        manager.createNativeQuery("""
                INSERT INTO user_role_scopes(user_id,role_id,device_group_id)
                SELECT :actor,r.id,:group FROM roles AS r
                WHERE r.code='owner_security_admin'
                """)
                .setParameter("actor", actor)
                .setParameter("group", group)
                .executeUpdate();
        return actor;
    }

    private void loginAs(UUID actor) {
        AdminPrincipal principal = Mockito.mock(AdminPrincipal.class);
        when(principal.isEnabled()).thenReturn(true);
        when(principal.userId()).thenReturn(actor);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    @AfterEach
    void removeSyntheticSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private String state(String query) {
        return (String) manager.createNativeQuery(query)
                .setParameter("device", deviceId).getSingleResult();
    }

    private long auditCount() {
        return ((Number) manager.createNativeQuery("""
                SELECT COUNT(*) FROM admin_access_log
                WHERE resource_id=:device AND action='device.revoke'
                """).setParameter("device", deviceId).getSingleResult()).longValue();
    }

    @Test
    void scopedOwnerRevokesActiveIdentityAndCredentialsOnceAndDeniesHeartbeat() {
        assertTrue(revocation.revoke(deviceId));
        assertFalse(revocation.revoke(deviceId));
        assertEquals("revoked", state("""
                SELECT enrollment_state FROM devices WHERE id=:device
                """));
        assertEquals("revoked", state("""
                SELECT status FROM device_credentials WHERE device_id=:device
                """));
        assertEquals(1L, auditCount());
        assertEquals(1L, ((Number) manager.createNativeQuery("""
                SELECT COUNT(*) FROM device_credentials
                WHERE device_id=:device AND revoked_at IS NOT NULL
                """).setParameter("device", deviceId).getSingleResult()).longValue());

        var heartbeatPayload = new DeviceHeartbeat(1, deviceId, "2026-10-30T09:00:00Z",
                "0.1.0", 1, "running", Map.of(), 0, 0,
                null, null, 0, false, List.of());
        assertEquals(DeviceHeartbeatRejection.Reason.REVOKED_DEVICE,
                assertThrows(DeviceHeartbeatRejection.class, () ->
                        heartbeat.ingest(credential, deviceId, heartbeatPayload)).reason());
        assertEquals(0L, ((Number) manager.createNativeQuery("""
                SELECT COUNT(*) FROM device_heartbeat_latest WHERE device_id=:device
                """).setParameter("device", deviceId).getSingleResult()).longValue());
    }

    @Test
    void wrongGroupAndDisabledSecurityAdminCannotRevokeOrAudit() {
        loginAs(wrongGroupAdminId);
        assertThrows(AccessDeniedException.class, () -> revocation.revoke(deviceId));
        new TransactionTemplate(transactions).executeWithoutResult(ignored ->
                manager.createNativeQuery("""
                        UPDATE users SET disabled_at=clock_timestamp() WHERE id=:id
                        """).setParameter("id", adminId).executeUpdate());
        loginAs(adminId); // Stale authenticated principal cannot bypass a DB disable.
        assertThrows(AccessDeniedException.class, () -> revocation.revoke(deviceId));
        assertEquals("active", state("""
                SELECT enrollment_state FROM devices WHERE id=:device
                """));
        assertEquals("active", state("""
                SELECT status FROM device_credentials WHERE device_id=:device
                """));
        assertEquals(0L, auditCount());
    }

    @Test
    void failedAuditInsertRollsBackBothCredentialAndDeviceRevocation() {
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(ignored -> {
            manager.createNativeQuery("""
                    CREATE FUNCTION synthetic_revoke_audit_failure()
                    RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN
                      IF NEW.action = 'device.revoke' THEN
                        RAISE EXCEPTION 'synthetic revoke audit failure';
                      END IF;
                      RETURN NEW;
                    END
                    $$
                    """).executeUpdate();
            manager.createNativeQuery("""
                    CREATE TRIGGER synthetic_revoke_audit_failure
                    BEFORE INSERT ON admin_access_log
                    FOR EACH ROW EXECUTE FUNCTION synthetic_revoke_audit_failure()
                    """).executeUpdate();
        });
        try {
            assertThrows(RuntimeException.class, () -> revocation.revoke(deviceId));
            assertEquals("active", state("""
                    SELECT enrollment_state FROM devices WHERE id=:device
                    """));
            assertEquals("active", state("""
                    SELECT status FROM device_credentials WHERE device_id=:device
                    """));
            assertEquals(0L, auditCount());
        } finally {
            tx.executeWithoutResult(ignored -> {
                manager.createNativeQuery("""
                        DROP TRIGGER IF EXISTS synthetic_revoke_audit_failure
                        ON admin_access_log
                        """).executeUpdate();
                manager.createNativeQuery("""
                        DROP FUNCTION IF EXISTS synthetic_revoke_audit_failure()
                        """).executeUpdate();
            });
        }
        assertTrue(revocation.revoke(deviceId));
        assertEquals("revoked", state("""
                SELECT enrollment_state FROM devices WHERE id=:device
                """));
        assertEquals(1L, auditCount());
    }

    @Test
    void revokingPendingDeviceAlsoRevokesRegisteredPendingCredential() {
        new TransactionTemplate(transactions).executeWithoutResult(ignored -> {
            manager.createNativeQuery("""
                    UPDATE devices SET enrollment_state='pending_certificate'
                    WHERE id=:device
                    """).setParameter("device", deviceId).executeUpdate();
            manager.createNativeQuery("""
                    UPDATE device_credentials SET status='pending'
                    WHERE device_id=:device
                    """).setParameter("device", deviceId).executeUpdate();
        });
        assertTrue(revocation.revoke(deviceId));
        assertEquals("revoked", state("""
                SELECT enrollment_state FROM devices WHERE id=:device
                """));
        assertEquals("revoked", state("""
                SELECT status FROM device_credentials WHERE device_id=:device
                """));
        assertEquals(1L, auditCount());
    }
}
