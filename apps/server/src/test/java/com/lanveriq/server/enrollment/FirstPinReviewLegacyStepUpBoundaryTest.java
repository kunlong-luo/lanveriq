package com.lanveriq.server.enrollment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pre-V15 approval votes must not be grandfathered as MFA evidence. */
class FirstPinReviewLegacyStepUpBoundaryTest {
    @Test
    void oldV14FirstVoteWithoutStepUpEvidenceIsNotUsable() {
        UUID reviewer = UUID.randomUUID();
        assertFalse(FirstPinReviewLedgerService.hasStepUpEvidence(
                new Object[]{reviewer, 0L, null, null, null}));
        assertFalse(FirstPinReviewLedgerService.hasStepUpEvidence(
                new Object[]{reviewer, 0L}));
        assertFalse(FirstPinReviewLedgerService.hasStepUpEvidence(null));
    }

    @Test
    void missingOrWrongMethodOrZeroProofIsRejected() {
        UUID reviewer = UUID.randomUUID();
        var at = OffsetDateTime.now();
        assertFalse(FirstPinReviewLedgerService.hasStepUpEvidence(
                new Object[]{reviewer, 0L, null, at, "webauthn"}));
        assertFalse(FirstPinReviewLedgerService.hasStepUpEvidence(
                new Object[]{reviewer, 0L, new UUID(0, 0), at, "webauthn"}));
        assertFalse(FirstPinReviewLedgerService.hasStepUpEvidence(
                new Object[]{reviewer, 0L, UUID.randomUUID(), null, "webauthn"}));
        assertFalse(FirstPinReviewLedgerService.hasStepUpEvidence(
                new Object[]{reviewer, 0L, UUID.randomUUID(), at, "password"}));
    }

    @Test
    void newV15VoteWithCompletePersistedEvidenceMayBeInspected() {
        assertTrue(FirstPinReviewLedgerService.hasStepUpEvidence(
                new Object[]{UUID.randomUUID(), 0L, UUID.randomUUID(),
                        OffsetDateTime.now(), "webauthn"}));
        // This predicate only rejects legacy/malformed database history.
        // It does NOT verify a passkey challenge or authorize signing.
    }
}
