package com.lanveriq.server.enrollment;

import java.util.UUID;

/**
 * Deliberately no standalone consume() operation: pairing must consume the
 * verifier AND persist its validated pending CSR/device in one transaction.
 */
interface EnrollmentTokenRepository {
    UUID insertForScopedAdmin(
            UUID authenticatedAdminId,
            UUID deviceGroupId,
            long policyNoticeVersion,
            int lifetimeSeconds,
            byte[] tokenVerifier);
}
