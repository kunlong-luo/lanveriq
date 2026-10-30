package com.lanveriq.server.security;

import java.nio.CharBuffer;
import java.util.Objects;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Explicit, local-only first-admin bootstrap. Both caller and role-group
 * membership are created transactionally. No HTTP entrypoint or default
 * credentials are provided. The caller owns and wipes its password char[].
 */
@Service
public final class FirstAdminBootstrapService {

    private final FirstAdminBootstrapRepository repository;
    private final PasswordEncoder encoder;

    FirstAdminBootstrapService(FirstAdminBootstrapRepository repository, PasswordEncoder encoder) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.encoder = Objects.requireNonNull(encoder, "encoder");
    }

    static String normalizeLogin(String requested) {
        if (requested == null || requested.length() > 64) {
            throw new IllegalArgumentException("Invalid administrator login");
        }
        String normalized = requested.toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{2,63}")) {
            throw new IllegalArgumentException("Invalid administrator login");
        }
        return normalized;
    }

    static String validateGroupName(String requested) {
        if (requested == null || requested.length() > 120 ||
                !requested.matches("[A-Za-z0-9][A-Za-z0-9 ._-]{2,119}") ||
                !requested.equals(requested.strip())) {
            throw new IllegalArgumentException("Invalid initial device-group name");
        }
        return requested;
    }

    static void validatePassword(char[] password) {
        if (password == null || password.length < 16 || password.length > 128) {
            throw new IllegalArgumentException("Administrator password length must be 16–128 characters");
        }
        for (char c : password) {
            if (Character.isISOControl(c) || Character.isSurrogate(c)) {
                throw new IllegalArgumentException("Administrator password contains unsupported characters");
            }
        }
    }

    @Transactional
    public FirstAdminBootstrapRepository.BootstrappedAdmin bootstrap(
            String login, String groupName, char[] password) {
        String canonicalLogin = normalizeLogin(login);
        String safeGroup = validateGroupName(groupName);
        validatePassword(password);
        // Spring Security's maintained Argon2id implementation. The password
        // is not passed to JPA or stored; only the versioned salted hash is.
        String hash = encoder.encode(CharBuffer.wrap(password));
        return repository.createFirstOwner(canonicalLogin, hash, safeGroup);
    }
}
