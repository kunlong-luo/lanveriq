package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRetentionRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRetentionResponse;
import software.amazon.awssdk.services.s3.model.ObjectLockMode;
import software.amazon.awssdk.services.s3.model.ObjectLockRetention;
import software.amazon.awssdk.services.s3.model.ObjectLockRetentionMode;

/** Offline SDK-model fixtures only: NO S3 service/account/KMS is contacted. */
class StagedS3VersionedWitnessEvidenceReaderTest {
    private static final String BUCKET = "security-only-witness-archive";
    private static final String KEY = "untrusted/stream-1/checkpoint.bin";
    private static final String VERSION = "exact-version-M2";
    private static final String OWNER = "123456789012";
    private static final Instant OBSERVED =
            Instant.parse("2026-11-22T12:00:00Z");
    private static final Instant MIN_UNTIL =
            Instant.parse("2026-11-29T12:00:00Z");
    private static final Instant RETAIN_UNTIL =
            Instant.parse("2026-12-22T12:00:00Z");
    private static final byte[] BYTES =
            "synthetic detached witness bytes; NOT OFFICIAL FIDO OR WORM"
                    .getBytes(StandardCharsets.UTF_8);

    private final StagedS3VersionedWitnessEvidenceReader reader =
            new StagedS3VersionedWitnessEvidenceReader();

    private static String sha(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static StagedS3VersionedWitnessEvidenceReader
            .IndependentlyAssertedObjectPin pin() {
        return new StagedS3VersionedWitnessEvidenceReader
                .IndependentlyAssertedObjectPin(
                        BUCKET, KEY, VERSION, OWNER, sha(BYTES), MIN_UNTIL);
    }

    private static GetObjectRequest get() {
        return GetObjectRequest.builder().bucket(BUCKET).key(KEY)
                .versionId(VERSION).expectedBucketOwner(OWNER).build();
    }

    private static GetObjectRetentionRequest retainGet() {
        return GetObjectRetentionRequest.builder().bucket(BUCKET).key(KEY)
                .versionId(VERSION).expectedBucketOwner(OWNER).build();
    }

    private static GetObjectResponse response() {
        return GetObjectResponse.builder().versionId(VERSION)
                .contentLength((long)BYTES.length)
                .objectLockMode(ObjectLockMode.COMPLIANCE)
                .objectLockRetainUntilDate(RETAIN_UNTIL)
                // Deliberately NOT the true digest: ETag must be ignored.
                .eTag("not-a-sha256-of-payload")
                .build();
    }

    private static ResponseBytes<GetObjectResponse> bytes() {
        return ResponseBytes.fromByteArray(response(), BYTES);
    }

    private static GetObjectRetentionResponse retention() {
        return GetObjectRetentionResponse.builder()
                .retention(ObjectLockRetention.builder()
                        .mode(ObjectLockRetentionMode.COMPLIANCE)
                        .retainUntilDate(RETAIN_UNTIL).build())
                .build();
    }

    private StagedS3VersionedWitnessEvidenceReader.UntrustedVersionReadback
            check(
                    StagedS3VersionedWitnessEvidenceReader
                            .IndependentlyAssertedObjectPin pin,
                    GetObjectRequest get,
                    ResponseBytes<GetObjectResponse> bytes,
                    GetObjectRetentionRequest getRetention,
                    GetObjectRetentionResponse retention,
                    Instant observed) {
        return reader.inspectSpecificVersionOfflineOnly(pin, get, bytes,
                getRetention, retention, observed);
    }

    @Test
    void c18cExactVersionComplianceAndActualBytesMatchUntrustedOnly() {
        var result = check(pin(), get(), bytes(), retainGet(),
                retention(), OBSERVED);
        assertEquals(VERSION,result.versionId());
        assertEquals(BUCKET,result.bucket());
        assertEquals(KEY,result.key());
        assertEquals(sha(BYTES),result.contentSha256());
        assertEquals(RETAIN_UNTIL,result.retainUntil());
        // The result deliberately has no trusted/provisioned/approved state.
    }

    @Test
    void c18cRejectsUnversionedWrongVersionWrongOwnerAndPartialReads() {
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get().toBuilder().versionId((String)null).build(),
                        bytes(),retainGet(),retention(),OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get().toBuilder().versionId("new-version").build(),
                        bytes(),retainGet(),retention(),OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get().toBuilder().bucket("attacker").build(),
                        bytes(),retainGet(),retention(),OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get().toBuilder().key("swapped-key").build(),
                        bytes(),retainGet(),retention(),OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get().toBuilder()
                        .expectedBucketOwner("999999999999").build(),
                        bytes(),retainGet(),retention(),OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get().toBuilder().range("bytes=0-8").build(),
                        bytes(),retainGet(),retention(),OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get().toBuilder().partNumber(1).build(),
                        bytes(),retainGet(),retention(),OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get(),bytes(),
                        retainGet().toBuilder().versionId("new-version").build(),
                        retention(),OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get(),bytes(),
                        retainGet().toBuilder().expectedBucketOwner("999999999999").build(),
                        retention(),OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get(),bytes(),
                        retainGet().toBuilder().key("other-key").build(),
                        retention(),OBSERVED));
    }

    @Test
    void c18cRejectsWrongReturnedVersionDeleteMarkerAndTamperedBytes() {
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get(),ResponseBytes.fromByteArray(
                        response().toBuilder().versionId("new-version").build(),
                        BYTES),retainGet(),retention(),OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get(),ResponseBytes.fromByteArray(
                        response().toBuilder().deleteMarker(true).build(),
                        BYTES),retainGet(),retention(),OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get(),ResponseBytes.fromByteArray(
                        response().toBuilder().contentRange("bytes 0-8/100").build(),
                        BYTES),retainGet(),retention(),OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get(),ResponseBytes.fromByteArray(
                        response().toBuilder()
                                .contentLength((long)BYTES.length+1).build(),
                        BYTES),retainGet(),retention(),OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get(),ResponseBytes.fromByteArray(response(),
                        "tampered".getBytes(StandardCharsets.UTF_8)),
                        retainGet(),retention(),OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get(),ResponseBytes.fromByteArray(response(),
                        new byte[0]),retainGet(),retention(),OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get(),ResponseBytes.fromByteArray(
                        response().toBuilder().contentLength(1024L*1024L+1).build(),
                        new byte[1024*1024+1]),retainGet(),retention(),OBSERVED));
        var badDigest=new StagedS3VersionedWitnessEvidenceReader
                .IndependentlyAssertedObjectPin(BUCKET,KEY,VERSION,OWNER,
                        "0".repeat(64),MIN_UNTIL);
        assertThrows(AccessDeniedException.class,() ->
                check(badDigest,get(),bytes(),retainGet(),retention(),OBSERVED));
    }

    @Test
    void c18cRejectsGovernanceMissingRetentionExpiryAndInconsistentHeaders() {
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get(),ResponseBytes.fromByteArray(
                        response().toBuilder()
                                .objectLockMode(ObjectLockMode.GOVERNANCE).build(),
                        BYTES),retainGet(),retention(),OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get(),bytes(),retainGet(),
                        GetObjectRetentionResponse.builder()
                                .retention(ObjectLockRetention.builder()
                                        .mode(ObjectLockRetentionMode.GOVERNANCE)
                                        .retainUntilDate(RETAIN_UNTIL).build())
                                .build(),OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get(),bytes(),retainGet(),
                        GetObjectRetentionResponse.builder().build(),OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get(),ResponseBytes.fromByteArray(
                        response().toBuilder()
                                .objectLockRetainUntilDate((Instant)null).build(),
                        BYTES),retainGet(),retention(),OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get(),bytes(),retainGet(),
                        GetObjectRetentionResponse.builder()
                                .retention(ObjectLockRetention.builder()
                                        .mode(ObjectLockRetentionMode.COMPLIANCE)
                                        .retainUntilDate(MIN_UNTIL).build())
                                .build(),OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get(),bytes(),retainGet(),retention(),
                        RETAIN_UNTIL.plusSeconds(1)));
        var longerPolicy=new StagedS3VersionedWitnessEvidenceReader
                .IndependentlyAssertedObjectPin(BUCKET,KEY,VERSION,OWNER,
                        sha(BYTES),RETAIN_UNTIL.plusSeconds(60));
        assertThrows(AccessDeniedException.class,() ->
                check(longerPolicy,get(),bytes(),retainGet(),retention(),
                        OBSERVED));
    }

    @Test
    void c18cRejectsMissingOrSelfAssertedPins() {
        assertThrows(AccessDeniedException.class,() ->
                check(null,get(),bytes(),retainGet(),retention(),OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get(),bytes(),retainGet(),null,OBSERVED));
        assertThrows(AccessDeniedException.class,() ->
                check(pin(),get(),bytes(),retainGet(),retention(),null));
        var nullVersion=new StagedS3VersionedWitnessEvidenceReader
                .IndependentlyAssertedObjectPin(BUCKET,KEY,"null",OWNER,
                        sha(BYTES),MIN_UNTIL);
        assertThrows(AccessDeniedException.class,() ->
                check(nullVersion,get(),bytes(),retainGet(),retention(),OBSERVED));
        var bogusOwner=new StagedS3VersionedWitnessEvidenceReader
                .IndependentlyAssertedObjectPin(BUCKET,KEY,VERSION,"tester",
                        sha(BYTES),MIN_UNTIL);
        assertThrows(AccessDeniedException.class,() ->
                check(bogusOwner,get(),bytes(),retainGet(),retention(),OBSERVED));
    }
}
