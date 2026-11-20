package com.lanveriq.server.security;

import jakarta.persistence.EntityManager;
import java.security.interfaces.ECPublicKey;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * D4-C4: synthetic-only append-only UNTRUSTED observations, never a FIDO MDS
 * cache, signer approval, trust anchor, WebAuthn MFA or attestation admission.
 * The signer public key comes from disposable test context, NOT a FIDO root.
 * This class is NOT a Spring Bean. Both operations require one enclosing PG18
 * transaction with mandatory audit; the database serializes concurrent writes.
 */
final class StagedOfflineFidoMdsObservationLedger {
    private final EntityManager manager;
    private final StagedOfflineSignedFidoMdsEnvelopeInspector inspector;

    StagedOfflineFidoMdsObservationLedger(EntityManager manager, Clock clock) {
        this.manager = Objects.requireNonNull(manager);
        inspector = new StagedOfflineSignedFidoMdsEnvelopeInspector(
                Objects.requireNonNull(clock));
    }

    UUID openSyntheticUntrustedStream() {
        AdminPrincipal actor = actor();
        var ids = manager.createNativeQuery("""
                INSERT INTO admin_fido_mds_untrusted_streams_staging
                  (created_by,creator_session_version,stream_state)
                VALUES (:actor,:version,'synthetic_untrusted')
                RETURNING stream_id
                """).setParameter("actor", actor.userId())
                .setParameter("version", actor.sessionVersion()).getResultList();
        if (ids.size() != 1 || !(ids.getFirst() instanceof UUID id)) {
            throw denied();
        }
        audited(actor, id, "admin.passkey.mds.untrusted.stream.open");
        return id;
    }

    UUID observeSyntheticOnly(UUID streamId, String compactJws, ECPublicKey testKey) {
        AdminPrincipal actor = actor();
        if (streamId == null || compactJws == null || testKey == null) {
            throw denied();
        }
        // A preliminary read is only for performing the JWS sequence test.
        // The V25 database trigger re-checks under a FOR UPDATE stream lock.
        long previous = ((Number) manager.createNativeQuery("""
                SELECT COALESCE(MAX(sequence_no),0)
                FROM admin_fido_mds_untrusted_observations_staging
                WHERE stream_id=:stream
                """).setParameter("stream",streamId).getSingleResult()).longValue();
        var inspected = inspector.inspectSyntheticOnly(compactJws, testKey, previous);
        var previousRows = manager.createNativeQuery("""
                SELECT jws_sha256 FROM admin_fido_mds_untrusted_observations_staging
                WHERE stream_id=:stream ORDER BY sequence_no DESC LIMIT 1
                """).setParameter("stream", streamId).getResultList();
        byte[] predecessor = previousRows.isEmpty() ? null : (byte[]) previousRows.getFirst();
        var ids = manager.createNativeQuery("""
                INSERT INTO admin_fido_mds_untrusted_observations_staging
                  (stream_id,sequence_no,previous_jws_sha256,jws_sha256,
                   synthetic_claimed_issued_at,observed_by,observer_session_version,
                   observation_state)
                VALUES (:stream,:seq,:previous,
                        sha256(convert_to(:jws,'UTF8')),
                        :issued,:actor,:version,'observed_untrusted')
                RETURNING observation_id
                """).setParameter("stream",streamId)
                .setParameter("seq",inspected.observedSequence())
                .setParameter("previous",predecessor)
                .setParameter("jws",compactJws)
                .setParameter("issued",inspected.syntheticIssuedAt())
                .setParameter("actor",actor.userId())
                .setParameter("version",actor.sessionVersion())
                .getResultList();
        if (ids.size() != 1 || !(ids.getFirst() instanceof UUID id)) {
            throw denied();
        }
        // The JWS SHA is PostgreSQL-computed from the passed JWS bytes; never
        // trusted as FIDO provenance. Comparing Java digest detects encoding
        // mismatch without persisting full synthetic input.
        byte[] postgresDigest = (byte[]) manager.createNativeQuery("""
                SELECT jws_sha256 FROM admin_fido_mds_untrusted_observations_staging
                WHERE observation_id=:id
                """).setParameter("id",id).getSingleResult();
        if (!HexFormat.of().formatHex(postgresDigest).equals(inspected.jwsSha256())) {
            throw denied();
        }
        audited(actor, id, "admin.passkey.mds.untrusted.observation");
        return id;
    }

    private void audited(AdminPrincipal actor, UUID resource, String action) {
        int count = manager.createNativeQuery("""
                INSERT INTO admin_access_log(principal_id,resource_id,action,outcome)
                VALUES (:actor,:resource,:action,'allowed')
                """).setParameter("actor",actor.userId())
                .setParameter("resource",resource)
                .setParameter("action",action).executeUpdate();
        if (count != 1) {
            throw new IllegalStateException("Untrusted synthetic MDS audit missing");
        }
    }

    private static AdminPrincipal actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AdminPrincipal admin)
                || !admin.isEnabled() || admin.userId() == null
                || admin.sessionVersion() < 0) {
            throw denied();
        }
        return admin;
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Synthetic MDS observation denied");
    }
}
