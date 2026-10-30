package com.lanveriq.server.security;

import java.util.Locale;
import java.util.Objects;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security DaoAuthenticationProvider fetches hashed credentials through
 * Spring Data JPA. No default user, hardcoded password or username-based scope.
 */
@Service
public final class AdminIdentityUserDetailsService implements UserDetailsService {

    private final AdminAccountRepository repository;

    AdminIdentityUserDetailsService(AdminAccountRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.length() > 190) {
            throw missing();
        }
        String normalized = username.toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9][a-z0-9._-]{2,63}")) {
            throw missing();
        }
        var account = repository.findByNormalizedLogin(normalized).orElseThrow(
                AdminIdentityUserDetailsService::missing);
        return new AdminPrincipal(account.getId(), account.getNormalizedLogin(),
                account.getPasswordHash(), account.isEnabled());
    }

    private static UsernameNotFoundException missing() {
        return new UsernameNotFoundException("Invalid administrator credentials");
    }
}
