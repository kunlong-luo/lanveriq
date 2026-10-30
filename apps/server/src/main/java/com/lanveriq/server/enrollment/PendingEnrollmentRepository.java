package com.lanveriq.server.enrollment;

import java.util.UUID;

/**
 * An atomic, PostgreSQL-backed one-use token + pending-device transition.
 * There is intentionally no public consume-token-only API.
 */
interface PendingEnrollmentRepository {
    PendingDevice consumeAndRegister(byte[] verifier, long policyNoticeVersion,
                                     CertificateSigningRequestValidator.ValidatedCsr csr);

    record PendingDevice(UUID deviceId, UUID deviceGroupId) {
    }
}
