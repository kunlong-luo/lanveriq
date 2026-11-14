package com.lanveriq.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lanveriq.server.security.AdminPrincipal;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Base64;
import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Actual disposable PG18/Flyway V14 transaction acceptance. Only synthetic
 * principals and credentials. No signer, CA, HTTP, Agent or production data.
 */
@Testcontainers
@Import(PostgreSqlFirstPinDoubleReviewIT.SyntheticStepUpConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "lanveriq.bootstrap.enabled=false",
        "lanveriq.console.auth.enabled=false",
        "lanveriq.first-pin-review.ledger-enabled=true"
})
class PostgreSqlFirstPinDoubleReviewIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6")
            .withDatabaseName("lanveriq_first_pin_review_synthetic")
            .withUsername("lanveriq_test")
            .withPassword("synthetic-only-no-login");

    @Autowired
    private EntityManager manager;

    @Autowired
    private PlatformTransactionManager txManager;

    @Autowired
    private FirstPinReviewLedgerService ledger;

    @Autowired
    private SyntheticStepUpVerifier syntheticVerifier;

    /**
     * Synthetic tests ONLY: this mock is not and must never become a
     * production MFA/passkey implementation.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class SyntheticStepUpConfig {
        @Bean
        @Primary
        SyntheticStepUpVerifier syntheticStepUpVerifier() {
            return new SyntheticStepUpVerifier();
        }
    }

    static class SyntheticStepUpVerifier implements FirstPinReviewStepUpVerifier {
        final AtomicReference<VerifiedEvidence> override = new AtomicReference<>();
        volatile boolean deny;

        @Override
        public VerifiedEvidence verify(Challenge challenge) {
            if (deny) {
                throw new AccessDeniedException("Synthetic reviewer reauthentication denied");
            }
            var custom = override.get();
            if (custom != null) {
                return custom;
            }
            return new VerifiedEvidence(UUID.randomUUID(), challenge.actorId(),
                    challenge.requestId(), challenge.deviceId(),
                    challenge.sessionVersion(), challenge.action(),
                    Instant.now(), "webauthn");
        }

        void reset() {
            deny = false;
            override.set(null);
        }
    }

    private UUID group;
    private UUID device;
    private UUID applicant;
    private UUID first;
    private UUID second;
    private UUID third;
    private UUID outsider;
    private UUID request;

    private void tx(Runnable action) {
        new TransactionTemplate(txManager).executeWithoutResult(ignored -> action.run());
    }

    private UUID insert(String sql) {
        return (UUID) manager.createNativeQuery(sql).getSingleResult();
    }

    private UUID group() {
        return (UUID) manager.createNativeQuery("""
                INSERT INTO device_groups(name) VALUES(:name) RETURNING id
                """).setParameter("name", "A11-synthetic-" + UUID.randomUUID())
                .getSingleResult();
    }

    private UUID user(UUID groupId, boolean owner) {
        UUID id = (UUID) manager.createNativeQuery("""
                INSERT INTO users(normalized_login,password_hash)
                VALUES(:login,'synthetic-not-a-usable-hash') RETURNING id
                """).setParameter("login", "a11-" + UUID.randomUUID()).getSingleResult();
        if (owner) {
            assertEquals(1, manager.createNativeQuery("""
                    INSERT INTO user_role_scopes(user_id,role_id,device_group_id)
                    SELECT :user,id,:group FROM roles WHERE code='owner_security_admin'
                    """).setParameter("user", id).setParameter("group", groupId)
                    .executeUpdate());
        }
        return id;
    }

    private void login(UUID id) {
        AdminPrincipal p = Mockito.mock(AdminPrincipal.class);
        Mockito.when(p.userId()).thenReturn(id);
        Mockito.when(p.isEnabled()).thenReturn(true);
        Mockito.when(p.sessionVersion()).thenReturn(0L);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(p, null, List.of()));
    }

    private long countVotes() {
        return ((Number) manager.createNativeQuery("""
                SELECT COUNT(*) FROM first_pin_review_approvals WHERE request_id=:id
                """).setParameter("id", request).getSingleResult()).longValue();
    }

    private long countVoteAudit() {
        return ((Number) manager.createNativeQuery("""
                SELECT COUNT(*) FROM admin_access_log
                 WHERE resource_id=:id AND action='first_pin.review.vote'
                """).setParameter("id", request).getSingleResult()).longValue();
    }

    @BeforeEach
    void syntheticFixture() {
        syntheticVerifier.reset();
        tx(() -> {
            group = group();
            UUID otherGroup = group();
            applicant = user(group, true);
            first = user(group, true);
            second = user(group, true);
            third = user(group, true);
            outsider = user(otherGroup, true);
            device = (UUID) manager.createNativeQuery("""
                    INSERT INTO devices(group_id,enrollment_state)
                    VALUES(:group,'active') RETURNING id
                    """).setParameter("group", group).getSingleResult();
            manager.createNativeQuery("""
                    INSERT INTO device_credentials
                    (device_id,client_cert_sha256_fingerprint,issuer_id,serial_number,
                     csr_spki_digest,status,issued_at,expires_at)
                    VALUES (:device,:fingerprint,'synthetic-ca',:serial,:spki,
                            'active',clock_timestamp()-INTERVAL '1 hour',
                            clock_timestamp()+INTERVAL '1 day')
                    """).setParameter("device", device)
                    .setParameter("fingerprint", UUID.randomUUID().toString().replace("-", "").repeat(2))
                    .setParameter("serial", UUID.randomUUID().toString())
                    .setParameter("spki", "a".repeat(64)).executeUpdate();
        });
        login(applicant);
        request = ledger.request(device);
        assertNotNull(request);
        assertEquals(0L, countVotes());
    }

    @AfterEach
    void cleanupAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void twoDistinctReviewersCommitWithRequestAndAuditWithoutSigning() {
        login(first);
        assertEquals(FirstPinReviewLedgerService.Outcome.PENDING_SECOND_REVIEW,
                ledger.review(request));
        login(second);
        assertEquals(FirstPinReviewLedgerService.Outcome
                .TWO_REVIEWS_RECORDED_NOT_AUTHORIZED_TO_SIGN, ledger.review(request));
        assertEquals(2L, countVotes());
        assertEquals(2L, countVoteAudit());
        assertEquals(1L, ((Number) manager.createNativeQuery("""
                SELECT COUNT(*) FROM first_pin_review_requests WHERE id=:request
                """).setParameter("request", request).getSingleResult()).longValue());
        assertEquals(0L, ((Number) manager.createNativeQuery("""
                SELECT COUNT(*) FROM first_pin_review_revocations WHERE request_id=:id
                """).setParameter("id", request).getSingleResult()).longValue());
    }

    @Test
    void selfReviewWrongGroupAndReplayAreRejectedWithoutAudit() {
        login(applicant);
        assertThrows(AccessDeniedException.class, () -> ledger.review(request));
        login(outsider);
        assertThrows(AccessDeniedException.class, () -> ledger.review(request));
        assertEquals(0L, countVotes());
        assertEquals(0L, countVoteAudit());

        login(first);
        ledger.review(request);
        assertThrows(AccessDeniedException.class, () -> ledger.review(request));
        login(second);
        ledger.review(request);
        login(third);
        assertThrows(AccessDeniedException.class, () -> ledger.review(request));
        assertEquals(2L, countVotes());
        assertEquals(2L, countVoteAudit());
    }

    @Test
    void revokedFirstReviewerOrApplicantSessionRotationBlocksSecond() {
        login(first);
        ledger.review(request);
        tx(() -> manager.createNativeQuery("""
                DELETE FROM user_role_scopes WHERE user_id=:user
                """).setParameter("user", first).executeUpdate());
        login(second);
        assertThrows(AccessDeniedException.class, () -> ledger.review(request));
        assertEquals(1L, countVotes());

        tx(() -> manager.createNativeQuery("""
                INSERT INTO user_role_scopes(user_id,role_id,device_group_id)
                SELECT :user,id,:group FROM roles WHERE code='owner_security_admin'
                """).setParameter("user", first).setParameter("group", group)
                .executeUpdate());
        tx(() -> manager.createNativeQuery("""
                UPDATE users SET session_version=session_version+1 WHERE id=:user
                """).setParameter("user", applicant).executeUpdate());
        assertThrows(AccessDeniedException.class, () -> ledger.review(request));
        assertEquals(1L, countVotes());
        assertEquals(1L, countVoteAudit());
    }

    @Test
    void silentlySuppressedReviewAuditRollsBackVoteThenAllowsRetry() {
        tx(() -> {
            manager.createNativeQuery("""
                    CREATE FUNCTION a11_synthetic_suppress_vote_audit()
                    RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN
                        IF NEW.action='first_pin.review.vote' THEN
                            RETURN NULL;
                        END IF;
                        RETURN NEW;
                    END;
                    $$
                    """).executeUpdate();
            manager.createNativeQuery("""
                    CREATE TRIGGER a11_synthetic_suppress_vote_audit
                    BEFORE INSERT ON admin_access_log FOR EACH ROW
                    EXECUTE FUNCTION a11_synthetic_suppress_vote_audit()
                    """).executeUpdate();
        });
        try {
            login(first);
            assertThrows(AccessDeniedException.class, () -> ledger.review(request));
            assertEquals(0L, countVotes());
            assertEquals(0L, countVoteAudit());
        } finally {
            tx(() -> {
                manager.createNativeQuery("""
                        DROP TRIGGER IF EXISTS a11_synthetic_suppress_vote_audit
                        ON admin_access_log
                        """).executeUpdate();
                manager.createNativeQuery("""
                        DROP FUNCTION IF EXISTS a11_synthetic_suppress_vote_audit()
                        """).executeUpdate();
            });
        }
        login(first);
        assertEquals(FirstPinReviewLedgerService.Outcome.PENDING_SECOND_REVIEW,
                ledger.review(request));
        assertEquals(1L, countVotes());
        assertEquals(1L, countVoteAudit());
    }

    @Test
    void postgresConstraintsRejectSkippedSecondStepAndHistoryMutation() {
        assertThrows(RuntimeException.class, () -> tx(() ->
                manager.createNativeQuery("""
                        INSERT INTO first_pin_review_approvals
                          (request_id,step,reviewer_id,reviewer_session_version)
                        VALUES (:request,2,:reviewer,0)
                        """).setParameter("request", request)
                        .setParameter("reviewer", second).executeUpdate()));
        assertEquals(0L, countVotes());
        login(first);
        ledger.review(request);
        assertThrows(RuntimeException.class, () -> tx(() ->
                manager.createNativeQuery("""
                        UPDATE first_pin_review_approvals SET reviewer_id=:replacement
                         WHERE request_id=:request
                        """).setParameter("replacement", third)
                        .setParameter("request", request).executeUpdate()));
        assertThrows(RuntimeException.class, () -> tx(() ->
                manager.createNativeQuery("""
                        DELETE FROM first_pin_review_requests WHERE id=:request
                        """).setParameter("request", request).executeUpdate()));
        assertEquals(1L, countVotes());
        assertEquals(1L, countVoteAudit());
    }

    @Test
    void unverifiedOrMisboundStepUpNeverWritesAnApprovalOrAudit() {
        login(first);
        syntheticVerifier.deny = true;
        assertThrows(AccessDeniedException.class, () -> ledger.review(request));
        assertEquals(0L, countVotes());
        assertEquals(0L, countVoteAudit());

        syntheticVerifier.deny = false;
        syntheticVerifier.override.set(new FirstPinReviewStepUpVerifier.VerifiedEvidence(
                UUID.randomUUID(), applicant, request, device, 0,
                FirstPinReviewStepUpVerifier.REVIEW_ACTION, Instant.now(), "webauthn"));
        assertThrows(AccessDeniedException.class, () -> ledger.review(request));

        syntheticVerifier.override.set(new FirstPinReviewStepUpVerifier.VerifiedEvidence(
                UUID.randomUUID(), first, UUID.randomUUID(), device, 0,
                FirstPinReviewStepUpVerifier.REVIEW_ACTION, Instant.now(), "webauthn"));
        assertThrows(AccessDeniedException.class, () -> ledger.review(request));

        syntheticVerifier.override.set(new FirstPinReviewStepUpVerifier.VerifiedEvidence(
                UUID.randomUUID(), first, request, UUID.randomUUID(), 0,
                FirstPinReviewStepUpVerifier.REVIEW_ACTION, Instant.now(), "webauthn"));
        assertThrows(AccessDeniedException.class, () -> ledger.review(request));

        syntheticVerifier.override.set(new FirstPinReviewStepUpVerifier.VerifiedEvidence(
                UUID.randomUUID(), first, request, device, 0,
                "first_pin.issue", Instant.now(), "webauthn"));
        assertThrows(AccessDeniedException.class, () -> ledger.review(request));

        syntheticVerifier.override.set(new FirstPinReviewStepUpVerifier.VerifiedEvidence(
                UUID.randomUUID(), first, request, device, 1,
                FirstPinReviewStepUpVerifier.REVIEW_ACTION, Instant.now(), "webauthn"));
        assertThrows(AccessDeniedException.class, () -> ledger.review(request));
        assertEquals(0L, countVotes());
        assertEquals(0L, countVoteAudit());
    }

    @Test
    void expiredFutureOrWrongMethodStepUpIsRejectedAndDoesNotConsumeVote() {
        login(first);
        for (var evidence : List.of(
                new FirstPinReviewStepUpVerifier.VerifiedEvidence(
                        UUID.randomUUID(), first, request, device, 0,
                        FirstPinReviewStepUpVerifier.REVIEW_ACTION,
                        Instant.now().minusSeconds(300), "webauthn"),
                new FirstPinReviewStepUpVerifier.VerifiedEvidence(
                        UUID.randomUUID(), first, request, device, 0,
                        FirstPinReviewStepUpVerifier.REVIEW_ACTION,
                        Instant.now().plusSeconds(90), "webauthn"),
                new FirstPinReviewStepUpVerifier.VerifiedEvidence(
                        UUID.randomUUID(), first, request, device, 0,
                        FirstPinReviewStepUpVerifier.REVIEW_ACTION,
                        Instant.now(), "password"))) {
            syntheticVerifier.override.set(evidence);
            assertThrows(AccessDeniedException.class, () -> ledger.review(request));
        }
        syntheticVerifier.reset();
        assertEquals(0L, countVotes());
        assertEquals(0L, countVoteAudit());
    }

    @Test
    void oneStepUpProofCannotBeReplayedByAnotherIndependentReviewer() {
        login(first);
        var proof = UUID.randomUUID();
        syntheticVerifier.override.set(new FirstPinReviewStepUpVerifier.VerifiedEvidence(
                proof, first, request, device, 0,
                FirstPinReviewStepUpVerifier.REVIEW_ACTION, Instant.now(), "webauthn"));
        ledger.review(request);
        login(second);
        syntheticVerifier.override.set(new FirstPinReviewStepUpVerifier.VerifiedEvidence(
                proof, second, request, device, 0,
                FirstPinReviewStepUpVerifier.REVIEW_ACTION, Instant.now(), "webauthn"));
        assertThrows(RuntimeException.class, () -> ledger.review(request));
        assertEquals(1L, countVotes());
        assertEquals(1L, countVoteAudit());
    }

    @Test
    void sqlCannotInsertEvidenceLessVoteEvenWithGroupPrivilege() {
        assertThrows(RuntimeException.class, () -> tx(() ->
                manager.createNativeQuery("""
                    INSERT INTO first_pin_review_approvals
                      (request_id,step,reviewer_id,reviewer_session_version)
                    VALUES (:request,1,:reviewer,0)
                    """).setParameter("request", request)
                    .setParameter("reviewer", first).executeUpdate()));
        assertEquals(0L, countVotes());
        assertEquals(0L, countVoteAudit());
    }

    // V17 synthetic-only verifier: test callback is NEVER built into
    // src/main, never an authenticated Passkey/WebAuthn cryptographic proof.
    private StagedFirstPinPasskeyChallengeLedger challengeLedger(
            FirstPinWebAuthnAssertionVerifier verifier) {
        return new StagedFirstPinPasskeyChallengeLedger(manager, verifier);
    }

    private StagedFirstPinPasskeyChallengeLedger.IssuedChallenge issueChallenge(
            StagedFirstPinPasskeyChallengeLedger staged) {
        return new TransactionTemplate(txManager)
                .execute(ignored -> staged.issue(request));
    }

    private void consumeChallenge(StagedFirstPinPasskeyChallengeLedger staged,
            StagedFirstPinPasskeyChallengeLedger.IssuedChallenge issued,
            String nonce, byte[] assertion) {
        tx(() -> staged.consumeAfterVerifiedAssertion(
                issued.id(), nonce, assertion));
    }

    private long challengeAuditCount(String action) {
        return ((Number) manager.createNativeQuery("""
                SELECT COUNT(*) FROM admin_access_log
                WHERE resource_id=:request AND action=:action
                """).setParameter("request", request).setParameter("action", action)
                .getSingleResult()).longValue();
    }

    @Test
    void challengeIsServerRandomHashedAndProductionVerifierCannotConsume() throws Exception {
        login(first);
        var production = challengeLedger(new DenyAllFirstPinWebAuthnAssertionVerifier());
        var issued = issueChallenge(production);
        assertEquals(43, issued.challengeBase64Url().length(),
                "32 random bytes encoded as canonical unpadded Base64url");
        byte[] raw = Base64.getUrlDecoder().decode(issued.challengeBase64Url());
        assertEquals(32, raw.length);
        byte[] digest = (byte[]) manager.createNativeQuery("""
                SELECT challenge_sha256 FROM admin_passkey_step_up_challenges
                WHERE id=:id
                """).setParameter("id", issued.id()).getSingleResult();
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(raw), digest);
        assertEquals(1L, challengeAuditCount("first_pin.step_up.challenge.issue"));

        assertThrows(AccessDeniedException.class, () ->
                consumeChallenge(production, issued, issued.challengeBase64Url(),
                        new byte[]{1, 2, 3}));
        assertEquals(0L, challengeAuditCount("first_pin.step_up.challenge.consume"));
        assertEquals(0L, countVotes());
        assertEquals(0L, countVoteAudit());
        assertEquals(1L, ((Number) manager.createNativeQuery("""
                SELECT COUNT(*) FROM admin_passkey_step_up_challenges
                WHERE id=:id AND consumed_at IS NULL
                """).setParameter("id", issued.id()).getSingleResult()).longValue());
    }

    @Test
    void syntheticVerifiedChallengeIsOneUseAndBoundToActorSessionRequestAndAction() {
        login(first);
        var staged = challengeLedger(bound -> {
            assertEquals(first, bound.actorId());
            assertEquals(request, bound.reviewRequestId());
            assertEquals(device, bound.deviceId());
            assertEquals(0L, bound.sessionVersion());
            assertEquals(FirstPinReviewStepUpVerifier.REVIEW_ACTION, bound.action());
        });
        var issued = issueChallenge(staged);
        login(second);
        assertThrows(AccessDeniedException.class, () ->
                consumeChallenge(staged, issued, issued.challengeBase64Url(), new byte[]{5}));
        login(first);
        assertThrows(AccessDeniedException.class, () ->
                consumeChallenge(staged, issued, "A".repeat(43), new byte[]{5}));
        assertThrows(AccessDeniedException.class, () ->
                consumeChallenge(staged, issued, issued.challengeBase64Url(), new byte[0]));
        assertEquals(0L, challengeAuditCount("first_pin.step_up.challenge.consume"));
        consumeChallenge(staged, issued, issued.challengeBase64Url(), new byte[]{5});
        assertEquals(1L, challengeAuditCount("first_pin.step_up.challenge.consume"));
        assertThrows(AccessDeniedException.class, () ->
                consumeChallenge(staged, issued, issued.challengeBase64Url(), new byte[]{5}));
        assertEquals(1L, challengeAuditCount("first_pin.step_up.challenge.consume"));
        assertEquals(0L, countVotes());
    }

    @Test
    void staleOrExpiredChallengeCannotBeSpentOrHistoryRewritten() {
        login(first);
        var staged = challengeLedger(bound -> {});
        var issued = issueChallenge(staged);
        tx(() -> manager.createNativeQuery("""
                UPDATE users SET session_version=session_version+1
                WHERE id=:actor
                """).setParameter("actor", first).executeUpdate());
        assertThrows(AccessDeniedException.class, () ->
                consumeChallenge(staged, issued, issued.challengeBase64Url(), new byte[]{8}));
        assertThrows(AccessDeniedException.class, () -> issueChallenge(staged));

        // The database rejects attempts to extend a challenge deadline,
        // rebind a challenge to a second device, or erase its audit history.
        assertThrows(RuntimeException.class, () -> tx(() ->
                manager.createNativeQuery("""
                    UPDATE admin_passkey_step_up_challenges
                       SET expires_at=clock_timestamp()+INTERVAL '3 minutes'
                     WHERE id=:id
                    """).setParameter("id", issued.id()).executeUpdate()));
        assertThrows(RuntimeException.class, () -> tx(() ->
                manager.createNativeQuery("""
                    UPDATE admin_passkey_step_up_challenges
                       SET device_id=:other WHERE id=:id
                    """).setParameter("other", UUID.randomUUID())
                .setParameter("id", issued.id()).executeUpdate()));
        assertThrows(RuntimeException.class, () -> tx(() ->
                manager.createNativeQuery("""
                    DELETE FROM admin_passkey_step_up_challenges WHERE id=:id
                    """).setParameter("id", issued.id()).executeUpdate()));
        assertEquals(0L, challengeAuditCount("first_pin.step_up.challenge.consume"));
        assertEquals(0L, countVotes());
    }

    @Test
    void expiredChallengeCannotBeConsumedEvenWithSyntheticVerifier() {
        login(first);
        var staged = challengeLedger(bound -> {});
        var issued = issueChallenge(staged);
        byte[] raw = Base64.getUrlDecoder().decode(issued.challengeBase64Url());
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(raw);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
        UUID old = new TransactionTemplate(txManager).execute(ignored ->
                (UUID) manager.createNativeQuery("""
                    INSERT INTO admin_passkey_step_up_challenges
                      (actor_id,actor_session_version,request_id,device_id,
                       action,challenge_sha256,created_at,expires_at)
                    VALUES (:actor,0,:request,:device,'first_pin.review',:digest,
                            clock_timestamp()-INTERVAL '10 minutes',
                            clock_timestamp()-INTERVAL '8 minutes')
                    RETURNING id
                    """).setParameter("actor", first).setParameter("request", request)
                    .setParameter("device", device)
                    .setParameter("digest", MessageDigest.isEqual(digest, raw)
                            ? raw : java.util.Arrays.copyOf(digest, digest.length))
                    .getSingleResult());
        // A distinct synthetic expired nonce is required because challenge
        // hashes are globally unique; use a new CSPRNG-derived value.
        assertTrue(old != null);
        assertThrows(AccessDeniedException.class, () -> tx(() ->
                staged.consumeAfterVerifiedAssertion(old,
                        issued.challengeBase64Url(), new byte[]{9})));
        assertEquals(0L, countVotes());
    }

    @Test
    void twoConcurrentSecondReviewsSerializeWithOnlyOneCommittedWinner() throws Exception {
        login(first);
        ledger.review(request);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> {
                login(second);
                start.await();
                try {
                    ledger.review(request);
                    return true;
                } catch (AccessDeniedException expected) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            var b = pool.submit(() -> {
                login(third);
                start.await();
                try {
                    ledger.review(request);
                    return true;
                } catch (AccessDeniedException expected) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
            start.countDown();
            assertEquals(1, (a.get(25, TimeUnit.SECONDS) ? 1 : 0)
                    + (b.get(25, TimeUnit.SECONDS) ? 1 : 0));
        }
        assertEquals(2L, countVotes());
        assertEquals(2L, countVoteAudit());
    }
}
