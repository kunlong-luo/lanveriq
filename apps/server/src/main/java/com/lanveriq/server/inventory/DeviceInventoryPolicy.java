package com.lanveriq.server.inventory;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Strict V0.1 inventory bounds; no OS user/session, Wi-Fi location or serial. */
final class DeviceInventoryPolicy {
    private static final Instant EARLIEST = Instant.parse("2020-01-01T00:00:00Z");

    private DeviceInventoryPolicy() {
    }

    static Instant validate(DeviceInventorySnapshot body, UUID pathDeviceId, Instant now) {
        if (body == null || pathDeviceId == null
                || !pathDeviceId.equals(body.deviceId())
                || body.snapshotId() == null
                || body.snapshotId().equals(new UUID(0L, 0L))
                || body.schemaVersion() != 1
                || body.inventoryRevision() <= 0 || body.inventoryRevision() == Long.MAX_VALUE
                || body.hardware() == null || body.os() == null
                || body.interfaces() == null || body.volumes() == null
                || body.capabilities() == null
                || body.interfaces().size() > 32 || body.volumes().size() > 64) {
            throw invalid();
        }
        Instant observed;
        try {
            if (body.observedAt() == null || !body.observedAt().endsWith("Z")) {
                throw invalid();
            }
            observed = Instant.parse(body.observedAt());
        } catch (DateTimeParseException error) {
            throw invalid();
        }
        if (observed.isBefore(EARLIEST) || observed.isAfter(now.plusSeconds(300))) {
            throw invalid();
        }
        var hardware = body.hardware();
        bounded(hardware.manufacturer(), 256);
        bounded(hardware.model(), 256);
        if (hardware.cpu() != null) {
            bounded(hardware.cpu().model(), 256);
            bounded(hardware.cpu().arch(), 64);
            positive(hardware.cpu().physicalCores(), 1024);
            positive(hardware.cpu().logicalCores(), 2048);
            if (hardware.cpu().physicalCores() != null
                    && hardware.cpu().logicalCores() != null
                    && hardware.cpu().logicalCores() < hardware.cpu().physicalCores()) {
                throw invalid();
            }
        }
        if (hardware.memory() != null && hardware.memory().totalBytes() != null
                && hardware.memory().totalBytes() < 0) {
            throw invalid();
        }
        var os = body.os();
        if (!Set.of("windows", "linux").contains(os.family())) {
            throw invalid();
        }
        bounded(os.name(), 128);
        bounded(os.version(), 128);
        bounded(os.build(), 128);
        bounded(os.kernelRelease(), 128);
        bounded(os.arch(), 64);

        Set<String> interfaceKeys = new HashSet<>();
        for (var nic : body.interfaces()) {
            if (nic == null || !key(nic.key()) || !interfaceKeys.add(nic.key())
                    || !required(nic.name(), 128)
                    || nic.addresses() == null || nic.addresses().size() > 16) {
                throw invalid();
            }
            bounded(nic.kind(), 32);
            if (nic.linkState() != null
                    && !Set.of("up", "down", "unknown").contains(nic.linkState())) {
                throw invalid();
            }
            if (nic.macAddress() != null
                    && !nic.macAddress().matches("([0-9a-f]{2}:){5}[0-9a-f]{2}")) {
                throw invalid();
            }
            Set<String> addresses = new HashSet<>();
            for (var address : nic.addresses()) {
                if (address == null || !ipLiteral(address.ip(), address.family())
                        || address.prefixLength() == null
                        || address.prefixLength() < 0
                        || address.prefixLength() > ("ipv4".equals(address.family()) ? 32 : 128)
                        || address.scope() != null
                        && !Set.of("global", "private", "link_local", "loopback", "unknown")
                        .contains(address.scope())
                        || !addresses.add(address.ip() + "/" + address.prefixLength())) {
                    throw invalid();
                }
            }
        }

        Set<String> volumeKeys = new HashSet<>();
        for (var volume : body.volumes()) {
            if (volume == null || !key(volume.key())
                    || !volumeKeys.add(volume.key())
                    || !required(volume.mountPoint(), 256)
                    || volume.totalBytes() == null || volume.totalBytes() < 0
                    || volume.freeBytes() != null
                    && (volume.freeBytes() < 0 || volume.freeBytes() > volume.totalBytes())) {
                throw invalid();
            }
            bounded(volume.filesystem(), 64);
        }

        if (body.capabilities().size() > 16) {
            throw invalid();
        }
        for (Map.Entry<String, String> item : body.capabilities().entrySet()) {
            if (!key(item.getKey()) || !Set.of("available", "not_collected",
                    "unsupported", "unavailable", "permission_denied",
                    "transient_error").contains(item.getValue())) {
                throw invalid();
            }
        }
        return observed;
    }

    private static boolean ipLiteral(String ip, String family) {
        if (ip == null || ip.length() > 45 || !ip.matches("[0-9A-Fa-f:.]+")) {
            return false;
        }
        // Numeric shape only: never let InetAddress resolve arbitrary names.
        if ("ipv4".equals(family)) {
            if (!ip.matches("([0-9]{1,3}\\.){3}[0-9]{1,3}")) {
                return false;
            }
        } else if ("ipv6".equals(family)) {
            if (!ip.contains(":")) {
                return false;
            }
        } else {
            return false;
        }
        try {
            InetAddress parsed = InetAddress.getByName(ip);
            return "ipv4".equals(family)
                    ? parsed instanceof Inet4Address : parsed instanceof Inet6Address;
        } catch (java.net.UnknownHostException error) {
            return false;
        }
    }

    private static void positive(Integer value, int maximum) {
        if (value != null && (value <= 0 || value > maximum)) {
            throw invalid();
        }
    }

    private static boolean key(String text) {
        return text != null && text.matches("[A-Za-z0-9._:-]{1,128}");
    }

    private static boolean required(String text, int maximum) {
        return text != null && !text.isBlank() && validText(text, maximum);
    }

    private static void bounded(String text, int maximum) {
        if (text != null && !validText(text, maximum)) {
            throw invalid();
        }
    }

    private static boolean validText(String text, int maximum) {
        return text.length() <= maximum
                && text.codePoints().noneMatch(Character::isISOControl);
    }

    private static DeviceInventoryRejection invalid() {
        return new DeviceInventoryRejection(
                DeviceInventoryRejection.Reason.INVALID_REQUEST);
    }
}
