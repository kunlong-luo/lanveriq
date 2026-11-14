package com.lanveriq.server.enrollment;

import com.lanveriq.server.security.AdminPrincipal;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A11-B INTERNAL transaction-only persistence for synthetic first-pin reviews.
 * Independently disabled by default, with NO HTTP/controller, signer/CA, Agent,
 * issuance, quarantine release or production approval consumer.
 *
 * The caller's Spring Security principal is checked against currently locked
 * PostgreSQL users and owner group grants on each step. These durable votes
 * are NOT MFA, an attested clock or cryptographic authorization. The terminal
 * status is a diagnostic ledger result ONLY.
 */
@Service
@ConditionalOnProperty(prefix = "lanveriq.first-pin-review", name = "ledger-enabled",
        havingValue = "true")
class FirstPinReviewLedgerService {
    enum Outcome {
        PENDING_SECOND_REVIEW, TWO_REVIEWS_RECORDED_NOT_AUTHORIZED_TO_SIGN
    }

    private final EntityManager manager;

    FirstPinReviewLedgerService(EntityManager manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    @Transactional
    public UUID request(UUID deviceId) {
        AdminPrincipal actor = authenticated();
        if (deviceId == null || deviceId.equals(new UUID(0, 0))) {
            throw denied();
        }
        // Device and current TLS credential identities are always server
        // loaded; never taken from JSON/Caller supplied CSR, epoch or key.
        var credentials = manager.createNativeQuery("""
                SELECT d.group_id,c.id,c.credential_epoch_id,
                       c.client_cert_sha256_fingerprint,c.csr_spki_digest
                  FROM devices AS d
                  JOIN device_credentials AS c ON c.device_id=d.id
                 WHERE d.id=:device AND d.enrollment_state='active'
                   AND d.revoked_at IS NULL AND c.status='active'
                   AND c.revoked_at IS NULL AND c.expires_at>clock_timestamp()
                 FOR UPDATE OF d,c
                """).setParameter("device", deviceId).getResultList();
        if (credentials.size() != 1) {
            throw denied();
        }
        Object[] credential = (Object[]) credentials.getFirst();
        UUID group = (UUID) credential[0];
        long version = currentScopedOwnerVersion(actor.userId(), group);
        if (version != actor.sessionVersion()) {
            throw denied();
        }
        UUID requestId = (UUID) manager.createNativeQuery("""
                INSERT INTO first_pin_review_requests
                  (device_id,device_group_id,credential_id,credential_epoch_id,
                   cert_sha256_fingerprint,csr_spki_sha256,requested_by,
                   applicant_session_version)
                VALUES (:device,:group,:credential,:epoch,:fingerprint,:spki,
                        :actor,:version)
                RETURNING id
                """)
                .setParameter("device", deviceId)
                .setParameter("group", group)
                .setParameter("credential", credential[1])
                .setParameter("epoch", credential[2])
                .setParameter("fingerprint", credential[3])
                .setParameter("spki", credential[4])
                .setParameter("actor", actor.userId())
                .setParameter("version", version)
                .getSingleResult();
        audit(actor.userId(), requestId, group, "first_pin.review.request");
        return requestId;
    }

    @Transactional
    public Outcome review(UUID requestId) {
        AdminPrincipal actor = authenticated();
        if (requestId == null || requestId.equals(new UUID(0, 0))) {
            throw denied();
        }

        // Standard lock order: device -> request -> users/grants, followed by
        // a fresh READ COMMITTED authorization statement after lock waits.
        var devices = manager.createNativeQuery("""
                SELECT device_id FROM first_pin_review_requests WHERE id=:request
                """).setParameter("request", requestId).getResultList();
        if (devices.size() != 1) {
            throw denied();
        }
        UUID deviceId = (UUID) devices.getFirst();
        var deviceLocked = manager.createNativeQuery("""
                SELECT id FROM devices WHERE id=:device FOR UPDATE
                """).setParameter("device", deviceId).getResultList();
        if (deviceLocked.size() != 1) {
            throw denied();
        }
        var requestRows = manager.createNativeQuery("""
                SELECT r.device_group_id,r.requested_by,r.applicant_session_version,
                       r.credential_id,r.credential_epoch_id,
                       r.cert_sha256_fingerprint,r.csr_spki_sha256
                  FROM first_pin_review_requests AS r
                 WHERE r.id=:request AND r.device_id=:device
                 FOR UPDATE OF r
                """).setParameter("request", requestId)
                .setParameter("device", deviceId).getResultList();
        if (requestRows.size() != 1) {
            throw denied();
        }
        Object[] row = (Object[]) requestRows.getFirst();
        UUID group = (UUID) row[0];
        UUID applicant = (UUID) row[1];
        long applicantVersion = ((Number) row[2]).longValue();
        if (actor.userId().equals(applicant)) {
            throw denied();
        }
        var validCredential = manager.createNativeQuery("""
                SELECT c.id FROM devices AS d
                JOIN device_credentials AS c ON c.device_id=d.id
                WHERE d.id=:device AND d.group_id=:group
                  AND d.enrollment_state='active' AND d.revoked_at IS NULL
                  AND c.id=:credential AND c.credential_epoch_id=:epoch
                  AND c.client_cert_sha256_fingerprint=:fingerprint
                  AND c.csr_spki_digest=:spki
                  AND c.status='active' AND c.revoked_at IS NULL
                  AND c.expires_at>clock_timestamp()
                  AND NOT EXISTS (SELECT 1 FROM first_pin_review_revocations
                                  WHERE request_id=:request)
                FOR SHARE OF c
                """)
                .setParameter("device", deviceId)
                .setParameter("group", group)
                .setParameter("credential", row[3])
                .setParameter("epoch", row[4])
                .setParameter("fingerprint", row[5])
                .setParameter("spki", row[6])
                .setParameter("request", requestId).getResultList();
        if (validCredential.size() != 1) {
            throw denied();
        }
        var approvals = manager.createNativeQuery("""
                SELECT reviewer_id,reviewer_session_version
                  FROM first_pin_review_approvals
                 WHERE request_id=:request ORDER BY step
                """).setParameter("request", requestId).getResultList();
        if (approvals.size() > 1) {
            // Never issue a third vote or replay an already completed review.
            throw denied();
        }
        UUID first = null;
        long firstVersion = -1;
        if (approvals.size() == 1) {
            Object[] firstRow = (Object[]) approvals.getFirst();
            first = (UUID) firstRow[0];
            firstVersion = ((Number) firstRow[1]).longValue();
            if (actor.userId().equals(first)) {
                throw denied();
            }
        }

        // All subject/grant locks are held until commit. Sort IDs to avoid
        // cross-review deadlocks, and do not use cached session role claims.
        var people = new TreeSet<UUID>();
        people.add(applicant);
        people.add(actor.userId());
        if (first != null) {
            people.add(first);
        }
        for (UUID person : people) {
            long currentVersion = currentScopedOwnerVersion(person, group);
            if ((person.equals(applicant) && currentVersion != applicantVersion)
                    || (person.equals(actor.userId())
                        && currentVersion != actor.sessionVersion())
                    || (person.equals(first) && currentVersion != firstVersion)) {
                throw denied();
            }
        }
        int step = first == null ? 1 : 2;
        int inserted = manager.createNativeQuery("""
                INSERT INTO first_pin_review_approvals
                    (request_id,step,reviewer_id,reviewer_session_version)
                VALUES (:request,:step,:actor,:version)
                """).setParameter("request", requestId)
                .setParameter("step", step)
                .setParameter("actor", actor.userId())
                .setParameter("version", actor.sessionVersion()).executeUpdate();
        if (inserted != 1) {
            throw denied();
        }
        audit(actor.userId(), requestId, group, "first_pin.review.vote");
        return step == 1
                ? Outcome.PENDING_SECOND_REVIEW
                : Outcome.TWO_REVIEWS_RECORDED_NOT_AUTHORIZED_TO_SIGN;
    }

    private long currentScopedOwnerVersion(UUID subject, UUID group) {
        // Fresh statement under stable PostgreSQL user and role-scope SHARE
        // locks. Account disable, membership delete and session rotation
        // cannot commit behind the checked review while this transaction runs.
        var rows = manager.createNativeQuery("""
                SELECT u.session_version FROM users AS u
                JOIN user_role_scopes AS s ON s.user_id=u.id
                JOIN roles AS role ON role.id=s.role_id
                WHERE u.id=:subject AND u.disabled_at IS NULL
                  AND s.device_group_id=:group
                  AND role.code='owner_security_admin'
                FOR SHARE OF u,s
                """).setParameter("subject", subject)
                .setParameter("group", group).getResultList();
        if (rows.size() != 1) {
            throw denied();
        }
        return ((Number) rows.getFirst()).longValue();
    }

    private void audit(UUID actor, UUID requestId, UUID group, String action) {
        int recorded = manager.createNativeQuery("""
                INSERT INTO admin_access_log
                  (principal_id,resource_id,device_group_id,action,outcome)
                VALUES (:actor,:request,:group,:action,'allowed')
                """).setParameter("actor", actor).setParameter("request", requestId)
                .setParameter("group", group).setParameter("action", action)
                .executeUpdate();
        if (recorded != 1) {
            throw denied();
        }
    }

    private static AdminPrincipal authenticated() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AdminPrincipal p)
                || !p.isEnabled() || p.userId() == null) {
            throw denied();
        }
        return (AdminPrincipal) authentication.getPrincipal();
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("First-pin review denied");
    }
}
