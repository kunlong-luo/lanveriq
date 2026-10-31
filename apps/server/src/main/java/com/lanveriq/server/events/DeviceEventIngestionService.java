package com.lanveriq.server.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Internal SRV-03 entry point. No HTTP endpoint, mTLS trust-proxy adapter or
 * client-cert activation exists yet. An ACK leaves the Spring proxy only
 * AFTER its PostgreSQL commit completes successfully.
 */
@Service
public class DeviceEventIngestionService {

    private final EventIngestionRepository repository;
    private final DeviceEventBatchPolicy policy;

    @Autowired
    DeviceEventIngestionService(EventIngestionRepository repository, JsonMapper mapper) {
        this(repository, new DeviceEventBatchPolicy(mapper, false));
    }

    DeviceEventIngestionService(EventIngestionRepository repository, DeviceEventBatchPolicy policy) {
        this.repository = Objects.requireNonNull(repository);
        this.policy = Objects.requireNonNull(policy);
    }

    @Transactional
    public DeviceBatchAcknowledgement ingest(VerifiedDeviceCertificate identity,
                                      UUID pathDeviceId, DeviceEventBatch batch) {
        var validated = policy.validate(pathDeviceId, identity, batch);
        repository.appendOrVerify(identity, validated);
        return new DeviceBatchAcknowledgement(1,
                validated.stream().map(DeviceEventBatchPolicy.CanonicalEvent::deviceSequence).toList(),
                Instant.now(), UUID.randomUUID());
    }
}
