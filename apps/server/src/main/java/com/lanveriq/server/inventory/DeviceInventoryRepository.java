package com.lanveriq.server.inventory;

import com.lanveriq.server.events.VerifiedDeviceCertificate;
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
