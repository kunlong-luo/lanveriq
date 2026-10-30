package com.lanveriq.server.security;

import java.util.List;

/**
 * V0.1 deliberately excludes audit exports and recording permissions.
 * Roles always require an assigned device group; sensitive NIC/IP/MAC reads
 * additionally require an explicit current internal_operational grant.
 */
public enum AdminAction {
    VIEW_HEALTH(List.of("owner_security_admin", "it_operator", "security_auditor",
            "recording_reviewer"), false),
    VIEW_NETWORK(List.of("owner_security_admin", "it_operator"), true),
    ISSUE_ENROLLMENT(List.of("owner_security_admin"), false),
    REVOKE_DEVICE(List.of("owner_security_admin"), false);

    private final List<String> allowedRoleCodes;
    private final boolean requiresInternalOperational;

    AdminAction(List<String> allowedRoleCodes, boolean requiresInternalOperational) {
        this.allowedRoleCodes = List.copyOf(allowedRoleCodes);
        this.requiresInternalOperational = requiresInternalOperational;
    }

    public List<String> allowedRoleCodes() {
        return allowedRoleCodes;
    }

    public boolean requiresInternalOperational() {
        return requiresInternalOperational;
    }
}
