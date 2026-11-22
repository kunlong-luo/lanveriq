package com.lanveriq.server.security;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

/**
 * A11-D4-C16: verify a DETACHED Ed25519-signed checkpoint of the latest
 * synthetic V30 acquisition receipt against a live PostgreSQL read.
 *
 * No signer, private witness key, Spring bean, controller, network transport,
 * external witness storage or automatic acceptance is installed.
 *
 * All publicly supplied pins/cursors are UNTRUSTED unless a separate system
 * actually provisions them outside PostgreSQL. A caller-created signature
 * and key pin, or DB-only storage of this artifact, offers no independent
 * rollback protection. This reader NEVER grants FIDO/WebAuthn/CA access.
 */
final class StagedFidoMdsDetachedWitnessReader {
    private static final String DOMAIN =
            "lanveriq/fido-mds/detached-witness-checkpoint/v1\n";

    private final EntityManager manager;

    StagedFidoMdsDetachedWitnessReader(EntityManager manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    record PinnedWitnessKey(PublicKey key, String keySha256) {}

    record DetachedCheckpoint(
            UUID streamId, UUID acquisitionId, UUID sourceReceiptId,
            long acquisitionSequence, String acquisitionSha256,
            String sourceReceiptSha256, String acquisitionKeySha256,
            long witnessSequence, String previousWitnessSha256,
            Instant assertedWitnessTime) {}

    record SignedCheckpoint(DetachedCheckpoint claim, byte[] signature) {
        SignedCheckpoint {
            signature = signature == null ? null : signature.clone();
        }

        @Override public byte[] signature() {
            return signature == null ? null : signature.clone();
        }
    }

    record ExternalCursor(long nextWitnessSequence,
                          String priorDetachedWitnessSha256) {}

    record UntrustedDetachedWitnessMatch(UUID streamId,
                                         long acquisitionSequence,
                                         long witnessSequence,
                                         String detachedWitnessSha256) {}

    UntrustedDetachedWitnessMatch inspectAgainstCurrentDbTailOfflineOnly(
            PinnedWitnessKey independentlyProvisioned,
            SignedCheckpoint candidate,
            ExternalCursor independentlyRetainedCursor,
            UUID expectedStream,
            Instant locallyObservedTime) {
        if (independentlyProvisioned == null || candidate == null
                || candidate.claim() == null
                || independentlyRetainedCursor == null
                || expectedStream == null || locallyObservedTime == null) {
            throw denied();
        }
        try {
            DetachedCheckpoint claim = candidate.claim();
            PublicKey key = independentlyProvisioned.key();
            String keyPin = independentlyProvisioned.keySha256();
            byte[] signature = candidate.signature();
            if (key == null || !isEd25519(key)
                    || !validHash(keyPin) || !keyPin.equals(sha256(key.getEncoded()))
                    || signature == null || signature.length != 64
                    || claim.streamId() == null
                    || !claim.streamId().equals(expectedStream)
                    || claim.acquisitionId() == null
                    || claim.sourceReceiptId() == null
                    || claim.acquisitionSequence() <= 0
                    || claim.witnessSequence() <= 0
                    || claim.witnessSequence()
                            != independentlyRetainedCursor.nextWitnessSequence()
                    || !validHash(claim.acquisitionSha256())
                    || !validHash(claim.sourceReceiptSha256())
                    || !validHash(claim.acquisitionKeySha256())
                    // Witness and acquisition signers must not be the same
                    // key. Independent human custody is still not proven.
                    || keyPin.equals(claim.acquisitionKeySha256())
                    || claim.assertedWitnessTime() == null
                    || claim.assertedWitnessTime()
                            .isAfter(locallyObservedTime.plusSeconds(300))
                    || !validPrevious(claim, independentlyRetainedCursor)) {
                throw denied();
            }
            byte[] transcript = transcript(claim);
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(transcript);
            if (!verifier.verify(signature)) {
                throw denied();
            }

            // Exactly ONE SQL statement takes an internally consistent PG
            // read snapshot. Do not query an older matching row while a
            // later unaudited row exists: select the real tail FIRST, and
            // verify its attached V26 source and audit afterwards.
            var latest = manager.createNativeQuery("""
                    SELECT a.acquisition_id,
                           a.source_receipt_id,
                           a.acquisition_sequence,
                           a.acquisition_sha256,
                           s.receipt_sha256,
                           a.acquisition_key_sha256,
                           a.evidence_state,
                           s.evidence_state,
                           (SELECT COUNT(*) FROM admin_access_log log
                            WHERE log.resource_id=a.acquisition_id
                              AND log.principal_id=a.recorded_by
                              AND log.action=
                                  'admin.passkey.mds.source.untrusted.acquisition'
                              AND log.outcome='allowed') AS audit_count
                    FROM admin_fido_mds_acquisition_receipts_staging a
                    LEFT JOIN admin_fido_mds_source_receipts_staging s
                           ON s.receipt_id=a.source_receipt_id
                          AND s.stream_id=a.stream_id
                    WHERE a.stream_id=:stream
                    ORDER BY a.acquisition_sequence DESC
                    LIMIT 1
                    """).setParameter("stream", expectedStream).getResultList();
            if (latest.size() != 1) {
                throw denied();
            }
            Object[] current = (Object[]) latest.getFirst();
            if (!claim.acquisitionId().equals(current[0])
                    || !claim.sourceReceiptId().equals(current[1])
                    || claim.acquisitionSequence()
                            != ((Number) current[2]).longValue()
                    || !claim.acquisitionSha256().equals(
                            encodeHash(current[3]))
                    || !claim.sourceReceiptSha256().equals(
                            encodeHash(current[4]))
                    || !claim.acquisitionKeySha256().equals(
                            encodeHash(current[5]))
                    || !"acquisition_untrusted".equals(current[6])
                    || !"source_untrusted".equals(current[7])
                    || ((Number) current[8]).longValue() != 1L) {
                throw denied();
            }

            // A witness receipt is the SHA-256 of both canonical signed
            // bytes and signature, suitable for separately RETAINED external
            // predecessor references; no external store is configured here.
            byte[] entire = new byte[transcript.length + signature.length];
            System.arraycopy(transcript, 0, entire, 0, transcript.length);
            System.arraycopy(signature, 0, entire, transcript.length,
                    signature.length);
            return new UntrustedDetachedWitnessMatch(
                    claim.streamId(), claim.acquisitionSequence(),
                    claim.witnessSequence(), sha256(entire));
        } catch (GeneralSecurityException | IllegalArgumentException |
                 ClassCastException error) {
            throw denied();
        }
    }

    static byte[] transcript(DetachedCheckpoint claim) {
        if (claim == null || claim.streamId() == null
                || claim.acquisitionId() == null || claim.sourceReceiptId() == null
                || claim.acquisitionSequence() <= 0
                || claim.witnessSequence() <= 0
                || !validHash(claim.acquisitionSha256())
                || !validHash(claim.sourceReceiptSha256())
                || !validHash(claim.acquisitionKeySha256())
                || claim.assertedWitnessTime() == null
                || (claim.witnessSequence() == 1
                    ? claim.previousWitnessSha256() != null
                    : !validHash(claim.previousWitnessSha256()))) {
            throw denied();
        }
        return (DOMAIN + claim.streamId() + "\n"
                + claim.acquisitionId() + "\n"
                + claim.sourceReceiptId() + "\n"
                + claim.acquisitionSequence() + "\n"
                + claim.acquisitionSha256() + "\n"
                + claim.sourceReceiptSha256() + "\n"
                + claim.acquisitionKeySha256() + "\n"
                + claim.witnessSequence() + "\n"
                + (claim.previousWitnessSha256() == null
                        ? "GENESIS" : claim.previousWitnessSha256()) + "\n"
                + claim.assertedWitnessTime() + "\n"
                ).getBytes(StandardCharsets.US_ASCII);
    }

    private static boolean validPrevious(DetachedCheckpoint claim,
                                         ExternalCursor external) {
        if (external.nextWitnessSequence() <= 0) {
            return false;
        }
        if (external.nextWitnessSequence() == 1) {
            return claim.previousWitnessSha256() == null
                    && external.priorDetachedWitnessSha256() == null;
        }
        return validHash(external.priorDetachedWitnessSha256())
                && external.priorDetachedWitnessSha256().equals(
                        claim.previousWitnessSha256());
    }

    private static String encodeHash(Object value) {
        if (!(value instanceof byte[] bytes) || bytes.length != 32) {
            throw denied();
        }
        return HexFormat.of().formatHex(bytes);
    }

    private static String sha256(byte[] input) throws GeneralSecurityException {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(input));
    }

    private static boolean validHash(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static boolean isEd25519(PublicKey key) {
        return "Ed25519".equalsIgnoreCase(key.getAlgorithm())
                || "EdDSA".equalsIgnoreCase(key.getAlgorithm());
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException(
                "Detached offline FIDO source witness is missing or invalid");
    }
}
