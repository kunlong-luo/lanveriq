package com.lanveriq.server.events;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal-only device activation; not reachable from HTTP. Caddy mTLS
 * verification and server-side issuer/SPKI/leaf registration remain separate
 * prerequisites. No credential can be activated from a caller-provided header.
 */
@Service
public class DeviceActivationService {

    private final DeviceActivationRepository repository;
    private final Clock clock;

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
                || request.agentVersion() == null
                || !request.agentVersion().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")) {
            throw new DeviceActivationRejection(DeviceActivationRejection.Reason.INVALID_REQUEST);
        }
        repository.activate(verifiedPeer, pathDeviceId);
        return new DeviceActivationReceipt(1, pathDeviceId, "active",
                clock.instant(), UUID.randomUUID());
    }
}
