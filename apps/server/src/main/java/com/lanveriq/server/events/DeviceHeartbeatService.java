package com.lanveriq.server.events;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * V0.1 heartbeat service. HTTP ingress exists but remains default-OFF and
 * requires reviewed two-hop mTLS; no Agent runtime upload is enabled.
 */
@Service
public class DeviceHeartbeatService {

    private final DeviceHeartbeatRepository repository;
    private final DeviceHeartbeatPolicy policy;
    private final Clock clock;

    @Autowired
    DeviceHeartbeatService(DeviceHeartbeatRepository repository, JsonMapper mapper) {
        this(repository, new DeviceHeartbeatPolicy(mapper, Clock.systemUTC()), Clock.systemUTC());
    }

    DeviceHeartbeatService(DeviceHeartbeatRepository repository,
                           DeviceHeartbeatPolicy policy, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.policy = Objects.requireNonNull(policy);
        this.clock = Objects.requireNonNull(clock);
    }

    /** Only a current, authenticated client certificate may query the durable
     * accepted UUID ledger. An absent row is UNKNOWN, not an invitation to
     * reuse or replace an ambiguously delivered heartbeat. */
    @Transactional
    public Optional<DeviceHeartbeatLookupReceipt> lookupAccepted(
            VerifiedDeviceCertificate identity, UUID deviceId, UUID requestId) {
        if (requestId == null || requestId.equals(new UUID(0, 0))
                || deviceId == null || identity == null
                || !deviceId.equals(identity.deviceId())) {
            throw new DeviceHeartbeatRejection(DeviceHeartbeatRejection.Reason.INVALID_SCHEMA);
        }
        if (!repository.wasAccepted(identity, deviceId, requestId)) {
            return Optional.empty();
        }
        return Optional.of(new DeviceHeartbeatLookupReceipt(1, deviceId,
                requestId, true, clock.instant()));
    }


    /** Internal-only read. Do NOT expose as HTTP until a separately reviewed
     * exact-route mTLS ingress and negative L2 checks are approved. A valid
     * current epoch does not retroactively authenticate an old pending UUID.
     */
    @Transactional
    public DeviceCredentialEpochReceipt inspectCurrentCredentialEpoch(
            VerifiedDeviceCertificate identity, UUID deviceId) {
        if (identity == null || deviceId == null || !deviceId.equals(identity.deviceId())
                || deviceId.equals(new UUID(0, 0))) {
            throw new DeviceHeartbeatRejection(DeviceHeartbeatRejection.Reason.INVALID_SCHEMA);
        }
        UUID serverEpoch = repository.currentCredentialEpoch(identity, deviceId);
        return new DeviceCredentialEpochReceipt(1, deviceId, serverEpoch,
                identity.sha256Fingerprint(), clock.instant());
    }

    @Transactional
    public DeviceHeartbeatReceipt ingest(VerifiedDeviceCertificate identity,
                                          UUID pathDeviceId, DeviceHeartbeat heartbeat) {
        var validated = policy.validate(identity, pathDeviceId, heartbeat);
        repository.record(identity, pathDeviceId, validated);
        // Echo only the request ID validated with the accepted body; do not
        // generate an unrelated UUID that would allow stale ACK confusion.
        return new DeviceHeartbeatReceipt(clock.instant(), validated.requestId());
    }
}
