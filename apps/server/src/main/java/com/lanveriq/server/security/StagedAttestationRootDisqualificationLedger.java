package com.lanveriq.server.security;

import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A11-D2-D4-C1: permanent deny-only terminal status for an UNTRUSTED offline
 * root proposal, not revocation of an operational trust anchor, certificate
 * PKIX path or WebAuthn user credential. Never installed as a Spring Bean.
 * Caller MUST own one transaction: suppression of the audit rolls back the
 * disqualification. This cannot grant MFA, login or issuance.
 */
final class StagedAttestationRootDisqualificationLedger {
    enum Reason {
        OPERATOR_WITHDRAWAL("operator_withdrawal"),
        SUSPECTED_COMPROMISE("suspected_compromise"),
        METADATA_STATUS_CHANGED("metadata_status_changed");

        final String code;
        Reason(String code) { this.code = code; }
    }

    private final EntityManager manager;

    StagedAttestationRootDisqualificationLedger(EntityManager manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    void disqualifyUntrustedCandidate(UUID proposalId, Reason reason) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (proposalId == null || reason == null
                || auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AdminPrincipal actor)
                || !actor.isEnabled() || actor.userId() == null
                || actor.sessionVersion() < 0) {
            throw denied();
        }
        // Never accept a caller-provided digest, policy, signed MDS status,
        // receipt timestamp, or authority/approval bit.
        var rows = manager.createNativeQuery("""
                INSERT INTO admin_passkey_attestation_root_disqualifications_staging
                  (proposal_id,disqualified_by,disqualifier_session_version,
                   certificate_sha256,reason_code,decision_state)
                SELECT proposal_id,:actor,:version,certificate_sha256,:reason,
                       'disqualified_untrusted'
                  FROM admin_passkey_attestation_root_proposals_staging
                 WHERE proposal_id=:proposal AND trust_state='untrusted_proposal'
                RETURNING proposal_id
                """).setParameter("proposal", proposalId)
                .setParameter("actor", actor.userId())
                .setParameter("version", actor.sessionVersion())
                .setParameter("reason", reason.code)
                .getResultList();
        if (rows.size() != 1 || !proposalId.equals(rows.getFirst())) {
            throw denied();
        }
        int audited = manager.createNativeQuery("""
                INSERT INTO admin_access_log(principal_id,resource_id,action,outcome)
                VALUES (:actor,:proposal,
                        'admin.passkey.root.candidate.disqualified_untrusted','allowed')
                """).setParameter("actor", actor.userId())
                .setParameter("proposal", proposalId).executeUpdate();
        if (audited != 1) {
            throw new IllegalStateException("Untrusted root disqualification audit missing");
        }
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Untrusted root candidate disqualification denied");
    }
}
