package com.lanveriq.server.events;

import java.time.Clock;
import java.util.Objects;
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

    @Transactional
    public DeviceHeartbeatReceipt ingest(VerifiedDeviceCertificate identity,
                                          UUID pathDeviceId, DeviceHeartbeat heartbeat) {
        var validated = policy.validate(identity, pathDeviceId, heartbeat);
        repository.record(identity, pathDeviceId, validated);
        // Echo only the request ID validated with the accepted body; do not
        // generate an unrelated UUID that would allow stale ACK confusion.
        return new DeviceHeartbeatReceipt(clock.instant(), heartbeat.requestId());
    }
}
