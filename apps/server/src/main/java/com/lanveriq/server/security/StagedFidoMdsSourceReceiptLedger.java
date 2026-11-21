package com.lanveriq.server.security;

import jakarta.persistence.EntityManager;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A11-D4-C5/V26: transaction-scoped, NOT-a-Bean append-only storage for
 * screening evidence only. V26 SQL locks the V25 synthetic stream, enforces
 * live actor/session, monotonic BLOB/time sequence, immutable root/time pins
 * and database-owned chained digest. V27 automatically writes and checks
 * the linked database audit even for direct SQL inserts. V28 additionally
 * stages optional cross-stream fences without making them trusted.\n * An out-of-band asserted fingerprint
 * is NOT approved FIDO trust. No verifier or credential/issuer consumer.
 */
final class StagedFidoMdsSourceReceiptLedger {
    private final EntityManager manager;

    StagedFidoMdsSourceReceiptLedger(EntityManager manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    UUID appendUntrustedOnly(UUID streamId,
            StagedFidoMdsSourceVerifier.UntrustedSourceEvidence evidence) {
        AdminPrincipal admin = actor();
        if (streamId == null || evidence == null || evidence.blobNumber() <= 0
                || evidence.signedTimeSequence() <= 0
                || evidence.checkedAt() == null) {
            throw denied();
        }
        final byte[] leaf, jws, root, crl, timeKey, timeProof;
        try {
            leaf = digest(evidence.leafSha256());
            jws = digest(evidence.blobSha256());
            root = digest(evidence.rootSha256());
            crl = digest(evidence.crlEvidenceSha256());
            timeKey = digest(evidence.timeKeySha256());
            timeProof = digest(evidence.signedTimeProofSha256());
        } catch (IllegalArgumentException exception) {
            throw denied();
        }
        // Optimistic read is only a candidate value. V26 insert trigger
        // rechecks under FOR UPDATE, so stale writers fail (never retry
        // automatically, reusing old time/source proof).
        var previous = manager.createNativeQuery("""
                SELECT receipt_sequence, receipt_sha256
                FROM admin_fido_mds_source_receipts_staging
                WHERE stream_id=:stream ORDER BY receipt_sequence DESC LIMIT 1
                """).setParameter("stream", streamId).getResultList();
        long nextSequence = 1;
        byte[] predecessor = null;
        if (!previous.isEmpty()) {
            Object[] last = (Object[]) previous.getFirst();
            long lastSeq = ((Number) last[0]).longValue();
            if (lastSeq == Long.MAX_VALUE) {
                throw denied();
            }
            nextSequence = lastSeq + 1;
            predecessor = (byte[]) last[1];
        }
        var receipts = manager.createNativeQuery("""
                INSERT INTO admin_fido_mds_source_receipts_staging
                  (stream_id,receipt_sequence,blob_no,signed_time_sequence,
                   signed_time_at,leaf_sha256,jws_sha256,root_sha256,
                   crl_evidence_sha256,time_key_sha256,signed_time_proof_sha256,
                   previous_receipt_sha256,observed_by,observer_session_version)
                VALUES
                  (:stream,:seq,:blob,:time_seq,:checked,:leaf,:jws,:root,
                   :crl,:time_key,:proof,:previous,:actor,:version)
                RETURNING receipt_id
                """)
                .setParameter("stream", streamId)
                .setParameter("seq", nextSequence)
                .setParameter("blob", evidence.blobNumber())
                .setParameter("time_seq", evidence.signedTimeSequence())
                .setParameter("checked", evidence.checkedAt())
                .setParameter("leaf", leaf)
                .setParameter("jws", jws)
                .setParameter("root", root)
                .setParameter("crl", crl)
                .setParameter("time_key", timeKey)
                .setParameter("proof", timeProof)
                .setParameter("previous", predecessor)
                .setParameter("actor", admin.userId())
                .setParameter("version", admin.sessionVersion())
                .getResultList();
        if (receipts.size() != 1 || !(receipts.getFirst() instanceof UUID id)) {
            throw denied();
        }
        // V27 AFTER INSERT trigger inserts this receipt's audit inside the
        // same PostgreSQL transaction, even for direct SQL writers. When an
        // audit is suppressed the trigger raises, aborting the receipt.
        // Do NOT insert a second audit record from this Java path.
        return id;
    }

    /**
     * V28 untrusted grouping only: the caller-asserted pins are deliberately
     * NOT an official FIDO approval or trusted-time enrollment. Must run
     * inside a caller-owned transaction with appendFencedUntrustedOnly.
     */
    UUID openUntrustedFenceOnly(String rootSha256, String timeKeySha256) {
        AdminPrincipal admin = actor();
        byte[] root = digest(rootSha256);
        byte[] timeKey = digest(timeKeySha256);
        var rows = manager.createNativeQuery("""
                INSERT INTO admin_fido_mds_source_fences_staging
                  (created_by,creator_session_version,root_sha256,time_key_sha256)
                VALUES (:actor,:version,:root,:time_key)
                RETURNING fence_id
                """)
                .setParameter("actor",admin.userId())
                .setParameter("version",admin.sessionVersion())
                .setParameter("root",root)
                .setParameter("time_key",timeKey)
                .getResultList();
        if (rows.size()!=1 || !(rows.getFirst() instanceof UUID id)) {
            throw denied();
        }
        return id;
    }

    /**
     * Append both the V26 untrusted receipt and its V28 cross-stream fence
     * checkpoint in ONE caller-owned database transaction. V28 serializes
     * competing writers by fence FOR UPDATE and recomputes the predecessor.
     * A new fence can always reset this SYNTHETIC boundary: it must never
     * become production anti-rollback or a credential authorization source.
     */
    UUID appendFencedUntrustedOnly(UUID fenceId, UUID streamId,
            StagedFidoMdsSourceVerifier.UntrustedSourceEvidence evidence) {
        if (fenceId == null) {
            throw denied();
        }
        AdminPrincipal admin = actor();
        UUID receiptId = appendUntrustedOnly(streamId,evidence);
        var prior = manager.createNativeQuery("""
                SELECT checkpoint_sequence,checkpoint_sha256
                FROM admin_fido_mds_source_fence_checkpoints_staging
                WHERE fence_id=:fence
                ORDER BY checkpoint_sequence DESC LIMIT 1
                """).setParameter("fence",fenceId).getResultList();
        long next=1;
        byte[] predecessor=null;
        if (!prior.isEmpty()) {
            Object[] last=(Object[])prior.getFirst();
            long seq=((Number)last[0]).longValue();
            if (seq==Long.MAX_VALUE) {
                throw denied();
            }
            next=seq+1;
            predecessor=(byte[])last[1];
        }
        var checkpoints=manager.createNativeQuery("""
                INSERT INTO admin_fido_mds_source_fence_checkpoints_staging
                  (fence_id,source_receipt_id,checkpoint_sequence,
                   previous_checkpoint_sha256,observed_by,
                   observer_session_version)
                VALUES (:fence,:receipt,:seq,:prev,:actor,:version)
                RETURNING checkpoint_id
                """)
                .setParameter("fence",fenceId)
                .setParameter("receipt",receiptId)
                .setParameter("seq",next)
                .setParameter("prev",predecessor)
                .setParameter("actor",admin.userId())
                .setParameter("version",admin.sessionVersion())
                .getResultList();
        if (checkpoints.size()!=1
                || !(checkpoints.getFirst() instanceof UUID checkpointId)) {
            throw denied();
        }
        return checkpointId;
    }

    private static byte[] digest(String lowerHex) {
        if (lowerHex == null || !lowerHex.matches("[0-9a-f]{64}")) {
            throw denied();
        }
        return HexFormat.of().parseHex(lowerHex);
    }

    private static AdminPrincipal actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AdminPrincipal admin)
                || !admin.isEnabled() || admin.userId() == null
                || admin.sessionVersion() < 0) {
            throw denied();
        }
        return admin;
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException("Untrusted MDS source evidence denied");
    }
}
