package com.lanveriq.server.events;

import java.util.UUID;

/** Invoked inside one Spring transaction; identity is never from request JSON. */
interface DeviceActivationRepository {

    void activate(VerifiedDeviceCertificate verifiedPeer, UUID deviceId);
}
