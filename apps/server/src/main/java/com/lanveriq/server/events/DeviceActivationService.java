package com.lanveriq.server.events;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Device activation service. A strictly gated HTTP route is source-ready but
 * disabled by default; Caddy mTLS and independent certificate registration
 * are mandatory before any real activation. No body/header is device identity.
 */
@Service
public class DeviceActivationService {

    private final DeviceActivationRepository repository;
    private final Clock clock;

    @Autowired
    DeviceActivationService(DeviceActivationRepository repository) {
        this(repository, Clock.systemUTC());
    }

    DeviceActivationService(DeviceActivationRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public DeviceActivationReceipt activate(VerifiedDeviceCertificate verifiedPeer,
                                             UUID pathDeviceId,
                                             DeviceActivationRequest request) {
        if (verifiedPeer == null || pathDeviceId == null || request == null
                || !pathDeviceId.equals(verifiedPeer.deviceId())
                || !pathDeviceId.equals(request.deviceId())
                || pathDeviceId.equals(new UUID(0L, 0L))
                || request.schemaVersion() != 1
                || request.requestId() == null
                || request.requestId().equals(new UUID(0L, 0L))
                || request.agentVersion() == null
                || !request.agentVersion().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new DeviceActivationRejection(DeviceActivationRejection.Reason.INVALID_REQUEST);
        }
        repository.activate(verifiedPeer, pathDeviceId);
        return new DeviceActivationReceipt(1, pathDeviceId, "active",
                clock.instant(), request.requestId());
    }
}
