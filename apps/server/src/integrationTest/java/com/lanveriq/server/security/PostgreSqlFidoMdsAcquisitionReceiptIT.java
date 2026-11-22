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
