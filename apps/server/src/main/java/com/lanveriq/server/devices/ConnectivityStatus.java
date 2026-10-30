package com.lanveriq.server.devices;

/** Connectivity is based on authenticated, server-received heartbeats only. */
public enum ConnectivityStatus {
    ONLINE,
    STALE,
    OFFLINE,
    REVOKED
}
