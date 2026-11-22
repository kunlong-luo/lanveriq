package com.lanveriq.server.security;

import jakarta.persistence.EntityManager;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * D4-C15: package-private, non-Bean staging writer, not production admission.
 * Caller owns one transaction; V30 DB guards compute the chained digest,
 * serialize source-stream writes and automatically insert the audit.
 *
 * A supplied untrusted evidence record is NOT a substitute for upstream
 * cryptographic verification or independently provisioned acquisition keys.
 * No method here verifies Ed25519, fetches MDS or authorizes FIDO/CA.
 */
final class StagedFidoMdsAcquisitionReceiptLedger {
    private final EntityManager manager;

    StagedFidoMdsAcquisitionReceiptLedger(EntityManager manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    UUID appendUntrustedOnly(
            UUID sourceReceiptId,
            StagedFidoMdsAcquisitionReceiptScreen.UntrustedAcquisitionEvidence evidence,
            StagedFidoMdsAcquisitionReceiptScreen.SignedAcquisition signed) {
        var admin = actor();
        if (sourceReceiptId == null || evidence == null || signed == null
                || signed.claim() == null || signed.signature() == null
                || evidence.sequence() <= 0
                || signed.claim().sequence() != evidence.sequence()) {
            throw denied();
        }
        byte[] transcript = StagedFidoMdsAcquisitionReceiptScreen
                .transcript(signed.claim());
        if (transcript.length < 128 || transcript.length > 32768
                || signed.signature().length != 64) {
            throw denied();
        }
        final byte[] key, jws, crl;
        try {
            key = digest(evidence.acquisitionKeySha256());
            jws = digest(evidence.jwsSha256());
            crl = digest(evidence.crlEvidenceSha256());
            if (!MessageDigest.isEqual(
                    sha256(transcript), digest(evidence.signedManifestSha256()))
                    || !MessageDigest.isEqual(
                    sha256(signed.signature()),
                    digest(evidence.acquisitionSignatureSha256()))
                    || !evidence.jwsSha256().equals(
                            signed.claim().jwsSha256())
                    || !evidence.crlEvidenceSha256().equals(
                            signed.claim().crlEvidenceSha256())) {
                throw denied();
            }
        } catch (GeneralSecurityException invalid) {
            throw denied();
        }
        var source = manager.createNativeQuery("""
                SELECT stream_id,receipt_sequence
                FROM admin_fido_mds_source_receipts_staging
                WHERE receipt_id=:id AND evidence_state='source_untrusted'
                """).setParameter("id",sourceReceiptId).getResultList();
        if (source.size()!=1) {
            throw denied();
        }
        Object[] row=(Object[])source.getFirst();
        UUID streamId=(UUID)row[0];
        if (((Number)row[1]).longValue()!=evidence.sequence()) {
            throw denied();
        }
        var prior=manager.createNativeQuery("""
                SELECT acquisition_sequence,acquisition_sha256
                FROM admin_fido_mds_acquisition_receipts_staging
                WHERE stream_id=:stream
                ORDER BY acquisition_sequence DESC LIMIT 1
                """).setParameter("stream",streamId).getResultList();
        byte[] predecessor=null;
        if (prior.isEmpty()) {
            if (evidence.sequence()!=1) {
                throw denied();
            }
        } else {
            Object[] previous=(Object[])prior.getFirst();
            long last=((Number)previous[0]).longValue();
            if (last==Long.MAX_VALUE || evidence.sequence()!=last+1) {
                throw denied();
            }
            predecessor=(byte[])previous[1];
        }
        // Optimistic read is only a hint; V30 serializes on the V25 stream
        // FOR UPDATE and rejects races/stale predecessors from direct SQL.
        var results=manager.createNativeQuery("""
                INSERT INTO admin_fido_mds_acquisition_receipts_staging
                  (stream_id,source_receipt_id,acquisition_sequence,
                   signed_manifest,signature_bytes,acquisition_key_sha256,
                   jws_sha256,crl_evidence_sha256,
                   previous_acquisition_sha256,recorded_by,
                   recorder_session_version)
                VALUES (:stream,:source,:seq,:manifest,:signature,
                        :key,:jws,:crl,:previous,:actor,:version)
                RETURNING acquisition_id
                """)
                .setParameter("stream",streamId)
                .setParameter("source",sourceReceiptId)
                .setParameter("seq",evidence.sequence())
                .setParameter("manifest",transcript)
                .setParameter("signature",signed.signature())
                .setParameter("key",key)
                .setParameter("jws",jws)
                .setParameter("crl",crl)
                .setParameter("previous",predecessor)
                .setParameter("actor",admin.userId())
                .setParameter("version",admin.sessionVersion())
                .getResultList();
        if (results.size()!=1 || !(results.getFirst() instanceof UUID id)) {
            throw denied();
        }
        return id;
    }

    private static byte[] digest(String hex) {
        if (hex==null || !hex.matches("[0-9a-f]{64}")) {
            throw denied();
        }
        return HexFormat.of().parseHex(hex);
    }

    private static byte[] sha256(byte[] raw) throws GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256").digest(raw);
    }

    private static AdminPrincipal actor() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if (auth==null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AdminPrincipal principal)
                || !principal.isEnabled() || principal.userId()==null
                || principal.sessionVersion()<0) {
            throw denied();
        }
        return principal;
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException(
                "Untrusted acquisition staging evidence rejected");
    }
}
