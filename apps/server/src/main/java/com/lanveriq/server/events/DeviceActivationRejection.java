package com.lanveriq.server.events;

/**
 * Internal-only rejection. Deliberately no 'unknown device' vs 'wrong cert'
 * distinction that could be exposed in a future HTTP error mapper.
 */
public final class DeviceActivationRejection extends RuntimeException {

    public enum Reason { INVALID_REQUEST, DENIED }

    private final Reason reason;

    DeviceActivationRejection(Reason reason) {
        super(reason.name());
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
