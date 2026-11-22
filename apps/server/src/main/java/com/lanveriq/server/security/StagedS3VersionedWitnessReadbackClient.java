package com.lanveriq.server.security;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRetentionRequest;

/**
 * A11-D4-C18d: bounded, version-specific AWS SDK READ-only bridge to the C18c
 * offline evidence screen. This is a security RESEARCH/STAGING type only.
 *
 * The S3Client is supplied by an explicit caller, NEVER constructed or
 * injected as a Spring bean here. This class is not installed on application
 * boot and has no production caller, secrets, region, IAM, write/retention/
 * delete API, trusted clock, WebAuthn/CA/Agent privilege or endpoint.
 * Supplying an active client would invoke S3: CI injects MOCKS ONLY.
 * The pin and SDK responses do NOT prove cross-domain custody.
 */
final class StagedS3VersionedWitnessReadbackClient {
    // Bound both the declared S3 size and the actual streamed bytes.
    // Future authorized archival support needs separately approved streaming
    // and retention/rollover design, not a silently larger byte array.
    private static final int MAX_BYTES = 1024 * 1024;

    private final S3Client client;
    private final StagedS3VersionedWitnessEvidenceReader inspector;

    StagedS3VersionedWitnessReadbackClient(S3Client client) {
        this.client = Objects.requireNonNull(client);
        this.inspector = new StagedS3VersionedWitnessEvidenceReader();
    }

    StagedS3VersionedWitnessEvidenceReader.UntrustedVersionReadback
            readSpecificVersionOfflineTestOnly(
                    StagedS3VersionedWitnessEvidenceReader
                            .IndependentlyAssertedObjectPin externalPin,
                    Instant observedAt) {
        // Check every caller-supplied authority pin BEFORE invoking the
        // supplied S3Client: invalid pins must cause ZERO network attempts.
        if (externalPin == null || observedAt == null
                || !nonblank(externalPin.bucket())
                || !nonblank(externalPin.key())
                || !nonblank(externalPin.versionId())
                || "null".equals(externalPin.versionId())
                || externalPin.expectedBucketOwner() == null
                || !externalPin.expectedBucketOwner().matches("[0-9]{12}")
                || externalPin.expectedContentSha256() == null
                || !externalPin.expectedContentSha256()
                        .matches("[0-9a-f]{64}")
                || externalPin.minimumRetainUntil() == null
                || !externalPin.minimumRetainUntil().isAfter(observedAt)) {
            throw denied();
        }

        // The exact version and expected bucket-owner apply to BOTH SDK
        // reads. No latest-object fallback, unversioned HEAD, prefixes,
        // listing, ranged/partial reads or candidate version discovery.
        var get = GetObjectRequest.builder()
                .bucket(externalPin.bucket())
                .key(externalPin.key())
                .versionId(externalPin.versionId())
                .expectedBucketOwner(externalPin.expectedBucketOwner())
                .build();
        var getRetention = GetObjectRetentionRequest.builder()
                .bucket(externalPin.bucket())
                .key(externalPin.key())
                .versionId(externalPin.versionId())
                .expectedBucketOwner(externalPin.expectedBucketOwner())
                .build();
        try {
            ResponseBytes<GetObjectResponse> bounded;
            try (var stream = client.getObject(get)) {
                if (stream == null || stream.response() == null) {
                    throw denied();
                }
                var metadata = stream.response();
                Long claimedSize = metadata.contentLength();
                if (claimedSize == null || claimedSize <= 0
                        || claimedSize > MAX_BYTES
                        || !externalPin.versionId()
                                .equals(metadata.versionId())
                        || Boolean.TRUE.equals(metadata.deleteMarker())) {
                    stream.abort(); // do not drain a potentially huge response
                    throw denied();
                }

                // readNBytes(limit+1) detects oversized bodies even if the
                // returned Content-Length is forged. Never call unbounded
                // readAllBytes()/getObjectAsBytes().
                byte[] body = stream.readNBytes(MAX_BYTES + 1);
                if (body.length > MAX_BYTES
                        || body.length != claimedSize.longValue()) {
                    stream.abort();
                    throw denied();
                }
                bounded = ResponseBytes.fromByteArray(metadata, body);
            }

            var retention = client.getObjectRetention(getRetention);
            return inspector.inspectSpecificVersionOfflineOnly(externalPin,
                    get, bounded, getRetention, retention, observedAt);
        } catch (IOException | RuntimeException error) {
            // Includes SDK 403/404/timeout, missing retention permissions,
            // malformed response, version mismatch and failing stream reads.
            // Do not retry a different version or downgrade to GOVERNANCE.
            throw denied();
        }
    }

    private static boolean nonblank(String value) {
        return value != null && !value.isBlank()
                && value.equals(value.strip());
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException(
                "Exact-version S3 witness readback or retention unavailable");
    }
}
