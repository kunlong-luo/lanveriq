package com.lanveriq.server.security;

import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * B4-B INTERNAL ONLY: two independent acknowledgments of UNTRUSTED offline
 * candidate screening. Not a real WebAuthn passkey MFA or root approval.
 * A complete pair NEVER creates an accepted trust anchor or issues a key.
 * The caller must use one real PG18 transaction with mandatory audit.
 */
final class StagedAttestationRootScreeningLedger {
    private final EntityManager manager;

    StagedAttestationRootScreeningLedger(EntityManager manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    void screenUntrustedCandidate(UUID proposalId, int step) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (proposalId == null || (step != 1 && step != 2)
                || auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AdminPrincipal reviewer)
                || !reviewer.isEnabled() || reviewer.userId() == null
                || reviewer.sessionVersion() < 0) {
            throw denied();
        }
        // Never accept a caller-provided root fingerprint, approve flag,
        // attestation outcome, FIDO metadata assertion or review timestamp.
        // Database recalculates and locks the candidate lineage and scope.
        var rows = manager.createNativeQuery("""
                INSERT INTO admin_passkey_attestation_root_screenings_staging
                  (proposal_id,step,reviewer_id,reviewer_session_version,
                   certificate_sha256,screening_state)
                SELECT proposal_id,:step,:reviewer,:version,certificate_sha256,
                       'screened_untrusted'
                FROM admin_passkey_attestation_root_proposals_staging
                WHERE proposal_id=:proposal AND trust_state='untrusted_proposal'
                RETURNING proposal_id
                """).setParameter("proposal", proposalId)
                .setParameter("step", step)
                .setParameter("reviewer", reviewer.userId())
                .setParameter("version", reviewer.sessionVersion())
                .getResultList();
        if (rows.size() != 1 || !proposalId.equals(rows.getFirst())) {
            throw denied();
        }
        var count = manager.createNativeQuery("""
                INSERT INTO admin_access_log(principal_id,resource_id,action,outcome)
                VALUES (:actor,:proposal,
                        :action,'allowed')
                """).setParameter("actor", reviewer.userId())
                .setParameter("proposal", proposalId)
                .setParameter("action", step == 1
                        ? "admin.passkey.root.screening.untrusted.step1"
                        : "admin.passkey.root.screening.untrusted.step2")
                .executeUpdate();
        if (count != 1) {
            throw new IllegalStateException("Untrusted root screening audit missing");
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Untrusted root candidate screening denied");
    }
}
