package com.lanveriq.server.enrollment;

import java.util.UUID;

/** One Spring transaction owns review, certificate registration and audit. */
interface DeviceCertificateRegistrationRepository {
    PendingReview lockPendingForScopedAdmin(UUID adminId, UUID deviceId);

    void registerReviewed(UUID adminId, UUID deviceId,
                          ClientCertificateProfileValidator.VerifiedLeaf signedLeaf);

    record PendingReview(UUID deviceId, UUID groupId, String csrSpkiSha256) {
    }
}
