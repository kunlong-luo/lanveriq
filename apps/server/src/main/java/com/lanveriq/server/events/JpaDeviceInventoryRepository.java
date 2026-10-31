package com.lanveriq.server.events;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Exactly one latest snapshot with complete NIC/IP/volume child replacement.
 * Locks device before reading current revision, so concurrent late snapshots
 * cannot regress state. One service transaction covers all SQL statements.
 *
 * JPA native SQL stays under Hibernate/Spring managed persistence. No JDBC.
 */
@Repository
final class JpaDeviceInventoryRepository implements DeviceInventoryRepository {

    private final EntityManager manager;

    JpaDeviceInventoryRepository(EntityManager manager) {
        this.manager = Objects.requireNonNull(manager);
    }

    @Override
    public DeviceInventoryReceipt persist(VerifiedDeviceCertificate peer, UUID deviceId,
                                          DeviceInventorySnapshot snapshot, String digest,
                                          String capabilitiesJson, Instant observedAt) {
        List<?> verified = manager.createNativeQuery("""
                SELECT d.id
                FROM devices AS d
                JOIN device_credentials AS c ON c.device_id=d.id
                WHERE d.id=:device AND d.enrollment_state='active'
                  AND d.revoked_at IS NULL
                  AND c.client_cert_sha256_fingerprint=:fingerprint
                  AND c.status='active' AND c.revoked_at IS NULL
                  AND c.issued_at<=clock_timestamp()
                  AND c.expires_at>clock_timestamp()
                FOR UPDATE OF d, c
                """)
                .setParameter("device", deviceId)
                .setParameter("fingerprint", peer.sha256Fingerprint())
                .getResultList();
        if (verified.size() != 1) {
            throw rejection(DeviceInventoryRejection.Reason.REVOKED_DEVICE);
        }

        List<?> existing = manager.createNativeQuery("""
                SELECT inventory_revision,snapshot_id,content_digest
                FROM device_inventory_current WHERE device_id=:device
                """)
                .setParameter("device", deviceId)
                .getResultList();
        if (existing.size() > 1) {
            throw rejection(DeviceInventoryRejection.Reason.REVISION_CONFLICT);
        }
        if (!existing.isEmpty()) {
            Object[] previous = (Object[]) existing.getFirst();
            long storedRevision = ((Number) previous[0]).longValue();
            UUID storedId = (UUID) previous[1];
            String storedDigest = ((String) previous[2]).strip();
            if (snapshot.inventoryRevision() < storedRevision) {
                return new DeviceInventoryReceipt(1, deviceId, storedId,
                        storedRevision, DeviceInventoryReceipt.Outcome.stale_ignored, Instant.now());
            }
            if (snapshot.inventoryRevision() == storedRevision) {
                if (!storedId.equals(snapshot.snapshotId()) || !storedDigest.equals(digest)) {
                    throw rejection(DeviceInventoryRejection.Reason.REVISION_CONFLICT);
                }
                return new DeviceInventoryReceipt(1, deviceId, storedId,
                        storedRevision, DeviceInventoryReceipt.Outcome.duplicate, Instant.now());
            }
            // This is a *current state*, not an append-only event. Cascading
            // child replacement inside this Spring transaction is reversible
            // if any subsequent insert/constraint fails. Never modify events.
            manager.createNativeQuery("""
                    DELETE FROM device_inventory_current WHERE device_id=:device
                    """).setParameter("device", deviceId).executeUpdate();
        }

        var hw = snapshot.hardware();
        var cpu = hw.cpu();
        var os = snapshot.os();
        var memory = hw.memory();
        var receiptRows = manager.createNativeQuery("""
                INSERT INTO device_inventory_current(
                    device_id,schema_version,snapshot_id,inventory_revision,
                    content_digest,observed_at,received_at,manufacturer,model,
                    cpu_model,cpu_arch,cpu_physical_cores,cpu_logical_cores,
                    memory_total_bytes,os_family,os_name,os_version,os_build,
                    os_kernel_release,os_arch,capabilities_json)
                VALUES (
                    :device,1,:snapshot_id,:revision,:digest,
                    CAST(:observed_at AS timestamptz),clock_timestamp(),
                    CAST(:manufacturer AS varchar),CAST(:model AS varchar),
                    CAST(:cpu_model AS varchar),CAST(:cpu_arch AS varchar),
                    CAST(:cpu_physical AS integer),CAST(:cpu_logical AS integer),
                    CAST(:memory_total AS bigint),:os_family,
                    CAST(:os_name AS varchar),CAST(:os_version AS varchar),
                    CAST(:os_build AS varchar),CAST(:os_kernel AS varchar),
                    CAST(:os_arch AS varchar),CAST(:capabilities AS jsonb))
                RETURNING received_at
                """)
                .setParameter("device", deviceId)
                .setParameter("snapshot_id", snapshot.snapshotId())
                .setParameter("revision", snapshot.inventoryRevision())
                .setParameter("digest", digest)
                .setParameter("observed_at", observedAt.toString())
                .setParameter("manufacturer", hw.manufacturer())
                .setParameter("model", hw.model())
                .setParameter("cpu_model", cpu == null ? null : cpu.model())
                .setParameter("cpu_arch", cpu == null ? null : cpu.arch())
                .setParameter("cpu_physical", cpu == null ? null : cpu.physicalCores())
                .setParameter("cpu_logical", cpu == null ? null : cpu.logicalCores())
                .setParameter("memory_total", memory == null ? null : memory.totalBytes())
                .setParameter("os_family", os.family())
                .setParameter("os_name", os.name())
                .setParameter("os_version", os.version())
                .setParameter("os_build", os.build())
                .setParameter("os_kernel", os.kernelRelease())
                .setParameter("os_arch", os.arch())
                .setParameter("capabilities", capabilitiesJson)
                .getResultList();
        if (receiptRows.size() != 1) {
            throw rejection(DeviceInventoryRejection.Reason.REVISION_CONFLICT);
        }

        for (var nic : snapshot.interfaces()) {
            manager.createNativeQuery("""
                    INSERT INTO device_inventory_interfaces(
                        device_id,interface_key,interface_name,kind,mac_address,
                        link_state,is_virtual)
                    VALUES (:device,:key,:name,CAST(:kind AS varchar),
                        CAST(:mac AS varchar),CAST(:state AS varchar),
                        CAST(:virtual AS boolean))
                    """)
                    .setParameter("device", deviceId)
                    .setParameter("key", nic.key())
                    .setParameter("name", nic.name())
                    .setParameter("kind", nic.kind())
                    .setParameter("mac", nic.macAddress())
                    .setParameter("state", nic.linkState())
                    .setParameter("virtual", nic.isVirtual())
                    .executeUpdate();
            for (var address : nic.addresses()) {
                manager.createNativeQuery("""
                        INSERT INTO device_inventory_addresses(
                            device_id,interface_key,ip,prefix_length,family,scope)
                        VALUES (:device,:interface_key,CAST(:ip AS inet),
                            :prefix,:family,CAST(:scope AS varchar))
                        """)
                        .setParameter("device", deviceId)
                        .setParameter("interface_key", nic.key())
                        .setParameter("ip", address.ip())
                        .setParameter("prefix", address.prefixLength())
                        .setParameter("family", address.family())
                        .setParameter("scope", address.scope())
                        .executeUpdate();
            }
        }
        for (var volume : snapshot.volumes()) {
            manager.createNativeQuery("""
                    INSERT INTO device_inventory_volumes(
                        device_id,volume_key,mount_point,filesystem,total_bytes,
                        free_bytes,is_system)
                    VALUES (:device,:key,:mount,CAST(:fs AS varchar),:total,
                        CAST(:free AS bigint),CAST(:system AS boolean))
                    """)
                    .setParameter("device", deviceId)
                    .setParameter("key", volume.key())
                    .setParameter("mount", volume.mountPoint())
                    .setParameter("fs", volume.filesystem())
                    .setParameter("total", volume.totalBytes())
                    .setParameter("free", volume.freeBytes())
                    .setParameter("system", volume.isSystem())
                    .executeUpdate();
        }
        // Storage timestamp is created by PostgreSQL, not by the endpoint.
        // The app-clock response time is advisory; ordering uses revision only.
        return new DeviceInventoryReceipt(1, deviceId, snapshot.snapshotId(),
                snapshot.inventoryRevision(), DeviceInventoryReceipt.Outcome.applied, Instant.now());
    }

    private static DeviceInventoryRejection rejection(DeviceInventoryRejection.Reason reason) {
        return new DeviceInventoryRejection(reason);
    }
}
