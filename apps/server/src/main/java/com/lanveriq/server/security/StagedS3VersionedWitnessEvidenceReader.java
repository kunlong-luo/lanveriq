package com.lanveriq.server.security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.security.access.AccessDeniedException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRetentionRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRetentionResponse;
import software.amazon.awssdk.services.s3.model.ObjectLockMode;
import software.amazon.awssdk.services.s3.model.ObjectLockRetentionMode;

/**
 * D4-C18c: offline, fail-closed inspection of a SPECIFIC version's S3 SDK
 * request/response evidence; does NOT fetch S3, attest the AWS account or
 * retain anything. Every input here is caller-supplied and therefore
 * untrusted without independently governed custody/read-back.
 *
 * No Spring bean, S3Client, credentials, signer, network calls, write API,
 * WebAuthn/CA/Agent authorization or feature flag.
 */
final class StagedS3VersionedWitnessEvidenceReader {
    private static final int MAX_OFFLINE_BYTES = 1024 * 1024;

    /**
     * All pins, especially the owner and version, must be approved outside
     * the database/app control domain. Code cannot prove that provenance.
     */
    record IndependentlyAssertedObjectPin(
            String bucket, String key, String versionId,
            String expectedBucketOwner, String expectedContentSha256,
            Instant minimumRetainUntil) {}

    record UntrustedVersionReadback(
            String bucket, String key, String versionId,
            String contentSha256, Instant retainUntil) {}

    UntrustedVersionReadback inspectSpecificVersionOfflineOnly(
            IndependentlyAssertedObjectPin expected,
            GetObjectRequest objectRequest,
            ResponseBytes<GetObjectResponse> objectReadback,
            GetObjectRetentionRequest retentionRequest,
            GetObjectRetentionResponse retentionReadback,
            Instant locallyObservedTime) {
        if (expected == null || objectRequest == null
                || objectReadback == null || objectReadback.response() == null
                || retentionRequest == null || retentionReadback == null
                || retentionReadback.retention() == null
                || locallyObservedTime == null
                || !nonblank(expected.bucket())
                || !nonblank(expected.key())
                || !validVersion(expected.versionId())
                || expected.expectedBucketOwner() == null
                || !expected.expectedBucketOwner().matches("[0-9]{12}")
                || !validHash(expected.expectedContentSha256())
                || expected.minimumRetainUntil() == null
                || !expected.minimumRetainUntil()
                        .isAfter(locallyObservedTime)) {
            throw denied();
        }

        // Both calls MUST address precisely the pinned version. An unversioned
        // GET can silently select a later object, or a delete marker.
        if (!expected.bucket().equals(objectRequest.bucket())
                || !expected.key().equals(objectRequest.key())
                || !expected.versionId().equals(objectRequest.versionId())
                || !expected.expectedBucketOwner()
                        .equals(objectRequest.expectedBucketOwner())
                || objectRequest.range() != null
                || objectRequest.partNumber() != null
                || !expected.bucket().equals(retentionRequest.bucket())
                || !expected.key().equals(retentionRequest.key())
                || !expected.versionId().equals(retentionRequest.versionId())
                || !expected.expectedBucketOwner()
                        .equals(retentionRequest.expectedBucketOwner())) {
            throw denied();
        }

        var response = objectReadback.response();
        var retention = retentionReadback.retention();
        Instant objectUntil = response.objectLockRetainUntilDate();
        Instant separatelyReadUntil = retention.retainUntilDate();
        if (!expected.versionId().equals(response.versionId())
                || Boolean.TRUE.equals(response.deleteMarker())
                || response.contentRange() != null
                || response.objectLockMode() != ObjectLockMode.COMPLIANCE
                || retention.mode() != ObjectLockRetentionMode.COMPLIANCE
                || objectUntil == null || separatelyReadUntil == null
                || !objectUntil.equals(separatelyReadUntil)
                || separatelyReadUntil.isBefore(expected.minimumRetainUntil())
                || !separatelyReadUntil.isAfter(locallyObservedTime)) {
            throw denied();
        }

        // NEVER treat ETag or claimed S3 checksum headers as the independent
        // bytes digest, especially for multipart / encrypted objects.
        // ResponseBytes.asByteArray() returns a safe copy.
        byte[] bytes = objectReadback.asByteArray();
        if (bytes.length == 0 || bytes.length > MAX_OFFLINE_BYTES
                || response.contentLength() == null
                || response.contentLength() != (long) bytes.length) {
            throw denied();
        }
        try {
            String actual = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
            if (!expected.expectedContentSha256().equals(actual)) {
                throw denied();
            }
            return new UntrustedVersionReadback(expected.bucket(),
                    expected.key(), expected.versionId(), actual,
                    separatelyReadUntil);
        } catch (GeneralSecurityException | IllegalArgumentException error) {
            throw denied();
        }
    }

    private static boolean nonblank(String input) {
        return input != null && !input.isBlank()
                && input.equals(input.strip());
    }

    private static boolean validVersion(String version) {
        return nonblank(version) && !"null".equals(version);
    }

    private static boolean validHash(String input) {
        return input != null && input.matches("[0-9a-f]{64}");
    }

    private static AccessDeniedException denied() {
        return new AccessDeniedException(
                "S3 specific-version witness/retention evidence invalid or untrusted");
    }
}
