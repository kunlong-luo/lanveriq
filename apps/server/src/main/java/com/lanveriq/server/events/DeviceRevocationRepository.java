package com.lanveriq.server.events;

import java.util.UUID;

/** One authorized Spring transaction owns the identity revocation and audit. */
interface DeviceRevocationRepository {
    /**
     * @return true only for a first successful transition; false for an
     * authorized idempotent retry on an already-revoked device.
     */
    boolean revoke(UUID authenticatedAdminId, UUID deviceId);
}
