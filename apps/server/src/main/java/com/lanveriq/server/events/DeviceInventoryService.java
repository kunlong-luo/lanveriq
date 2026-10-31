package com.lanveriq.server.events;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * INV-02: internal-only latest inventory. Not connected to HTTP, Caddy or
 * Agent runtime before DB/mTLS and organizational gates are accepted.
 */
@Service
public class DeviceInventoryService {

    private static final int MAX_REQUEST_BYTES = 128 * 1024;
    private final DeviceInventoryRepository repository;
    private final JsonMapper mapper;
    private final Clock clock;

    @Autowired
    DeviceInventoryService(DeviceInventoryRepository repository, JsonMapper mapper) {
        this(repository, mapper, Clock.systemUTC());
    }

    DeviceInventoryService(DeviceInventoryRepository repository, JsonMapper mapper, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.mapper = Objects.requireNonNull(mapper);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public DeviceInventoryReceipt ingest(VerifiedDeviceCertificate verifiedPeer,
                                         UUID pathDeviceId, DeviceInventorySnapshot snapshot) {
        if (verifiedPeer == null || pathDeviceId == null
                || !verifiedPeer.deviceId().equals(pathDeviceId)) {
            throw new DeviceInventoryRejection(DeviceInventoryRejection.Reason.INVALID_REQUEST);
        }
        var observed = DeviceInventoryPolicy.validate(snapshot, pathDeviceId, clock.instant());
        byte[] serialized = mapper.writeValueAsBytes(snapshot);
        if (serialized.length > MAX_REQUEST_BYTES) {
            throw new DeviceInventoryRejection(DeviceInventoryRejection.Reason.REQUEST_TOO_LARGE);
        }
        final String digest;
        try {
            digest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(serialized));
        } catch (NoSuchAlgorithmException impossibleOnJava25) {
            throw new IllegalStateException("Java SHA-256 unavailable", impossibleOnJava25);
        }
        return repository.persist(verifiedPeer, pathDeviceId, snapshot, digest,
                mapper.writeValueAsString(snapshot.capabilities()), observed);
    }
}
