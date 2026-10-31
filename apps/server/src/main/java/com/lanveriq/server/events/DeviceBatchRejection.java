package com.lanveriq.server.events;

/** Stable reason for a future strictly authenticated REST exception mapper. */
public final class DeviceBatchRejection extends RuntimeException {

    public enum Reason { INVALID_SCHEMA, REQUEST_TOO_LARGE, SEQUENCE_CONFLICT, REVOKED_DEVICE }

    private final Reason reason;

    public DeviceBatchRejection(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
