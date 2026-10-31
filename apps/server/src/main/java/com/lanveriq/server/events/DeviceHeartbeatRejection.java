package com.lanveriq.server.events;

/** Internal fail-closed heartbeat response reason; not an HTTP error mapping. */
public final class DeviceHeartbeatRejection extends RuntimeException {

    public enum Reason { INVALID_SCHEMA, REQUEST_TOO_LARGE, REVOKED_DEVICE }

    private final Reason reason;

    DeviceHeartbeatRejection(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
