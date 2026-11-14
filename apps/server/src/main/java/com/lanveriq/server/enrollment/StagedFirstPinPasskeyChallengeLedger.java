package com.lanveriq.server.enrollment;

import com.lanveriq.server.security.AdminPrincipal;
import jakarta.persistence.EntityManager;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * A11-D2-C server-generated, persisted, one-use WebAuthn challenge foundation.
 *
 * NOT @Component/@Service/@Bean; no HTTP/Agent/CA caller. V17 consumption is
 * NOT a validated MFA proof, and is NEVER connected to first-pin review votes.
 * In production the injected assertion verifier can only deny. A synthetic
 * fake exists solely in PG18 integration test source to prove transaction,
 * expiration, actor/session/request/device/action binding and replay safety.
 *
 * All methods must execute inside a Spring transaction. Tests use a real
 * TransactionTemplate and PostgreSQL 18; no in-memory nonce store.
 */
final class StagedFirstPinPasskeyChallengeLedger {
    record IssuedChallenge(UUID id, String challengeBase64Url) {}

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final UUID NIL = new UUID(0, 0);
    private static final String ACTION = FirstPinReviewStepUpVerifier.REVIEW_ACTION;
    private final EntityManager manager;
    private final FirstPinWebAuthnAssertionVerifier verifier;

    StagedFirstPinPasskeyChallengeLedger(
            EntityManager manager, FirstPinWebAuthnAssertionVerifier verifier) {
        this.manager = Objects.requireNonNull(manager);
        this.verifier = Objects.requireNonNull(verifier);
    }

    @Transactional
    IssuedChallenge issue(UUID reviewRequestId) {
        var principal = principal();
        var scope = authorize(principal, reviewRequestId);
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        byte[] digest = sha256(raw);
        var inserted = manager.createNativeQuery("""
                INSERT INTO admin_passkey_step_up_challenges
                    (actor_id,actor_session_version,request_id,device_id,action,
                     challenge_sha256,created_at,expires_at)
                SELECT :actor,:version,:request,:device,:action,:digest,
                       clock_timestamp(),clock_timestamp()+INTERVAL '2 minutes'
                RETURNING id
                """).setParameter("actor", principal.userId())
                .setParameter("version", principal.sessionVersion())
                .setParameter("request", reviewRequestId)
                .setParameter("device", scope.deviceId())
                .setParameter("action", ACTION)
                .setParameter("digest", digest).getResultList();
        if (inserted.size() != 1) {
            throw denied();
        }
        UUID challengeId = (UUID) inserted.getFirst();
        audit(principal.userId(), reviewRequestId, scope.groupId(),
                "first_pin.step_up.challenge.issue");
        // The raw nonce is returned once only and never stored in PostgreSQL,
        // a log, session identity, audit record or review vote.
        return new IssuedChallenge(challengeId,
                Base64.getUrlEncoder().withoutPadding().encodeToString(raw));
    }

    @Transactional
    void consumeAfterVerifiedAssertion(
            UUID challengeId, String challengeBase64Url, byte[] untrustedAssertion) {
        var principal = principal();
        if (challengeId == null || NIL.equals(challengeId)
                || untrustedAssertion == null || untrustedAssertion.length == 0
                || untrustedAssertion.length > 16384) {
            throw denied();
        }

        byte[] raw;
        try {
            raw = Base64.getUrlDecoder().decode(challengeBase64Url);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw denied();
        }
        if (raw.length != 32 || !Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw).equals(challengeBase64Url)) {
            throw denied();
        }

        // The initial read is never authorization. Immutable request/device
        // bindings are rechecked under database locks in authorize() and in
        // the row update after the independent verifier returns.
        var requests = manager.createNativeQuery("""
                SELECT request_id FROM admin_passkey_step_up_challenges
                WHERE id=:challenge
                """).setParameter("challenge", challengeId).getResultList();
        if (requests.size() != 1) {
            throw denied();
        }
        UUID requestId = (UUID) requests.getFirst();
        var scope = authorize(principal, requestId);

        var locked = manager.createNativeQuery("""
                SELECT actor_id,actor_session_version,request_id,device_id,
                       action,challenge_sha256,consumed_at
                FROM admin_passkey_step_up_challenges
                WHERE id=:challenge FOR UPDATE
                """).setParameter("challenge", challengeId).getResultList();
        if (locked.size() != 1) {
            throw denied();
        }
        Object[] row = (Object[]) locked.getFirst();
        byte[] expected = (byte[]) row[5];
        byte[] provided = sha256(raw);
        if (!principal.userId().equals(row[0])
                || principal.sessionVersion() != ((Number) row[1]).longValue()
                || !requestId.equals(row[2])
                || !scope.deviceId().equals(row[3])
                || !ACTION.equals(row[4])
                || row[6] != null
                || !MessageDigest.isEqual(expected, provided)) {
            throw denied();
        }

        // Only an independent cryptographic verifier may authorize consuming
        // a challenge. The sole production implementation ALWAYS throws.
        // The synthetic integration fake is not deployed or registered.
        verifier.verify(new FirstPinWebAuthnAssertionVerifier.BoundAssertion(
                principal.userId(), requestId, scope.deviceId(),
                principal.sessionVersion(), ACTION, challengeBase64Url,
                untrustedAssertion.clone()));

        var updated = manager.createNativeQuery("""
                UPDATE admin_passkey_step_up_challenges
                   SET consumed_at=clock_timestamp()
                 WHERE id=:challenge AND actor_id=:actor
                   AND actor_session_version=:version
                   AND request_id=:request AND device_id=:device
                   AND action=:action AND challenge_sha256=:digest
                   AND consumed_at IS NULL
                   AND expires_at > clock_timestamp()
                RETURNING id
                """).setParameter("challenge", challengeId)
                .setParameter("actor", principal.userId())
                .setParameter("version", principal.sessionVersion())
                .setParameter("request", requestId)
                .setParameter("device", scope.deviceId())
                .setParameter("action", ACTION)
                .setParameter("digest", provided).getResultList();
        if (updated.size() != 1) {
            throw denied();
        }
        // This audit proves only a transaction completed using the injected
        // verifier. It is NOT a WebAuthn proof, signer grant or review vote.
        audit(principal.userId(), requestId, scope.groupId(),
                "first_pin.step_up.challenge.consume");
    }

    private record Scope(UUID deviceId, UUID groupId) {}

    private Scope authorize(AdminPrincipal principal, UUID requestId) {
        if (requestId == null || NIL.equals(requestId)) {
            throw denied();
        }
        var references = manager.createNativeQuery("""
                SELECT device_id FROM first_pin_review_requests WHERE id=:request
                """).setParameter("request", requestId).getResultList();
        if (references.size() != 1) {
            throw denied();
        }
        UUID deviceId = (UUID) references.getFirst();
        var devices = manager.createNativeQuery("""
                SELECT id FROM devices WHERE id=:device AND enrollment_state='active'
                  AND revoked_at IS NULL FOR UPDATE
                """).setParameter("device", deviceId).getResultList();
        if (devices.size() != 1) {
            throw denied();
        }
        var requests = manager.createNativeQuery("""
                SELECT r.device_group_id,r.requested_by,r.applicant_session_version
                FROM first_pin_review_requests AS r
                JOIN devices AS d ON d.id=r.device_id
                WHERE r.id=:request AND r.device_id=:device
                  AND d.group_id=r.device_group_id
                  AND NOT EXISTS (SELECT 1 FROM first_pin_review_revocations AS x
                                  WHERE x.request_id=r.id)
                FOR UPDATE OF r
                """).setParameter("request", requestId)
                .setParameter("device", deviceId).getResultList();
        if (requests.size() != 1) {
            throw denied();
        }
        Object[] row = (Object[]) requests.getFirst();
        UUID groupId = (UUID) row[0];
        UUID applicant = (UUID) row[1];
        if (principal.userId().equals(applicant)) {
            throw denied();
        }
        var validCredential = manager.createNativeQuery("""
                SELECT c.id FROM first_pin_review_requests AS r
                JOIN device_credentials AS c ON c.id=r.credential_id
                WHERE r.id=:request AND c.device_id=:device
                  AND c.credential_epoch_id=r.credential_epoch_id
                  AND c.client_cert_sha256_fingerprint=r.cert_sha256_fingerprint
                  AND c.csr_spki_digest=r.csr_spki_sha256
                  AND c.status='active' AND c.revoked_at IS NULL
                  AND c.expires_at>clock_timestamp()
                FOR SHARE OF c
                """).setParameter("request", requestId)
                .setParameter("device", deviceId).getResultList();
        if (validCredential.size() != 1) {
            throw denied();
        }
        var votes = manager.createNativeQuery("""
                SELECT reviewer_id FROM first_pin_review_approvals
                WHERE request_id=:request
                """).setParameter("request", requestId).getResultList();
        if (votes.size() >= 2 || votes.contains(principal.userId())) {
            throw denied();
        }
        for (UUID subject : new TreeSet<>(java.util.List.of(applicant, principal.userId()))) {
            var versions = manager.createNativeQuery("""
                    SELECT u.session_version FROM users AS u
                    JOIN user_role_scopes AS s ON s.user_id=u.id
                    JOIN roles AS role ON role.id=s.role_id
                    WHERE u.id=:actor AND u.disabled_at IS NULL
                      AND s.device_group_id=:group AND role.code='owner_security_admin'
                    FOR SHARE OF u,s
                    """).setParameter("actor", subject)
                    .setParameter("group", groupId).getResultList();
            if (versions.size() != 1 || ((Number) versions.getFirst()).longValue()
                    != (subject.equals(applicant)
                    ? ((Number) row[2]).longValue() : principal.sessionVersion())) {
                throw denied();
            }
        }
        return new Scope(deviceId, groupId);
    }

    private void audit(UUID actorId, UUID requestId, UUID group, String action) {
        int recorded = manager.createNativeQuery("""
                INSERT INTO admin_access_log
                    (principal_id,resource_id,device_group_id,action,outcome)
                VALUES (:actor,:request,:group,:action,'allowed')
                """).setParameter("actor", actorId)
                .setParameter("request", requestId)
                .setParameter("group", group)
                .setParameter("action", action).executeUpdate();
        if (recorded != 1) {
            throw denied();
        }
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException impossibleWithStandardJdk) {
            throw new IllegalStateException("SHA-256 unavailable", impossibleWithStandardJdk);
        }
    }

    private static AdminPrincipal principal() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AdminPrincipal admin)
                || !admin.isEnabled() || admin.userId() == null) {
            throw denied();
        }
        return admin;
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Trusted reviewer challenge denied");
    }
}
