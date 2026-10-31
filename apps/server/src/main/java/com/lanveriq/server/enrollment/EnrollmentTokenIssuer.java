package com.lanveriq.server.enrollment;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal SRV-02 issuance slice. No HTTP route exists until AUTH-01 binds
 * authenticated session identity to this call and applies CSRF/rate limits.
 */
@Service
public class EnrollmentTokenIssuer {

    private static final int MIN_LIFETIME_SECONDS = 60;
    private static final int MAX_LIFETIME_SECONDS = 15 * 60;

    private final EnrollmentTokenRepository repository;
    private final EnrollmentTokenCodec codec;

    @Autowired
    EnrollmentTokenIssuer(EnrollmentTokenRepository repository) {
        this(repository, new EnrollmentTokenCodec());
    }

    EnrollmentTokenIssuer(EnrollmentTokenRepository repository, EnrollmentTokenCodec codec) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    /**
     * Caller must obtain authenticatedAdminId from a verified server session,
     * never from an untrusted request body. SQL separately enforces group scope.
     */
    @Transactional
    public IssuedToken issue(
            UUID authenticatedAdminId,
            UUID deviceGroupId,
            long policyNoticeVersion,
            Duration lifetime) {
        Objects.requireNonNull(authenticatedAdminId, "authenticatedAdminId");
        Objects.requireNonNull(deviceGroupId, "deviceGroupId");
        Objects.requireNonNull(lifetime, "lifetime");
        if (policyNoticeVersion < 1 || lifetime.getNano() != 0 ||
                lifetime.getSeconds() < MIN_LIFETIME_SECONDS ||
                lifetime.getSeconds() > MAX_LIFETIME_SECONDS) {
            throw new IllegalArgumentException("Invalid notice version or token lifetime");
        }

        var secret = codec.generate();
        UUID tokenId = repository.insertForScopedAdmin(
                authenticatedAdminId, deviceGroupId, policyNoticeVersion,
                Math.toIntExact(lifetime.getSeconds()), secret.verifier());
        // Do not put token/secret in toString, access audit, DB rows or logs.
        return new IssuedToken(tokenId, secret.plaintext(), lifetime);
    }

    public static final class IssuedToken {
        private final UUID id;
        private final String plaintext;
        private final Duration lifetime;

        private IssuedToken(UUID id, String plaintext, Duration lifetime) {
            this.id = Objects.requireNonNull(id, "id");
            this.plaintext = plaintext;
            this.lifetime = lifetime;
        }

        public UUID id() {
            return id;
        }

        public String plaintext() {
            return plaintext;
        }

        public Duration lifetime() {
            return lifetime;
        }

        @Override
        public String toString() {
            return "IssuedToken[id=" + id + ", plaintext=REDACTED]";
        }
    }
}
