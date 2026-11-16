package com.lanveriq.server.security;

import jakarta.persistence.EntityManager;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A11-D2-D4-A: append-only UNTRUSTED registration response digest.
 *
 * No @Component/@Bean/@Service/Controller, no trusted credential consumer.
 * The caller MUST invoke V19 consume and stageUntrusted in ONE enclosing PG18
 * transaction. Even a successful write proves neither real WebAuthn4J
 * attestation nor authentication/step-up or authorization to issue certificates.
 */
final class StagedPasskeyRegistrationCandidateLedger {
    private final EntityManager manager;

    StagedPasskeyRegistrationCandidateLedger(EntityManager manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    void stageUntrusted(UUID consumedChallengeId, byte[] credentialId,
                        byte[] untrustedRegistrationResponse) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AdminPrincipal actor)
                || !actor.isEnabled() || actor.userId() == null
                || actor.sessionVersion() < 0 || consumedChallengeId == null
                || credentialId == null || credentialId.length < 16
                || credentialId.length > 1024
                || untrustedRegistrationResponse == null
                || untrustedRegistrationResponse.length == 0
                || untrustedRegistrationResponse.length > 32768) {
            throw denied();
        }
        byte[] sha256;
        try {
            sha256 = MessageDigest.getInstance("SHA-256")
                    .digest(untrustedRegistrationResponse);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
        // V20 composite FKs and insert guard verify that this is a LIVE,
        // already-consumed V19 nonce, still owned by the current principal/
        // session/role, and a non-revoked existing V16/V18 credential pair.
        var rows = manager.createNativeQuery("""
                INSERT INTO admin_passkey_registration_candidates_staging
                  (challenge_id,actor_id,actor_session_version,rp_id,credential_id,
                   registration_response_sha256,evidence_grade)
                VALUES (:nonce,:actor,:version,'console.lanveriq.internal',
                        :credential,:digest,'untrusted_candidate')
                RETURNING challenge_id
                """).setParameter("nonce", consumedChallengeId)
                .setParameter("actor", actor.userId())
                .setParameter("version", actor.sessionVersion())
                .setParameter("credential", credentialId.clone())
                .setParameter("digest", sha256).getResultList();
        if (rows.size() != 1 || !consumedChallengeId.equals(rows.getFirst())) {
            throw denied();
        }
        int audit = manager.createNativeQuery("""
                INSERT INTO admin_access_log
                    (principal_id,resource_id,action,outcome)
                VALUES (:actor,:nonce,'admin.passkey.registration.candidate.untrusted',
                        'allowed')
                """).setParameter("actor", actor.userId())
                .setParameter("nonce", consumedChallengeId).executeUpdate();
        if (audit != 1) {
            throw new IllegalStateException("Passkey candidate staging audit missing");
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Untrusted registration candidate rejected");
    }
}
