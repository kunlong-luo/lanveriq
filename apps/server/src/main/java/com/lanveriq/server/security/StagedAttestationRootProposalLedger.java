package com.lanveriq.server.security;

import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * B4-A internal offline-root PROPOSAL writer, never a Spring bean or route.
 * The caller must provide ONE enclosing PG18 transaction; any missing audit
 * MUST abort the transaction. A successful proposal is never a trust anchor.
 */
final class StagedAttestationRootProposalLedger {
    private final EntityManager manager;
    private final StagedAttestationRootCertificateInspector inspector;

    StagedAttestationRootProposalLedger(EntityManager manager, Clock clock) {
        this.manager = Objects.requireNonNull(manager);
        this.inspector = new StagedAttestationRootCertificateInspector(clock);
    }

    UUID stageOfflineUntrustedRoot(byte[] proposedCertificateDer) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AdminPrincipal actor)
                || !actor.isEnabled() || actor.userId() == null
                || actor.sessionVersion() < 0) {
            throw denied();
        }
        byte[] validated = inspector.inspectUntrustedDer(proposedCertificateDer);
        var rows = manager.createNativeQuery("""
                INSERT INTO admin_passkey_attestation_root_proposals_staging
                  (submitted_by,submitted_session_version,certificate_der,
                   source,trust_state)
                VALUES (:actor,:version,:der,'offline_candidate','untrusted_proposal')
                RETURNING proposal_id
                """).setParameter("actor", actor.userId())
                .setParameter("version", actor.sessionVersion())
                .setParameter("der", validated)
                .getResultList();
        if (rows.size() != 1 || !(rows.getFirst() instanceof UUID id)) {
            throw denied();
        }
        int audited = manager.createNativeQuery("""
                INSERT INTO admin_access_log(principal_id,resource_id,action,outcome)
                VALUES (:actor,:proposal,
                        'admin.passkey.attestation.root.untrusted_proposal','allowed')
                """).setParameter("actor", actor.userId())
                .setParameter("proposal", id).executeUpdate();
        if (audited != 1) {
            throw new IllegalStateException("Root proposal audit missing");
        }
        return id;
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Untrusted attestation root proposal denied");
    }
}
