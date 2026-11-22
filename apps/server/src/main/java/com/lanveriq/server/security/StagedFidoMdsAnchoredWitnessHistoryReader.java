package com.lanveriq.server.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;

/**
 * A11-D4-C17: offline inspection of a COMPLETE, independently retained,
 * signed witness history before checking its head against PostgreSQL V30.
 *
 * This is NOT external WORM storage, a trusted clock, independent key
 * custody, production source attestation, or a trust-admission service.
 * The history, head and Ed25519 pin MUST genuinely originate outside the
 * controlled database for rollback detection to mean anything.
 * No Spring bean, endpoint, writer, background task or production switch.
 */
final class StagedFidoMdsAnchoredWitnessHistoryReader {
    // Bound offline imports so an untrusted journal cannot exhaust memory/CPU.
    // Operators must implement separately audited archival rollover before
    // this bounded synthetic verifier could be used for a larger journal.
    private static final int MAX_CHECKPOINTS = 4096;

    private final StagedFidoMdsDetachedWitnessReader currentTailReader;

    StagedFidoMdsAnchoredWitnessHistoryReader(
            StagedFidoMdsDetachedWitnessReader currentTailReader) {
        this.currentTailReader = Objects.requireNonNull(currentTailReader);
    }

    record IndependentlyRetainedHead(
            UUID streamId,
            long witnessSequence,
            String detachedWitnessSha256,
            String witnessPublicKeySha256) {}

    StagedFidoMdsDetachedWitnessReader.UntrustedDetachedWitnessMatch
            inspectCompleteHistoryAgainstCurrentDbOfflineOnly(
                    StagedFidoMdsDetachedWitnessReader.PinnedWitnessKey externalPin,
                    List<StagedFidoMdsDetachedWitnessReader.SignedCheckpoint>
                            externallyRetainedHistory,
                    IndependentlyRetainedHead externalHead,
                    UUID expectedStream,
                    Instant locallyObservedTime) {
        if (externalPin == null || externalPin.key() == null
                || externallyRetainedHistory == null
                || externallyRetainedHistory.isEmpty()
                || externallyRetainedHistory.size() > MAX_CHECKPOINTS
                || externalHead == null || expectedStream == null
                || locallyObservedTime == null
                || !expectedStream.equals(externalHead.streamId())
                || externalHead.witnessSequence()
                        != externallyRetainedHistory.size()
                || !validHash(externalHead.detachedWitnessSha256())
                || !validHash(externalHead.witnessPublicKeySha256())) {
            throw denied();
        }

        try {
            var verifierKey = externalPin.key();
            if (!"Ed25519".equalsIgnoreCase(verifierKey.getAlgorithm())
                    && !"EdDSA".equalsIgnoreCase(verifierKey.getAlgorithm())) {
                throw denied();
            }
            String pin = hash(verifierKey.getEncoded());
            if (!pin.equals(externalPin.keySha256())
                    || !pin.equals(externalHead.witnessPublicKeySha256())) {
                throw denied();
            }

            String previous = null;
            long previousAcquisitionSequence = 0;
            for (int index = 0; index < externallyRetainedHistory.size();
                    index++) {
                var signed = externallyRetainedHistory.get(index);
                if (signed == null || signed.claim() == null) {
                    throw denied();
                }
                var claim = signed.claim();
                if (!expectedStream.equals(claim.streamId())
                        || claim.witnessSequence() != index + 1L
                        || !Objects.equals(previous,
                                claim.previousWitnessSha256())
                        || claim.acquisitionSequence()
                                < previousAcquisitionSequence
                        || claim.assertedWitnessTime() == null
                        || claim.assertedWitnessTime().isAfter(
                                locallyObservedTime.plusSeconds(300))
                        || pin.equals(claim.acquisitionKeySha256())) {
                    throw denied();
                }

                byte[] transcript =
                        StagedFidoMdsDetachedWitnessReader.transcript(claim);
                byte[] signature = signed.signature();
                if (signature == null || signature.length != 64) {
                    throw denied();
                }
                var verifier = Signature.getInstance("Ed25519");
                verifier.initVerify(verifierKey);
                verifier.update(transcript);
                if (!verifier.verify(signature)) {
                    throw denied();
                }
                byte[] signedBytes =
                        new byte[transcript.length + signature.length];
                System.arraycopy(transcript, 0, signedBytes, 0,
                        transcript.length);
                System.arraycopy(signature, 0, signedBytes,
                        transcript.length, signature.length);
                previous = hash(signedBytes);
                previousAcquisitionSequence = claim.acquisitionSequence();
            }

            if (!externalHead.detachedWitnessSha256().equals(previous)) {
                throw denied();
            }

            // Only the actual signed head may be compared against the CURRENT
            // V30 tail. This separate PG snapshot check enforces audit/row
            // identity and rejects databases that have advanced or rewound.
            var latest = externallyRetainedHistory.getLast();
            var latestClaim = latest.claim();
            var matchingCursor =
                    new StagedFidoMdsDetachedWitnessReader.ExternalCursor(
                            latestClaim.witnessSequence(),
                            latestClaim.previousWitnessSha256());
            var result = currentTailReader.inspectAgainstCurrentDbTailOfflineOnly(
                    externalPin, latest, matchingCursor, expectedStream,
                    locallyObservedTime);
            if (!previous.equals(result.detachedWitnessSha256())) {
                throw denied();
            }
            return result; // UNTRUSTED only: never authorize WebAuthn/CA/Agent.
        } catch (GeneralSecurityException | IllegalArgumentException |
                 ClassCastException error) {
            throw denied();
        }
    }

    private static String hash(byte[] bytes) throws GeneralSecurityException {
        if (bytes == null) {
            throw denied();
        }
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static boolean validHash(String hash) {
        return hash != null && hash.matches("[0-9a-f]{64}");
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException(
                "Detached FIDO witness history or independent head invalid");
    }
}
