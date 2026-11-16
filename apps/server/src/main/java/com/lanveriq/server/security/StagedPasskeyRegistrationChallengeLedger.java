package com.lanveriq.server.security;

import jakarta.persistence.EntityManager;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A11-D2-D3. Internal one-use registration challenge foundation (V19).
 * No @Component/@Service/@Bean/Controller or production caller.
 *
 * Calling consume proves only that an injected verifier was executed and
 * the challenge transaction committed. It DOES NOT register a credential,
 * trust attestation data, create a security principal or authorize review.
 * Every method MUST run in the same enclosing Spring/PG18 transaction.
 */
final class StagedPasskeyRegistrationChallengeLedger {
    interface RegistrationProofVerifier {
        void verify(BoundRegistration input);
    }
    record BoundRegistration(UUID actorId, long sessionVersion,
            String challengeBase64Url, byte[] untrustedAttestation) {}
    record IssuedChallenge(UUID id, String nonceBase64Url) {}

    private static final SecureRandom RNG = new SecureRandom();
    private static final String RP = "console.lanveriq.internal";
    private static final String ORIGIN = "https://" + RP;
    private final EntityManager entityManager;
    private final RegistrationProofVerifier verifier;

    StagedPasskeyRegistrationChallengeLedger(
            EntityManager entityManager, RegistrationProofVerifier verifier) {
        this.entityManager = Objects.requireNonNull(entityManager);
        this.verifier = Objects.requireNonNull(verifier);
    }

    IssuedChallenge issue() {
        var actor = current();
        ensureAuthorized(actor);
        byte[] nonce = new byte[32];
        RNG.nextBytes(nonce);
        UUID id = (UUID) entityManager.createNativeQuery("""
                INSERT INTO admin_passkey_registration_challenges
                    (actor_id,actor_session_version,rp_id,origin,challenge_sha256,
                     created_at,expires_at)
                VALUES (:actor,:version,:rp,:origin,:hash,clock_timestamp(),
                        clock_timestamp()+INTERVAL '2 minutes')
                RETURNING id
                """).setParameter("actor", actor.userId())
                .setParameter("version", actor.sessionVersion())
                .setParameter("rp", RP).setParameter("origin", ORIGIN)
                .setParameter("hash", sha256(nonce)).getSingleResult();
        audit(actor.userId(), "admin.passkey.registration.challenge.issue");
        return new IssuedChallenge(id,
                Base64.getUrlEncoder().withoutPadding().encodeToString(nonce));
    }

    void consume(UUID challengeId, String canonicalBase64Url, byte[] attestation) {
        var actor = current();
        if (challengeId == null || canonicalBase64Url == null
                || attestation == null || attestation.length == 0
                || attestation.length > 32768) {
            throw denied();
        }
        byte[] nonce;
        try {
            nonce = Base64.getUrlDecoder().decode(canonicalBase64Url);
        } catch (IllegalArgumentException bad) {
            throw denied();
        }
        if (nonce.length != 32 || !Base64.getUrlEncoder().withoutPadding()
                .encodeToString(nonce).equals(canonicalBase64Url)) {
            throw denied();
        }
        ensureAuthorized(actor);
        var rows = entityManager.createNativeQuery("""
                SELECT actor_id,actor_session_version,rp_id,origin,challenge_sha256,
                       consumed_at,(expires_at > clock_timestamp())
                  FROM admin_passkey_registration_challenges
                 WHERE id=:id FOR UPDATE
                """).setParameter("id", challengeId).getResultList();
        if (rows.size() != 1) {
            throw denied();
        }
        Object[] row = (Object[]) rows.getFirst();
        byte[] provided = sha256(nonce);
        if (!actor.userId().equals(row[0])
                || actor.sessionVersion() != ((Number) row[1]).longValue()
                || !RP.equals(row[2]) || !ORIGIN.equals(row[3])
                || !MessageDigest.isEqual((byte[]) row[4], provided)
                || row[5] != null || !Boolean.TRUE.equals(row[6])) {
            throw denied();
        }
        // Verification is a dependency, never a browser-supplied boolean.
        // Production has NO caller/source to make this a trusted enrollment.
        verifier.verify(new BoundRegistration(actor.userId(), actor.sessionVersion(),
                canonicalBase64Url, attestation.clone()));
        var updated = entityManager.createNativeQuery("""
                UPDATE admin_passkey_registration_challenges
                   SET consumed_at=clock_timestamp()
                 WHERE id=:id AND actor_id=:actor
                   AND actor_session_version=:version
                   AND rp_id=:rp AND origin=:origin
                   AND challenge_sha256=:hash
                   AND consumed_at IS NULL AND expires_at > clock_timestamp()
                RETURNING id
                """).setParameter("id", challengeId)
                .setParameter("actor", actor.userId())
                .setParameter("version", actor.sessionVersion())
                .setParameter("rp", RP).setParameter("origin", ORIGIN)
                .setParameter("hash", provided).getResultList();
        if (updated.size() != 1) {
            throw denied();
        }
        audit(actor.userId(), "admin.passkey.registration.challenge.consume.staged");
    }

    private void ensureAuthorized(AdminPrincipal actor) {
        var active = entityManager.createNativeQuery("""
                SELECT u.session_version FROM users u
                JOIN admin_passkey_user_handles h ON h.user_id=u.id
                WHERE u.id=:actor AND u.disabled_at IS NULL
                  AND EXISTS (
                      SELECT 1 FROM user_role_scopes s
                      JOIN roles r ON r.id=s.role_id
                      WHERE s.user_id=u.id AND r.code='owner_security_admin'
                  )
                FOR SHARE OF u,h
                """).setParameter("actor", actor.userId()).getResultList();
        if (active.size() != 1
                || ((Number) active.getFirst()).longValue() != actor.sessionVersion()) {
            throw denied();
        }
    }

    private void audit(UUID actor, String action) {
        int inserted = entityManager.createNativeQuery("""
                INSERT INTO admin_access_log(principal_id,resource_id,action,outcome)
                VALUES (:actor,:actor,:action,'allowed')
                """).setParameter("actor", actor).setParameter("action", action)
                .executeUpdate();
        if (inserted != 1) {
            throw new IllegalStateException("Registration challenge audit missing");
        }
    }

    private static AdminPrincipal current() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AdminPrincipal actor)
                || !actor.isEnabled() || actor.userId() == null
                || actor.sessionVersion() < 0) {
            throw denied();
        }
        return actor;
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Missing SHA-256", impossible);
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Passkey registration challenge denied");
    }
}
