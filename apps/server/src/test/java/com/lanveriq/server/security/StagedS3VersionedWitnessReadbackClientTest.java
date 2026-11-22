package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRetentionRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRetentionResponse;
import software.amazon.awssdk.services.s3.model.ObjectLockMode;
import software.amazon.awssdk.services.s3.model.ObjectLockRetention;
import software.amazon.awssdk.services.s3.model.ObjectLockRetentionMode;
import software.amazon.awssdk.services.s3.model.S3Exception;

/** A11-C18d: Mockito-only official SDK boundary. NO AWS/cloud endpoint. */
class StagedS3VersionedWitnessReadbackClientTest {
    private static final String BUCKET = "test-independent-security-bucket";
    private static final String KEY = "untrusted/witness/stream/seq-2";
    private static final String VERSION = "immutable-object-version-2";
    private static final String OWNER = "123456789012";
    private static final Instant TIME = Instant.parse("2026-11-22T12:00:00Z");
    private static final Instant UNTIL = Instant.parse("2026-12-31T12:00:00Z");
    private static final Instant MIN = Instant.parse("2026-12-01T12:00:00Z");
    private static final byte[] BYTES =
            "only synthetic witness bytes for Mockito".getBytes(
                    StandardCharsets.US_ASCII);

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static StagedS3VersionedWitnessEvidenceReader
            .IndependentlyAssertedObjectPin pin() throws Exception {
        return new StagedS3VersionedWitnessEvidenceReader
                .IndependentlyAssertedObjectPin(
                        BUCKET,KEY,VERSION,OWNER,sha(BYTES),MIN);
    }

    private static GetObjectResponse response() {
        return GetObjectResponse.builder().versionId(VERSION)
                .contentLength((long)BYTES.length)
                .objectLockMode(ObjectLockMode.COMPLIANCE)
                .objectLockRetainUntilDate(UNTIL).build();
    }

    private static ResponseInputStream<GetObjectResponse> stream(
            GetObjectResponse response, byte[] bytes) {
        return new ResponseInputStream<>(response,
                new ByteArrayInputStream(bytes));
    }

    private static GetObjectRetentionResponse retention() {
        return GetObjectRetentionResponse.builder()
                .retention(ObjectLockRetention.builder()
                        .mode(ObjectLockRetentionMode.COMPLIANCE)
                        .retainUntilDate(UNTIL).build())
                .build();
    }

    private static void validMocks(S3Client client) {
        when(client.getObject(any(GetObjectRequest.class))).thenAnswer(
                ignored -> stream(response(), BYTES));
        when(client.getObjectRetention(any(GetObjectRetentionRequest.class)))
                .thenReturn(retention());
    }

    @Test
    void c18dCallsOnlyExactVersionWithExpectedOwnerAndReturnsUntrusted()
            throws Exception {
        S3Client client = mock(S3Client.class);
        validMocks(client);
        var reader = new StagedS3VersionedWitnessReadbackClient(client);
        var result = reader.readSpecificVersionOfflineTestOnly(pin(),TIME);
        assertEquals(VERSION,result.versionId());
        assertEquals(sha(BYTES),result.contentSha256());
        assertEquals(UNTIL,result.retainUntil());

        var order = inOrder(client);
        order.verify(client).getObject(org.mockito.ArgumentMatchers.argThat(
                req -> BUCKET.equals(req.bucket()) && KEY.equals(req.key())
                        && VERSION.equals(req.versionId())
                        && OWNER.equals(req.expectedBucketOwner())
                        && req.range() == null && req.partNumber() == null));
        order.verify(client).getObjectRetention(
                org.mockito.ArgumentMatchers.argThat(
                req -> BUCKET.equals(req.bucket()) && KEY.equals(req.key())
                        && VERSION.equals(req.versionId())
                        && OWNER.equals(req.expectedBucketOwner())));
        order.verifyNoMoreInteractions();
    }

    @Test
    void c18dInvalidIndependentPinFailsBeforeAnySdkCall() throws Exception {
        S3Client client = mock(S3Client.class);
        var reader = new StagedS3VersionedWitnessReadbackClient(client);
        assertThrows(AccessDeniedException.class,() ->
                reader.readSpecificVersionOfflineTestOnly(null,TIME));
        assertThrows(AccessDeniedException.class,() ->
                reader.readSpecificVersionOfflineTestOnly(pin(),null));
        assertThrows(AccessDeniedException.class,() ->
                reader.readSpecificVersionOfflineTestOnly(
                        new StagedS3VersionedWitnessEvidenceReader
                                .IndependentlyAssertedObjectPin(
                                        BUCKET,KEY,"null",OWNER,sha(BYTES),MIN),
                        TIME));
        assertThrows(AccessDeniedException.class,() ->
                reader.readSpecificVersionOfflineTestOnly(
                        new StagedS3VersionedWitnessEvidenceReader
                                .IndependentlyAssertedObjectPin(
                                        BUCKET,KEY,VERSION,"not-an-account",
                                        sha(BYTES),MIN),TIME));
        assertThrows(AccessDeniedException.class,() ->
                reader.readSpecificVersionOfflineTestOnly(
                        new StagedS3VersionedWitnessEvidenceReader
                                .IndependentlyAssertedObjectPin(
                                        BUCKET,KEY,VERSION,OWNER,
                                        "0".repeat(64),TIME),TIME));
        verifyNoInteractions(client);
    }

    @Test
    void c18dGetObject403AndMissingVersionFailClosedWithoutFallback()
            throws Exception {
        S3Client client = mock(S3Client.class);
        when(client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403)
                        .message("test AccessDenied").build());
        var reader = new StagedS3VersionedWitnessReadbackClient(client);
        assertThrows(AccessDeniedException.class,() ->
                reader.readSpecificVersionOfflineTestOnly(pin(),TIME));
        verify(client).getObject(any(GetObjectRequest.class));
        verify(client,never()).getObjectRetention(
                any(GetObjectRetentionRequest.class));
    }

    @Test
    void c18dMissingRetention403NeverDowngradesToObjectHeader()
            throws Exception {
        S3Client client=mock(S3Client.class);
        when(client.getObject(any(GetObjectRequest.class))).thenAnswer(
                ignored -> stream(response(),BYTES));
        when(client.getObjectRetention(any(GetObjectRetentionRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403)
                        .message("test retention permission denied").build());
        assertThrows(AccessDeniedException.class,() ->
                new StagedS3VersionedWitnessReadbackClient(client)
                        .readSpecificVersionOfflineTestOnly(pin(),TIME));
        verify(client).getObject(any(GetObjectRequest.class));
        verify(client).getObjectRetention(
                any(GetObjectRetentionRequest.class));
    }

    @Test
    void c18dMissingWrongVersionAndGovernanceDenyBeforeOrAfterRetention()
            throws Exception {
        S3Client client=mock(S3Client.class);
        when(client.getObject(any(GetObjectRequest.class))).thenAnswer(
                ignored -> stream(response().toBuilder()
                        .versionId("newer-unauthorized-version").build(),BYTES));
        var reader=new StagedS3VersionedWitnessReadbackClient(client);
        assertThrows(AccessDeniedException.class,() ->
                reader.readSpecificVersionOfflineTestOnly(pin(),TIME));
        verify(client,never()).getObjectRetention(
                any(GetObjectRetentionRequest.class));

        S3Client downgrade=mock(S3Client.class);
        when(downgrade.getObject(any(GetObjectRequest.class))).thenAnswer(
                ignored -> stream(response(),BYTES));
        when(downgrade.getObjectRetention(any(GetObjectRetentionRequest.class)))
                .thenReturn(GetObjectRetentionResponse.builder()
                        .retention(ObjectLockRetention.builder()
                                .mode(ObjectLockRetentionMode.GOVERNANCE)
                                .retainUntilDate(UNTIL).build()).build());
        assertThrows(AccessDeniedException.class,() ->
                new StagedS3VersionedWitnessReadbackClient(downgrade)
                        .readSpecificVersionOfflineTestOnly(pin(),TIME));
    }

    @Test
    void c18dForgedLengthOversizeOrTamperingCannotReachAcceptance()
            throws Exception {
        // Declared content longer than 1 MiB is refused BEFORE reading.
        S3Client tooLong=mock(S3Client.class);
        when(tooLong.getObject(any(GetObjectRequest.class))).thenAnswer(
                ignored -> stream(response().toBuilder()
                        .contentLength(1024L*1024L+1).build(),
                        new byte[0]));
        assertThrows(AccessDeniedException.class,() ->
                new StagedS3VersionedWitnessReadbackClient(tooLong)
                        .readSpecificVersionOfflineTestOnly(pin(),TIME));
        verify(tooLong,never()).getObjectRetention(
                any(GetObjectRetentionRequest.class));

        // A forged small Content-Length cannot hide extra body bytes.
        S3Client extraBytes=mock(S3Client.class);
        when(extraBytes.getObject(any(GetObjectRequest.class))).thenAnswer(
                ignored -> stream(response(),new byte[1024*1024+1]));
        assertThrows(AccessDeniedException.class,() ->
                new StagedS3VersionedWitnessReadbackClient(extraBytes)
                        .readSpecificVersionOfflineTestOnly(pin(),TIME));
        verify(extraBytes,never()).getObjectRetention(
                any(GetObjectRetentionRequest.class));

        S3Client tampered=mock(S3Client.class);
        when(tampered.getObject(any(GetObjectRequest.class))).thenAnswer(
                ignored -> stream(response(),"forged".getBytes(
                        StandardCharsets.US_ASCII)));
        assertThrows(AccessDeniedException.class,() ->
                new StagedS3VersionedWitnessReadbackClient(tampered)
                        .readSpecificVersionOfflineTestOnly(pin(),TIME));
    }

    @Test
    void c18dInterruptedStreamIsRejectedWithoutRetentionRead()
            throws Exception {
        S3Client client=mock(S3Client.class);
        when(client.getObject(any(GetObjectRequest.class))).thenAnswer(
                ignored -> new ResponseInputStream<>(response(),
                        new InputStream() {
                            @Override public int read() throws IOException {
                                throw new IOException("synthetic broken stream");
                            }
                        }));
        assertThrows(AccessDeniedException.class,() ->
                new StagedS3VersionedWitnessReadbackClient(client)
                        .readSpecificVersionOfflineTestOnly(pin(),TIME));
        verify(client,never()).getObjectRetention(
                any(GetObjectRetentionRequest.class));
    }
}
