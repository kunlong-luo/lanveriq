package com.lanveriq.server.enrollment;

import static com.lanveriq.server.enrollment.FirstPinReviewDryRunGate.Decision.APPROVED_FOR_SIGNING;
import static com.lanveriq.server.enrollment.FirstPinReviewDryRunGate.Decision.DENIED;
import static com.lanveriq.server.enrollment.FirstPinReviewDryRunGate.Decision.PENDING_SECOND_REVIEW;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lanveriq.server.enrollment.FirstPinReviewDryRunGate.Snapshot;
import com.lanveriq.server.enrollment.FirstPinReviewDryRunGate.State;
import com.lanveriq.server.security.AdminAction;
import com.lanveriq.server.security.AdminPrincipal;
import com.lanveriq.server.security.AdminScopeService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class FirstPinReviewDryRunGateTest {
    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID DEVICE = UUID.randomUUID();
    private static final UUID GROUP = UUID.randomUUID();
    private static final UUID APPLICANT = UUID.randomUUID();
    private static final UUID FIRST = UUID.randomUUID();
    private static final UUID SECOND = UUID.randomUUID();
    private static final UUID REQUEST = UUID.randomUUID();
    private static final UUID EPOCH = UUID.randomUUID();
    private static final String CSR = "a".repeat(64);
    private static final String LEAF = "b".repeat(64);

    private final AdminScopeService scopes = Mockito.mock(AdminScopeService.class);
    private final FirstPinReviewDryRunGate gate = new FirstPinReviewDryRunGate(scopes);

    @BeforeEach
    void authorizeSyntheticCurrentScopes() {
        when(scopes.allowed(SECOND, GROUP, AdminAction.REVIEW_FIRST_PIN)).thenReturn(true);
        when(scopes.allowed(FIRST, GROUP, AdminAction.REVIEW_FIRST_PIN)).thenReturn(true);
    }

    @AfterEach
    void clearSession() {
        SecurityContextHolder.clearContext();
    }

    private void login(UUID userId, boolean enabled) {
        AdminPrincipal p = Mockito.mock(AdminPrincipal.class);
        when(p.userId()).thenReturn(userId);
        when(p.isEnabled()).thenReturn(enabled);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(p, null, List.of()));
    }

    private Snapshot request(UUID tenant, UUID device, UUID group,
                             UUID applicant, UUID first, UUID second,
                             String action, String csr, long policy, long generation) {
        return new Snapshot(tenant, device, group, REQUEST, applicant, first, second,
                EPOCH, action, csr, LEAF, policy, generation);
    }

    private Snapshot complete() {
        return request(TENANT, DEVICE, GROUP, APPLICANT, FIRST, SECOND,
                "first_pin", CSR, 11, 7);
    }

    private State state(boolean firstCommitted, boolean secondCommitted) {
        return new State(TENANT, DEVICE, GROUP, 11, 7,
                true, true, true, true, true, firstCommitted, secondCommitted);
    }

    @Test
    void anonymousForgedStringAndDisabledPrincipalCannotReview() {
        assertEquals(DENIED, gate.inspect(complete(), state(true, true)));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "spoofed-admin-id", null, List.of()));
        assertEquals(DENIED, gate.inspect(complete(), state(true, true)));
        login(SECOND, false);
        assertEquals(DENIED, gate.inspect(complete(), state(true, true)));
        verify(scopes, never()).allowed(SECOND, GROUP, AdminAction.ISSUE_ENROLLMENT);
    }

    @Test
    void authorizedTwoDistinctAdministratorsOnlyProduceDiagnosticEligibility() {
        login(SECOND, true);
        assertEquals(APPROVED_FOR_SIGNING, gate.inspect(complete(), state(true, true)));
        verify(scopes).allowed(SECOND, GROUP, AdminAction.REVIEW_FIRST_PIN);
        verify(scopes).allowed(FIRST, GROUP, AdminAction.REVIEW_FIRST_PIN);
        // No signer, reviewer DB mutation, CA, network request or Agent call exists.
    }

    @Test
    void oneReviewerOrMissingDurableSecondReviewStaysPending() {
        login(SECOND, true);
        var noReview = request(TENANT, DEVICE, GROUP, APPLICANT, null, null,
                "first_pin", CSR, 11, 7);
        assertEquals(PENDING_SECOND_REVIEW, gate.inspect(noReview, state(false, false)));
        var firstOnly = request(TENANT, DEVICE, GROUP, APPLICANT, FIRST, null,
                "first_pin", CSR, 11, 7);
        assertEquals(PENDING_SECOND_REVIEW, gate.inspect(firstOnly, state(true, false)));
        assertEquals(DENIED, gate.inspect(complete(), state(true, false)));
        assertEquals(DENIED, gate.inspect(firstOnly, state(false, true)));
    }

    @Test
    void requesterSelfReviewAndReviewerDuplicationAreDenied() {
        login(SECOND, true);
        var selfFirst = request(TENANT, DEVICE, GROUP, FIRST, FIRST, SECOND,
                "first_pin", CSR, 11, 7);
        assertEquals(DENIED, gate.inspect(selfFirst, state(true, true)));
        var sameReviewer = request(TENANT, DEVICE, GROUP, APPLICANT, SECOND, SECOND,
                "first_pin", CSR, 11, 7);
        assertEquals(DENIED, gate.inspect(sameReviewer, state(true, true)));
        var requestBySecond = request(TENANT, DEVICE, GROUP, SECOND, FIRST, SECOND,
                "first_pin", CSR, 11, 7);
        assertEquals(DENIED, gate.inspect(requestBySecond, state(true, true)));
        var thirdReviewer = request(TENANT, DEVICE, GROUP, APPLICANT, FIRST, UUID.randomUUID(),
                "first_pin", CSR, 11, 7);
        assertEquals(DENIED, gate.inspect(thirdReviewer, state(true, true)));
    }

    @Test
    void groupTenantDeviceScopeAndPolicyGenerationMismatchAreDenied() {
        login(SECOND, true);
        for (var candidate : List.of(
                request(UUID.randomUUID(), DEVICE, GROUP, APPLICANT, FIRST, SECOND,
                        "first_pin", CSR, 11, 7),
                request(TENANT, UUID.randomUUID(), GROUP, APPLICANT, FIRST, SECOND,
                        "first_pin", CSR, 11, 7),
                request(TENANT, DEVICE, UUID.randomUUID(), APPLICANT, FIRST, SECOND,
                        "first_pin", CSR, 11, 7),
                request(TENANT, DEVICE, GROUP, APPLICANT, FIRST, SECOND,
                        "first_pin", CSR, 12, 7),
                request(TENANT, DEVICE, GROUP, APPLICANT, FIRST, SECOND,
                        "first_pin", CSR, 11, 8))) {
            assertEquals(DENIED, gate.inspect(candidate, state(true, true)));
        }
    }

    @Test
    void unsignedActionOrNonCanonicalDigestsDoNotReachReview() {
        login(SECOND, true);
        for (var candidate : List.of(
                request(TENANT, DEVICE, GROUP, APPLICANT, FIRST, SECOND,
                        "rotate", CSR, 11, 7),
                request(TENANT, DEVICE, GROUP, APPLICANT, FIRST, SECOND,
                        "first_pin", "A".repeat(64), 11, 7),
                request(TENANT, DEVICE, GROUP, APPLICANT, FIRST, SECOND,
                        "first_pin", "a".repeat(63), 11, 7))) {
            assertEquals(DENIED, gate.inspect(candidate, state(true, true)));
        }
        var leafInvalid = new Snapshot(TENANT, DEVICE, GROUP, REQUEST, APPLICANT,
                FIRST, SECOND, EPOCH, "first_pin", CSR, "not-a-digest", 11, 7);
        assertEquals(DENIED, gate.inspect(leafInvalid, state(true, true)));
        assertEquals(DENIED, gate.inspect(null, state(true, true)));
        assertEquals(DENIED, gate.inspect(complete(), null));
    }

    @Test
    void invalidSessionReauthenticationMfaRevocationAndDeviceStateFailClosed() {
        login(SECOND, true);
        var base = state(true, true);
        for (var evidence : List.of(
                new State(TENANT, DEVICE, GROUP, 11, 7,
                        false, true, true, true, true, true, true),
                new State(TENANT, DEVICE, GROUP, 11, 7,
                        true, false, true, true, true, true, true),
                new State(TENANT, DEVICE, GROUP, 11, 7,
                        true, true, false, true, true, true, true),
                new State(TENANT, DEVICE, GROUP, 11, 7,
                        true, true, true, false, true, true, true),
                new State(TENANT, DEVICE, GROUP, 11, 7,
                        true, true, true, true, false, true, true))) {
            assertEquals(DENIED, gate.inspect(complete(), evidence));
        }
        assertEquals(APPROVED_FOR_SIGNING, gate.inspect(complete(), base));
    }

    @Test
    void revokedCurrentScopeOfEitherReviewerRejectsEvenSignedLookingRequest() {
        login(SECOND, true);
        when(scopes.allowed(SECOND, GROUP, AdminAction.REVIEW_FIRST_PIN)).thenReturn(false);
        assertEquals(DENIED, gate.inspect(complete(), state(true, true)));

        when(scopes.allowed(SECOND, GROUP, AdminAction.REVIEW_FIRST_PIN)).thenReturn(true);
        when(scopes.allowed(FIRST, GROUP, AdminAction.REVIEW_FIRST_PIN)).thenReturn(false);
        assertEquals(DENIED, gate.inspect(complete(), state(true, true)));
    }
}
