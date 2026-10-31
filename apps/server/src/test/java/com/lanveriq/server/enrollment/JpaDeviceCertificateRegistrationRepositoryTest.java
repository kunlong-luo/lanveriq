package com.lanveriq.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

class JpaDeviceCertificateRegistrationRepositoryTest {

    private final EntityManager manager = Mockito.mock(EntityManager.class);
    private final Query review = Mockito.mock(Query.class);
    private final Query updateCsr = Mockito.mock(Query.class);
    private final Query insert = Mockito.mock(Query.class);
    private final Query audit = Mockito.mock(Query.class);
    private final UUID admin = UUID.randomUUID();
    private final UUID device = UUID.randomUUID();
    private final UUID group = UUID.randomUUID();

    private void wire() {
        when(manager.createNativeQuery(anyString())).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FOR UPDATE OF d")) return review;
            if (sql.contains("UPDATE device_enrollment_csrs")) return updateCsr;
            if (sql.contains("INSERT INTO device_credentials")) return insert;
            return audit;
        });
        for (Query query : List.of(review, updateCsr, insert, audit)) {
            when(query.setParameter(anyString(), Mockito.any())).thenReturn(query);
        }
    }

    private ClientCertificateProfileValidator.VerifiedLeaf leaf() {
        return new ClientCertificateProfileValidator.VerifiedLeaf(
                "f".repeat(64), "a".repeat(64), "42", "b".repeat(64),
                Instant.parse("2026-10-31T00:00:00Z"),
                Instant.parse("2026-12-30T00:00:00Z"));
    }

    @Test
    void locksPendingDeviceAndRechecksLiveGroupAdminBeforeRegistration() {
        wire();
        when(review.getResultList()).thenReturn(List.<Object[]>of(
                new Object[]{group, "b".repeat(64)}));
        var actual = new JpaDeviceCertificateRegistrationRepository(manager)
                .lockPendingForScopedAdmin(admin, device);
        assertEquals(device, actual.deviceId());
        assertEquals(group, actual.groupId());
        assertEquals("b".repeat(64), actual.csrSpkiSha256());

        var sql = ArgumentCaptor.forClass(String.class);
        verify(manager).createNativeQuery(sql.capture());
        assertTrue(sql.getValue().contains("FOR UPDATE OF d"));
        assertTrue(sql.getValue().contains("owner_security_admin"));
        assertTrue(sql.getValue().contains("u.disabled_at IS NULL"));
        assertTrue(sql.getValue().contains("r.pending_expires_at>clock_timestamp()"));
        assertTrue(sql.getValue().contains("NOT EXISTS"));
    }

    @Test
    void deniedOrExpiredPendingEnrollmentCannotStartRegistration() {
        wire();
        when(review.getResultList()).thenReturn(List.of());
        assertThrows(AccessDeniedException.class,
                () -> new JpaDeviceCertificateRegistrationRepository(manager)
                        .lockPendingForScopedAdmin(admin, device));
        verify(updateCsr, never()).executeUpdate();
        verify(insert, never()).getResultList();
    }

    @Test
    void certificateAndReviewAndAuditShareOneRepositoryOperation() {
        wire();
        when(updateCsr.executeUpdate()).thenReturn(1);
        when(insert.getResultList()).thenReturn(List.of(UUID.randomUUID()));
        new JpaDeviceCertificateRegistrationRepository(manager)
                .registerReviewed(admin, device, leaf());

        var sql = ArgumentCaptor.forClass(String.class);
        verify(manager, Mockito.times(3)).createNativeQuery(sql.capture());
        assertTrue(sql.getAllValues().get(0).contains("reviewed_by=:admin_id"));
        assertTrue(sql.getAllValues().get(1).contains("status,issued_at,expires_at"));
        assertTrue(sql.getAllValues().get(1).contains("'pending'"));
        assertTrue(sql.getAllValues().get(1).contains("NOT EXISTS"));
        assertTrue(sql.getAllValues().get(2).contains("device_certificate.register"));
        verify(updateCsr).executeUpdate();
        verify(insert).getResultList();
        verify(audit).executeUpdate();
    }

    @Test
    void failedReviewCannotWriteCertificateOrAudit() {
        wire();
        when(updateCsr.executeUpdate()).thenReturn(0);
        assertThrows(AccessDeniedException.class,
                () -> new JpaDeviceCertificateRegistrationRepository(manager)
                        .registerReviewed(admin, device, leaf()));
        verify(insert, never()).getResultList();
        verify(audit, never()).executeUpdate();
    }

    @Test
    void lostOrConflictingInsertCannotEmitSuccessAudit() {
        wire();
        when(updateCsr.executeUpdate()).thenReturn(1);
        when(insert.getResultList()).thenReturn(List.of());
        assertThrows(AccessDeniedException.class,
                () -> new JpaDeviceCertificateRegistrationRepository(manager)
                        .registerReviewed(admin, device, leaf()));
        verify(audit, never()).executeUpdate();
    }
}
