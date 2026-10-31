package com.lanveriq.server.events;

import java.util.UUID;

/** Invoked only within the public heartbeat service's Spring transaction. */
interface DeviceHeartbeatRepository {
    void record(VerifiedDeviceCertificate identity, UUID deviceId,
                DeviceHeartbeatPolicy.ValidHeartbeat heartbeat);
}
