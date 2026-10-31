package com.lanveriq.server.enrollment;

import com.lanveriq.server.security.AdminPrincipal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Strictly opt-in internal-only registrar. It is not a certificate authority.
 * Only an authenticated server-side admin principal can review/register a
 * signed public leaf against an independently provisioned client CA.
 * No HTTP endpoint exists. Never accept the trust root from request JSON.
 */
@Service
@ConditionalOnProperty(prefix = "lanveriq.client-cert-registration", name = "enabled",
        havingValue = "true")
public class ReviewedDeviceCertificateRegistrationService {
    private final DeviceCertificateRegistrationRepository repository;
    private final ClientCertificateProfileValidator validator;

    ReviewedDeviceCertificateRegistrationService(
            DeviceCertificateRegistrationRepository repository,
            ClientCertificateProfileValidator validator) {
        this.repository = Objects.requireNonNull(repository);
        this.validator = Objects.requireNonNull(validator);
    }

    @Transactional
    public RegisteredCertificate register(UUID deviceId, String signedPublicChainPem) {
        if (deviceId == null || signedPublicChainPem == null) {
            throw new IllegalArgumentException("Device and signed public leaf required");
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AdminPrincipal principal)
                || !principal.isEnabled()) {
            throw new AccessDeniedException("Authenticated security administrator required");
        }
        UUID adminId = principal.userId();
        var pending = repository.lockPendingForScopedAdmin(adminId, deviceId);
        var verified = validator.validate(signedPublicChainPem, deviceId,
                pending.csrSpkiSha256());
        repository.registerReviewed(adminId, deviceId, verified);
        return new RegisteredCertificate(deviceId, verified.fingerprint(),
                verified.expiresAt());
    }

    public record RegisteredCertificate(UUID deviceId, String fingerprint,
                                        Instant expiresAt) {
    }
}
