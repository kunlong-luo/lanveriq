package com.lanveriq.server.events;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Canonical current-credential epoch GET, only after three explicit gates.
 * In production no route is mounted; this is not signed issuance, credential
 * rotation, a heartbeat ACK or permission to clear quarantined requests.
 * Caddy-to-Tomcat pinned TLS identity + exact Caddy client assertion + JPA
 * active-credential locks are independently required before any response.
 */
@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = {
        "lanveriq.agent.ingress.assertions-enabled",
        "lanveriq.agent.heartbeat.http-enabled",
        "lanveriq.agent.credential-epoch.http-enabled"}, havingValue = "true")
final class AgentCredentialEpochRestController {
    private final AgentIngressAssertionPolicy ingress;
    private final DeviceHeartbeatService heartbeats;

    AgentCredentialEpochRestController(AgentIngressAssertionPolicy ingress,
                                      DeviceHeartbeatService heartbeats) {
        this.ingress = Objects.requireNonNull(ingress);
        this.heartbeats = Objects.requireNonNull(heartbeats);
    }

    @GetMapping(path = "/api/v1/devices/{deviceId}/heartbeats/credential-epoch",
            produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<DeviceCredentialEpochReceipt> inspect(
            HttpServletRequest request, @PathVariable UUID deviceId) {
        var identity = ingress.resolveCurrentCredentialEpoch(request, deviceId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(heartbeats.inspectCurrentCredentialEpoch(identity, deviceId));
    }

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<Void> deniedPeer(SecurityException ignored) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .cacheControl(CacheControl.noStore()).build();
    }

    @ExceptionHandler(DeviceHeartbeatRejection.class)
    ResponseEntity<Void> deniedCredential(DeviceHeartbeatRejection error) {
        var status = error.reason() == DeviceHeartbeatRejection.Reason.INVALID_SCHEMA
                ? HttpStatus.BAD_REQUEST : HttpStatus.FORBIDDEN;
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).build();
    }
}
