package com.lanveriq.server.enrollment;

import com.lanveriq.server.security.AdminAction;
import com.lanveriq.server.security.AdminPrincipal;
import com.lanveriq.server.security.AdminScopeService;
import java.util.Objects;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A11-A: INTERNAL, READ-ONLY authorization policy probe. Deliberately not a
 * Spring bean, controller, signer or certificate registration entrypoint.
 *
 * The Snapshot and State must eventually be loaded under a single locked,
 * audited, tenant-scoped database transaction. This dry run DOES NOT establish
 * their provenance, authenticated MFA, committed reviewer approvals or trusted
 * time. Even APPROVED_FOR_SIGNING is diagnostic ONLY and MUST NOT authorize a
 * cryptographic signature, enrollment, Agent first-pin, quarantine release or
 * certificate rotation. Production signing remains impossible here.
 */
final class FirstPinReviewDryRunGate {

    enum Decision {
        DENIED,
        PENDING_SECOND_REVIEW,
        APPROVED_FOR_SIGNING
    }

    record Snapshot(
            UUID tenantId, UUID deviceId, UUID groupId, UUID requestId,
            UUID applicantId, UUID firstReviewerId, UUID secondReviewerId,
            UUID credentialEpochId, String action, String csrSpkiSha256,
            String certDerSha256, long policyVersion, long generation) {
    }

    /** Simulated authoritative facts, not a request DTO or authorization token. */
    record State(
            UUID tenantId, UUID deviceId, UUID groupId,
            long policyVersion, long generation,
            boolean requestActive, boolean certificateActive,
            boolean reviewNotRevoked, boolean freshReauthentication,
            boolean mfaVerified, boolean firstReviewCommitted,
            boolean secondReviewCommitted) {
    }

    private final AdminScopeService scopes;

    FirstPinReviewDryRunGate(AdminScopeService scopes) {
        this.scopes = Objects.requireNonNull(scopes, "scopes");
    }

    Decision inspect(Snapshot request, State authoritative) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AdminPrincipal admin)
                || !admin.isEnabled() || isMissing(admin.userId())
                || request == null || authoritative == null
                || isMissing(request.tenantId()) || isMissing(request.groupId())
                || isMissing(request.deviceId()) || isMissing(request.requestId())
                || isMissing(request.applicantId()) || isMissing(request.credentialEpochId())
                || !"first_pin".equals(request.action())
                || !validSha256(request.csrSpkiSha256())
                || !validSha256(request.certDerSha256())
                || request.policyVersion() <= 0 || request.generation() <= 0
                || !request.tenantId().equals(authoritative.tenantId())
                || !request.deviceId().equals(authoritative.deviceId())
                || !request.groupId().equals(authoritative.groupId())
                || request.policyVersion() != authoritative.policyVersion()
                || request.generation() != authoritative.generation()
                || !authoritative.requestActive() || !authoritative.certificateActive()
                || !authoritative.reviewNotRevoked()
                || !authoritative.freshReauthentication() || !authoritative.mfaVerified()
                || admin.userId().equals(request.applicantId())) {
            return Decision.DENIED;
        }

        // Never trust a cached ROLE_ authority: recheck current DB scope for
        // the authenticated actor before consulting even the dry-run review.
        if (!scopes.allowed(admin.userId(), request.groupId(), AdminAction.REVIEW_FIRST_PIN)) {
            return Decision.DENIED;
        }

        UUID first = request.firstReviewerId();
        UUID second = request.secondReviewerId();
        if (first == null && !authoritative.firstReviewCommitted()
                && second == null && !authoritative.secondReviewCommitted()) {
            // No first approval is not a second-review queue position.
            return Decision.DENIED;
        }
        if (isMissing(first) || first.equals(request.applicantId())
                || !scopes.allowed(first, request.groupId(), AdminAction.REVIEW_FIRST_PIN)) {
            return Decision.DENIED;
        }
        if (!authoritative.firstReviewCommitted()) {
            // Any claimed second approval without an initial durable approval
            // is inconsistent and cannot become a pending/approved outcome.
            return authoritative.secondReviewCommitted() || second != null
                    ? Decision.DENIED : Decision.PENDING_SECOND_REVIEW;
        }
        if (second == null && !authoritative.secondReviewCommitted()) {
            return Decision.PENDING_SECOND_REVIEW;
        }
        if (isMissing(second) || !authoritative.secondReviewCommitted()
                || second.equals(first) || second.equals(request.applicantId())
                || !second.equals(admin.userId())) {
            return Decision.DENIED;
        }
        return Decision.APPROVED_FOR_SIGNING;
    }

    private static boolean isMissing(UUID value) {
        return value == null || value.equals(new UUID(0, 0));
    }

    private static boolean validSha256(String digest) {
        if (digest == null || digest.length() != 64) {
            return false;
        }
        for (int i = 0; i < 64; i++) {
            char c = digest.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))) {
                return false;
            }
        }
        return true;
    }
}
