package com.lanveriq.server.inventory;

/** Never return raw SQL, hostname or network-address detail to a caller. */
public final class DeviceInventoryRejection extends RuntimeException {
    public enum Reason { INVALID_REQUEST, REQUEST_TOO_LARGE, REVOKED_DEVICE, REVISION_CONFLICT }

    private final Reason reason;

    DeviceInventoryRejection(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
