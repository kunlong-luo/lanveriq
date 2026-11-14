package com.lanveriq.server.security;

import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

/**
 * A11-D2-D1: PostgreSQL latest-signCount atomic compare-and-advance.
 *
 * INTERNAL ONLY: no @Component, @Service, @Bean, Controller or WebAuthn
 * authentication integration. This is NOT a cryptographic assertion verifier.
 * A future caller must verify signature, UV, RP ID, origin, user ownership and
 * one-time challenge BEFORE using this gate, in the SAME transaction. Never
 * use an incoming counter alone as MFA evidence or return reviewer proof.
 *
 * 0/0 authenticators are supported only as the diagnostic NO_COUNTER_SIGNAL
 * outcome: it creates no audit, mutates nothing and does NOT authorize login.
 */
final class StagedWebAuthnCounterGate {
    enum Outcome { ADVANCED, NO_COUNTER_SIGNAL }

    private final EntityManager manager;

    StagedWebAuthnCounterGate(EntityManager manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    Outcome compareAndAdvance(UUID actor, byte[] credentialId, String rpId,
            long expectedStoredCounter, long observedCounter, boolean observedBackupState) {
        if (actor == null || credentialId == null || credentialId.length < 16
                || credentialId.length > 1024 || rpId == null || rpId.isBlank()
                || expectedStoredCounter < 0 || observedCounter < 0
                || observedCounter > 4_294_967_295L
                || (observedCounter != 0 && observedCounter <= expectedStoredCounter)
                || (expectedStoredCounter != 0 && observedCounter == 0)) {
            throw denied();
        }

        // 0/0 is a legitimate counterless synced-passkey state, but WITHOUT
        // a signed fresh challenge it provides NO authorization signal.
        // Confirm live owner and RP before returning the diagnostic outcome.
        if (expectedStoredCounter == 0 && observedCounter == 0) {
            var alive = manager.createNativeQuery("""
                    SELECT c.credential_id
                    FROM admin_passkey_credentials AS c
                    JOIN users AS u ON u.id=c.user_id
                    WHERE c.credential_id=:credential AND c.user_id=:actor
                      AND c.rp_id=:rp AND c.signature_count=0
                      AND c.revoked_at IS NULL AND u.disabled_at IS NULL
                      AND (NOT :backup OR c.backup_eligible)
                    """).setParameter("credential", credentialId)
                    .setParameter("actor", actor)
                    .setParameter("rp", rpId)
                    .setParameter("backup", observedBackupState)
                    .getResultList();
            if (alive.size() != 1) {
                throw denied();
            }
            return Outcome.NO_COUNTER_SIGNAL;
        }

        // PostgreSQL atomic compare-and-swap against CURRENT persisted value,
        // not a registration-time attestation signCount. Competing assertions
        // carrying the same expected counter can commit only once.
        var accepted = manager.createNativeQuery("""
                UPDATE admin_passkey_credentials AS c
                   SET signature_count=:observed,
                       backup_state=:backup,
                       last_verified_at=clock_timestamp()
                  FROM users AS u
                 WHERE c.credential_id=:credential
                   AND c.user_id=:actor AND c.rp_id=:rp
                   AND c.user_id=u.id AND u.disabled_at IS NULL
                   AND c.revoked_at IS NULL
                   AND c.signature_count=:expected
                   AND :observed > c.signature_count
                   AND :observed <= 4294967295
                   AND (NOT :backup OR c.backup_eligible)
                RETURNING c.user_id
                """).setParameter("credential", credentialId)
                .setParameter("actor", actor)
                .setParameter("rp", rpId)
                .setParameter("expected", expectedStoredCounter)
                .setParameter("observed", observedCounter)
                .setParameter("backup", observedBackupState)
                .getResultList();
        if (accepted.size() != 1 || !actor.equals(accepted.getFirst())) {
            throw denied();
        }
        int audit = manager.createNativeQuery("""
                INSERT INTO admin_access_log
                    (principal_id,resource_id,action,outcome)
                VALUES (:actor,:actor,'admin.passkey.counter.advance.staged','allowed')
                """).setParameter("actor", actor).executeUpdate();
        if (audit != 1) {
            // The future enclosing Spring transaction MUST roll back both
            // the counter update and audit if a trigger suppresses the audit.
            throw new IllegalStateException("Passkey counter audit not persisted");
        }
        return Outcome.ADVANCED;
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Passkey counter advance denied");
    }
}
