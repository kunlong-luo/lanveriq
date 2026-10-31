package com.lanveriq.server.events;

import java.time.Instant;
import java.util.UUID;

/** All methods called inside the same @Transactional service invocation. */
interface DeviceInventoryRepository {
    DeviceInventoryReceipt persist(
            VerifiedDeviceCertificate verifiedPeer,
            UUID deviceId,
            DeviceInventorySnapshot snapshot,
            String digest,
            String capabilitiesJson,
            Instant observedAt);
}
