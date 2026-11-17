package com.lanveriq.server.security;

import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A11-D2-D4-B3: record ONLY that a V20 candidate was NOT trusted.
 * No Bean/controller, no cryptographic proof or authenticator admission.
 * Caller must supply a transaction; failure of the mandatory audit MUST
 * roll back the receipt along with any preceding nonce/candidate writes.
 */
final class StagedAttestationRejectionLedger {
    private final EntityManager manager;

    StagedAttestationRejectionLedger(EntityManager manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    void recordUnconfiguredTrustRoots(UUID challengeId) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (challengeId == null || authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AdminPrincipal actor)
                || !actor.isEnabled() || actor.userId() == null
                || actor.sessionVersion() < 0) {
            throw denied();
        }
        // No parameters carrying user-selected proof status, AAGUID, policy,
        // trust-root digest, credential ID or response digest are accepted.
        // The full immutable lineage is copied atomically from V20.
        var rows = manager.createNativeQuery("""
                INSERT INTO admin_passkey_attestation_rejections_staging
                  (challenge_id,actor_id,actor_session_version,rp_id,
                   credential_id,registration_response_sha256,
                   evidence_grade,rejection_code)
                SELECT c.challenge_id,c.actor_id,c.actor_session_version,
                       c.rp_id,c.credential_id,c.registration_response_sha256,
                       'not_trusted','trust_roots_unconfigured'
                FROM admin_passkey_registration_candidates_staging c
                JOIN users u ON u.id=c.actor_id
                JOIN admin_passkey_credentials k ON k.credential_id=c.credential_id
                  AND k.user_id=c.actor_id AND k.rp_id=c.rp_id
                WHERE c.challenge_id=:nonce
                  AND c.actor_id=:actor AND c.actor_session_version=:version
                  AND u.disabled_at IS NULL AND u.session_version=:version
                  AND k.revoked_at IS NULL
                  AND EXISTS (SELECT 1 FROM user_role_scopes s
                    JOIN roles r ON r.id=s.role_id
                    WHERE s.user_id=c.actor_id AND r.code='owner_security_admin')
                RETURNING challenge_id
                """).setParameter("nonce", challengeId)
                .setParameter("actor", actor.userId())
                .setParameter("version", actor.sessionVersion()).getResultList();
        if (rows.size() != 1 || !challengeId.equals(rows.getFirst())) {
            throw denied();
        }
        int audited = manager.createNativeQuery("""
                INSERT INTO admin_access_log
                    (principal_id,resource_id,action,outcome)
                VALUES (:actor,:nonce,
                        'admin.passkey.attestation.untrusted_no_roots','denied')
                """).setParameter("actor", actor.userId())
                .setParameter("nonce", challengeId).executeUpdate();
        if (audited != 1) {
            throw new IllegalStateException("Attestation denial audit not persisted");
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Untrusted attestation receipt rejected");
    }
}
