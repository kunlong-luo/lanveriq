package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.security.access.AccessDeniedException;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real PostgreSQL 18.6/Flyway V30 isolated, synthetic-only constraints.
 * SQL tests are not Ed25519 verification: direct SQL submits deliberately
 * non-cryptographic 64-byte dummy signatures to prove that even such
 * untrusted inserts remain immutable, monotonic and transaction-audited.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "lanveriq.bootstrap.enabled=false",
        "lanveriq.console.auth.enabled=false"
})
class PostgreSqlFidoMdsAcquisitionReceiptIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("lanveriq_c15_untrusted_synthetic")
            .withUsername("lanveriq_test")
            .withPassword("synthetic-test-only");

    @Autowired EntityManager manager;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;

    record Stream(UUID owner, UUID id) {}
    record Source(UUID id, UUID stream, long sequence) {}
    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }
    private void inTransaction(Runnable operation) {
        transaction().executeWithoutResult(ignored -> operation.run());
    }
    private static byte[] hash(int seed) {
        byte[] data=new byte[32];
        Arrays.fill(data,(byte)seed);
        return data;
    }
    private static byte[] signature(int seed) {
        byte[] data=new byte[64];
        Arrays.fill(data,(byte)seed);
        return data;
    }
    private static byte[] manifest(int sequence) {
        return ("lanveriq/a11/mds/acquisition/receipt/test-only/v1\n"
                + "synthetic signed material; no real network/fido login\n"
                + "receipt=" + sequence + "\n" + "x".repeat(220))
                .getBytes(StandardCharsets.US_ASCII);
    }
    private long count(String table) {
        String sql=switch(table) {
            case "acquisition" -> "SELECT COUNT(*) FROM admin_fido_mds_acquisition_receipts_staging";
            case "audit" -> "SELECT COUNT(*) FROM admin_access_log WHERE action='admin.passkey.mds.source.untrusted.acquisition'";
            default -> throw new IllegalArgumentException("unknown table");
        };
        return ((Number)manager.createNativeQuery(sql).getSingleResult()).longValue();
    }
    private Stream createOwnerStream() {
        return transaction().execute(ignored -> {
            UUID user=(UUID)manager.createNativeQuery("""
                    INSERT INTO users(normalized_login,password_hash)
                    VALUES (:name,'synthetic-unusable-hash') RETURNING id
                    """).setParameter("name","c15.synthetic."+UUID.randomUUID())
                    .getSingleResult();
            UUID group=(UUID)manager.createNativeQuery("""
                    INSERT INTO device_groups(name)
                    VALUES (:name) RETURNING id
                    """).setParameter("name","C15 synthetic "+UUID.randomUUID())
                    .getSingleResult();
            int assigned=manager.createNativeQuery("""
                    INSERT INTO user_role_scopes(user_id,role_id,device_group_id)
                    SELECT :actor,id,:grp FROM roles
                    WHERE code='owner_security_admin'
                    """).setParameter("actor",user).setParameter("grp",group)
                    .executeUpdate();
            assertEquals(1,assigned);
            UUID stream=(UUID)manager.createNativeQuery("""
                    INSERT INTO admin_fido_mds_untrusted_streams_staging
                      (created_by,creator_session_version)
                    VALUES (:actor,0) RETURNING stream_id
                    """).setParameter("actor",user).getSingleResult();
            return new Stream(user,stream);
        });
    }
    private Source source(Stream stream, long sequence) {
        return transaction().execute(ignored -> {
            // V26 is already an append-only DB-owned chain. Do not skip
            // its predecessor merely because the new V30 receipt is tested
            // separately: the source must be valid before V30 can reference it.
            var previous=manager.createNativeQuery("""
                    SELECT receipt_sha256
                    FROM admin_fido_mds_source_receipts_staging
                    WHERE stream_id=:stream
                    ORDER BY receipt_sequence DESC LIMIT 1
                    """).setParameter("stream",stream.id()).getResultList();
            byte[] parent=previous.isEmpty() ? null : (byte[])previous.getFirst();
            UUID id=(UUID)manager.createNativeQuery("""
                    INSERT INTO admin_fido_mds_source_receipts_staging
                      (stream_id,receipt_sequence,blob_no,signed_time_sequence,
                       signed_time_at,leaf_sha256,jws_sha256,root_sha256,
                       crl_evidence_sha256,time_key_sha256,
                       signed_time_proof_sha256,previous_receipt_sha256,
                       observed_by,observer_session_version)
                    VALUES (:stream,:seq,:seq,:seq,clock_timestamp(),
                            :leaf,:jws,:root,:crl,:time_key,:proof,:parent,:actor,0)
                    RETURNING receipt_id
                    """)
                    .setParameter("stream",stream.id())
                    .setParameter("seq",sequence)
                    .setParameter("leaf",hash(1))
                    .setParameter("jws",hash(10+(int)sequence))
                    .setParameter("root",hash(2))
                    .setParameter("crl",hash(20+(int)sequence))
                    .setParameter("time_key",hash(3))
                    .setParameter("proof",hash(30+(int)sequence))
                    .setParameter("parent",parent)
                    .setParameter("actor",stream.owner())
                    .getSingleResult();
            return new Source(id,stream.id(),sequence);
        });
    }
    private UUID insert(Stream stream, Source source, long sequence,
                        byte[] predecessor, byte[] key, byte[] jws,
                        byte[] crl) {
        return (UUID) manager.createNativeQuery("""
                INSERT INTO admin_fido_mds_acquisition_receipts_staging
                  (stream_id,source_receipt_id,acquisition_sequence,
                   signed_manifest,signature_bytes,acquisition_key_sha256,
                   jws_sha256,crl_evidence_sha256,
                   previous_acquisition_sha256,recorded_by,
                   recorder_session_version)
                VALUES (:stream,:source,:seq,:manifest,:sig,:key,
                        :jws,:crl,:prev,:actor,0)
                RETURNING acquisition_id
                """)
                .setParameter("stream",stream.id())
                .setParameter("source",source.id())
                .setParameter("seq",sequence)
                .setParameter("manifest",manifest((int)sequence))
                .setParameter("sig",signature((int)sequence))
                .setParameter("key",key)
                .setParameter("jws",jws)
                .setParameter("crl",crl)
                .setParameter("prev",predecessor)
                .setParameter("actor",stream.owner())
                .getSingleResult();
    }
    private UUID normalInsert(Stream stream, Source source, byte[] predecessor,
                              byte[] key) {
        return transaction().execute(ignored ->
                insert(stream,source,source.sequence(),predecessor,key,
                       hash(10+(int)source.sequence()),
                       hash(20+(int)source.sequence())));
    }
    private byte[] digest(UUID acquisitionId) {
        return (byte[])manager.createNativeQuery("""
                SELECT acquisition_sha256
                FROM admin_fido_mds_acquisition_receipts_staging
                WHERE acquisition_id=:id
                """).setParameter("id",acquisitionId).getSingleResult();
    }
    private void suppressAcquisitionAudit() {
        inTransaction(() -> {
            manager.createNativeQuery("""
                    CREATE FUNCTION c15_test_audit_suppress()
                    RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN
                        IF NEW.action='admin.passkey.mds.source.untrusted.acquisition'
                        THEN RETURN NULL; END IF;
                        RETURN NEW;
                    END;
                    $$
                    """).executeUpdate();
            manager.createNativeQuery("""
                    CREATE TRIGGER c15_test_audit_suppress
                    BEFORE INSERT ON admin_access_log
                    FOR EACH ROW EXECUTE FUNCTION c15_test_audit_suppress()
                    """).executeUpdate();
        });
    }
    private void dropSuppression() {
        inTransaction(() -> {
            manager.createNativeQuery("""
                    DROP TRIGGER IF EXISTS c15_test_audit_suppress
                    ON admin_access_log
                    """).executeUpdate();
            manager.createNativeQuery("""
                    DROP FUNCTION IF EXISTS c15_test_audit_suppress()
                    """).executeUpdate();
        });
    }
    @AfterEach
    void cleanupSyntheticSuppressorOnly() {
        dropSuppression();
    }


    private static String hexSha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private StagedFidoMdsDetachedWitnessReader.DetachedCheckpoint
            witnessFor(Stream stream, UUID acquisitionId,
                       long witnessSequence, String predecessor) {
        Object[] row=(Object[])manager.createNativeQuery("""
                SELECT a.source_receipt_id,a.acquisition_sequence,
                       a.acquisition_sha256,s.receipt_sha256,
                       a.acquisition_key_sha256
                FROM admin_fido_mds_acquisition_receipts_staging a
                JOIN admin_fido_mds_source_receipts_staging s
                  ON s.receipt_id=a.source_receipt_id
                WHERE a.acquisition_id=:acquisition
                """).setParameter("acquisition",acquisitionId).getSingleResult();
        return new StagedFidoMdsDetachedWitnessReader.DetachedCheckpoint(
                stream.id(),acquisitionId,(UUID)row[0],
                ((Number)row[1]).longValue(),
                HexFormat.of().formatHex((byte[])row[2]),
                HexFormat.of().formatHex((byte[])row[3]),
                HexFormat.of().formatHex((byte[])row[4]),
                witnessSequence,predecessor,
                Instant.parse("2026-11-22T10:00:00Z"));
    }

    private static StagedFidoMdsDetachedWitnessReader.SignedCheckpoint
            signDetached(
                    StagedFidoMdsDetachedWitnessReader.DetachedCheckpoint claim,
                    KeyPair keyPair) throws Exception {
        var signer=Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(StagedFidoMdsDetachedWitnessReader.transcript(claim));
        return new StagedFidoMdsDetachedWitnessReader.SignedCheckpoint(
                claim,signer.sign());
    }

    @Test
    void c16DetachedWitnessMatchesExactPostgresTailAndRejectsOldForgedOrForeign()
            throws Exception {
        Stream stream=createOwnerStream();
        Source first=source(stream,1);
        UUID firstId=normalInsert(stream,first,null,hash(55));
        var witnessKey=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var attackerKey=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var pin=new StagedFidoMdsDetachedWitnessReader.PinnedWitnessKey(
                witnessKey.getPublic(),hexSha256(witnessKey.getPublic().getEncoded()));
        var reader=new StagedFidoMdsDetachedWitnessReader(manager);
        Instant checkTime=Instant.parse("2026-11-22T11:00:00Z");
        var firstClaim=witnessFor(stream,firstId,1,null);
        var signedFirst=signDetached(firstClaim,witnessKey);
        var cursorOne=new StagedFidoMdsDetachedWitnessReader.ExternalCursor(1,null);
        var witnessOne=reader.inspectAgainstCurrentDbTailOfflineOnly(
                pin,signedFirst,cursorOne,stream.id(),checkTime);
        assertEquals(1,witnessOne.acquisitionSequence());
        assertEquals(1,witnessOne.witnessSequence());
        assertEquals(64,witnessOne.detachedWitnessSha256().length());

        // A different key cannot authenticate the same exported transcript.
        var forged=signDetached(firstClaim,attackerKey);
        assertThrows(AccessDeniedException.class,() ->
                reader.inspectAgainstCurrentDbTailOfflineOnly(
                        pin,forged,cursorOne,stream.id(),checkTime));
        var wrongPin=new StagedFidoMdsDetachedWitnessReader.PinnedWitnessKey(
                witnessKey.getPublic(),"f".repeat(64));
        assertThrows(AccessDeniedException.class,() ->
                reader.inspectAgainstCurrentDbTailOfflineOnly(
                        wrongPin,signedFirst,cursorOne,stream.id(),checkTime));
        assertThrows(AccessDeniedException.class,() ->
                reader.inspectAgainstCurrentDbTailOfflineOnly(
                        pin,signedFirst,
                        new StagedFidoMdsDetachedWitnessReader.ExternalCursor(2,
                                witnessOne.detachedWitnessSha256()),
                        stream.id(),checkTime));
        assertThrows(AccessDeniedException.class,() ->
                reader.inspectAgainstCurrentDbTailOfflineOnly(
                        pin,signedFirst,cursorOne,UUID.randomUUID(),checkTime));
        assertThrows(AccessDeniedException.class,() ->
                reader.inspectAgainstCurrentDbTailOfflineOnly(
                        pin,signedFirst,cursorOne,stream.id(),
                        Instant.parse("2026-11-21T00:00:00Z")));

        Source second=source(stream,2);
        UUID secondId=normalInsert(stream,second,digest(firstId),hash(55));
        // The original valid witness is now historical: it MUST NOT be
        // mistaken for a signature of the new CURRENT database tail.
        assertThrows(AccessDeniedException.class,() ->
                reader.inspectAgainstCurrentDbTailOfflineOnly(
                        pin,signedFirst,cursorOne,stream.id(),checkTime));

        var secondClaim=witnessFor(stream,secondId,2,
                witnessOne.detachedWitnessSha256());
        var signedSecond=signDetached(secondClaim,witnessKey);
        var cursorTwo=new StagedFidoMdsDetachedWitnessReader.ExternalCursor(
                2,witnessOne.detachedWitnessSha256());
        var matched=reader.inspectAgainstCurrentDbTailOfflineOnly(
                pin,signedSecond,cursorTwo,stream.id(),checkTime);
        assertEquals(2,matched.acquisitionSequence());
        assertEquals(2,matched.witnessSequence());
        assertTrue(!matched.detachedWitnessSha256()
                .equals(witnessOne.detachedWitnessSha256()));

        // Even the real witness key cannot sign a changed database digest
        // and have it accepted as the actual immutable V30 record.
        var swapped=new StagedFidoMdsDetachedWitnessReader.DetachedCheckpoint(
                secondClaim.streamId(),secondClaim.acquisitionId(),
                secondClaim.sourceReceiptId(),secondClaim.acquisitionSequence(),
                "0".repeat(64),secondClaim.sourceReceiptSha256(),
                secondClaim.acquisitionKeySha256(),secondClaim.witnessSequence(),
                secondClaim.previousWitnessSha256(),
                secondClaim.assertedWitnessTime());
        var signedSwapped=signDetached(swapped,witnessKey);
        assertThrows(AccessDeniedException.class,() ->
                reader.inspectAgainstCurrentDbTailOfflineOnly(
                        pin,signedSwapped,cursorTwo,stream.id(),checkTime));

        var crossSource=new StagedFidoMdsDetachedWitnessReader.DetachedCheckpoint(
                secondClaim.streamId(),secondClaim.acquisitionId(),
                UUID.randomUUID(),secondClaim.acquisitionSequence(),
                secondClaim.acquisitionSha256(),secondClaim.sourceReceiptSha256(),
                secondClaim.acquisitionKeySha256(),secondClaim.witnessSequence(),
                secondClaim.previousWitnessSha256(),
                secondClaim.assertedWitnessTime());
        assertThrows(AccessDeniedException.class,() ->
                reader.inspectAgainstCurrentDbTailOfflineOnly(
                        pin,signDetached(crossSource,witnessKey),cursorTwo,
                        stream.id(),checkTime));

        var bogusExternal=new StagedFidoMdsDetachedWitnessReader.ExternalCursor(
                2,"0".repeat(64));
        assertThrows(AccessDeniedException.class,() ->
                reader.inspectAgainstCurrentDbTailOfflineOnly(
                        pin,signedSecond,bogusExternal,stream.id(),checkTime));

        // A returned signature array cannot change a previously validated
        // immutable detached object through aliasing.
        byte[] mutated=signedSecond.signature();
        mutated[0]^=0x01;
        assertEquals(2,reader.inspectAgainstCurrentDbTailOfflineOnly(
                pin,signedSecond,cursorTwo,stream.id(),checkTime)
                .witnessSequence());
        // These database rows have deliberately synthetic 64-byte
        // NON-Ed25519 signatures. A witness signature never promotes them.
        assertEquals("acquisition_untrusted",manager.createNativeQuery("""
                SELECT evidence_state
                FROM admin_fido_mds_acquisition_receipts_staging
                WHERE acquisition_id=:id
                """).setParameter("id",secondId).getSingleResult());
    }

    @Test
    void c17AnchoredWitnessHistoryRejectsTruncationForgeryAndStaleDatabaseTail()
            throws Exception {
        Stream stream=createOwnerStream();
        Source first=source(stream,1);
        UUID firstId=normalInsert(stream,first,null,hash(55));
        var genuine=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var attacker=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String publicPin=hexSha256(genuine.getPublic().getEncoded());
        var pin=new StagedFidoMdsDetachedWitnessReader.PinnedWitnessKey(
                genuine.getPublic(),publicPin);
        var tailReader=new StagedFidoMdsDetachedWitnessReader(manager);
        var historyReader=new StagedFidoMdsAnchoredWitnessHistoryReader(
                tailReader);
        Instant observed=Instant.parse("2026-11-22T11:00:00Z");
        var signedFirst=signDetached(witnessFor(stream,firstId,1,null),
                genuine);
        var firstMatch=tailReader.inspectAgainstCurrentDbTailOfflineOnly(
                pin,signedFirst,
                new StagedFidoMdsDetachedWitnessReader.ExternalCursor(1,null),
                stream.id(),observed);

        Source second=source(stream,2);
        UUID secondId=normalInsert(stream,second,digest(firstId),hash(55));
        var signedSecond=signDetached(witnessFor(stream,secondId,2,
                firstMatch.detachedWitnessSha256()),genuine);
        var secondMatch=tailReader.inspectAgainstCurrentDbTailOfflineOnly(
                pin,signedSecond,
                new StagedFidoMdsDetachedWitnessReader.ExternalCursor(
                        2,firstMatch.detachedWitnessSha256()),
                stream.id(),observed);
        var externallyPinnedHead=
                new StagedFidoMdsAnchoredWitnessHistoryReader
                        .IndependentlyRetainedHead(
                                stream.id(),2,
                                secondMatch.detachedWitnessSha256(),publicPin);
        var complete=java.util.List.of(signedFirst,signedSecond);
        var matched=historyReader.inspectCompleteHistoryAgainstCurrentDbOfflineOnly(
                pin,complete,externallyPinnedHead,stream.id(),observed);
        assertEquals(2,matched.acquisitionSequence());
        assertEquals(secondMatch.detachedWitnessSha256(),
                matched.detachedWitnessSha256());

        // The externally retained high-water head forbids quiet truncation.
        assertThrows(AccessDeniedException.class,() ->
                historyReader.inspectCompleteHistoryAgainstCurrentDbOfflineOnly(
                        pin,java.util.List.of(signedFirst),
                        externallyPinnedHead,stream.id(),observed));
        assertThrows(AccessDeniedException.class,() ->
                historyReader.inspectCompleteHistoryAgainstCurrentDbOfflineOnly(
                        pin,java.util.List.of(signedSecond,signedFirst),
                        externallyPinnedHead,stream.id(),observed));
        assertThrows(AccessDeniedException.class,() ->
                historyReader.inspectCompleteHistoryAgainstCurrentDbOfflineOnly(
                        pin,java.util.List.of(signedFirst,signedFirst),
                        externallyPinnedHead,stream.id(),observed));
        var attackerSignedSecond=signDetached(signedSecond.claim(),attacker);
        assertThrows(AccessDeniedException.class,() ->
                historyReader.inspectCompleteHistoryAgainstCurrentDbOfflineOnly(
                        pin,java.util.List.of(signedFirst,attackerSignedSecond),
                        externallyPinnedHead,stream.id(),observed));
        assertThrows(AccessDeniedException.class,() ->
                historyReader.inspectCompleteHistoryAgainstCurrentDbOfflineOnly(
                        pin,complete,
                        new StagedFidoMdsAnchoredWitnessHistoryReader
                                .IndependentlyRetainedHead(
                                        stream.id(),2,"0".repeat(64),publicPin),
                        stream.id(),observed));
        assertThrows(AccessDeniedException.class,() ->
                historyReader.inspectCompleteHistoryAgainstCurrentDbOfflineOnly(
                        pin,complete,
                        new StagedFidoMdsAnchoredWitnessHistoryReader
                                .IndependentlyRetainedHead(
                                        UUID.randomUUID(),2,
                                        secondMatch.detachedWitnessSha256(),
                                        publicPin),
                        stream.id(),observed));
        assertThrows(AccessDeniedException.class,() ->
                historyReader.inspectCompleteHistoryAgainstCurrentDbOfflineOnly(
                        new StagedFidoMdsDetachedWitnessReader.PinnedWitnessKey(
                                genuine.getPublic(),"f".repeat(64)),
                        complete,externallyPinnedHead,stream.id(),observed));
        assertThrows(AccessDeniedException.class,() ->
                historyReader.inspectCompleteHistoryAgainstCurrentDbOfflineOnly(
                        pin,complete,externallyPinnedHead,
                        UUID.randomUUID(),observed));
        assertThrows(AccessDeniedException.class,() ->
                historyReader.inspectCompleteHistoryAgainstCurrentDbOfflineOnly(
                        pin,complete,externallyPinnedHead,stream.id(),
                        Instant.parse("2026-11-21T00:00:00Z")));

        // Even a genuine old signature plus old head is NOT the current
        // database tail after a later V30 append.
        var oldHead=new StagedFidoMdsAnchoredWitnessHistoryReader
                .IndependentlyRetainedHead(
                        stream.id(),1,firstMatch.detachedWitnessSha256(),
                        publicPin);
        assertThrows(AccessDeniedException.class,() ->
                historyReader.inspectCompleteHistoryAgainstCurrentDbOfflineOnly(
                        pin,java.util.List.of(signedFirst),oldHead,
                        stream.id(),observed));
        Source third=source(stream,3);
        normalInsert(stream,third,digest(secondId),hash(55));
        assertThrows(AccessDeniedException.class,() ->
                historyReader.inspectCompleteHistoryAgainstCurrentDbOfflineOnly(
                        pin,complete,externallyPinnedHead,stream.id(),observed));

        // Neither verified history nor old head changes the untrusted V30
        // evidence state or authorizes real FIDO / WebAuthn / CA operations.
        assertEquals("acquisition_untrusted",manager.createNativeQuery("""
                SELECT evidence_state
                FROM admin_fido_mds_acquisition_receipts_staging
                WHERE acquisition_id=:id
                """).setParameter("id",secondId).getSingleResult());
    }

    @Test
    void c18DbOwnerRewindRejectsSurvivingHostRetainedWitnessHighWater(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path outsidePostgres)
            throws Exception {
        // Isolated PG18 owner-level rewrite rehearsal, NOT a pg_dump/pg_restore
        // or production WORM exercise. A separate host temp file survives the
        // database write; it is not an independently governed immutable store.
        Stream stream=createOwnerStream();
        Source first=source(stream,1);
        UUID firstId=normalInsert(stream,first,null,hash(55));
        var key=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String publicPin=hexSha256(key.getPublic().getEncoded());
        var pin=new StagedFidoMdsDetachedWitnessReader.PinnedWitnessKey(
                key.getPublic(),publicPin);
        var tailReader=new StagedFidoMdsDetachedWitnessReader(manager);
        var historyReader=new StagedFidoMdsAnchoredWitnessHistoryReader(
                tailReader);
        Instant observed=Instant.parse("2026-11-22T11:00:00Z");

        var signedFirst=signDetached(witnessFor(stream,firstId,1,null),key);
        var firstMatch=tailReader.inspectAgainstCurrentDbTailOfflineOnly(
                pin,signedFirst,
                new StagedFidoMdsDetachedWitnessReader.ExternalCursor(1,null),
                stream.id(),observed);
        Source second=source(stream,2);
        UUID secondId=normalInsert(stream,second,digest(firstId),hash(55));
        var signedSecond=signDetached(witnessFor(stream,secondId,2,
                firstMatch.detachedWitnessSha256()),key);
        var secondMatch=tailReader.inspectAgainstCurrentDbTailOfflineOnly(
                pin,signedSecond,
                new StagedFidoMdsDetachedWitnessReader.ExternalCursor(
                        2,firstMatch.detachedWitnessSha256()),
                stream.id(),observed);
        var complete=java.util.List.of(signedFirst,signedSecond);

        // Persist the independent-in-this-test cursor OUTSIDE the PG18
        // container before the database is deliberately rewound. JDK
        // Properties is a disposable test fixture, not production custody.
        var externalFile=outsidePostgres.resolve("c18-host-high-water.properties");
        var fixture=new java.util.Properties();
        fixture.setProperty("stream",stream.id().toString());
        fixture.setProperty("sequence","2");
        fixture.setProperty("digest",secondMatch.detachedWitnessSha256());
        fixture.setProperty("key",publicPin);
        try (var output=java.nio.file.Files.newOutputStream(externalFile,
                java.nio.file.StandardOpenOption.CREATE_NEW,
                java.nio.file.StandardOpenOption.WRITE)) {
            fixture.store(output,"synthetic only; NOT WORM or trusted custody");
        }
        var initialHead=new StagedFidoMdsAnchoredWitnessHistoryReader
                .IndependentlyRetainedHead(
                        stream.id(),2,secondMatch.detachedWitnessSha256(),
                        publicPin);
        assertEquals(2,historyReader.inspectCompleteHistoryAgainstCurrentDbOfflineOnly(
                pin,complete,initialHead,stream.id(),observed)
                .acquisitionSequence());

        // DB-owner/migrator privileges can DISABLE user triggers. A real
        // remote WORM witness MUST NOT be inside that administrative domain.
        // This simulates a malicious V30+audit rewind only: no backup is
        // produced, no full restore or external object lock is claimed.
        inTransaction(() -> {
            manager.createNativeQuery("""
                    ALTER TABLE admin_fido_mds_acquisition_receipts_staging
                    DISABLE TRIGGER USER
                    """).executeUpdate();
            manager.createNativeQuery("""
                    ALTER TABLE admin_access_log DISABLE TRIGGER USER
                    """).executeUpdate();
            try {
                assertEquals(1,manager.createNativeQuery("""
                        DELETE FROM admin_fido_mds_acquisition_receipts_staging
                        WHERE acquisition_id=:id
                        """).setParameter("id",secondId).executeUpdate());
                assertEquals(1,manager.createNativeQuery("""
                        DELETE FROM admin_access_log
                        WHERE resource_id=:id AND action=
                            'admin.passkey.mds.source.untrusted.acquisition'
                        """).setParameter("id",secondId).executeUpdate());
            } finally {
                manager.createNativeQuery("""
                        ALTER TABLE admin_access_log ENABLE TRIGGER USER
                        """).executeUpdate();
                manager.createNativeQuery("""
                        ALTER TABLE admin_fido_mds_acquisition_receipts_staging
                        ENABLE TRIGGER USER
                        """).executeUpdate();
            }
        });

        var persisted=new java.util.Properties();
        try (var input=java.nio.file.Files.newInputStream(externalFile)) {
            persisted.load(input);
        }
        var retainedHead=new StagedFidoMdsAnchoredWitnessHistoryReader
                .IndependentlyRetainedHead(
                        UUID.fromString(persisted.getProperty("stream")),
                        Long.parseLong(persisted.getProperty("sequence")),
                        persisted.getProperty("digest"),
                        persisted.getProperty("key"));
        assertEquals(initialHead,retainedHead);
        // Signatures and archive still authenticate, but the recovered/rewound
        // DB no longer matches the independently retained HIGHER head.
        assertThrows(AccessDeniedException.class,() ->
                historyReader.inspectCompleteHistoryAgainstCurrentDbOfflineOnly(
                        pin,complete,retainedHead,stream.id(),observed));

        // Explicit limitation: if an attacker also replaces the externally
        // supplied high-water and history with old genuine material, this
        // verifier CANNOT know that a rollback occurred. Actual WORM+KMS is
        // mandatory before G-02 may close.
        var attackerSubstitutedOldHead=
                new StagedFidoMdsAnchoredWitnessHistoryReader
                        .IndependentlyRetainedHead(
                                stream.id(),1,
                                firstMatch.detachedWitnessSha256(),publicPin);
        assertEquals(1,historyReader.inspectCompleteHistoryAgainstCurrentDbOfflineOnly(
                pin,java.util.List.of(signedFirst),attackerSubstitutedOldHead,
                stream.id(),observed).witnessSequence());
        assertEquals("acquisition_untrusted",manager.createNativeQuery("""
                SELECT evidence_state
                FROM admin_fido_mds_acquisition_receipts_staging
                WHERE acquisition_id=:id
                """).setParameter("id",firstId).getSingleResult());
    }


    private static byte[] c18fSyntheticHeadBytes(
            StagedFidoMdsAnchoredWitnessHistoryReader.IndependentlyRetainedHead
                    head) throws Exception {
        // A small test-only Properties payload. NOT a canonical signed
        // manifest, protected archive format, trusted time or real WORM.
        var properties = new java.util.Properties();
        properties.setProperty("stream",head.streamId().toString());
        properties.setProperty("sequence",Long.toString(head.witnessSequence()));
        properties.setProperty("digest",head.detachedWitnessSha256());
        properties.setProperty("key",head.witnessPublicKeySha256());
        var bytes = new java.io.ByteArrayOutputStream();
        properties.store(bytes,"C18f SYNTHETIC ONLY / NO WORM");
        return bytes.toByteArray();
    }

    private static StagedImmutableWitnessArchiveReader.ReadOnlyArchivePort
            c18fUntrustedPort(
                    StagedImmutableWitnessArchiveReader.VersionLocator locator,
                    byte[] payload, boolean bypassable) {
        // In-memory fake: both retention responses can be fabricated by its
        // operator. This intentionally CANNOT satisfy independent G-02.
        var protection=bypassable
                ? StagedImmutableWitnessArchiveReader.ProtectionClaim.BYPASSABLE
                : StagedImmutableWitnessArchiveReader.ProtectionClaim
                        .NON_BYPASSABLE_WORM;
        Instant until=Instant.parse("2026-12-31T00:00:00Z");
        return new StagedImmutableWitnessArchiveReader.ReadOnlyArchivePort() {
            @Override public StagedImmutableWitnessArchiveReader.VersionedObjectRead
                    readExactVersion(
                            StagedImmutableWitnessArchiveReader.VersionLocator
                                    requested) {
                assertEquals(locator,requested);
                return new StagedImmutableWitnessArchiveReader
                        .VersionedObjectRead(
                                locator,payload.length,protection,until,
                                new java.io.ByteArrayInputStream(payload));
            }

            @Override public StagedImmutableWitnessArchiveReader.VersionedRetentionRead
                    readExactVersionRetention(
                            StagedImmutableWitnessArchiveReader.VersionLocator
                                    requested) {
                assertEquals(locator,requested);
                return new StagedImmutableWitnessArchiveReader
                        .VersionedRetentionRead(locator,protection,until);
            }
        };
    }

    @Test
    void c18fCloudNeutralArchiveClaimBindsToCurrentPostgresTail()
            throws Exception {
        // REAL disposable PG18.6 transactions + signature verification,
        // but the archive and custody claims are SYNTHETIC bytes only.
        Stream stream=createOwnerStream();
        Source first=source(stream,1);
        UUID firstId=normalInsert(stream,first,null,hash(55));
        var signer=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String publicPin=hexSha256(signer.getPublic().getEncoded());
        var keyPin=new StagedFidoMdsDetachedWitnessReader.PinnedWitnessKey(
                signer.getPublic(),publicPin);
        var tail=new StagedFidoMdsDetachedWitnessReader(manager);
        var history=new StagedFidoMdsAnchoredWitnessHistoryReader(tail);
        Instant observed=Instant.parse("2026-11-22T11:00:00Z");
        var signedFirst=signDetached(witnessFor(stream,firstId,1,null),signer);
        var firstMatch=tail.inspectAgainstCurrentDbTailOfflineOnly(
                keyPin,signedFirst,
                new StagedFidoMdsDetachedWitnessReader.ExternalCursor(1,null),
                stream.id(),observed);
        var firstHead=new StagedFidoMdsAnchoredWitnessHistoryReader
                .IndependentlyRetainedHead(stream.id(),1,
                        firstMatch.detachedWitnessSha256(),publicPin);

        Source second=source(stream,2);
        UUID secondId=normalInsert(stream,second,digest(firstId),hash(55));
        var signedSecond=signDetached(witnessFor(stream,secondId,2,
                firstMatch.detachedWitnessSha256()),signer);
        var secondMatch=tail.inspectAgainstCurrentDbTailOfflineOnly(
                keyPin,signedSecond,
                new StagedFidoMdsDetachedWitnessReader.ExternalCursor(
                        2,firstMatch.detachedWitnessSha256()),
                stream.id(),observed);
        var secondHead=new StagedFidoMdsAnchoredWitnessHistoryReader
                .IndependentlyRetainedHead(stream.id(),2,
                        secondMatch.detachedWitnessSha256(),publicPin);
        var complete=java.util.List.of(signedFirst,signedSecond);

        byte[] currentBytes=c18fSyntheticHeadBytes(secondHead);
        var currentLocator=new StagedImmutableWitnessArchiveReader
                .VersionLocator("synthetic-host-not-independent",
                        "untrusted/"+stream.id()+"/high-water","v2");
        var currentArchivePin=new StagedImmutableWitnessArchiveReader
                .IndependentlyAssertedPin(currentLocator,
                        hexSha256(currentBytes),
                        Instant.parse("2026-12-01T00:00:00Z"));
        var archiveVerifier=new StagedImmutableWitnessArchiveReader();
        var currentReadback=archiveVerifier.inspectVersionOfflineOnly(
                c18fUntrustedPort(currentLocator,currentBytes,false),
                currentArchivePin,observed);
        assertEquals(currentArchivePin.contentSha256(),
                currentReadback.contentSha256());

        var parsed=new java.util.Properties();
        parsed.load(new java.io.ByteArrayInputStream(currentBytes));
        var parsedHead=new StagedFidoMdsAnchoredWitnessHistoryReader
                .IndependentlyRetainedHead(
                        UUID.fromString(parsed.getProperty("stream")),
                        Long.parseLong(parsed.getProperty("sequence")),
                        parsed.getProperty("digest"),
                        parsed.getProperty("key"));
        assertEquals(secondHead,parsedHead);
        assertEquals(2,history.inspectCompleteHistoryAgainstCurrentDbOfflineOnly(
                keyPin,complete,parsedHead,stream.id(),observed)
                .witnessSequence());

        // A real-looking, internally valid OLD version claim/retention and
        // valid first signature can pass the synthetic archive verifier;
        // it must NOT match the newer PG18 database tail.
        byte[] oldBytes=c18fSyntheticHeadBytes(firstHead);
        var oldLocator=new StagedImmutableWitnessArchiveReader
                .VersionLocator(currentLocator.administrativeDomain(),
                        currentLocator.objectKey(),"v1");
        var oldArchivePin=new StagedImmutableWitnessArchiveReader
                .IndependentlyAssertedPin(oldLocator,hexSha256(oldBytes),
                        currentArchivePin.minimumRetainUntil());
        assertEquals(oldArchivePin.contentSha256(),
                archiveVerifier.inspectVersionOfflineOnly(
                        c18fUntrustedPort(oldLocator,oldBytes,false),
                        oldArchivePin,observed).contentSha256());
        assertThrows(AccessDeniedException.class,() ->
                history.inspectCompleteHistoryAgainstCurrentDbOfflineOnly(
                        keyPin,java.util.List.of(signedFirst),firstHead,
                        stream.id(),observed));

        // Version substitution, forged payload and bypassable retention
        // cannot be silently promoted into a successful archive result.
        assertThrows(AccessDeniedException.class,() ->
                archiveVerifier.inspectVersionOfflineOnly(
                        c18fUntrustedPort(currentLocator,oldBytes,false),
                        currentArchivePin,observed));
        assertThrows(AccessDeniedException.class,() ->
                archiveVerifier.inspectVersionOfflineOnly(
                        c18fUntrustedPort(currentLocator,currentBytes,true),
                        currentArchivePin,observed));
        assertEquals("acquisition_untrusted",manager.createNativeQuery("""
                SELECT evidence_state
                FROM admin_fido_mds_acquisition_receipts_staging
                WHERE acquisition_id=:id
                """).setParameter("id",secondId).getSingleResult());
    }

    private static void c18bPgCommand(String... command) throws Exception {
        // Run real PostgreSQL 18 client binaries *inside* the disposable
        // Testcontainers image, not a SQL imitation or invented XML.
        var result=postgres.execInContainer(command);
        assertEquals(0,result.getExitCode(),
                "PostgreSQL 18 command failed: "
                +String.join(" ",command)+"\n"
                +result.getStdout()+"\n"+result.getStderr());
    }

    private org.hibernate.Session c18bRestoredSession(
            String dbName, java.sql.Connection connection) {
        return entityManagerFactory.unwrap(org.hibernate.SessionFactory.class)
                .withOptions().connection(connection).openSession();
    }

    private org.springframework.jdbc.datasource.DriverManagerDataSource
            c18bRestoredDataSource(String dbName) {
        // Standard Spring/Hibernate test harness, not a handwritten
        // production JDBC DAO or a new database authority.
        var source=new org.springframework.jdbc.datasource.DriverManagerDataSource();
        source.setDriverClassName("org.postgresql.Driver");
        source.setUrl("jdbc:postgresql://"+postgres.getHost()+":"
                +postgres.getMappedPort(5432)+"/"+dbName);
        source.setUsername(postgres.getUsername());
        source.setPassword(postgres.getPassword());
        return source;
    }

    @Test
    void c18bActualPgDumpRestoreRejectsStaleBackupAndAcceptsFreshBackup(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path outsideDb)
            throws Exception {
        // Genuine pg_dump -Fc + pg_restore --exit-on-error on PostgreSQL 18.6.
        // All DB copies and signing keys are synthetic/disposable; the host
        // cursor is NOT external WORM/KMS or independently approved custody.
        String nonce=UUID.randomUUID().toString().replace("-","")
                .substring(0,12);
        String oldBackup="/tmp/c18b-"+nonce+"-old.dump";
        String freshBackup="/tmp/c18b-"+nonce+"-fresh.dump";
        String oldDb="c18b_old_"+nonce;
        String freshDb="c18b_fresh_"+nonce;
        String username=postgres.getUsername();
        String originalDb=postgres.getDatabaseName();

        Stream stream=createOwnerStream();
        Source source1=source(stream,1);
        UUID firstId=normalInsert(stream,source1,null,hash(55));
        var signer=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String publicPin=hexSha256(signer.getPublic().getEncoded());
        var pin=new StagedFidoMdsDetachedWitnessReader.PinnedWitnessKey(
                signer.getPublic(),publicPin);
        var liveReader=new StagedFidoMdsDetachedWitnessReader(manager);
        Instant observed=Instant.parse("2026-11-22T11:00:00Z");
        var signedFirst=signDetached(witnessFor(stream,firstId,1,null),
                signer);
        var firstMatch=liveReader.inspectAgainstCurrentDbTailOfflineOnly(
                pin,signedFirst,
                new StagedFidoMdsDetachedWitnessReader.ExternalCursor(1,null),
                stream.id(),observed);

        // Snapshot 1 is taken BEFORE the second committed V30 record.
        c18bPgCommand("pg_dump","--username="+username,
                "--dbname="+originalDb,"--format=custom",
                "--file="+oldBackup);
        c18bPgCommand("pg_restore","--list",oldBackup);

        Source source2=source(stream,2);
        UUID secondId=normalInsert(stream,source2,digest(firstId),hash(55));
        var signedSecond=signDetached(witnessFor(stream,secondId,2,
                firstMatch.detachedWitnessSha256()),signer);
        var secondMatch=liveReader.inspectAgainstCurrentDbTailOfflineOnly(
                pin,signedSecond,
                new StagedFidoMdsDetachedWitnessReader.ExternalCursor(
                        2,firstMatch.detachedWitnessSha256()),
                stream.id(),observed);
        var complete=java.util.List.of(signedFirst,signedSecond);
        var pinnedHead=new StagedFidoMdsAnchoredWitnessHistoryReader
                .IndependentlyRetainedHead(stream.id(),2,
                        secondMatch.detachedWitnessSha256(),publicPin);
        var externalFile=outsideDb.resolve("c18b-external-head.properties");
        var properties=new java.util.Properties();
        properties.setProperty("stream",stream.id().toString());
        properties.setProperty("sequence","2");
        properties.setProperty("digest",pinnedHead.detachedWitnessSha256());
        properties.setProperty("key",publicPin);
        try (var output=java.nio.file.Files.newOutputStream(externalFile,
                java.nio.file.StandardOpenOption.CREATE_NEW,
                java.nio.file.StandardOpenOption.WRITE)) {
            properties.store(output,"SYNTHETIC ONLY - not immutable custody");
        }

        // Snapshot 2 is the current V30 tail and must be accepted.
        c18bPgCommand("pg_dump","--username="+username,
                "--dbname="+originalDb,"--format=custom",
                "--file="+freshBackup);
        c18bPgCommand("pg_restore","--list",freshBackup);

        // Restore into NEW databases, never replace the Spring/JPA source
        // database used by the rest of the integration suite.
        c18bPgCommand("createdb","--username="+username,
                "--owner="+username,oldDb);
        c18bPgCommand("createdb","--username="+username,
                "--owner="+username,freshDb);
        c18bPgCommand("pg_restore","--exit-on-error",
                "--no-owner","--no-acl","--username="+username,
                "--dbname="+oldDb,oldBackup);
        c18bPgCommand("pg_restore","--exit-on-error",
                "--no-owner","--no-acl","--username="+username,
                "--dbname="+freshDb,freshBackup);

        var recovered=new java.util.Properties();
        try (var input=java.nio.file.Files.newInputStream(externalFile)) {
            recovered.load(input);
        }
        var externallyRetained=new StagedFidoMdsAnchoredWitnessHistoryReader
                .IndependentlyRetainedHead(
                        UUID.fromString(recovered.getProperty("stream")),
                        Long.parseLong(recovered.getProperty("sequence")),
                        recovered.getProperty("digest"),
                        recovered.getProperty("key"));
        assertEquals(pinnedHead,externallyRetained);

        var oldData=c18bRestoredDataSource(oldDb);
        try (var connection=oldData.getConnection();
             var restored=c18bRestoredSession(oldDb,connection)) {
            assertEquals(1L,((Number)restored.createNativeQuery("""
                    SELECT COUNT(*)
                    FROM admin_fido_mds_acquisition_receipts_staging
                    WHERE stream_id=:stream
                    """).setParameter("stream",stream.id())
                    .getSingleResult()).longValue());
            var history=new StagedFidoMdsAnchoredWitnessHistoryReader(
                    new StagedFidoMdsDetachedWitnessReader(restored));
            // Real old backup restored successfully but MUST be denied
            // against the higher external head. Signature-valid != fresh.
            assertThrows(AccessDeniedException.class,() ->
                    history.inspectCompleteHistoryAgainstCurrentDbOfflineOnly(
                            pin,complete,externallyRetained,stream.id(),observed));
            // Known failure mode: substituting the old signed archive AND
            // its old cursor would pass: only genuine off-DB custody fixes it.
            var tamperedOldHead=new StagedFidoMdsAnchoredWitnessHistoryReader
                    .IndependentlyRetainedHead(
                            stream.id(),1,firstMatch.detachedWitnessSha256(),
                            publicPin);
            assertEquals(1,history.inspectCompleteHistoryAgainstCurrentDbOfflineOnly(
                    pin,java.util.List.of(signedFirst),tamperedOldHead,
                    stream.id(),observed).witnessSequence());
        }

        var freshData=c18bRestoredDataSource(freshDb);
        try (var connection=freshData.getConnection();
             var restored=c18bRestoredSession(freshDb,connection)) {
            assertEquals(2L,((Number)restored.createNativeQuery("""
                    SELECT COUNT(*)
                    FROM admin_fido_mds_acquisition_receipts_staging
                    WHERE stream_id=:stream
                    """).setParameter("stream",stream.id())
                    .getSingleResult()).longValue());
            var history=new StagedFidoMdsAnchoredWitnessHistoryReader(
                    new StagedFidoMdsDetachedWitnessReader(restored));
            var accepted=history.inspectCompleteHistoryAgainstCurrentDbOfflineOnly(
                    pin,complete,externallyRetained,stream.id(),observed);
            assertEquals(2,accepted.acquisitionSequence());
            assertEquals(secondMatch.detachedWitnessSha256(),
                    accepted.detachedWitnessSha256());
        }
    }

    @Test
    void v30SourceBoundAcquisitionPersistsDbHashesAndExactlyOneAudit() {
        Stream stream=createOwnerStream();
        Source first=source(stream,1);
        long before=count("acquisition");
        long auditBefore=count("audit");
        UUID id=normalInsert(stream,first,null,hash(55));
        assertNotNull(id);
        assertEquals(before+1,count("acquisition"));
        assertEquals(auditBefore+1,count("audit"));
        Object[] evidence=(Object[])manager.createNativeQuery("""
                SELECT signed_manifest_sha256,sha256(signed_manifest),
                       signature_sha256,sha256(signature_bytes),
                       acquisition_sha256,evidence_state
                FROM admin_fido_mds_acquisition_receipts_staging
                WHERE acquisition_id=:id
                """).setParameter("id",id).getSingleResult();
        assertArrayEquals((byte[])evidence[1],(byte[])evidence[0]);
        assertArrayEquals((byte[])evidence[3],(byte[])evidence[2]);
        assertEquals(32,((byte[])evidence[4]).length);
        assertEquals("acquisition_untrusted",evidence[5]);
        assertEquals(1L,((Number)manager.createNativeQuery("""
                SELECT COUNT(*) FROM admin_access_log
                WHERE action='admin.passkey.mds.source.untrusted.acquisition'
                  AND resource_id=:id AND principal_id=:actor
                """).setParameter("id",id).setParameter("actor",stream.owner())
                .getSingleResult()).longValue());
    }

    @Test
    void v30RejectsReplaySkippedSequenceSwappedSourceAndMutatedOrTruncatedHistory() {
        Stream one=createOwnerStream();
        Source first=source(one,1);
        Source second=source(one,2);
        Stream other=createOwnerStream();
        Source otherSource=source(other,1);
        long baseline=count("acquisition");
        assertThrows(RuntimeException.class, () ->
                normalInsert(one,second,null,hash(55)));
        assertThrows(RuntimeException.class, () ->
                transaction().execute(ignored ->
                        insert(one,first,1,null,hash(55),hash(255),hash(21))));
        assertThrows(RuntimeException.class, () ->
                transaction().execute(ignored ->
                        insert(one,otherSource,1,null,hash(55),hash(11),hash(21))));
        assertEquals(baseline,count("acquisition"));
        UUID committed=normalInsert(one,first,null,hash(55));
        assertThrows(RuntimeException.class, () ->
                normalInsert(one,first,null,hash(55)));
        assertThrows(RuntimeException.class, () ->
                normalInsert(one,second,hash(99),hash(55)));
        assertThrows(RuntimeException.class, () ->
                normalInsert(one,second,digest(committed),hash(88)));
        assertEquals(baseline+1,count("acquisition"));
        UUID next=normalInsert(one,second,digest(committed),hash(55));
        assertNotNull(next);
        assertEquals(baseline+2,count("acquisition"));
        assertThrows(RuntimeException.class, () -> inTransaction(() ->
                manager.createNativeQuery("""
                        UPDATE admin_fido_mds_acquisition_receipts_staging
                        SET evidence_state='acquisition_untrusted'
                        WHERE acquisition_id=:id
                        """).setParameter("id",committed).executeUpdate()));
        assertThrows(RuntimeException.class, () -> inTransaction(() ->
                manager.createNativeQuery("""
                        DELETE FROM admin_fido_mds_acquisition_receipts_staging
                        WHERE acquisition_id=:id
                        """).setParameter("id",next).executeUpdate()));
        assertThrows(RuntimeException.class, () -> inTransaction(() ->
                manager.createNativeQuery("""
                        TRUNCATE admin_fido_mds_acquisition_receipts_staging CASCADE
                        """).executeUpdate()));
        assertEquals(baseline+2,count("acquisition"));
        assertNotNull(digest(committed));
        assertNotNull(digest(next));
    }

    @Test
    void v30SuppressedAcquisitionAuditRollsBackThenRetryAndAuditCannotBeRebound() {
        Stream stream=createOwnerStream();
        Source first=source(stream,1);
        long before=count("acquisition");
        long auditBefore=count("audit");
        suppressAcquisitionAudit();
        assertThrows(RuntimeException.class, () ->
                normalInsert(stream,first,null,hash(55)));
        assertEquals(before,count("acquisition"));
        assertEquals(auditBefore,count("audit"));
        dropSuppression();
        UUID accepted=normalInsert(stream,first,null,hash(55));
        assertEquals(before+1,count("acquisition"));
        assertEquals(auditBefore+1,count("audit"));
        assertThrows(RuntimeException.class, () -> inTransaction(() ->
                manager.createNativeQuery("""
                        UPDATE admin_access_log SET outcome='allowed'
                        WHERE resource_id=:id
                          AND action='admin.passkey.mds.source.untrusted.acquisition'
                        """).setParameter("id",accepted).executeUpdate()));
        assertThrows(RuntimeException.class, () -> inTransaction(() ->
                manager.createNativeQuery("""
                        DELETE FROM admin_access_log
                        WHERE resource_id=:id
                          AND action='admin.passkey.mds.source.untrusted.acquisition'
                        """).setParameter("id",accepted).executeUpdate()));
        assertThrows(RuntimeException.class, () -> inTransaction(() ->
                manager.createNativeQuery("TRUNCATE admin_access_log")
                        .executeUpdate()));
        assertEquals(before+1,count("acquisition"));
        assertEquals(auditBefore+1,count("audit"));
    }
}
