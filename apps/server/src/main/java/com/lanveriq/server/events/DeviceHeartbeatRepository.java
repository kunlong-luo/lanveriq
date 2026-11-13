package com.lanveriq.server.events;

import java.util.UUID;

/** Invoked only within the public heartbeat service's Spring transaction. */
interface DeviceHeartbeatRepository {
    void record(VerifiedDeviceCertificate identity, UUID deviceId,
                DeviceHeartbeatPolicy.ValidHeartbeat heartbeat);

    /** Under the same canonical device->credential transaction locks, read
     * only ledger entries bound to the exact submitting certificate. Legacy
     * unbound rows remain unresolved. A negative result must NEVER be
     * interpreted as proof that retrying a request is safe. */
    boolean wasAccepted(VerifiedDeviceCertificate identity, UUID deviceId, UUID requestId);
}
