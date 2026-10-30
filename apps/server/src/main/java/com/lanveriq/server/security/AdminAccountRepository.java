package com.lanveriq.server.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data JPA only: no application JdbcTemplate/JdbcClient DAO. */
interface AdminAccountRepository extends JpaRepository<AdminAccount, UUID> {
    Optional<AdminAccount> findByNormalizedLogin(String normalizedLogin);
}
