package com.lanveriq.server.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;

class AdminScopeServiceTest {

    private final AdminScopeRepository repository = Mockito.mock(AdminScopeRepository.class);
    private final AdminScopeService scope = new AdminScopeService(repository);
    private final UUID admin = UUID.randomUUID();
    private final UUID group = UUID.randomUUID();

    @Test
    void roleMappingNeverGrantsSensitiveNetworkAccessImplicitly() {
        assertTrue(AdminAction.VIEW_NETWORK.requiresInternalOperational());
        assertFalse(AdminAction.VIEW_HEALTH.requiresInternalOperational());
        assertFalse(AdminAction.ISSUE_ENROLLMENT.requiresInternalOperational());
        assertFalse(AdminAction.VIEW_NETWORK.allowedRoleCodes().contains("security_auditor"));
        assertFalse(AdminAction.ISSUE_ENROLLMENT.allowedRoleCodes().contains("it_operator"));
    }

    @Test
    void permissionsAreCheckedFreshOnEveryCallNotCachedInSession() {
        when(repository.hasCurrentGrant(admin, group, AdminAction.VIEW_HEALTH))
                .thenReturn(true, false);
        assertTrue(scope.allowed(admin, group, AdminAction.VIEW_HEALTH));
        assertFalse(scope.allowed(admin, group, AdminAction.VIEW_HEALTH));
        verify(repository, Mockito.times(2)).hasCurrentGrant(admin, group, AdminAction.VIEW_HEALTH);
    }

    @Test
    void scopeMustDenyMissingOrRevokedGrant() {
        assertThrows(AccessDeniedException.class,
                () -> scope.require(admin, group, AdminAction.VIEW_NETWORK));
        when(repository.hasCurrentGrant(admin, group, AdminAction.ISSUE_ENROLLMENT))
                .thenReturn(true);
        scope.require(admin, group, AdminAction.ISSUE_ENROLLMENT);
    }
}
